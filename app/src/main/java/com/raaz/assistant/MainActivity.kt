package com.raaz.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.raaz.assistant.background.*
import com.raaz.assistant.data.*
import com.raaz.assistant.features.*
import com.raaz.assistant.gemini.*
import com.raaz.assistant.ui.JarvisApp
import com.raaz.assistant.voice.*
import com.raaz.assistant.setup.SetupWizard
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
 private lateinit var vm:AssistantViewModel
 private lateinit var hub:HubViewModel
 private lateinit var live:GeminiLiveController
 private lateinit var coordinator:HomeVoiceCoordinator
 private var fallback:VoiceSessionManager?=null // Device TTS is initialized only after explicit fallback use.
 private var featureEntry by mutableStateOf<String?>(null)
 private var setupRequest by mutableLongStateOf(0L)
 private var platformPermissionTicket:Long?=null

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  vm=ViewModelProvider(this)[AssistantViewModel::class.java]
  hub=ViewModelProvider(this)[HubViewModel::class.java]
  featureEntry=intent.getStringExtra("assistant_hub")
  live=GeminiLiveController(this){stopFallback();vm.stop()}
  coordinator=HomeVoiceCoordinator(lifecycleScope,GeminiHomeAdapter(live),object:HomeFallbackVoice {
   override fun start(){fallbackEngine().start(vm.ui.value.settings.language)}
   override fun stop(){stopFallback()}
   override fun active()=fallback?.isBusy()==true
  },VoicePreferences(AndroidSettingsStore(this)),{BackgroundRuntime.running.value||BackgroundRuntime.authorized},{vm.stop()})
  setContent {
   val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
    val ticket=platformPermissionTicket;platformPermissionTicket=null
    if(ticket!=null)coordinator.permissionResult(ticket,granted)
   }
   val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->if(bitmap!=null)vm.attach(bitmap)}
   val featureState by hub.state.collectAsStateWithLifecycle()
   val backgroundRunning by BackgroundRuntime.running.collectAsStateWithLifecycle()
   val nativeState by live.state.collectAsStateWithLifecycle()
   val configuration by live.configuration.collectAsStateWithLifecycle()
   val voiceState by coordinator.state.collectAsStateWithLifecycle()
   JarvisApp(
    vm=vm,native=nativeState,configuration=configuration,voice=voiceState,
    onListen={requestVoiceStart(microphone)},onMode=coordinator::select,
    onMute={if(BackgroundRuntime.running.value)startService(Intent(this,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.MUTE))else coordinator.mute()},
    onVoiceSurface=coordinator::setSurfaceVisible,setupRequest=setupRequest,
    onCamera={try{camera.launch(null)}catch(e:Exception){vm.fail("Camera application is unavailable.")}},
    onSend={text->if(!hub.routeCommand(this,text))vm.send(text,{answer->
     if(vm.ui.value.settings.voice&&coordinator.mayUseDeviceSpeech())fallbackEngine().speakOnce(answer)
    })},
    onStop=::stopAll,onStopText=vm::stop,
    setupContent={done,providers,background,test->SetupWizard(live,done,providers,background,test)},
    liveContent={GeminiLiveScreen(live,voiceState,onStart={coordinator.select(HomeVoiceMode.GEMINI_LIVE);requestVoiceStart(microphone)},onStop=::stopAll,onMute=coordinator::mute,onSaved=coordinator::configurationUpdated,backgroundRunning=backgroundRunning)},
    featureContent={initial->HubScreen(hub,initial,nativeState,::startBackground,::stopAll)},
    featureNotice=featureState.error?:featureState.notice,
    pendingTasks=featureState.tasks.count{it.status in pendingTaskStates},initialFeature=featureEntry
   )
  }
 }
 private fun requestVoiceStart(launcher:ActivityResultLauncher<String>) {
  val granted=ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  if(!granted&&platformPermissionTicket!=null){coordinator.reportError("Finish the current microphone permission request first.");return}
  when(val result=coordinator.start(granted)) {
   is VoiceStartResult.PermissionRequired->{platformPermissionTicket=result.ticket;try{launcher.launch(Manifest.permission.RECORD_AUDIO)}catch(e:Exception){platformPermissionTicket=null;coordinator.stop();coordinator.reportError("The microphone permission dialog could not open. Return to Home and try again.")}}
   VoiceStartResult.SetupRequired->setupRequest++
   else->Unit
  }
 }
 private fun fallbackEngine():VoiceSessionManager {
  fallback?.let{return it}
  return VoiceSessionManager(this,object:VoiceSessionManager.Events {
   override fun status(state:AssistantState){vm.status(state);vm.voiceActive(fallback?.isBusy()==true)}
   override fun transcript(text:String)=vm.transcript(text)
   override fun level(value:Float)=vm.level(value)
   override fun turn(text:String){
    val engine=fallback?:return
    if(!coordinator.mayUseDeviceSpeech())return
    if(hub.routeCommand(this@MainActivity,text)){engine.speak("I will check that device request. Review Tasks or the error shown in JARVIS.");return}
    val generation=engine.sessionGeneration()
    vm.send(text,{if(coordinator.mayUseDeviceSpeech()&&engine.isSession(generation))engine.speak(it)},{if(coordinator.mayUseDeviceSpeech()&&engine.isSession(generation))engine.finish(it)})
   }
   override fun error(message:String){vm.stop();vm.voiceActive(false);vm.fail(message)}
  }).also{fallback=it}
 }
 private fun stopFallback(){val old=fallback;fallback=null;old?.close();vm.voiceActive(false)}
 private fun startBackground(){lifecycleScope.launch {
  coordinator.stopAndAwait();vm.stop()
  if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){hub.fail("Return to the foreground before starting microphone mode.");return@launch}
  if(!SettingsRepository(this@MainActivity).hasGeminiLiveKey()){hub.fail("Configure the Gemini Live credential before enabling wake-to-conversation mode.");return@launch}
  BackgroundRuntime.authorized=true
  try{ContextCompat.startForegroundService(this@MainActivity,Intent(this@MainActivity,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.START))}catch(e:Exception){BackgroundRuntime.authorized=false;hub.fail("Android refused background microphone mode. Check foreground-service permissions.")}
 }}
 private fun stopAll(){coordinator.stop();vm.stop();hub.stopExecution();BackgroundRuntime.authorized=false;if(BackgroundRuntime.running.value)startService(Intent(this,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.STOP))}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);featureEntry=intent.getStringExtra("assistant_hub")}
 override fun onStart(){super.onStart();coordinator.setForeground(true);hub.foreground(true);AssistantVisibility.foreground=true}
 override fun onStop(){coordinator.setForeground(false);hub.foreground(false);AssistantVisibility.foreground=false;hub.onBackground();super.onStop()}
 override fun onDestroy(){coordinator.close();live.close();super.onDestroy()}
}
