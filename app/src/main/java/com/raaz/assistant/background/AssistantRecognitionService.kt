package com.raaz.assistant.background

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.*
import androidx.core.content.ContextCompat
import com.raaz.assistant.features.FeatureStore
import com.raaz.assistant.gemini.AudioOwnership
import java.util.UUID

object AssistantVisibility {@Volatile var foreground=false;@Volatile var diagnostic:String?=null}
fun selectRecognitionComponent(available:List<String>,preferred:String?,ownPackage:String):String? {
 val external=available.filter{it.contains('/')&&it.substringBefore('/')!=ownPackage}
 return preferred?.takeIf{it in external}?:external.firstOrNull()
}
fun externalRecognizer(context:Context):ComponentName? {
 val available=context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE),0).map{ComponentName(it.serviceInfo.packageName,it.serviceInfo.name).flattenToString()}
 val preferred=Settings.Secure.getString(context.contentResolver,"voice_recognition_service")
 return selectRecognitionComponent(available,preferred,context.packageName)?.let{ComponentName.unflattenFromString(it)}
}
/** Required assistant metadata points to a real, opt-in foreground-only delegate, never itself. */
class AssistantRecognitionService:RecognitionService() {
 private var recognizer:SpeechRecognizer?=null;private var owner:String?=null;private var generation=0
 private val handler=Handler(Looper.getMainLooper());private var callback:Callback?=null
 private fun allowed():Boolean {
  val store=FeatureStore(this)
  return try{AssistantVisibility.foreground&&store.flag("delegate_recognition")&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED}finally{store.close()}
 }
 private fun reject(callback:Callback,code:Int){try{callback.error(code)}catch(e:Exception){AssistantVisibility.diagnostic="Recognition caller disconnected; no capture was started."}}
 override fun onStartListening(intent:Intent,callback:Callback){
  if(this.callback!=null){reject(callback,SpeechRecognizer.ERROR_RECOGNIZER_BUSY);return}
  val component=externalRecognizer(this)
  if(!allowed()||component==null){reject(callback,SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);return}
  val lease="assistant-recognition-${UUID.randomUUID()}"
  if(!AudioOwnership.process.acquire(lease)){reject(callback,SpeechRecognizer.ERROR_RECOGNIZER_BUSY);return}
  owner=lease;this.callback=callback;val token=++generation
  fun deliver(action:()->Unit){if(token==generation&&this.callback===callback)try{action()}catch(e:Exception){AssistantVisibility.diagnostic="Recognition callback failed; delegate resources were closed.";cleanup()}}
  try{
   recognizer=SpeechRecognizer.createSpeechRecognizer(this,component).also{engine->engine.setRecognitionListener(object:RecognitionListener{
    override fun onReadyForSpeech(params:Bundle?)=deliver{callback.readyForSpeech(params?:Bundle())}
    override fun onBeginningOfSpeech()=deliver{callback.beginningOfSpeech()}
    override fun onRmsChanged(rmsdB:Float)=deliver{callback.rmsChanged(rmsdB)}
    override fun onBufferReceived(buffer:ByteArray?){} // No raw audio is forwarded or retained here.
    override fun onEndOfSpeech()=deliver{callback.endOfSpeech()}
    override fun onPartialResults(partialResults:Bundle?)=deliver{callback.partialResults(partialResults?:Bundle())}
    override fun onResults(results:Bundle?){if(token!=generation||this@AssistantRecognitionService.callback!==callback)return;deliver{callback.results(results?:Bundle())};cleanup()}
    override fun onError(error:Int){if(token!=generation||this@AssistantRecognitionService.callback!==callback)return;deliver{callback.error(error)};cleanup()}
    override fun onEvent(eventType:Int,params:Bundle?){}
   });engine.startListening(intent)}
   val monitor=object:Runnable{override fun run(){if(token!=generation)return;if(!allowed()){deliver{callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)};cleanup()}else handler.postDelayed(this,200)}};handler.postDelayed(monitor,200)
  }catch(e:Exception){deliver{callback.error(SpeechRecognizer.ERROR_CLIENT)};cleanup()}
 }
 override fun onStopListening(callback:Callback){if(this.callback===callback)recognizer?.stopListening()}
 override fun onCancel(callback:Callback){if(this.callback===callback)cleanup()}
 private fun cleanup(){generation++;handler.removeCallbacksAndMessages(null);callback=null;var clean=true;val old=recognizer;recognizer=null;try{old?.cancel()}catch(e:Exception){clean=false};try{old?.destroy()}catch(e:Exception){clean=false};owner?.let{if(clean)AudioOwnership.process.release(it)else AudioOwnership.process.quarantine(it)};owner=null}
 override fun onDestroy(){cleanup();super.onDestroy()}
}
