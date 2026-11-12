package com.raaz.assistant.gemini

import com.raaz.assistant.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Uses the same durable ledger as the UI. No tool executes here; only unlocked UI approval can execute. */
class ApprovedToolRequests(private val ledger:TaskLedger,private val prepare:suspend (ToolAction)->ToolAction,private val enabled:()->Boolean,private val timeoutMs:Long=90000) {
 private val mutex=Mutex();private val requests=mutableMapOf<String,String>()
 suspend fun propose(session:String,call:GeminiFunctionCall):JSONObject {
  if(!enabled())return JSONObject().put("status","unsupported").put("message","Assistant tools are disabled. Nothing executed.")
  val action=ToolAction(call.name,call.args)
  ToolRegistry.validateProvider(action)?.let{return JSONObject().put("status","unsupported").put("message",it)}
  val key="$session/${call.id}"
  val id=mutex.withLock {
   requests[key]?:run {
    check(requests.size<100){"Tool request limit reached."}
    val task=ProposalFactory(ledger,prepare).create(action,"Gemini proposal")
    requests[key]=task.id;task.id
   }
  }
  try {
   val initial=ledger.tasks().find{it.id==id}?:error("Task unavailable")
   val result=if(initial.status==TaskStatus.NEEDS_APPROVAL)ToolApprovalAwaiter(ledger,timeoutMs=timeoutMs).await(id,enabled)else initial
   return providerTaskResult(result)
  }catch(e:CancellationException){withContext(NonCancellable){cancel(session,listOf(call.id))};throw e}
 }
 suspend fun cancel(session:String,ids:List<String>)=mutex.withLock {
  ids.forEach { id -> requests["$session/$id"]?.let{task->pendingTaskStates.forEach{from->ledger.transition(task,from,TaskStatus.CANCELLED,"Provider/session cancelled this pending approval. No automatic execution.")}} }
 }
 suspend fun finished(session:String)=mutex.withLock {
  val keys=requests.keys.filter{it.startsWith("$session/")}
  keys.forEach { key -> requests[key]?.let{id->pendingTaskStates.forEach{from->ledger.transition(id,from,TaskStatus.CANCELLED,"Voice session ended before approval. Prepare a new request if still wanted.")}};requests.remove(key) }
 }
}
/** Async is enabled only for exact models explicitly documented by Google, not arbitrary compatible IDs. */
fun geminiAsyncTools(model:String)=model.trim().removePrefix("models/") in setOf("gemini-3.8-live","gemini-3.8-live-extended-thinking")
fun configuredToolDeclarations(model:String,tools:org.json.JSONArray):org.json.JSONArray {
 val copy=org.json.JSONArray(tools.toString())
 for(i in 0 until copy.length()){
  val declaration=copy.getJSONObject(i)
  // The official FunctionDeclaration.parameters field uses the Schema enum (OBJECT/STRING).
  // Our registry holds standard JSON Schema; use its documented, mutually exclusive raw field.
  val schema=declaration.remove("parameters") as? JSONObject
  if(schema!=null){
   schema.put("additionalProperties",false)
   if(declaration.optString("name")=="mcp_call")schema.getJSONObject("properties").getJSONObject("arguments").put("additionalProperties",true)
   declaration.put("parametersJsonSchema",schema)
  }
  if(geminiAsyncTools(model))declaration.put("behavior","NON_BLOCKING")
 }
 return copy
}
