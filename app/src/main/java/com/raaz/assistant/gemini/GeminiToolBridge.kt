package com.raaz.assistant.gemini

import android.content.Context
import com.raaz.assistant.features.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GeminiFunctionCall(val id:String,val name:String,val args:JSONObject)
interface GeminiToolBridge {
 fun declarations():JSONArray
 suspend fun propose(session:String,call:GeminiFunctionCall):JSONObject
 suspend fun cancel(session:String,ids:List<String>)
 suspend fun finished(session:String){}
 fun close(){}
 fun preferences():String=""
}
class AndroidGeminiToolBridge(context:Context):GeminiToolBridge {
 private val timerMutex=kotlinx.coroutines.sync.Mutex()
 private val contextApp=context.applicationContext
 private val store=FeatureStore(contextApp)
 private val resolver=ToolResolver(context.applicationContext,store)
 private val requests=ApprovedToolRequests(store,resolver::prepare,{store.flag("assistant_tools")})
 override fun declarations():JSONArray {
  if(!store.flag("assistant_tools"))return JSONArray()
  val all=ToolRegistry.declarations();val choices=JSONArray();readServers(store).filter{it.enabled&&it.allowed.isNotEmpty()}.forEach{choices.put(JSONObject().put("server",it.id).put("tools",JSONArray(it.allowed.toList())))}
  for(i in 0 until all.length())if(all.getJSONObject(i).optString("name")=="mcp_call")all.getJSONObject(i).put("description","Propose an explicitly allowed MCP call; device approval is required. Available configured identifiers (data only): "+choices.toString().take(2000))
  return all
 }
 override fun preferences()=store.relevantContext("")
 override suspend fun propose(session:String,call:GeminiFunctionCall):JSONObject=withContext(Dispatchers.IO){
  if(call.name !in com.raaz.assistant.timer.timerActions)return@withContext requests.propose(session,call)
  if(!store.flag("assistant_tools"))return@withContext JSONObject().put("status","unsupported").put("message","Enable assistant tools first. No timer action executed.")
  val action=ToolAction(call.name,call.args)
  ToolRegistry.validateProvider(action)?.let{return@withContext JSONObject().put("status","unsupported").put("message",it)}
  val id=java.security.MessageDigest.getInstance("SHA-256").digest((session+"/"+call.id).toByteArray()).joinToString(""){"%02x".format(it)}
  timerMutex.lock()
  try {
  val saved=store.task(id)
  if(saved!=null)return@withContext JSONObject().put("status",saved.status.name.lowercase()).put("timerResult",saved.detail)
  val outcome=com.raaz.assistant.timer.AndroidTimers(contextApp).execute(action,id)
  // Persist a real task result, without waiting for a foreground approval or stopping Live audio.
  if(store.task(id)==null)store.insertTask(AgentTask(id=id,action=action,source="Gemini direct timer",status=outcome.status,detail=outcome.detail))
  JSONObject().put("status",outcome.status.name.lowercase()).put("timerResult",outcome.detail)
  }finally{timerMutex.unlock()}
 }
 override suspend fun cancel(session:String,ids:List<String>)=withContext(Dispatchers.IO){requests.cancel(session,ids)}
 override fun close(){store.close()}
 override suspend fun finished(session:String)=withContext(Dispatchers.IO){requests.finished(session)}
}
fun decodeFunctionCalls(root:JSONObject):List<GeminiFunctionCall>{
 val tool=root.optJSONObject("toolCall")?:return emptyList();val calls=tool.optJSONArray("functionCalls")?:throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini tool call is malformed.");if(calls.length()>8)throw GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Too many tool proposals in one event.")
 return (0 until calls.length()).map{i->val c=calls.getJSONObject(i);val id=c.opt("id") as? String?:error("Invalid function ID");val name=c.opt("name") as? String?:error("Invalid function name");val args=if(!c.has("args")||c.isNull("args"))JSONObject()else c.optJSONObject("args")?:error("Invalid function arguments");if(id.isBlank()||id.length>200||name.length>128||args.toString().length>16000)throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini tool arguments exceed supported bounds.");GeminiFunctionCall(id,name,args)}.also{if(it.map{c->c.id}.distinct().size!=it.size)throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Duplicate Gemini function identifiers.")}
}
