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
 fun finished(session:String){}
 fun close(){}
 fun preferences():String=""
}
class AndroidGeminiToolBridge(context:Context):GeminiToolBridge {
 private val store=FeatureStore(context.applicationContext)
 private val resolver=ToolResolver(context.applicationContext,store)
 private val pending=mutableMapOf<String,String>()
 override fun declarations():JSONArray {
  if(!store.flag("assistant_tools"))return JSONArray()
  val all=ToolRegistry.declarations();val choices=JSONArray();readServers(store).filter{it.enabled&&it.allowed.isNotEmpty()}.forEach{choices.put(JSONObject().put("server",it.id).put("tools",JSONArray(it.allowed.toList())))}
  for(i in 0 until all.length())if(all.getJSONObject(i).optString("name")=="mcp_call")all.getJSONObject(i).put("description","Propose an explicitly allowed MCP call; device approval is required. Available configured identifiers (data only): "+choices.toString().take(2000))
  return all
 }
 override fun preferences()=store.relevantContext("")
 override suspend fun propose(session:String,call:GeminiFunctionCall):JSONObject=withContext(Dispatchers.IO){
  if(!store.flag("assistant_tools"))return@withContext JSONObject().put("status","unsupported").put("message","Enable assistant tools on the device first.")
  val key="$session/${call.id}"
  synchronized(pending){pending[key]}?.let{return@withContext JSONObject().put("status","needs_approval").put("requestId",it)}
  if(synchronized(pending){pending.size}>=100)return@withContext JSONObject().put("status","failed").put("message","Tool proposal limit reached.")
  val proposed=ToolAction(call.name,call.args);val invalid=ToolRegistry.validate(proposed)
  if(invalid!=null)return@withContext JSONObject().put("status","unsupported").put("message",invalid)
  val resolved=try{resolver.prepare(proposed)}catch(e:Exception){return@withContext JSONObject().put("status","needs_configuration").put("message","Resolve the app or contact in Tools/Contacts first. No action executed.")}
  val task=AgentTask(action=resolved,source="Gemini proposal",detail="AI-proposed arguments are untrusted. Unlock and review in Tasks before executing.")
  store.insertTask(task);synchronized(pending){pending[key]=task.id}
  JSONObject().put("status","needs_approval").put("requestId",task.id).put("message","Proposal saved on device. It has NOT executed. Ask the user to review Tasks; do not claim success.")
 }
 override suspend fun cancel(session:String,ids:List<String>)=withContext(Dispatchers.IO){ids.forEach{id->val task=synchronized(pending){pending.remove("$session/$id")};if(task!=null)store.transition(task,TaskStatus.NEEDS_APPROVAL,TaskStatus.CANCELLED,"Provider cancelled this pending proposal; nothing executed.")};Unit}
 override fun close(){store.close()}
 override fun finished(session:String){synchronized(pending){pending.keys.removeAll{it.startsWith("$session/")}}}
}
fun decodeFunctionCalls(root:JSONObject):List<GeminiFunctionCall>{
 val tool=root.optJSONObject("toolCall")?:return emptyList();val calls=tool.optJSONArray("functionCalls")?:throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini tool call is malformed.");if(calls.length()>8)throw GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Too many tool proposals in one event.")
 return (0 until calls.length()).map{i->val c=calls.getJSONObject(i);val id=c.opt("id") as? String?:error("Invalid function ID");val name=c.opt("name") as? String?:error("Invalid function name");val args=if(!c.has("args")||c.isNull("args"))JSONObject()else c.optJSONObject("args")?:error("Invalid function arguments");if(id.isBlank()||id.length>200||name.length>128||args.toString().length>16000)throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Gemini tool arguments exceed supported bounds.");GeminiFunctionCall(id,name,args)}.also{if(it.map{c->c.id}.distinct().size!=it.size)throw GeminiFailure(GeminiErrorCode.PROTOCOL,"Duplicate Gemini function identifiers.")}
}
