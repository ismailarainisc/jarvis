package com.raaz.assistant.live

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LiveAnswer(val sdp:String,val sessionId:String)
class LiveSignalingClient {
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build()
 suspend fun exchange(endpoint:String,token:String,sdp:String):LiveAnswer=suspendCancellableCoroutine { continuation ->
  val request=try{Request.Builder().url(endpoint).header("Authorization","Bearer $token").post(JSONObject().put("sdp",sdp).toString().toRequestBody("application/json".toMediaType())).build()}catch(e:Exception){continuation.resumeWithException(IllegalArgumentException("Invalid backend URL or session token."));return@suspendCancellableCoroutine}
  val call=client.newCall(request);continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("Backend connection failed or timed out. Check HTTPS and your network."))}
   override fun onResponse(call:Call,response:Response){response.use { try {if(!it.isSuccessful)error(when(it.code){401,403->"Backend authentication rejected. Sign in with a valid session token.";429->"Session creation rate limit reached. Wait before starting again.";503->"Backend provider credentials are missing.";504->"Backend provider request timed out.";else->"Backend could not create GPT-Live session (HTTP ${it.code}). Check server credentials and account access."});val raw=it.body?.string()?:error("Empty signaling response.");val json=JSONObject(raw);val answer=LiveAnswer(json.getString("sdp"),json.getString("sessionId"));require(answer.sdp.startsWith("v=0")&&answer.sessionId.isNotBlank()){ "Backend returned an invalid SDP answer." };if(continuation.isActive)continuation.resume(answer)}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(IllegalStateException(if(e is org.json.JSONException) "Unexpected backend response format." else e.message?:"Unexpected backend response."))} }}
  })
 }
}
class LiveSessionRepository(private val signaling:LiveSignalingClient=LiveSignalingClient()) {
 suspend fun create(url:String,token:String,sdp:String):LiveAnswer {require(com.raaz.assistant.data.validEndpoint(url)){"Use an HTTPS backend endpoint without credentials or query strings."};require(token.isNotBlank()){ "Enter an authenticated backend session token." };return signaling.exchange(url,token,sdp)}
}
