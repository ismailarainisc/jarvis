package com.raaz.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AssistantRepository(context: Context) {
 private val transport=ChatCompletionClient()
 fun cancel(){transport.cancel()}
 private val prefs=context.getSharedPreferences("conversations",Context.MODE_PRIVATE)
 fun load():List<Message> = decodeHistory(prefs.getString("history","[]")?:"[]")
 fun save(messages:List<Message>) { load(); val a=JSONArray(); messages.takeLast(100).forEach { a.put(JSONObject().put("role",it.role).put("text",it.text)) }; val encoded=a.toString();if(encoded.length>MAX_HISTORY_CHARS)throw HistoryStorageException("Chat history reached its local storage limit. Export or explicitly clear history before continuing.");prefs.edit().putString("history",encoded).apply() }
 fun clear(){prefs.edit().remove("history").apply()}
 suspend fun reply(settings:Settings,key:String,messages:List<Message>,image:Bitmap?,onDelta:(String)->Unit={}):String = withContext(Dispatchers.IO) {
  providerValidation(settings.url,settings.model)?.let { error(it) }
  val arr=JSONArray(); messages.takeLast(20).forEachIndexed { index,m -> val obj=JSONObject().put("role",m.role); if (image!=null && index==messages.takeLast(20).lastIndex && m.role=="user") { val out=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,80,out);obj.put("content",JSONArray().put(JSONObject().put("type","text").put("text",m.text)).put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP))))) } else obj.put("content",m.text);arr.put(obj) }
  transport.complete(settings.url,key,JSONObject().put("model",settings.model).put("messages",arr).put("stream",settings.streaming).toString(),settings.streaming,onDelta)
 }
}
