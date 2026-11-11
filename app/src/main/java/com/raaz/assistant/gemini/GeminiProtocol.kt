package com.raaz.assistant.gemini

import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_GEMINI_LIVE_MODEL="gemini-3.8-live"
const val GEMINI_LIVE_SOCKET="wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
fun geminiModelName(value:String):String {val name=value.removePrefix("models/").trim();require(name.matches(Regex("[A-Za-z0-9._-]{1,120}"))){"Enter a valid Gemini Live model ID."};return "models/$name"}
fun geminiSetup(model:String,voice:String):String=JSONObject().put("setup",JSONObject()
 .put("model",geminiModelName(model))
 .put("generationConfig",JSONObject().put("responseModalities",JSONArray().put("AUDIO")).put("speechConfig",JSONObject().put("voiceConfig",JSONObject().put("prebuiltVoiceConfig",JSONObject().put("voiceName",voice)))))
 .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text","You are JARVIS, a helpful personal voice assistant. Reply naturally in the user's language. Be concise."))))
 .put("inputAudioTranscription",JSONObject()).put("outputAudioTranscription",JSONObject())
 .put("realtimeInputConfig",JSONObject().put("automaticActivityDetection",JSONObject().put("disabled",false)))).toString()
fun geminiAudioMessage(base64:String):String=JSONObject().put("realtimeInput",JSONObject().put("audio",JSONObject().put("data",base64).put("mimeType","audio/pcm;rate=16000"))).toString()
fun geminiStreamEnd():String="{\"realtimeInput\":{\"audioStreamEnd\":true}}"
data class GeminiAudioPart(val data:String,val sampleRate:Int)
data class GeminiServerEvent(val ready:Boolean=false,val interrupted:Boolean=false,val turnComplete:Boolean=false,val inputText:String?=null,val outputText:String?=null,val interimText:String?=null,val audio:List<GeminiAudioPart> = emptyList(),val error:Boolean=false,val goAway:Boolean=false,val unsupportedTool:Boolean=false,val processing:Boolean=false)
fun decodeGeminiEvent(value:String):GeminiServerEvent {
 val root=JSONObject(value);val content=root.optJSONObject("serverContent");val audio=mutableListOf<GeminiAudioPart>()
 val parts=content?.optJSONObject("modelTurn")?.optJSONArray("parts")
 if(parts!=null)for(i in 0 until parts.length()){val part=parts.optJSONObject(i)?:continue;if(part.optBoolean("thought"))continue;val inline=part.optJSONObject("inlineData")?:continue;val mime=inline.optString("mimeType");require(!mime.startsWith("audio/")||mime.startsWith("audio/pcm")){"Gemini returned an unsupported audio format."};if(mime.startsWith("audio/pcm")){val rate=Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()?:24000;require(rate==24000){"Gemini returned an unsupported PCM sample rate."};val data=inline.optString("data");if(data.isNotEmpty())audio.add(GeminiAudioPart(data,rate))}}
 return GeminiServerEvent(root.optJSONObject("setupComplete")!=null,content?.optBoolean("interrupted")==true,content?.optBoolean("turnComplete")==true,content?.optJSONObject("inputTranscription")?.optString("text"),content?.optJSONObject("outputTranscription")?.optString("text"),content?.optJSONObject("interimInputTranscription")?.optString("text"),audio,root.has("error"),root.has("goAway"),root.has("toolCall"),content?.optString("interactionStatus")=="IN_PROGRESS")
}

class GeminiSocketAuth(val url:String,val header:String,val secret:String){override fun toString()="GeminiSocketAuth(header=$header, credential=REDACTED)"}
/** Native clients can send auth headers; never place API keys or short-lived tokens in URLs. */
fun geminiSocketAuth(key:String):GeminiSocketAuth {require(key.isNotBlank());return if(key.startsWith("auth_tokens/"))GeminiSocketAuth(GEMINI_LIVE_SOCKET+"Constrained","Authorization","Token $key")else GeminiSocketAuth(GEMINI_LIVE_SOCKET,"x-goog-api-key",key)}
