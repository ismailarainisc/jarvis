package com.raaz.assistant.voice

import com.raaz.assistant.data.SettingsStore
import com.raaz.assistant.gemini.GeminiLiveUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class HomeVoiceMode { GEMINI_LIVE, ANDROID_FALLBACK }
class VoicePreferences(private val store:SettingsStore) {
 fun read():HomeVoiceMode=HomeVoiceMode.values().find{it.name==store.string("home_voice_mode")}?:HomeVoiceMode.GEMINI_LIVE
 fun save(mode:HomeVoiceMode)=store.edit(mapOf("home_voice_mode" to mode.name))
}
interface HomeNativeVoice {
 val state:StateFlow<GeminiLiveUi>
 fun configured():Boolean
 fun setForeground(value:Boolean)
 fun setVisible(value:Boolean)
 fun start()
 fun stop()
 suspend fun stopAndAwait()
 fun mute()
 fun permissionDenied()
}
interface HomeFallbackVoice {fun start();fun stop();fun active():Boolean=false}
data class HomeVoiceUi(val mode:HomeVoiceMode=HomeVoiceMode.GEMINI_LIVE,val switching:Boolean=false,val permissionRequest:Long?=null,val needsSetup:Boolean=false,val error:String?=null)
sealed class VoiceStartResult {object Accepted:VoiceStartResult();object SetupRequired:VoiceStartResult();object Ignored:VoiceStartResult();data class PermissionRequired(val ticket:Long):VoiceStartResult()}

/** One foreground command owner; GeminiSession remains the sole native audio/state owner. */
class HomeVoiceCoordinator(
 private val scope:CoroutineScope,
 private val native:HomeNativeVoice,
 private val fallback:HomeFallbackVoice,
 private val preferences:VoicePreferences,
 private val backgroundRunning:()->Boolean,
 private val cancelText:()->Unit={}
) {
 private val mutable=MutableStateFlow(HomeVoiceUi());val state=mutable.asStateFlow()
 private var foreground=false;private var visible=false;private var closed=false;private var epoch=0L;private var command:Job?=null
 init{try{mutable.value=HomeVoiceUi(mode=preferences.read())}catch(e:Exception){mutable.value=HomeVoiceUi(error="Saved voice mode could not be read. Native Gemini remains the default; settings were not overwritten.")}}
 fun setForeground(value:Boolean){foreground=value;native.setForeground(value);if(!value)stop()}
 fun setSurfaceVisible(value:Boolean){visible=value;native.setVisible(value);if(!value)stop()}
 private fun available()=!closed&&foreground&&visible
 fun start(permissionGranted:Boolean):VoiceStartResult {
  if(!available()||state.value.switching||state.value.permissionRequest!=null||native.state.value.active||fallback.active())return VoiceStartResult.Ignored
  if(backgroundRunning()){mutable.value=state.value.copy(error="Background mode owns audio. Stop it before starting foreground voice.");return VoiceStartResult.Ignored}
  if(state.value.mode==HomeVoiceMode.GEMINI_LIVE&&!native.configured()){mutable.value=state.value.copy(needsSetup=true,error=null);return VoiceStartResult.SetupRequired}
  val token=++epoch;mutable.value=state.value.copy(error=null,needsSetup=false)
  if(!permissionGranted){mutable.value=state.value.copy(permissionRequest=token);return VoiceStartResult.PermissionRequired(token)}
  begin(token,state.value.mode);return VoiceStartResult.Accepted
 }
 fun permissionResult(ticket:Long,granted:Boolean){
  if(ticket!=state.value.permissionRequest||ticket!=epoch)return
  mutable.value=state.value.copy(permissionRequest=null)
  if(!available()||backgroundRunning())return
  if(!granted){if(state.value.mode==HomeVoiceMode.GEMINI_LIVE)native.permissionDenied();mutable.value=state.value.copy(error="Microphone permission denied. Enable it in Android app settings before starting.");return}
  begin(ticket,state.value.mode)
 }
 private fun begin(token:Long,mode:HomeVoiceMode){
  mutable.value=state.value.copy(switching=true)
  command=scope.launch {
   try{
    cancelText();fallback.stop();native.stopAndAwait()
    if(token!=epoch||!available()||backgroundRunning())return@launch
    if(mode==HomeVoiceMode.GEMINI_LIVE){if(native.configured())native.start()else mutable.value=state.value.copy(needsSetup=true)}else fallback.start()
   }catch(e:CancellationException){throw e}catch(e:Exception){if(token==epoch)mutable.value=state.value.copy(error="Voice could not start safely. Check permissions, audio ownership and configuration.")}
   finally{if(token==epoch)mutable.value=state.value.copy(switching=false)}
  }
 }
 fun select(mode:HomeVoiceMode){
  if(mode==state.value.mode||closed)return
  if(backgroundRunning()){mutable.value=state.value.copy(error="Stop background mode before changing the foreground voice mode.");return}
  stop()
  try{preferences.save(mode);mutable.value=state.value.copy(mode=mode,error=null,needsSetup=false)}catch(e:Exception){mutable.value=state.value.copy(error="Could not save voice mode. The existing selection was preserved.")}
 }
 fun mute(){if(state.value.mode==HomeVoiceMode.GEMINI_LIVE&&!backgroundRunning()&&native.state.value.ready)native.mute()}
 fun stop(){++epoch;command?.cancel();command=null;if(fallback.active()||native.state.value.active||state.value.switching)cancelText();native.stop();fallback.stop();mutable.value=state.value.copy(switching=false,permissionRequest=null)}
 suspend fun stopAndAwait(){stop();native.stopAndAwait()}
 fun mayUseDeviceSpeech()=available()&&state.value.mode==HomeVoiceMode.ANDROID_FALLBACK&&!state.value.switching&&state.value.permissionRequest==null&&!native.state.value.active&&!backgroundRunning()
 fun reportError(message:String){mutable.value=state.value.copy(error=message)}
 fun configurationUpdated(){mutable.value=state.value.copy(needsSetup=false,error=null)}
 fun close(){if(closed)return;stop();closed=true}
}

/** Foreground approval/control work shares the native owner; setup/providers do not record. */
fun foregroundVoiceWorkspace(screen:String)=screen in setOf("HOME","LIVE","TASKS","HUB")
