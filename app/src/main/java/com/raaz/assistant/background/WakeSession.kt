package com.raaz.assistant.background

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WakeCapture {suspend fun start(onWake:()->Unit,onFailure:()->Unit);fun requestStop();suspend fun close():Boolean}
fun interface WakeFactory {fun create():WakeCapture}
interface BackgroundVoice {fun start();suspend fun stop()}
enum class WakePhase { STOPPED, ARMING, ARMED, HANDOFF, CONVERSATION, PAUSING, PAUSED, ERROR }
data class WakeState(val phase:WakePhase=WakePhase.STOPPED,val message:String="Stopped")
/** Serializes the actual service's recorder-to-Gemini handoff; late detector callbacks never reopen input. */
class WakeSession(private val scope:CoroutineScope,private val factory:WakeFactory,private val voice:BackgroundVoice,private val allowed:()->Boolean) {
 private val lock=Mutex();private var epoch=0L;private var capture:WakeCapture?=null;private var faulted=false
 private val mutable=MutableStateFlow(WakeState());val state=mutable.asStateFlow()
 fun arm(){val token=++epoch;capture?.requestStop();scope.launch{lock.withLock{
  if(token!=epoch)return@withLock
  if(!closeCapture()||faulted){fail();return@withLock};voice.stop()
  if(token!=epoch)return@withLock
  if(!allowed()){mutable.value=WakeState(WakePhase.ERROR,"Open the app, grant permissions and explicitly enable background mode.");return@withLock}
  mutable.value=WakeState(WakePhase.ARMING,"Initializing local wake detector")
  try{val next=factory.create();capture=next;next.start({scope.launch{wake(token)}},{scope.launch{if(token==epoch)pause(error=true)}})
   if(token!=epoch||!allowed()){closeCapture();return@withLock}
   mutable.value=WakeState(WakePhase.ARMED,"Listening locally for Jarvis; ambient audio is not sent to Gemini")
  }catch(e:CancellationException){withContext(NonCancellable){closeCapture()};throw e}catch(e:Exception){closeCapture();mutable.value=WakeState(WakePhase.ERROR,"Wake detector failed. Check its access key, microphone, license and device audio. Resume manually.")}
 }}}
 private suspend fun wake(token:Long)=lock.withLock{
  if(token!=epoch||mutable.value.phase!=WakePhase.ARMED)return@withLock
  mutable.value=WakeState(WakePhase.HANDOFF,"Releasing wake microphone before Gemini capture")
  if(!closeCapture()){fail();return@withLock}
  if(token!=epoch||!allowed()){mutable.value=WakeState(WakePhase.PAUSED,"Paused before conversation");return@withLock}
  voice.start();mutable.value=WakeState(WakePhase.CONVERSATION,"Wake detected; Gemini session requested. Read live connection status below.")
 }
 fun pause(error:Boolean=false){++epoch;capture?.requestStop();mutable.value=WakeState(WakePhase.PAUSING,"Stopping microphone and conversation");scope.launch{lock.withLock{val clean=closeCapture();voice.stop();if(!clean||faulted)fail()else mutable.value=WakeState(if(error)WakePhase.ERROR else WakePhase.PAUSED,if(error)"Audio was interrupted. Check calls, microphone and permissions; resume manually." else "Paused; no local wake capture or conversation")}}}
 suspend fun stop(){++epoch;capture?.requestStop();lock.withLock{val clean=closeCapture();voice.stop();if(!clean||faulted)fail()else mutable.value=WakeState(WakePhase.STOPPED,"Stopped")}}
 private suspend fun closeCapture():Boolean {val old=capture;capture=null;val clean=try{old?.close()!=false}catch(e:Exception){false};if(!clean)faulted=true;return clean}
 private fun fail(){faulted=true;mutable.value=WakeState(WakePhase.ERROR,"Microphone cleanup failed. Restart the application before recording again.")}
}
