package com.raaz.assistant.features

import kotlinx.coroutines.CancellationException

interface TaskLedger {
 fun tasks():List<AgentTask>
 fun insertTask(task:AgentTask)
 fun transition(id:String,from:TaskStatus,to:TaskStatus,detail:String):Boolean
}
fun interface ToolRunner {suspend fun run(action:ToolAction):ToolOutcome}
/** The same production coordinator is exercised by JVM tests with isolated ledger/platform ports. */
class TaskCoordinator(private val ledger:TaskLedger,private val unlocked:()->Boolean,private val runner:ToolRunner) {
 fun propose(action:ToolAction,source:String):AgentTask {
  val invalid=ToolRegistry.validate(action)
  val task=AgentTask(action=ToolAction.decode(action.encode()),source=source,status=if(invalid==null)TaskStatus.NEEDS_APPROVAL else TaskStatus.UNSUPPORTED,detail=invalid?:"Review arguments and destination. This request has not run.")
  ledger.insertTask(task);return task
 }
 suspend fun approve(id:String):ToolOutcome {
  val task=try{ledger.tasks().find{it.id==id}}catch(e:Exception){return ToolOutcome(TaskStatus.FAILED,"Task records could not be read. Nothing was executed.")}?:return ToolOutcome(TaskStatus.FAILED,"Task is no longer available.")
  if(!unlocked())return ToolOutcome(TaskStatus.NEEDS_APPROVAL,"Unlock the device before approving this action.")
  if(ToolRegistry.validate(task.action)!=null)return ToolOutcome(TaskStatus.UNSUPPORTED,"The saved action is no longer valid.")
  val claimed=try{ledger.transition(id,TaskStatus.NEEDS_APPROVAL,TaskStatus.EXECUTING,"Approved on this device; executing once.")}catch(e:Exception){return ToolOutcome(TaskStatus.FAILED,"Approval could not be stored. Nothing was executed.")}
  if(!claimed)return ToolOutcome(TaskStatus.CANCELLED,"Task is no longer awaiting approval. It was not repeated.")
  try{val outcome=runner.run(task.action);return recordOutcome(id,outcome)}
  catch(e:CancellationException){recordOutcome(id,ToolOutcome(TaskStatus.CANCELLED,"Cancelled. External action outcome may be unknown; no automatic retry."));throw e}
  catch(e:Exception){val outcome=ToolOutcome(TaskStatus.FAILED,"Tool request failed. External outcome may be unknown. Check permissions, app availability and configuration; do not repeat without checking. No automatic retry.");return recordOutcome(id,outcome)}
 }
 private fun recordOutcome(id:String,outcome:ToolOutcome):ToolOutcome=try{if(ledger.transition(id,TaskStatus.EXECUTING,outcome.status,outcome.detail))outcome else ToolOutcome(TaskStatus.FAILED,"Task status changed before outcome could be stored. External outcome may be unknown; no retry.")}catch(e:Exception){ToolOutcome(TaskStatus.FAILED,"Task outcome could not be stored. External outcome may be unknown; do not repeat without checking.")}
 fun cancel(id:String):Boolean=ledger.transition(id,TaskStatus.NEEDS_APPROVAL,TaskStatus.CANCELLED,"Cancelled before execution.")
}
