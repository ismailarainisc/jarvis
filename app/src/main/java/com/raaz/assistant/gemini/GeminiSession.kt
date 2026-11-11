package com.raaz.assistant.gemini

import com.raaz.assistant.live.AudioActivityDetector
import com.raaz.assistant.live.AudioLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual production coordinator. All state/commands are confined to the supplied UI dispatcher. */
class GeminiSession(
 private val scope:CoroutineScope,
 private val connections:GeminiConnectionFactory,
 private val audioFactory:GeminiAudioFactory,
 private val platforms:GeminiPlatformFactory,
 private val ownership:AudioOwnership=AudioOwnership.process,
 private val beforeStart:()->Unit={},
 private val clock:()->Long={System.nanoTime()/1_000_000},
 private val parserDispatcher:CoroutineDispatcher=Dispatchers.Default
) {
 private sealed class Wire { object Open:Wire();data class Message(val text:String):Wire();data class Failed(val failure:GeminiFailure):Wire();data class Closed(val code:Int):Wire() }
 private data class Envelope(val event:Wire,val chars:Int=0)
 private class Session(val owner:String) {
  val live=AtomicBoolean(true);val overflow=AtomicBoolean();val inboxChars=AtomicInteger();val inbox=Channel<Envelope>(32)
  var connection:GeminiConnection?=null;var platform:GeminiPlatform?=null;var audio:GeminiAudioSession?=null;var input:MicrophoneGate?=null
  var loop:Job?=null;var timeout:Job?=null;var monitor:Job?=null;var expiry:Job?=null
  var cleanupIssue=false;var setupSent=false;var outputGeneration=0L;var interrupted=false;var processing=false
  val inputDetector=AudioActivityDetector();val outputDetector=AudioActivityDetector()
 }
 private val mutable=MutableStateFlow(GeminiLiveUi());val state=mutable.asStateFlow()
 private var current:Session?=null;private var closing:Job?=null;private var cleanupBlocked=false;private var shuttingDown=false
 fun configurationResult(error:String?=null,notice:String?=null){if(!state.value.active)mutable.value=state.value.copy(error=error,notice=notice)}
 fun report(failure:GeminiFailure){val s=current;if(s!=null)terminate(s,failure)else if(closing?.isActive!=true)mutable.value=state.value.copy(phase=GeminiPhase.ERROR,error=failure.message,errorCode=failure.code,ready=false)}
 fun start(configuration:GeminiConfiguration){
  if(shuttingDown||state.value.active||closing?.isActive==true)return
  if(cleanupBlocked||ownership.failed()){report(GeminiFailure(GeminiErrorCode.CLEANUP,"Audio cleanup failed. Restart the app before recording again."));return}
  val auth:GeminiSocketAuth;val setup:String
  try{auth=geminiSocketAuth(configuration.credential);setup=geminiSetup(configuration.model,configuration.voice)}catch(e:Exception){report(GeminiFailure(GeminiErrorCode.CONFIGURATION,"Enter a valid Google credential, Live model ID and supported voice."));return}
  try{beforeStart()}catch(e:Exception){report(GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"Could not stop the previous voice operation."));return}
  val s=Session("gemini-${UUID.randomUUID()}")
  if(!ownership.acquire(s.owner)){report(GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"Another voice operation is active or still closing. Wait for cleanup before starting."));return}
  current=s;mutable.value=GeminiLiveUi(phase=GeminiPhase.CONNECTING)
  try{
   s.platform=platforms.create({failure->post(s){terminate(s,failure)}},{routes,selected,notice->post(s){update(s,state.value.copy(routes=routes,route=selected,notice=notice))}})
   if(s.platform?.canRecord()!=true)throw GeminiFailure(GeminiErrorCode.FOREGROUND,"Open Gemini Live in the foreground and grant microphone permission before starting.")
   s.platform!!.acquire()
   s.connection=connections.create(object:GeminiConnection.Listener{
    override fun opened()=offer(s,Wire.Open)
    override fun message(text:String)=offer(s,Wire.Message(text),text.length)
    override fun failed(failure:GeminiFailure)=offer(s,Wire.Failed(failure))
    override fun closed(code:Int)=offer(s,Wire.Closed(code))
   })
   s.input=MicrophoneGate(s.connection!!)
   s.loop=scope.launch {
    for(envelope in s.inbox){try{
     if(!valid(s))break
     when(val event=envelope.event){
      Wire.Open->{if(!s.setupSent){s.setupSent=true;update(s,state.value.copy(phase=GeminiPhase.SETTING_UP));if(s.connection?.send(setup)!=true)throw GeminiFailure(GeminiErrorCode.NETWORK,"Could not send Gemini setup. Check the network.")}}
      is Wire.Message->{val parsed=withContext(parserDispatcher){decodeGeminiEvent(event.text)};if(valid(s))handle(s,parsed)}
      is Wire.Failed->terminate(s,event.failure)
      is Wire.Closed->terminate(s,closedFailure(event.code))
     }
    }catch(e:CancellationException){throw e}catch(e:GeminiFailure){if(valid(s))terminate(s,e)}catch(e:Exception){if(valid(s))terminate(s,GeminiFailure(GeminiErrorCode.PROTOCOL,"Live session processing failed. Check model compatibility and restart manually."))}
    finally{s.inboxChars.addAndGet(-envelope.chars)}}
   }
   s.timeout=scope.launch{delay(25000);if(valid(s)&&!state.value.ready)terminate(s,GeminiFailure(GeminiErrorCode.TIMEOUT,"Timed out waiting for Gemini setupComplete. Check the key, model access and network."))}
   s.monitor=scope.launch{while(valid(s)){delay(200);if(!valid(s))break;if(!recordingAllowed(s)){terminate(s,GeminiFailure(GeminiErrorCode.PERMISSION,"Microphone permission or foreground access was lost. Return to the app and grant access before restarting."));break};update(s,state.value.copy(sentBytes=s.input?.bytesQueued()?:0))}}
   s.connection!!.open(auth)
  }catch(e:GeminiFailure){terminate(s,e)}catch(e:Exception){terminate(s,GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"Unable to initialize voice resources. Check microphone and audio availability."))}
 }
 private fun recordingAllowed(s:Session):Boolean=try{s.platform?.canRecord()==true}catch(e:Exception){false}
 private fun valid(s:Session)=current===s&&s.live.get()
 private fun post(s:Session,action:()->Unit){scope.launch{if(valid(s))action()}}
 private fun offer(s:Session,event:Wire,chars:Int=0){
  if(!s.live.get())return
  if(chars>MAX_GEMINI_MESSAGE_CHARS){overloaded(s);return}
  val count=s.inboxChars.addAndGet(chars)
  if(count>MAX_GEMINI_MESSAGE_CHARS*2||!s.inbox.trySend(Envelope(event,chars)).isSuccess){s.inboxChars.addAndGet(-chars);overloaded(s)}
 }
 private fun overloaded(s:Session){if(s.overflow.compareAndSet(false,true))post(s){terminate(s,GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Gemini events exceeded the bounded processing buffer. Restart on a stable connection."))}}
 private fun update(s:Session,value:GeminiLiveUi){if(valid(s))mutable.value=value}
 private fun activity(s:Session,value:GeminiLiveUi=state.value):GeminiLiveUi {
  if(!value.ready)return value
  val phase=when{value.muted->GeminiPhase.MUTED;s.interrupted->GeminiPhase.INTERRUPTED;value.inputActive->GeminiPhase.USER_SPEAKING;value.outputActive->GeminiPhase.ASSISTANT_SPEAKING;s.processing->GeminiPhase.PROCESSING;value.capture->GeminiPhase.LISTENING;else->GeminiPhase.READY}
  return value.copy(phase=phase)
 }
 private suspend fun handle(s:Session,event:GeminiServerEvent){
  event.failure?.let{throw it}
  if(event.unsupportedTool)throw GeminiFailure(GeminiErrorCode.UNSUPPORTED_TOOL,"The model requested an unsupported tool. Restart without tool configuration.")
  if(event.ready&&!state.value.ready){
   if(!s.setupSent)throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini sent readiness before setup was sent.")
   if(s.platform?.canRecord()!=true)throw GeminiFailure(GeminiErrorCode.PERMISSION,"Microphone access was lost before capture started.")
   s.timeout?.cancel();update(s,state.value.copy(phase=GeminiPhase.READY,ready=true))
   val audio=audioFactory.create(s.input!!,audioEvents(s));s.audio=audio
   try{audio.start()}catch(e:CancellationException){throw e}catch(e:GeminiFailure){throw e}catch(e:Exception){throw GeminiFailure(GeminiErrorCode.CAPTURE,"Native microphone or playback initialization failed. Check device support and permission.")}
   if(!valid(s))return
  }
  if(event.goAway){
   update(s,state.value.copy(notice="Gemini announced that this session will expire. It will not reconnect or create a billable session automatically."))
   event.expiresInMs?.let{remaining->s.expiry?.cancel();s.expiry=scope.launch{delay(remaining);if(valid(s))terminate(s,GeminiFailure(GeminiErrorCode.EXPIRED,"Gemini's announced session deadline was reached. Start again when ready."))}}
  }
  if(!state.value.ready){if(event.audio.isNotEmpty())throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini sent audio before setupComplete.");return}
  if(event.interrupted){
   s.outputGeneration++;s.interrupted=true;s.outputDetector.reset()
   update(s,activity(s,state.value.copy(outputActive=false,playback=false,outputLevel=0f,interruptionCount=state.value.interruptionCount+1)))
   s.audio?.interrupt(s.outputGeneration)
   if(!valid(s))return
  }
  var next=state.value
  event.inputText?.let{next=next.copy(inputText=appendCaption(next.inputText,it),interimText="")}
  event.outputText?.let{next=next.copy(outputText=appendCaption(next.outputText,it))}
  event.interimText?.let{next=next.copy(interimText=it.takeLast(2000))}
  if(event.turnComplete){s.processing=event.processing;s.interrupted=false;next=next.copy(turnCount=next.turnCount+1)}
  if(event.waitingForInput){s.processing=false;s.interrupted=false}
  update(s,activity(s,next))
  if(!event.interrupted)for(part in event.audio){
   if(s.audio?.enqueue(part.pcm,s.outputGeneration)!=true)throw GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Gemini playback exceeded its bounded buffer. Check the output route and restart.")
   if(!valid(s))return
   s.interrupted=false;update(s,activity(s,state.value.copy(receivedBytes=state.value.receivedBytes+part.pcm.size)))
  }
 }
 private fun audioEvents(s:Session)=object:GeminiAudioSession.Events{
  override fun capture(active:Boolean)=post(s){if(!active)s.inputDetector.reset();update(s,activity(s,state.value.copy(capture=active,inputActive=if(active)state.value.inputActive else false,microphoneLevel=if(active)state.value.microphoneLevel else 0f)))}
  override fun microphone(level:AudioLevel)=post(s){if(!state.value.muted){val measured=s.inputDetector.update(level.rms,clock());if(measured)s.interrupted=false;update(s,activity(s,state.value.copy(microphoneLevel=level.display,microphoneSamples=state.value.microphoneSamples+1,inputActive=measured)))}}
  override fun playback(generation:Long,active:Boolean)=post(s){if(generation==s.outputGeneration){if(!active)s.outputDetector.reset();update(s,activity(s,state.value.copy(playback=active,outputActive=if(active)state.value.outputActive else false,outputLevel=if(active)state.value.outputLevel else 0f)))}}
  override fun output(generation:Long,level:AudioLevel)=post(s){if(generation==s.outputGeneration)update(s,activity(s,state.value.copy(outputLevel=level.display,outputSamples=state.value.outputSamples+1,outputActive=s.outputDetector.update(level.rms,clock()))))}
  override fun warning(message:String)=post(s){update(s,state.value.copy(notice=message))}
  override fun failed(failure:GeminiFailure)=post(s){terminate(s,failure)}
 }
 fun mute(){val s=current?:return;if(!state.value.ready)return
  try{val next=!state.value.muted;s.input?.setMuted(next);s.inputDetector.reset();update(s,activity(s,state.value.copy(muted=next,inputActive=false,microphoneLevel=0f)))}catch(e:GeminiFailure){terminate(s,e)}
 }
 fun selectRoute(id:Int){val s=current?:return;try{s.platform?.selectRoute(id)}catch(e:Exception){terminate(s,GeminiFailure(GeminiErrorCode.PLAYBACK,"Could not select the requested audio route."))}}
 fun stop(){current?.let{terminate(it,null)}}
 private fun terminate(s:Session,failure:GeminiFailure?){
  if(!valid(s))return
  current=null;s.live.set(false);s.input?.stop();s.timeout?.cancel();s.monitor?.cancel();s.expiry?.cancel();s.inbox.cancel();s.loop?.cancel()
  try{s.connection?.cancel()}catch(e:Exception){s.cleanupIssue=true};try{s.audio?.requestStop()}catch(e:Exception){s.cleanupIssue=true}
  mutable.value=state.value.copy(phase=GeminiPhase.DISCONNECTING,ready=false,muted=false,capture=false,playback=false,microphoneLevel=0f,outputLevel=0f,inputActive=false,outputActive=false,sentBytes=s.input?.bytesQueued()?:0,error=failure?.message,errorCode=failure?.code)
  closing=scope.launch {
   var clean=!s.cleanupIssue
   withContext(NonCancellable){
    try{if(s.audio?.close()==false)clean=false}catch(e:Exception){clean=false}
    try{if(s.platform?.release()==false)clean=false}catch(e:Exception){clean=false}
    if(clean)ownership.release(s.owner)else{cleanupBlocked=true;ownership.quarantine(s.owner)}
    val finalFailure=if(clean)failure else GeminiFailure(GeminiErrorCode.CLEANUP,"Audio cleanup reported a failure. Restart the app before recording again.")
    mutable.value=state.value.copy(phase=if(finalFailure==null)GeminiPhase.STOPPED else GeminiPhase.ERROR,ready=false,routes=emptyList(),route=null,error=finalFailure?.message,errorCode=finalFailure?.code)
   }
  }
 }
 suspend fun stopAndAwait(){stop();closing?.join()}
 suspend fun shutdown(){shuttingDown=true;stopAndAwait();connections.close()}
 private fun appendCaption(previous:String,delta:String):String {val result=(previous+delta).takeLast(16000);return if(result.firstOrNull()?.isLowSurrogate()==true)result.drop(1)else result}
}
