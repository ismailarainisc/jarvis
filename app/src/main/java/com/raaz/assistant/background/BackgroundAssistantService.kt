package com.raaz.assistant.background

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.raaz.assistant.MainActivity
import com.raaz.assistant.gemini.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object BackgroundRuntime {
 val authorization=BackgroundAuthorization()
 var authorized:Boolean get()=authorization.authorized;set(value){authorization.authorized=value}
 val wake=MutableStateFlow(WakeState())
 val live=MutableStateFlow(GeminiLiveUi())
 val running=MutableStateFlow(false)
}
/** Started only by a visible user gesture, never boot receivers or sticky process restoration. */
class BackgroundAssistantService:Service() {
 companion object {const val START="jarvis.background.START";const val STOP="jarvis.background.STOP";const val PAUSE="jarvis.background.PAUSE";const val RESUME="jarvis.background.RESUME";const val ROUTE="jarvis.background.ROUTE";const val MUTE="jarvis.background.MUTE";const val CHANNEL="jarvis_microphone";const val NOTIFICATION=61}
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var live:GeminiLiveController;private lateinit var wake:WakeSession
 private var started=false;private var stopping=false;private var stopFailure:String?=null
 override fun onCreate(){super.onCreate()
  live=GeminiLiveController(this){}
  wake=WakeSession(scope,WakeFactory{PorcupineWakeCapture(applicationContext){BackgroundRuntime.authorized&&started&&!stopping}},object:BackgroundVoice{
   override fun start(){live.setForeground(true);live.setScreenVisible(true);live.start()}
   override suspend fun stop(){live.stopAndAwait()}
  },{BackgroundRuntime.authorized&&started&&!stopping&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED})
  if(Build.VERSION.SDK_INT>=26)(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"JARVIS microphone",NotificationManager.IMPORTANCE_LOW).apply{description="Explicit local wake-word listening and Gemini conversation"})
  scope.launch{while(isActive){delay(500);if(started&&!stopping){val mic=ContextCompat.checkSelfPermission(this@BackgroundAssistantService,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;val notifications=com.raaz.assistant.features.backgroundNotificationVisible(this@BackgroundAssistantService);if(!mic||!notifications||!BackgroundRuntime.authorized)stopEverything()}}}
  scope.launch{wake.state.collect{BackgroundRuntime.wake.value=visibleWakeState(it,stopFailure);if(started&&!stopping)notifyState()}}
  scope.launch{live.state.collect{BackgroundRuntime.live.value=it;if(it.phase==GeminiPhase.ERROR&&wake.state.value.phase==WakePhase.CONVERSATION)wake.pause(error=true)}}
  scope.launch{live.state.map{Triple(it.phase,it.muted,it.errorCode)}.distinctUntilChanged().collect{if(started&&!stopping)notifyState()}}
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  val action=intent?.action
  if(action==STOP){stopEverything();return START_NOT_STICKY}
  val mic=ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  if(!BackgroundRuntime.authorization.accept(action==START,started,mic,com.raaz.assistant.features.backgroundNotificationVisible(this),stopping)){
   stopEverything("Background mode needs current microphone permission and a visible JARVIS microphone notification. Recheck app/channel settings.");return START_NOT_STICKY
  }
  try{
   if(!started){val n=notification();if(Build.VERSION.SDK_INT>=30)startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)else startForeground(NOTIFICATION,n);started=true;BackgroundRuntime.running.value=true}
   when(action){ROUTE->{if(live.state.value.ready)live.selectRoute(intent.getIntExtra("route",Int.MIN_VALUE))};START,RESUME->wake.arm();PAUSE->wake.pause();MUTE->{if(live.state.value.ready)live.mute()else wake.pause()}}
  }catch(e:Exception){stopEverything("Android could not start the microphone foreground service. Return to the app and check permissions.")}
  return START_NOT_STICKY
 }
 private fun notification():Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("assistant_hub","Background"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  fun control(action:String,id:Int)=PendingIntent.getService(this,id,Intent(this,BackgroundAssistantService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val text=if(live.state.value.active)"Gemini: ${live.state.value.phase.name}" else  BackgroundRuntime.wake.value.message
  return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS · microphone mode").setContentText(text).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true).setContentIntent(open)
   .addAction(0,"Pause / mute",control(MUTE,1)).addAction(0,"Resume wake",control(RESUME,2)).addAction(0,"STOP",control(STOP,3)).build()
 }
 private fun notifyState(){(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION,notification())}
 private fun stopEverything(failure:String?=null){if(failure!=null){stopFailure=failure;BackgroundRuntime.wake.value=visibleWakeState(wake.state.value,failure)};if(stopping)return;stopping=true;BackgroundRuntime.authorized=false;live.stop();wake.pause();scope.launch{try{wake.stop();live.close()}finally{started=false;BackgroundRuntime.running.value=false;if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE)else @Suppress("DEPRECATION") stopForeground(true);stopSelf()}}}
 override fun onTaskRemoved(rootIntent:Intent?){stopEverything();super.onTaskRemoved(rootIntent)}
 override fun onDestroy(){BackgroundRuntime.authorized=false;BackgroundRuntime.running.value=false;scope.launch{try{wake.stop();live.close()}finally{scope.cancel()}};super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
