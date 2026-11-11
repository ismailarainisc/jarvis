package com.raaz.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.raaz.assistant.data.AssistantState
import com.raaz.assistant.ui.JarvisApp
import com.raaz.assistant.voice.VoiceSessionManager
import com.raaz.assistant.gemini.GeminiLiveController
import com.raaz.assistant.gemini.GeminiLiveScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.features.*
import com.raaz.assistant.background.*
import android.content.Intent

class MainActivity:ComponentActivity(){
 private lateinit var vm:AssistantViewModel
 private lateinit var voice:VoiceSessionManager
 private lateinit var live:GeminiLiveController
 private lateinit var hub:HubViewModel
 private var featureEntry by mutableStateOf<String?>(null)
 private var pendingFallback=false
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);vm=ViewModelProvider(this)[AssistantViewModel::class.java];hub=ViewModelProvider(this)[HubViewModel::class.java];featureEntry=intent.getStringExtra("assistant_hub")
  voice=VoiceSessionManager(this,object:VoiceSessionManager.Events{
   override fun status(state:AssistantState){vm.status(state);vm.voiceActive(voice.isActive())}
   override fun transcript(text:String)=vm.transcript(text)
   override fun level(value:Float)=vm.level(value)
   override fun turn(text:String){if(hub.routeCommand(this@MainActivity,text)){voice.speak("I will check that device request. Review Tasks or the error shown in JARVIS.");return};val generation=voice.sessionGeneration();vm.send(text,{if(voice.isSession(generation))voice.speak(it)},{if(voice.isSession(generation))voice.finish(it)})}
   override fun error(message:String){vm.stop();vm.voiceActive(false);vm.fail(message)}
  })
  live=GeminiLiveController(this){pendingFallback=false;voice.stop();vm.stop()}
  setContent {
   val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
    if(pendingFallback&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){
     if(granted)beginFallback()else{pendingFallback=false;vm.voiceActive(false);vm.fail("Microphone permission denied. Enable it in Android app settings.")}
    }
   }
   val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->if(bitmap!=null)vm.attach(bitmap)}
   val featureState by hub.state.collectAsStateWithLifecycle()
   val liveState by live.state.collectAsStateWithLifecycle()
   JarvisApp(vm=vm,onListen={pendingFallback=true;live.stop();if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)beginFallback()else mic.launch(Manifest.permission.RECORD_AUDIO)},
    onCamera={try{camera.launch(null)}catch(e:Exception){vm.fail("Camera application is unavailable.")}},
    onSend={if(!hub.routeCommand(this,it))vm.send(it,{answer->if(vm.ui.value.settings.voice&&!live.state.value.active&&!BackgroundRuntime.running.value&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))voice.speakOnce(answer)})},
    onStop={stopAll()},liveContent={GeminiLiveScreen(live)},featureContent={initial->HubScreen(hub,initial,liveState,::startBackground,::stopAll)},featureNotice=featureState.error?:featureState.notice,pendingTasks=featureState.tasks.count{it.status==TaskStatus.NEEDS_APPROVAL},initialFeature=featureEntry)
  }
 }
 private fun beginFallback(){lifecycleScope.launch{
  live.stopAndAwait()
  if(pendingFallback&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){
   pendingFallback=false;voice.start(vm.ui.value.settings.language)
  }
 }}
 private fun startBackground(){lifecycleScope.launch{
  pendingFallback=false;voice.stop();vm.stop();live.stopAndAwait()
  if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){hub.fail("Return to the foreground before starting microphone mode.");return@launch}
  if(!com.raaz.assistant.data.SettingsRepository(this@MainActivity).hasGeminiLiveKey()){hub.fail("Configure the Gemini Live credential before enabling wake-to-conversation mode.");return@launch}
  BackgroundRuntime.authorized=true
  try{androidx.core.content.ContextCompat.startForegroundService(this@MainActivity,Intent(this@MainActivity,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.START))}catch(e:Exception){BackgroundRuntime.authorized=false;hub.fail("Android refused background microphone mode. Check foreground-service permissions.")}
 }}
 private fun stopAll(){pendingFallback=false;voice.stop();vm.stop();live.stop();hub.stopExecution();BackgroundRuntime.authorized=false;if(BackgroundRuntime.running.value)startService(Intent(this,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.STOP))}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);featureEntry=intent.getStringExtra("assistant_hub")}
 override fun onStart(){super.onStart();live.setForeground(true);hub.foreground(true);AssistantVisibility.foreground=true}
 override fun onStop(){
  pendingFallback=false;live.setForeground(false);hub.foreground(false);AssistantVisibility.foreground=false;hub.onBackground()
  if(voice.isActive()){voice.stop();vm.stop()}else{voice.stopOutput();if(!vm.ui.value.generating&&vm.ui.value.state==AssistantState.SPEAKING)vm.status(AssistantState.IDLE)}
  super.onStop()
 }
 override fun onDestroy(){live.close();voice.close();super.onDestroy()}
}
