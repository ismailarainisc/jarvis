package com.raaz.assistant.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Cancellation owns exactly one Call. Redirects never forward credentials to another destination. */
class ChatCompletionClient internal constructor(private val client:OkHttpClient=OkHttpClient.Builder()
 .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
 .connectTimeout(20,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).callTimeout(180,TimeUnit.SECONDS).build()) {
 private val active=AtomicReference<Call?>()
 fun cancel(){active.get()?.cancel()}
 suspend fun complete(url:String,key:String,body:String,streaming:Boolean,onDelta:(String)->Unit):String=suspendCancellableCoroutine { continuation->
  if(!validEndpoint(url)){continuation.resumeWith(Result.failure(IllegalArgumentException("Provider URL must use HTTPS without query or credentials.")));return@suspendCancellableCoroutine}
  val call=try{client.newCall(Request.Builder().url(url).header("Authorization","Bearer $key").post(body.toRequestBody("application/json".toMediaType())).build())}catch(e:Exception){continuation.resumeWith(Result.failure(IllegalArgumentException("Invalid provider configuration.")));return@suspendCancellableCoroutine}
  if(!active.compareAndSet(null,call)){continuation.resumeWith(Result.failure(IllegalStateException("Another provider request is still closing. Try again shortly.")));return@suspendCancellableCoroutine}
  continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){active.compareAndSet(call,null);continuation.resumeWith(Result.failure(e))}
   override fun onResponse(call:Call,response:Response){
    val result=runCatching{response.use{
     if(!response.isSuccessful)error(httpError(response.code))
     val source=response.body?.source()?:error("Provider returned an empty response.")
     if(!streaming){
      if(source.request(MAX_BODY+1))error("Provider response exceeded the supported size limit.")
      val text=try{JSONObject(source.readUtf8()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")}catch(e:Exception){error("Provider returned an unexpected response format.")}
      if(text.isBlank())error("Provider returned an empty response.");text
     }else{
      val events=SseEvents();val output=StringBuilder();var done=false;var received=0L
      while(continuation.isActive&&!source.exhausted()){
       val line=try{source.readUtf8LineStrict(MAX_LINE)}catch(e:java.io.EOFException){error("Streaming event was oversized or incomplete.")}
       received+=line.length+1;if(received>MAX_STREAM)error("Provider stream exceeded the supported size limit.")
       val event=events.line(line)?:continue
       if(event=="[DONE]"){done=true;break}
       val obj=try{JSONObject(event)}catch(e:Exception){error("Provider returned a malformed streaming event.")}
       if(obj.has("error"))error("Provider reported a streaming error.")
       val choices=obj.optJSONArray("choices");val part=if(choices!=null&&choices.length()>0)choices.optJSONObject(0)?.optJSONObject("delta")else null
       val value=part?.opt("content");if(value!=null&&value!==JSONObject.NULL&&value !is String)error("Provider returned an unsupported content format.")
       val delta=value as? String?:""
       if(output.length+delta.length>MAX_BODY)error("Provider response exceeded the supported size limit.")
       if(delta.isNotEmpty()&&continuation.isActive){output.append(delta);onDelta(delta)}
      }
      if(!done)error("Stream ended before completion. The response may be incomplete.")
      if(output.isBlank())error("Provider returned an empty response.");output.toString()
     }
    }}
    active.compareAndSet(call,null);continuation.resumeWith(result)
   }
  })
 }
 companion object {private const val MAX_BODY=2_000_000L;private const val MAX_LINE=262_144L;private const val MAX_STREAM=16_000_000L}
}
