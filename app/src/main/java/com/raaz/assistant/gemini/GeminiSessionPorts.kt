package com.raaz.assistant.gemini

import com.raaz.assistant.live.AudioLevel
import com.raaz.assistant.live.LiveAudioRoute
import java.util.concurrent.atomic.AtomicReference

/** Shared by native Gemini, fallback recognition and fallback TTS. A failed close retains the lease. */
class AudioOwnership {
 private val holder=AtomicReference<String?>(null)
 private val faulted=java.util.concurrent.atomic.AtomicBoolean()
 fun acquire(owner:String):Boolean=!faulted.get()&&(holder.compareAndSet(null,owner)||holder.get()==owner)
 fun release(owner:String):Boolean=holder.compareAndSet(owner,null)
 fun occupied():Boolean=holder.get()!=null
 fun quarantine(owner:String){if(holder.get()==owner)faulted.set(true)}
 fun failed():Boolean=faulted.get()
 companion object {val process=AudioOwnership()}
}
data class GeminiConfiguration(val model:String,val voice:String,val credential:String){override fun toString()="GeminiConfiguration(model=$model, voice=$voice, credential=REDACTED)"}
interface GeminiConnection {
 interface Listener {fun opened();fun message(text:String);fun failed(failure:GeminiFailure);fun closed(code:Int)}
 fun open(auth:GeminiSocketAuth)
 fun send(text:String):Boolean
 fun queuedBytes():Long
 fun cancel()
}
interface GeminiConnectionFactory {fun create(listener:GeminiConnection.Listener):GeminiConnection;fun close() {}}
interface GeminiPlatform {
 fun canRecord():Boolean
 fun acquire()
 fun selectRoute(id:Int)
 fun release():Boolean
}
interface GeminiPlatformFactory {
 fun create(onError:(GeminiFailure)->Unit,onRoutes:(List<LiveAudioRoute>,Int?,String?)->Unit):GeminiPlatform
}
interface GeminiAudioSession {
 interface Events {
  fun capture(active:Boolean)
  fun microphone(level:AudioLevel)
  fun playback(generation:Long,active:Boolean)
  fun output(generation:Long,level:AudioLevel)
  fun warning(message:String)
  fun failed(failure:GeminiFailure)
 }
 fun bufferedBytes():Int?=null
 fun requestStop()
 suspend fun start()
 suspend fun enqueue(pcm:ByteArray,generation:Long):Boolean
 suspend fun interrupt(generation:Long)
 suspend fun close():Boolean
}
fun interface GeminiAudioFactory {fun create(input:MicrophoneGate,events:GeminiAudioSession.Events):GeminiAudioSession}
