package com.raaz.assistant.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.raaz.assistant.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.webrtc.PeerConnection

/** Sole owner of native connection + audio UI state. Never delegates native audio to Android TTS. */
class LiveSessionController(private val context:Context,private val beforeStart:()->Unit) {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);private val epoch=SessionEpoch()
 private var client:AndroidWebRtcClient?=null;private var job:Job?=null;private var timeout:Job?=null;private var statsJob:Job?=null
 private val repo=LiveSessionRepository();private val settings=SettingsRepository(context)
 private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private var focus:AudioFocusRequest?=null;private var focusListener:AudioManager.OnAudioFocusChangeListener?=null
 private var devices:LiveAudioDeviceManager?=null;private var hasFocus=false;private var oldMode=AudioManager.MODE_NORMAL
 private var transportConnected=false;private var sessionStarted=false;private var expectedSession:String?=null;private var eventSession:String?=null
 private var cleanupFailed=false
 private val inputDetector=AudioActivityDetector();private val outputDetector=AudioActivityDetector();private val transcripts=LiveTranscriptProcessor()
 private val mutable=MutableStateFlow(LiveUiState());val state=mutable.asStateFlow()
 private fun publish(value:LiveUiState){mutable.value=value.copy(error=if(cleanupFailed) (value.error?.plus(" ")?:"")+"Local audio cleanup reported a failure. Restart the app before recording again." else value.error,voiceActivity=voiceActivity(value.state,value.muted,value.captureRunning,value.inputActive,value.outputActive))}
 private fun audioAllowed()=mutable.value.state !in setOf(LiveSessionState.DISCONNECTED,LiveSessionState.DISCONNECTING,LiveSessionState.ERROR)
 fun endpoint()=settings.liveEndpoint()
 fun configured()=settings.hasLiveToken()
 fun deleteToken(){if(audioAllowed())return;settings.removeLiveToken();publish(mutable.value.copy(error="Backend token deleted."))}
 fun save(endpoint:String,token:String){if(audioAllowed())return;try{settings.saveLive(endpoint.trim(),token);publish(mutable.value.copy(error=null))}catch(e:Exception){publish(mutable.value.copy(error="Use an HTTPS backend URL and a valid backend session token."))}}
 fun permissionDenied(){release();publish(LiveUiState(LiveSessionState.ERROR,"Microphone permission denied. Enable it in Android app settings."))}
 fun start(){if(cleanupFailed){publish(mutable.value);return};if(mutable.value.state !in setOf(LiveSessionState.DISCONNECTED,LiveSessionState.ERROR))return
  if(!permission()){permissionDenied();return}
  beforeStart();val token=epoch.next();transportConnected=false;sessionStarted=false;expectedSession=null;eventSession=null
  inputDetector.reset();outputDetector.reset();transcripts.clear();publish(LiveUiState(LiveSessionState.CONNECTING))
  job=scope.launch {try{
   val url=settings.liveEndpoint();val credential=settings.liveToken();require(com.raaz.assistant.data.validEndpoint(url)){"Configure an HTTPS signaling backend first."}
   acquireAudio(token)
   val created=AndroidWebRtcClient(context,object:AndroidWebRtcClient.Events{
    private fun accept()=epoch.accepts(token)
    override fun transport(state:PeerConnection.PeerConnectionState){scope.launch event@{if(!accept())return@event;when(state){
     PeerConnection.PeerConnectionState.CONNECTED->{transportConnected=true;ready()}
     PeerConnection.PeerConnectionState.FAILED,PeerConnection.PeerConnectionState.DISCONNECTED->fail("WebRTC connection lost. Check the network and start again.")
     PeerConnection.PeerConnectionState.CLOSED->if(mutable.value.state!=LiveSessionState.DISCONNECTING)fail("WebRTC connection closed unexpectedly.")
     else->{}
    }}}
    override fun message(value:String){scope.launch event@{if(!accept())return@event;try{val event=JSONObject(value);when(event.optString("type")){
     "session.started"->{eventSession=event.optJSONObject("session")?.optString("id");sessionStarted=true;ready()}
     "session.closed"->{val closing=mutable.value.state==LiveSessionState.DISCONNECTING;val captions=mutable.value.captions;release();publish(if(closing)LiveUiState(captions=captions)else LiveUiState(LiveSessionState.ERROR,"GPT-Live ended the session. Start again if you want to reconnect.",captions=captions))}
     "error"->fail("GPT-Live reported a session error. Check server account access and start again.")
     else->{if(audioAllowed())transcripts.decode(event)?.let{fragment->publish(mutable.value.copy(captions=(mutable.value.captions+fragment).takeLast(256)))}}
    }}catch(e:Exception){fail("Received an invalid GPT-Live session event.")}}}
    override fun channelClosed(){scope.launch{if(accept()&&mutable.value.state!=LiveSessionState.DISCONNECTING)fail("Session event channel closed unexpectedly.")}}
    override fun capture(running:Boolean){scope.launch{if(accept()&&audioAllowed()){if(!running)inputDetector.reset();publish(mutable.value.copy(captureRunning=running,microphoneLevel=if(running)mutable.value.microphoneLevel else 0f,inputActive=if(running)mutable.value.inputActive else false))}}}
    override fun playback(running:Boolean){scope.launch{if(accept()&&audioAllowed()){if(!running)outputDetector.reset();publish(mutable.value.copy(playbackRunning=running,playbackLevel=if(running)mutable.value.playbackLevel else 0f,outputActive=if(running)mutable.value.outputActive else false))}}}
    override fun microphoneAttached(){scope.launch{if(accept()&&audioAllowed())publish(mutable.value.copy(microphoneAttached=true))}}
    override fun remoteTrack(){scope.launch{if(accept()&&audioAllowed())publish(mutable.value.copy(remoteTrackAvailable=true))}}
    override fun microphoneLevel(level:AudioLevel){scope.launch{if(accept()&&audioAllowed()&&!mutable.value.muted)publish(mutable.value.copy(microphoneSamples=mutable.value.microphoneSamples+1,microphoneMeterAvailable=true,microphoneLevel=level.display,inputActive=inputDetector.update(level.rms,SystemClock.elapsedRealtime())))}}
    override fun playbackLevel(level:AudioLevel){scope.launch{if(accept()&&audioAllowed())publish(mutable.value.copy(playbackSamples=mutable.value.playbackSamples+1,playbackMeterAvailable=true,playbackLevel=level.display,outputActive=outputDetector.update(level.rms,SystemClock.elapsedRealtime())))}}
    override fun audioError(message:String){scope.launch{if(accept()&&audioAllowed())fail(message)}}
    override fun rtp(outbound:Long?,inbound:Long?){scope.launch{if(accept()&&audioAllowed())publish(mutable.value.copy(outboundPackets=outbound,inboundPackets=inbound))}}
   })
   if(!epoch.accepts(token)){created.close();return@launch};client=created;ensureActive()
   publish(mutable.value.copy(state=LiveSessionState.NEGOTIATING))
   val offer=created.offer();created.local(offer);withTimeout(10000){while(!created.iceComplete())delay(50)}
   val answer=repo.create(url,credential,created.localSdp());ensureActive();expectedSession=answer.sessionId;publish(mutable.value.copy(sessionId=answer.sessionId));created.remote(answer.sdp);ready()
   statsJob=scope.launch{while(epoch.accepts(token)&&audioAllowed()){if(!permission()){fail("Microphone permission was revoked. Grant permission before restarting.");break};client?.pollStats();delay(1000)}}
   if(mutable.value.state!=LiveSessionState.SESSION_READY)timeout=scope.launch{delay(25000);if(epoch.accepts(token)&&mutable.value.state !in setOf(LiveSessionState.SESSION_READY,LiveSessionState.DISCONNECTING))fail("Timed out waiting for transport and session.started. Check the network and GPT-Live access.")}
  }catch(e:CancellationException){if(e is TimeoutCancellationException)fail("ICE or session negotiation timed out. Check your network.");else throw e}catch(e:Exception){fail(e.message?:"Unable to establish GPT-Live. Check backend configuration.")} }
 }
 private fun permission()=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
 private fun ready(){if(mutable.value.state==LiveSessionState.DISCONNECTING)return;try{val next=connectionReadiness(transportConnected,expectedSession,if(sessionStarted)eventSession else null);if(next==LiveSessionState.SESSION_READY)timeout?.cancel();publish(mutable.value.copy(state=next))}catch(e:Exception){fail("Session identity did not match the signaling response.")}}
 fun mute(){if(mutable.value.state!=LiveSessionState.SESSION_READY)return;val value=!mutable.value.muted;try{client?.mute(value);inputDetector.reset();publish(mutable.value.copy(muted=value,microphoneLevel=0f,inputActive=false))}catch(e:Exception){fail("Microphone mute could not be changed. Restart the conversation.")}}
 fun selectRoute(id:Int){if(audioAllowed())devices?.select(id)}
 fun stop(graceful:Boolean=true){if(graceful&&mutable.value.state==LiveSessionState.DISCONNECTING)return;if(client==null){release();publish(LiveUiState());return}
  publish(mutable.value.copy(state=LiveSessionState.DISCONNECTING,muted=true,microphoneLevel=0f,playbackLevel=0f,inputActive=false,outputActive=false));try{client?.silence()}catch(e:Exception){fail("Native audio could not be stopped normally. Local cleanup was requested.");return};job?.cancel();job=null;statsJob?.cancel();statsJob=null;timeout?.cancel()
  if(graceful&&client?.requestClose()==true){timeout=scope.launch{delay(15000);val captions=mutable.value.captions;release();publish(LiveUiState(error="Closed locally; session.closed was not received before the cleanup deadline.",captions=captions))}}else{val captions=mutable.value.captions;release();publish(LiveUiState(captions=captions))}
 }
 private fun fail(message:String){val captions=mutable.value.captions;release();publish(LiveUiState(LiveSessionState.ERROR,message,captions=captions))}
 private fun release(){epoch.next();timeout?.cancel();timeout=null;job?.cancel();job=null;statsJob?.cancel();statsJob=null
  if(client?.close()==false)cleanupFailed=true;client=null
  if(devices?.stop()==false)cleanupFailed=true;devices=null
  val cleanup=AudioResourceCleanup();if(!cleanup.close(listOf(
   {if(hasFocus){if(Build.VERSION.SDK_INT>=26)focus?.let{audio.abandonAudioFocusRequest(it)}else @Suppress("DEPRECATION") focusListener?.let{audio.abandonAudioFocus(it)}}},
   {if(hasFocus)audio.mode=oldMode},
   {hasFocus=false;focus=null;focusListener=null}
  )))cleanupFailed=true
 }
 private fun acquireAudio(token:Long){oldMode=audio.mode
  val listener=AudioManager.OnAudioFocusChangeListener{change->if(change<0)scope.launch{if(epoch.accepts(token))fail("Audio focus was interrupted. Start again after the call or other audio activity.")}};focusListener=listener
  val result=if(Build.VERSION.SDK_INT>=26){val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(listener).build();focus=request;audio.requestAudioFocus(request)}else @Suppress("DEPRECATION") audio.requestAudioFocus(listener,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
  check(result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Another app is using audio. Stop it before starting GPT-Live."};hasFocus=true;audio.mode=AudioManager.MODE_IN_COMMUNICATION
  devices=LiveAudioDeviceManager(context,{routes,selected,notice->if(epoch.accepts(token))publish(mutable.value.copy(audioRoutes=routes,selectedRoute=selected,routeNotice=notice))},{if(epoch.accepts(token))fail("Audio output device disconnected. Check the route and start again.")}).also{it.start()}
 }
 fun close(){stop(false);scope.cancel()}
}
