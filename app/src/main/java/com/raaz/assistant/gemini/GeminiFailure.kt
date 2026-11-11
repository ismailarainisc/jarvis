package com.raaz.assistant.gemini

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

enum class GeminiErrorCode { AUTHENTICATION, CONFIGURATION, QUOTA, TIMEOUT, NETWORK, TLS, PROTOCOL, CAPTURE, PLAYBACK, BACKPRESSURE, PERMISSION, FOREGROUND, AUDIO_BUSY, SESSION_ENDED, EXPIRED, CLEANUP, STORAGE, UNSUPPORTED_TOOL }
class GeminiFailure(val code:GeminiErrorCode, message:String):Exception(message)
fun providerFailure(httpCode:Int?, status:String?=null):GeminiFailure = when {
 httpCode==429||status=="RESOURCE_EXHAUSTED" -> GeminiFailure(GeminiErrorCode.QUOTA,"Gemini quota or rate limit reached. Check AI Studio limits and retry manually later.")
 httpCode==401||httpCode==403||status in setOf("UNAUTHENTICATED","PERMISSION_DENIED") -> GeminiFailure(GeminiErrorCode.AUTHENTICATION,"Gemini rejected the credential or model access. Check the API key, token expiry and project permissions.")
 httpCode in setOf(400,404,422)||status in setOf("INVALID_ARGUMENT","NOT_FOUND") -> GeminiFailure(GeminiErrorCode.CONFIGURATION,"Gemini rejected the Live model, voice or setup. Check the model ID and supported voice in AI Studio.")
 httpCode in setOf(408,504)||status=="DEADLINE_EXCEEDED" -> GeminiFailure(GeminiErrorCode.TIMEOUT,"Gemini timed out. Check your network and restart manually.")
 else -> GeminiFailure(GeminiErrorCode.NETWORK,"Gemini is unavailable or the connection failed. Check your network and restart manually.")
}
fun networkFailure(error:Throwable, httpCode:Int?=null):GeminiFailure = when {
 httpCode!=null -> providerFailure(httpCode)
 error is SSLException -> GeminiFailure(GeminiErrorCode.TLS,"Secure Google connection failed. Check the network, device clock and certificate trust.")
 error is SocketTimeoutException -> GeminiFailure(GeminiErrorCode.TIMEOUT,"Connection to Gemini timed out. Check the network and restart manually.")
 else -> GeminiFailure(GeminiErrorCode.NETWORK,"Gemini Live connection failed. Check the network and restart manually.")
}
fun closedFailure(code:Int):GeminiFailure=when(code){
 1007,1009 -> GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini closed the connection because of invalid or oversized data. Check the app and model compatibility.")
 1008 -> GeminiFailure(GeminiErrorCode.CONFIGURATION,"Gemini rejected this session. Check the key, Live model, voice and account quota.")
 else -> GeminiFailure(GeminiErrorCode.SESSION_ENDED,"Gemini ended the session (code $code). Start again when ready; no automatic reconnect was attempted.")
}
