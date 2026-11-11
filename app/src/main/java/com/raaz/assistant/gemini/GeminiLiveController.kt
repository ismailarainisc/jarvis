package com.raaz.assistant.gemini

import android.content.Context
import android.os.SystemClock
import com.raaz.assistant.data.SettingsRepository
import kotlinx.coroutines.*

/** Android/configuration facade. GeminiSession is the single state owner, including shutdown. */
class GeminiLiveController(context:Context,beforeStart:()->Unit){
 private val app=context.applicationContext
 private val settings=SettingsRepository(app)
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 @Volatile private var foreground=false
 @Volatile private var visible=false
 private var destroyed=false
 private val session=GeminiSession(scope,OkHttpGeminiConnections(),
  GeminiAudioFactory{input,events->GeminiAudioEngine(input,events,{foreground&&visible&&androidx.core.content.ContextCompat.checkSelfPermission(app,android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED})},
  AndroidGeminiPlatforms(app){foreground&&visible},beforeStart=beforeStart,clock={SystemClock.elapsedRealtime()})
 val state=session.state
 fun setForeground(value:Boolean){foreground=value;if(!value)session.stop()}
 fun setScreenVisible(value:Boolean){visible=value;if(!value)session.stop()}
 fun model()=settings.geminiLiveModel()
 fun voice()=settings.geminiLiveVoice()
 fun configured()=settings.hasGeminiLiveKey()
 fun save(model:String,voice:String,key:String):Boolean {
  if(state.value.active)return false
  return try{settings.saveGeminiLive(model.trim(),voice.trim(),key.trim());session.configurationResult(notice="Gemini Live configuration saved. Connection has not been tested.");true}
  catch(e:Exception){session.configurationResult(error="Enter a valid Live model ID, voice and Google credential. Blank key preserves the saved credential.");false}
 }
 fun importSelected(){if(state.value.active)return;try{settings.importSelectedGeminiKey();session.configurationResult(notice="Selected Gemini provider credential imported securely.")}catch(e:Exception){session.configurationResult(error="Select a saved Google Gemini provider with a readable key first.")}}
 fun deleteKey(){if(!state.value.active){settings.removeGeminiLiveKey();session.configurationResult(notice="Gemini Live credential deleted.")}}
 fun permissionDenied(){session.report(GeminiFailure(GeminiErrorCode.PERMISSION,"Microphone permission denied. Enable it in Android app settings."))}
 fun start(){if(destroyed)return
  if(!foreground||!visible){session.report(GeminiFailure(GeminiErrorCode.FOREGROUND,"Return to Gemini Live in the foreground before starting."));return}
  try{session.start(GeminiConfiguration(settings.geminiLiveModel(),settings.geminiLiveVoice(),settings.geminiLiveKey()))}
  catch(e:Exception){session.report(GeminiFailure(GeminiErrorCode.STORAGE,"The saved credential is unavailable or cannot be decrypted. Re-enter it in Gemini Live settings."))}
 }
 fun mute()=session.mute()
 fun selectRoute(id:Int)=session.selectRoute(id)
 fun stop()=session.stop()
 suspend fun stopAndAwait()=session.stopAndAwait()
 fun close(){if(destroyed)return;destroyed=true;foreground=false;visible=false;session.stop();scope.launch{try{session.shutdown()}finally{scope.cancel()}}}
}
