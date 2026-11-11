package com.raaz.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AssistantRepository(context: Context) {
 @Volatile private var connection:HttpURLConnection?=null
 fun cancel(){connection?.disconnect()}
 private val prefs=context.getSharedPreferences("conversations",Context.MODE_PRIVATE)
 fun load():List<Message> = try { val a=JSONArray(prefs.getString("history","[]")); (0 until a.length()).map { val v=a.getJSONObject(it); Message(v.getString("role"),v.getString("text")) } } catch (_:Exception) { emptyList() }
 fun save(messages:List<Message>) { val a=JSONArray(); messages.takeLast(100).forEach { a.put(JSONObject().put("role",it.role).put("text",it.text)) }; prefs.edit().putString("history",a.toString()).apply() }
 fun clear(){prefs.edit().remove("history").apply()}
 suspend fun reply(settings:Settings,key:String,messages:List<Message>,image:Bitmap?,onDelta:(String)->Unit={}):String = withContext(Dispatchers.IO) {
  providerValidation(settings.url,settings.model)?.let { error(it) }
  val arr=JSONArray(); messages.takeLast(20).forEachIndexed { index,m -> val obj=JSONObject().put("role",m.role); if (image!=null && index==messages.takeLast(20).lastIndex && m.role=="user") { val out=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,80,out);obj.put("content",JSONArray().put(JSONObject().put("type","text").put("text",m.text)).put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP))))) } else obj.put("content",m.text);arr.put(obj) }
  val c=URL(settings.url).openConnection() as HttpURLConnection
  connection=c
  try { c.instanceFollowRedirects=false;c.connectTimeout=20000;c.readTimeout=90000;c.requestMethod="POST";c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer $key");c.doOutput=true;c.outputStream.use{it.write(JSONObject().put("model",settings.model).put("messages",arr).put("stream",settings.streaming).toString().toByteArray())};val code=c.responseCode;if(code !in 200..299) error(httpError(code)); if(!settings.streaming){val json=c.inputStream.bufferedReader().use{it.readText()};try{JSONObject(json).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")}catch(e:Exception){error("Provider returned an unexpected response format.")}} else {val events=SseEvents();val output=StringBuilder();var done=false;val reader=c.inputStream.bufferedReader();while(true){coroutineContext.ensureActive();val line=reader.readLine()?:break;val event=events.line(line)?:continue;if(event=="[DONE]"){done=true;break};val obj=try{JSONObject(event)}catch(e:Exception){error("Provider returned a malformed streaming event.")};if(obj.has("error"))error("Provider reported a streaming error.");val choices=obj.optJSONArray("choices");if(choices!=null && choices.length()>0){val part=choices.optJSONObject(0)?.optJSONObject("delta");val delta=if(part==null||part.isNull("content")) "" else part.optString("content","");if(delta.isNotEmpty()){output.append(delta);onDelta(delta)}}};if(!done)error("Stream ended before completion. The response may be incomplete.");if(output.isEmpty())error("Provider returned an empty response.");output.toString()} } finally { if(connection===c)connection=null;c.disconnect() }
 }
}
