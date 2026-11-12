package com.raaz.assistant.features

import kotlinx.coroutines.*
import org.json.JSONObject

val pendingTaskStates=setOf(TaskStatus.PROPOSED,TaskStatus.NEEDS_APPROVAL,TaskStatus.NEEDS_PERMISSION)
fun taskStatusLabel(status:TaskStatus)=when(status){TaskStatus.COMPLETED->"LEGACY RESULT · not independently verified";TaskStatus.UNSUPPORTED->"FAILED · unsupported";else->status.name.replace('_',' ')}
fun requiredAccess(action:ToolAction):String=when(action.name){
 "find_contacts"->"Optional READ_CONTACTS; explicit review before lookup"
 "dial","sms_compose","whatsapp_compose"->"Unlock + approval; Contacts only for contact lookup; final Call/Send is manual"
 "direct_call"->"Unlock + approval + direct-call opt-in + CALL_PHONE"
 "scroll_app","back_app"->"Manual accessibility enablement + per-app opt-in + unlocked target window"
 "save_memory","read_memory","delete_memory"->"Local Memory enabled + explicit review; no credentials"
 "mcp_call"->"Trusted enabled server + per-tool permission + approval"
 else->"Unlock + explicit action approval; no additional runtime permission"
}
/** Sanitized summary for Google: local contact/memory/results are deliberately not forwarded. */
fun providerTaskResult(task:AgentTask)=JSONObject().put("requestId",task.id).put("status",task.status.name.lowercase()).put("message",when(task.status){
 TaskStatus.VERIFIED->"The device verified this limited local operation. Private results are available in JARVIS Tasks; do not invent their content."
 TaskStatus.LAUNCHED->"Android accepted a launch/navigation request. Final external effect is NOT verified; do not claim a call connected or message was sent."
 TaskStatus.UNKNOWN_OUTCOME,TaskStatus.COMPLETED->"External outcome is unknown or unverified. Do not claim completion or automatically repeat it."
 TaskStatus.NEEDS_PERMISSION->"Required device permission/configuration is unavailable. Nothing executed. Ask the user to review capability settings."
 TaskStatus.CANCELLED->"The pending request was cancelled. No automatic retry."
 else->"The operation did not complete. Review the local task status; do not invent success."
}).also{if(task.status==TaskStatus.VERIFIED&&task.action.name=="device_status")publicDeviceSnapshot(task.detail)?.let{snapshot->it.put("deviceSnapshot",snapshot)}}
/** Validation/preparation failures are durable records, not disappearing errors. */
class ProposalFactory(private val ledger:TaskLedger,private val prepare:suspend (ToolAction)->ToolAction,private val timeoutMs:Long) {
 constructor(ledger:TaskLedger,prepare:suspend (ToolAction)->ToolAction):this(ledger,prepare,15000)
 suspend fun create(action:ToolAction,source:String):AgentTask {
  val invalid=ToolRegistry.validate(action)
  val task=if(invalid!=null)AgentTask(action=ToolAction.decode(action.encode()),source=source,status=TaskStatus.UNSUPPORTED,detail=invalid)else try{
   AgentTask(action=withTimeout(timeoutMs){prepare(action)},source=source,detail="Prepared only. Review target, arguments and required access before approving once.")
  }catch(e:TimeoutCancellationException){AgentTask(action=ToolAction.decode(action.encode()),source=source,status=TaskStatus.FAILED,detail="Preparation timed out. No action executed; check permissions/provider availability before preparing again.")}catch(e:CancellationException){throw e}catch(e:SecurityException){AgentTask(action=ToolAction.decode(action.encode()),source=source,status=TaskStatus.NEEDS_PERMISSION,detail="Required access is missing or revoked. Open setup, grant only the relevant permission, then prepare a new request.")}
  catch(e:Exception){AgentTask(action=ToolAction.decode(action.encode()),source=source,status=TaskStatus.FAILED,detail="App/contact is missing, ambiguous, or configuration is unavailable. Choose the exact app/contact or repair configuration and prepare a new request. Nothing executed.")}
  ledger.insertTask(task);return task
 }
}
/** Suspends only a tool worker, never the Gemini receive/audio loop. */
class ToolApprovalAwaiter(private val ledger:TaskLedger,private val pollMs:Long=250,private val timeoutMs:Long=90000) {
 suspend fun await(id:String,stillAllowed:()->Boolean={true}):AgentTask {
  val result=withTimeoutOrNull(timeoutMs){while(true){val t=ledger.task(id)?:error("Task missing");if(!stillAllowed()&&t.status in pendingTaskStates){pendingTaskStates.forEach{from->ledger.transition(id,from,TaskStatus.CANCELLED,"Assistant tool permission was revoked before approval.")};return@withTimeoutOrNull ledger.task(id)?:error("Task missing")};if(t.status !in pendingTaskStates&&t.status!=TaskStatus.EXECUTING)return@withTimeoutOrNull t;delay(pollMs)};@Suppress("UNREACHABLE_CODE") null}
  if(result!=null)return result
  ledger.transition(id,TaskStatus.NEEDS_APPROVAL,TaskStatus.CANCELLED,"Approval expired after 90 seconds. No action automatically executed or retried.")
  val t=ledger.task(id)?:error("Task missing")
  return if(t.status==TaskStatus.EXECUTING)t.copy(status=TaskStatus.UNKNOWN_OUTCOME,detail="Execution still in progress; no final result claimed.")else t
 }
}
object AutomationScope {
 val packages=setOf("com.google.android.youtube","com.android.chrome")
 fun allowed(target:String,active:String?,direction:String,enabled:Boolean,unlocked:Boolean)=enabled&&unlocked&&target in packages&&active==target&&direction in setOf("forward","backward","back")
}
fun searchInstalledApps(apps:List<InstalledApp>,query:String)=apps.filter{it.label.contains(query.trim(),true)||it.packageName.contains(query.trim(),true)}.take(30)

/** Only this exact, non-identifying Android formatter is eligible for cloud speech. Never forward arbitrary detail text. */
fun publicDeviceSnapshot(detail:String):String? {
 if(detail.length>300)return null
 val match=Regex("""Android snapshot: battery (not available|[0-9]{1,3}%); charging (?:true|false|not available); (?:No active network|Android reports validated internet|Network present; internet not validated|Local network; internet not reported)\. This does not test provider reachability\.""").matchEntire(detail)?:return null
 val battery=match.groupValues[1]
 val percent=battery.removeSuffix("%").toIntOrNull()
 if(battery!="not available"&&(percent==null||percent !in 0..100))return null
 return detail
}

/** geo is opaque: Uri.Builder.appendQueryParameter would erase its 0,0 opaque part. */
fun mapsGeoLink(query:String):String {
 require(query.isNotBlank()&&query.length<=200)
 return "geo:0,0?q="+java.net.URLEncoder.encode(query,"UTF-8").replace("+","%20")
}
/** Existing created timestamp makes native approval expiry survive process death without a schema reset. */
fun geminiApprovalExpired(task:AgentTask,now:Long)=task.source=="Gemini proposal"&&(task.created<0||now<task.created||now-task.created>=90000)
