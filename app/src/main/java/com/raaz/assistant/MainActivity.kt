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

class MainActivity:ComponentActivity(){
 private lateinit var vm:AssistantViewModel
 private lateinit var voice:VoiceSessionManager
 private lateinit var live:GeminiLiveController
 private var pendingFallback=false
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);vm=ViewModelProvider(this)[AssistantViewModel::class.java]
  voice=VoiceSessionManager(this,object:VoiceSessionManager.Events{
   override fun status(state:AssistantState){vm.status(state);vm.voiceActive(voice.isActive())}
   override fun transcript(text:String)=vm.transcript(text)
   override fun level(value:Float)=vm.level(value)
   override fun turn(text:String){val generation=voice.sessionGeneration();vm.send(text,{if(voice.isSession(generation))voice.speak(it)},{if(voice.isSession(generation))voice.finish(it)})}
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
   JarvisApp(vm=vm,onListen={pendingFallback=true;live.stop();if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)beginFallback()else mic.launch(Manifest.permission.RECORD_AUDIO)},
    onCamera={try{camera.launch(null)}catch(e:Exception){vm.fail("Camera application is unavailable.")}},
    onSend={vm.send(it,{answer->if(vm.ui.value.settings.voice&&!live.state.value.active&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))voice.speakOnce(answer)})},
    onStop={pendingFallback=false;voice.stop();vm.stop()},liveContent={GeminiLiveScreen(live)})
  }
 }
 private fun beginFallback(){lifecycleScope.launch{
  live.stopAndAwait()
  if(pendingFallback&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){
   pendingFallback=false;voice.start(vm.ui.value.settings.language)
  }
 }}
 override fun onStart(){super.onStart();live.setForeground(true)}
 override fun onStop(){
  pendingFallback=false;live.setForeground(false)
  if(voice.isActive()){voice.stop();vm.stop()}else{voice.stopOutput();if(!vm.ui.value.generating&&vm.ui.value.state==AssistantState.SPEAKING)vm.status(AssistantState.IDLE)}
  super.onStop()
 }
 override fun onDestroy(){live.close();voice.close();super.onDestroy()}
}
