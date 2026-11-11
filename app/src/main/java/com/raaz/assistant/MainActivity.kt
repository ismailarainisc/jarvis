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
import com.raaz.assistant.data.AssistantState
import com.raaz.assistant.ui.JarvisApp
import com.raaz.assistant.voice.VoiceSessionManager
import com.raaz.assistant.gemini.GeminiLiveController
import com.raaz.assistant.gemini.GeminiLiveScreen

class MainActivity:ComponentActivity(){
 private lateinit var vm:AssistantViewModel
 private lateinit var voice:VoiceSessionManager
 private lateinit var live:GeminiLiveController
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);vm=ViewModelProvider(this)[AssistantViewModel::class.java];voice=VoiceSessionManager(this,object:VoiceSessionManager.Events{
  override fun status(state:AssistantState){vm.status(state);vm.voiceActive(voice.isActive())}
  override fun transcript(text:String)=vm.transcript(text)
  override fun level(value:Float)=vm.level(value)
  override fun turn(text:String){vm.send(text,{voice.speak(it)},{voice.finish(it)})}
  override fun error(message:String){vm.voiceActive(false);vm.fail(message)}
 });live=GeminiLiveController(this){voice.stop();vm.stop()};setContent {val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){vm.voiceActive(true);voice.start(vm.ui.value.settings.language)}else{vm.voiceActive(false);vm.fail("Microphone permission denied. Allow it in Android app settings to use voice.")}};val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->if(bitmap!=null)vm.attach(bitmap)}
 JarvisApp(vm=vm,onListen={live.stop();if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){vm.voiceActive(true);voice.start(vm.ui.value.settings.language)}else mic.launch(Manifest.permission.RECORD_AUDIO)},onCamera={try{camera.launch(null)}catch(e:Exception){vm.fail("Camera application is unavailable.")}},onSend={vm.send(it,{answer->if(vm.ui.value.settings.voice&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))voice.speakOnce(answer)})},onStop={voice.stop();vm.stop()},liveContent={GeminiLiveScreen(live)})
 }}
 override fun onStop(){super.onStop();if(::live.isInitialized)live.stop();if(::voice.isInitialized){if(voice.isActive()){voice.stop();vm.stop()}else{voice.stopOutput();if(!vm.ui.value.generating&&vm.ui.value.state==AssistantState.SPEAKING)vm.status(AssistantState.IDLE)}}}
 override fun onDestroy(){if(::live.isInitialized)live.close();if(::voice.isInitialized)voice.close();super.onDestroy()}
}
