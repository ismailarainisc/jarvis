package com.raaz.assistant.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.raaz.assistant.live.AudioResourceCleanup
import com.raaz.assistant.live.LiveAudioDeviceManager
import com.raaz.assistant.live.LiveAudioRoute
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class OkHttpGeminiConnections internal constructor(
 private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
  .connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(20,TimeUnit.SECONDS).build(),
 private val testEndpoint:((String)->String)?=null
):GeminiConnectionFactory {
 override fun create(listener:GeminiConnection.Listener)=object:GeminiConnection{
  @Volatile private var socket:WebSocket?=null;@Volatile private var cancelled=false
  override fun open(auth:GeminiSocketAuth){
   require(auth.url==GEMINI_LIVE_SOCKET||auth.url==GEMINI_LIVE_SOCKET+"Constrained"){"Only the official Google Live destination is allowed."}
   if(cancelled)return
   val request=Request.Builder().url(testEndpoint?.invoke(auth.url)?:auth.url).header(auth.header,auth.secret).build()
   val created=client.newWebSocket(request,object:WebSocketListener(){
    override fun onOpen(webSocket:WebSocket,response:Response){if(cancelled)webSocket.cancel()else listener.opened()}
    override fun onMessage(webSocket:WebSocket,text:String){if(!cancelled){if(text.length>MAX_GEMINI_MESSAGE_CHARS)listener.failed(GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Gemini returned an oversized message."))else listener.message(text)}}
    override fun onMessage(webSocket:WebSocket,bytes:ByteString){if(!cancelled){if(bytes.size>MAX_GEMINI_MESSAGE_CHARS)listener.failed(GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Gemini returned an oversized message."))else listener.message(bytes.utf8())}}
    override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){if(!cancelled)listener.failed(networkFailure(t,response?.code))}
    override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,null);if(!cancelled)listener.closed(code)}
    override fun onClosed(webSocket:WebSocket,code:Int,reason:String){if(!cancelled)listener.closed(code)}
   })
   socket=created;if(cancelled)created.cancel()
  }
  override fun send(text:String)=!cancelled&&socket?.send(text)==true
  override fun queuedBytes()=socket?.queueSize()?:0L
  override fun cancel(){cancelled=true;socket?.cancel();socket=null}
 }
 override fun close(){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
}
class AndroidGeminiPlatforms(private val context:Context,private val foreground:()->Boolean):GeminiPlatformFactory {
 override fun create(onError:(GeminiFailure)->Unit,onRoutes:(List<LiveAudioRoute>,Int?,String?)->Unit)=object:GeminiPlatform{
  private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var acquired=false;private var hasFocus=false;private var oldMode=AudioManager.MODE_NORMAL
  private var request:AudioFocusRequest?=null;private var listener:AudioManager.OnAudioFocusChangeListener?=null;private var devices:LiveAudioDeviceManager?=null
  override fun canRecord()=foreground()&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  override fun acquire(){
   if(!canRecord())throw GeminiFailure(GeminiErrorCode.PERMISSION,"Open the app and grant microphone permission before starting.")
   oldMode=audio.mode;acquired=true
   val callback=AudioManager.OnAudioFocusChangeListener{change->if(acquired&&change<0)onError(GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"Audio focus was interrupted. Start again after the call or other audio activity."))};listener=callback
   val result=if(Build.VERSION.SDK_INT>=26){val r=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(callback).build();request=r;audio.requestAudioFocus(r)}else @Suppress("DEPRECATION") audio.requestAudioFocus(callback,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
   if(result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"Another application holds audio focus. Try again after it finishes.")
   hasFocus=true;audio.mode=AudioManager.MODE_IN_COMMUNICATION
   devices=LiveAudioDeviceManager(context,onRoutes,{onError(GeminiFailure(GeminiErrorCode.PLAYBACK,"The selected audio output disconnected. Choose a route and restart."))}).also{it.start()}
  }
  override fun selectRoute(id:Int){devices?.select(id)}
  override fun release():Boolean {
   acquired=false;var clean=true
   if(devices?.stop()==false)clean=false;devices=null
   if(!AudioResourceCleanup().close(listOf(
    {if(hasFocus){if(Build.VERSION.SDK_INT>=26)request?.let{audio.abandonAudioFocusRequest(it)}else @Suppress("DEPRECATION") listener?.let{audio.abandonAudioFocus(it)}}},
    {if(hasFocus)audio.mode=oldMode},
    {hasFocus=false;request=null;listener=null}
   )))clean=false
   return clean
  }
 }
}
