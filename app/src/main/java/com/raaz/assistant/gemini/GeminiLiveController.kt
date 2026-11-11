package com.raaz.assistant.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.ContextCompat
import com.raaz.assistant.data.SettingsRepository
import com.raaz.assistant.live.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/** Dedicated Gemini protocol/audio owner. No OpenAI backend, SDP, WebRTC, or TTS in this mode. */
data class GeminiLiveUi(val connection:String="STOPPED",val error:String?=null,val notice:String?=null,val active:Boolean=false,val ready:Boolean=false,val muted:Boolean=false,val capture:Boolean=false,val playback:Boolean=false,val microphoneLevel:Float=0f,val outputLevel:Float=0f,val microphoneSamples:Long=0,val outputSamples:Long=0,val inputActive:Boolean=false,val outputActive:Boolean=false,val inputText:String="",val outputText:String="",val interimText:String="",val sentBytes:Long=0,val receivedBytes:Long=0,val turnCount:Long=0,val interruptionCount:Long=0,val routes:List<LiveAudioRoute> = emptyList(),val route:Int?=null)
class GeminiLiveController(private val context:Context,private val beforeStart:()->Unit){
 private val settings=SettingsRepository(context);private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);private val epoch=SessionEpoch()
 private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(20,TimeUnit.SECONDS).build()
 private var socket:WebSocket?=null;private var engine:GeminiAudioEngine?=null;private var timer:Job?=null;private var devices:LiveAudioDeviceManager?=null
 private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager;private var focus:AudioFocusRequest?=null;private var focusListener:AudioManager.OnAudioFocusChangeListener?=null;private var hasFocus=false;private var oldMode=AudioManager.MODE_NORMAL
 @Volatile private var accepting=false;@Volatile private var microphoneMuted=false;@Volatile private var outboundSocket:WebSocket?=null
 private val sendLock=Any();private val queuedInput=java.util.concurrent.atomic.AtomicLong();private var lastInputMetric=0L
 private var cleanupFailed=false
 private val inputDetector=AudioActivityDetector();private val outputDetector=AudioActivityDetector();private val mutable=MutableStateFlow(GeminiLiveUi());val state=mutable.asStateFlow()
 fun model()=settings.geminiLiveModel();fun voice()=settings.geminiLiveVoice();fun configured()=settings.hasGeminiLiveKey()
 fun save(model:String,voice:String,key:String):Boolean {if(mutable.value.active)return false;return try{settings.saveGeminiLive(model.trim(),voice.trim(),key.trim());mutable.value=mutable.value.copy(error=null,notice="Gemini Live configuration saved. Connection has not been tested.");true}catch(e:Exception){mutable.value=mutable.value.copy(error="Enter a valid Live model ID, voice name and Google AI Studio API key. Blank key keeps the saved key.");false}}
 fun importSelected(){if(mutable.value.active)return;try{settings.importSelectedGeminiKey();mutable.value=mutable.value.copy(error=null,notice="Selected Gemini provider key imported securely.")}catch(e:Exception){mutable.value=mutable.value.copy(error="Select a saved Google Gemini provider with a valid key in AI Providers first.")}}
 fun deleteKey(){if(mutable.value.active)return;settings.removeGeminiLiveKey();mutable.value=mutable.value.copy(notice="Gemini Live key deleted.")}
 fun permissionDenied(){fail("Microphone permission denied. Allow it in Android app settings.")}
 fun start(){if(mutable.value.active)return;if(cleanupFailed){mutable.value=mutable.value.copy(error="Restart the app before recording again; previous audio cleanup reported a failure.");return};if(!permission()){permissionDenied();return};beforeStart();queuedInput.set(0);lastInputMetric=0;val token=epoch.next();inputDetector.reset();outputDetector.reset();mutable.value=GeminiLiveUi(connection="CONNECTING",active=true)
  try{val key=settings.geminiLiveKey();val model=settings.geminiLiveModel();geminiModelName(model);acquireAudio(token)
   val auth=geminiSocketAuth(key)
   val request=Request.Builder().url(auth.url).header(auth.header,auth.secret).build()
   val setup=geminiSetup(model,settings.geminiLiveVoice())
   socket=http.newWebSocket(request,object:WebSocketListener(){
    override fun onOpen(webSocket:WebSocket,response:Response){scope.launch{if(!epoch.accepts(token)){webSocket.cancel();return@launch};mutable.value=mutable.value.copy(connection="SETTING_UP");if(!webSocket.send(setup))fail("Could not send Gemini Live setup.")}}
    override fun onMessage(webSocket:WebSocket,text:String){scope.launch{if(epoch.accepts(token))receive(text,token)}}
    override fun onMessage(webSocket:WebSocket,bytes:ByteString){onMessage(webSocket,bytes.utf8())}
    override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){scope.launch{if(epoch.accepts(token))fail(when(response?.code){401,403->"Gemini rejected the key or model access. Check your Google AI Studio key and project permissions.";404->"Gemini Live model or endpoint was not found. Check the model ID.";429->"Gemini quota or rate limit reached. Check your account limits and retry later.";else->"Gemini Live connection failed. Check the network, key and model availability."})}}
    override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,null);scope.launch{if(epoch.accepts(token))fail("Gemini ended the Live session (code $code). Check model access, quota and session limits; Start again when ready.")}}
    override fun onClosed(webSocket:WebSocket,code:Int,reason:String){scope.launch{if(epoch.accepts(token))fail("Gemini Live disconnected. Start again to create a new session.")}}
   })
   timer=scope.launch{delay(25000);if(epoch.accepts(token)&&!mutable.value.ready)fail("Timed out waiting for Gemini setupComplete. Check key and Live model access.")}
  }catch(e:Exception){fail("Gemini Live could not start. Save a valid API key and Live model, then check microphone and audio availability.")}
 }
 private fun receive(text:String,token:Long){try{val event=decodeGeminiEvent(text);if(event.error){fail("Gemini rejected the request. Check API key, Live model, voice, quota and account access.");return};if(event.goAway){fail("Gemini announced session expiry. Start again when ready; automatic billable reconnection is disabled.");return};if(event.unsupportedTool){fail("This Live session requested an unsupported tool. Restart without tools.");return}
  if(event.ready&&!mutable.value.ready){timer?.cancel();mutable.value=mutable.value.copy(connection="READY",ready=true);startAudio(token)}
  if(!mutable.value.ready)return
  if(event.interrupted){engine?.clearPlayback();outputDetector.reset();mutable.value=mutable.value.copy(outputActive=false,playback=false,outputLevel=0f,interruptionCount=mutable.value.interruptionCount+1)}
  var current=mutable.value
  event.inputText?.let{current=current.copy(inputText=(current.inputText+it).takeLast(16000),interimText="")};event.outputText?.let{current=current.copy(outputText=(current.outputText+it).takeLast(16000))};event.interimText?.let{current=current.copy(interimText=it.takeLast(2000))}
  if(event.turnComplete)current=current.copy(turnCount=current.turnCount+1,connection=if(event.processing)"PROCESSING" else "READY")
  mutable.value=current
  for(part in if(event.interrupted)emptyList() else event.audio){val pcm=Base64.decode(part.data,Base64.DEFAULT);if(engine?.enqueue(pcm)!=true){fail("Gemini audio buffer overflow or invalid PCM. Stop and restart on a stable connection.");return};mutable.value=mutable.value.copy(receivedBytes=mutable.value.receivedBytes+pcm.size)}
 }catch(e:Exception){fail("Gemini returned an unsupported or malformed Live event/audio format.")}}
 private fun startAudio(token:Long){accepting=true;microphoneMuted=false;outboundSocket=socket
  val created=GeminiAudioEngine(context,object:GeminiAudioEngine.Events{
   private fun post(action:()->Unit){scope.launch{if(epoch.accepts(token)&&mutable.value.active)action()}}
   override fun microphone(data:ByteArray):Boolean = synchronized(sendLock){
    if(!epoch.accepts(token)||!accepting)return@synchronized false;if(microphoneMuted)return@synchronized true;val ws=outboundSocket?:return@synchronized false;if(ws.queueSize()>256000)return@synchronized false
    val sent=ws.send(geminiAudioMessage(Base64.encodeToString(data,Base64.NO_WRAP)))
    if(sent){val total=queuedInput.addAndGet(data.size.toLong());val now=SystemClock.elapsedRealtime();if(now-lastInputMetric>=200){lastInputMetric=now;post{mutable.value=mutable.value.copy(sentBytes=total)}}};sent
   }
   override fun micLevel(level:AudioLevel)=post{if(!mutable.value.muted)mutable.value=mutable.value.copy(microphoneLevel=level.display,microphoneSamples=mutable.value.microphoneSamples+1,inputActive=inputDetector.update(level.rms,SystemClock.elapsedRealtime()))}
   override fun outputLevel(level:AudioLevel)=post{mutable.value=mutable.value.copy(outputLevel=level.display,outputSamples=mutable.value.outputSamples+1,outputActive=outputDetector.update(level.rms,SystemClock.elapsedRealtime()))}
   override fun capture(active:Boolean)=post{mutable.value=mutable.value.copy(capture=active)}
   override fun playback(active:Boolean)=post{mutable.value=mutable.value.copy(playback=active,outputLevel=if(active)mutable.value.outputLevel else 0f,outputActive=if(active)mutable.value.outputActive else false)}
   override fun error(message:String)=post{fail(message)}
  });engine=created;try{created.start()}catch(e:Exception){fail("Native microphone or playback initialization failed. Check device audio support and permission.")}
 }
 fun mute(){if(!mutable.value.ready)return;val next=!mutable.value.muted
  val sent=synchronized(sendLock){microphoneMuted=next;if(next)socket?.send(geminiStreamEnd())==true else true};engine?.mute(next)
  if(!sent){fail("Could not pause Gemini input safely. Check the network.");return};inputDetector.reset();mutable.value=mutable.value.copy(muted=next,microphoneLevel=0f,inputActive=false)
 }
 fun selectRoute(id:Int){if(mutable.value.active)devices?.select(id)}
 fun stop(){release();mutable.value=mutable.value.copy(connection="STOPPED",sentBytes=queuedInput.get(),active=false,ready=false,muted=false,capture=false,playback=false,microphoneLevel=0f,outputLevel=0f,inputActive=false,outputActive=false,routes=emptyList(),route=null)}
 private fun fail(message:String){release();mutable.value=mutable.value.copy(connection="ERROR",sentBytes=queuedInput.get(),error=message,active=false,ready=false,capture=false,playback=false,microphoneLevel=0f,outputLevel=0f,inputActive=false,outputActive=false,routes=emptyList(),route=null)}
 private fun release(){epoch.next();synchronized(sendLock){accepting=false;microphoneMuted=true;outboundSocket=null};timer?.cancel();timer=null;var clean=true
  try{if(engine?.close()==false)clean=false}finally{engine=null;val ws=socket;socket=null;if(ws!=null){if(ws.queueSize()>0)ws.cancel()else{ws.close(1000,"User ended session");android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ws.cancel()},1500)}};if(devices?.stop()==false)clean=false;devices=null;val cleanup=AudioResourceCleanup();if(!cleanup.close(listOf({if(hasFocus){if(Build.VERSION.SDK_INT>=26)focus?.let{audio.abandonAudioFocusRequest(it)}else @Suppress("DEPRECATION") focusListener?.let{audio.abandonAudioFocus(it)}}},{if(hasFocus)audio.mode=oldMode},{hasFocus=false;focus=null;focusListener=null})))clean=false};if(!clean)cleanupFailed=true;if(!clean)mutable.value=mutable.value.copy(notice="Audio cleanup reported a failure. Restart the app before recording again.")
 }
 private fun permission()=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
 private fun acquireAudio(token:Long){oldMode=audio.mode;val listener=AudioManager.OnAudioFocusChangeListener{change->if(change<0)scope.launch{if(epoch.accepts(token))fail("Audio focus was interrupted. Start again after the call or other audio activity.")}};focusListener=listener
  val result=if(Build.VERSION.SDK_INT>=26){val req=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(listener).build();focus=req;audio.requestAudioFocus(req)}else @Suppress("DEPRECATION") audio.requestAudioFocus(listener,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
  check(result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED);hasFocus=true;audio.mode=AudioManager.MODE_IN_COMMUNICATION;devices=LiveAudioDeviceManager(context,{routes,selected,notice->if(epoch.accepts(token))mutable.value=mutable.value.copy(routes=routes,route=selected,notice=notice)},{if(epoch.accepts(token))fail("Audio output disconnected. Choose a route and Start again.")}).also{it.start()}
 }
 fun close(){stop();http.dispatcher.cancelAll();http.connectionPool.evictAll();scope.cancel()}
}
