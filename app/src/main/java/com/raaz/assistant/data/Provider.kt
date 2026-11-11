package com.raaz.assistant.data

import java.net.URI

data class ProviderConfiguration(val id:String,val name:String,val url:String,val model:String,val streaming:Boolean=false,val hasKey:Boolean=false)
enum class ProviderConnectionState { UNKNOWN, TESTING, CONNECTED, FAILED }
data class ProviderStatus(val state:ProviderConnectionState=ProviderConnectionState.UNKNOWN,val detail:String="Not tested")
interface AiProvider { suspend fun complete(settings:Settings,key:String,messages:List<Message>,onDelta:(String)->Unit):String }
fun validEndpoint(url:String):Boolean = try { val u=URI(url.trim());u.scheme.equals("https",true) && !u.host.isNullOrBlank() && u.userInfo==null && u.fragment==null && u.rawQuery==null } catch (_:Exception){false}
fun httpError(code:Int):String = when(code){401,403->"Authentication rejected or permission denied (HTTP $code).";404->"Endpoint or model was not found (HTTP 404).";408->"Provider request timed out (HTTP 408).";429->"Rate limit or quota reached (HTTP 429).";400,422->"Provider rejected the request or model (HTTP $code).";500,502,503,504->"Provider is unavailable (HTTP $code).";else->"Provider returned HTTP $code."}
/** Separates SSE events; JSON decoding happens only after a complete event. */
class SseEvents { private val data=StringBuilder(); fun line(line:String):String? { if(line.isEmpty()){if(data.isEmpty())return null;val event=data.toString();data.clear();return event};if(line.startsWith("data:")){if(data.length+line.length>262144)error("Provider streaming event exceeded the supported size limit.");if(data.isNotEmpty())data.append('\n');data.append(line.substring(5).removePrefix(" "))};return null } }
