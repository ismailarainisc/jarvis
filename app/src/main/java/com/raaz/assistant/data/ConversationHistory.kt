package com.raaz.assistant.data

import org.json.JSONArray
const val MAX_HISTORY_CHARS=8_000_000
class HistoryStorageException(message:String):IllegalStateException(message)
fun decodeHistory(raw:String):List<Message> {
 try{
  if(raw.length>MAX_HISTORY_CHARS)throw IllegalArgumentException()
  val a=JSONArray(raw);if(a.length()>100)throw IllegalArgumentException()
  return (0 until a.length()).map {val v=a.getJSONObject(it);val role=v.getString("role");val text=v.getString("text");require(role in setOf("user","assistant","system"));Message(role,text)}
 }catch(e:Exception){throw HistoryStorageException("Stored history is damaged or too large. Existing data was preserved. Restore it or explicitly clear history.")}
}
