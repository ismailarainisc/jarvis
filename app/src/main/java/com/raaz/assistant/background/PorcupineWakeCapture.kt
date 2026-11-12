package com.raaz.assistant.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.Porcupine
import com.raaz.assistant.features.FeatureStore
import com.raaz.assistant.gemini.AudioOwnership
import kotlinx.coroutines.*
import java.util.UUID

/** Low-level Porcupine avoids shared third-party recorder ownership and raw error logging. */
class PorcupineWakeCapture(private val context:Context,private val authorized:()->Boolean):WakeCapture {
 private val owner="wake-${UUID.randomUUID()}";@Volatile private var stopped=false
 private var detector:Porcupine?=null;private var recorder:AudioRecord?=null;private var thread:Thread?=null;private var owns=false
 private fun allowed()=authorized()&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
 override suspend fun start(onWake:()->Unit,onFailure:()->Unit)=withContext(Dispatchers.IO){
  check(!stopped&&allowed());check(AudioOwnership.process.acquire(owner)){"Another microphone session is active."};owns=true
  val store=FeatureStore(context)
  try{
   detector=Porcupine.Builder().setAccessKey(store.secret("porcupine")).setKeyword(Porcupine.BuiltInKeyword.JARVIS).build(context)
   val model=detector!!;ensureActive();check(allowed()&&!stopped)
   val minimum=AudioRecord.getMinBufferSize(model.sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(minimum>0)
   recorder=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(AudioFormat.Builder().setSampleRate(model.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(maxOf(minimum*2,model.frameLength*4)).build()
   ensureActive();check(recorder!!.state==AudioRecord.STATE_INITIALIZED&&allowed()&&!stopped);recorder!!.startRecording();check(recorder!!.recordingState==AudioRecord.RECORDSTATE_RECORDING)
   thread=Thread({try{
    val frame=ShortArray(model.frameLength);var offset=0;var last=android.os.SystemClock.elapsedRealtime()
    while(!stopped){
     if(!allowed()||(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode!=AudioManager.MODE_NORMAL)error("Wake capture unavailable")
     if(Build.VERSION.SDK_INT>=29&&recorder?.activeRecordingConfiguration?.isClientSilenced==true)error("Microphone silenced")
     val read=recorder?.read(frame,offset,frame.size-offset,AudioRecord.READ_BLOCKING)?:break
     if(stopped)break
     if(read<0||read>frame.size-offset)error("Invalid capture")
     if(read==0){if(android.os.SystemClock.elapsedRealtime()-last>3000)error("Capture stalled");Thread.sleep(5);continue}
     last=android.os.SystemClock.elapsedRealtime();offset+=read
     if(offset==frame.size){offset=0;if(model.process(frame)>=0){stopped=true;onWake();break}}
    }
   }catch(e:Exception){if(!stopped){stopped=true;onFailure()}}},"jarvis-local-wake").also{it.start()}
  }catch(e:SecurityException){throw IllegalStateException("Microphone permission was revoked during wake initialization.")}finally{store.close()}
 }
 override fun requestStop(){stopped=true}
 override suspend fun close():Boolean=withContext(NonCancellable+Dispatchers.IO){
  stopped=true;var clean=true
  fun attempt(block:()->Unit){try{block()}catch(e:Exception){clean=false}}
  attempt{if(recorder?.recordingState==AudioRecord.RECORDSTATE_RECORDING)recorder?.stop()}
  attempt{thread?.interrupt();thread?.join(1000);if(thread?.isAlive==true)clean=false}
  if(thread?.isAlive==true){if(owns)AudioOwnership.process.quarantine(owner);return@withContext false}
  attempt{recorder?.release();recorder=null};attempt{detector?.delete();detector=null}
  if(owns){if(clean)AudioOwnership.process.release(owner)else AudioOwnership.process.quarantine(owner);owns=false};clean
 }
}
