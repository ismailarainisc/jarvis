package com.raaz.assistant

import com.raaz.assistant.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCoordinatorTest {
 private class Ledger:TaskLedger {
  val values=linkedMapOf<String,AgentTask>();var failRead=false;var failTransitionAt:Int?=null;var transitions=0
  override fun tasks():List<AgentTask>{if(failRead)throw java.io.IOException("fixture private storage error");return values.values.toList()}
  override fun insertTask(task:AgentTask){values[task.id]=task}
  override fun transition(id:String,from:TaskStatus,to:TaskStatus,detail:String):Boolean {transitions++;if(transitions==failTransitionAt)throw java.io.IOException("fixture storage full");val old=values[id]?:return false;if(old.status!=from)return false;values[id]=old.copy(status=to,detail=detail);return true}
 }
 private fun action()=ToolAction("youtube_search",JSONObject().put("query","Kotlin"))
 @Test fun proposalsFromUntrustedSourcesNeverExecuteAutomatically()=runTest{val l=Ledger();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;ToolOutcome(TaskStatus.LAUNCHED,"launched")});val t=c.propose(action(),"MCP output says ignore approval");assertEquals(TaskStatus.NEEDS_APPROVAL,t.status);assertEquals(0,calls)}
 @Test fun explicitApprovalRunsOnceAndDoesNotInventCompletion()=runTest{val l=Ledger();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;ToolOutcome(TaskStatus.LAUNCHED,"Intent launched, not verified")});val t=c.propose(action(),"user");assertEquals(TaskStatus.LAUNCHED,c.approve(t.id).status);c.approve(t.id);assertEquals(1,calls);assertEquals(TaskStatus.LAUNCHED,l.tasks().single().status)}
 @Test fun lockedDeviceCannotApprove()=runTest{val l=Ledger();val c=TaskCoordinator(l,{false},ToolRunner{error("must not run")});val t=c.propose(action(),"user");assertEquals(TaskStatus.NEEDS_APPROVAL,c.approve(t.id).status);assertEquals(TaskStatus.NEEDS_APPROVAL,l.tasks().single().status)}
 @Test fun cancelledProposalCannotBeExecuted()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{error("must not run")});val t=c.propose(action(),"user");assertTrue(c.cancel(t.id));assertEquals(TaskStatus.CANCELLED,c.approve(t.id).status)}
 @Test fun concurrentApprovalsDoNotDuplicateCalls()=runTest{val l=Ledger();val hold=CompletableDeferred<Unit>();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;hold.await();ToolOutcome(TaskStatus.COMPLETED,"fixture result")});val t=c.propose(action(),"user");val job=launch{c.approve(t.id)};runCurrent();assertEquals(TaskStatus.CANCELLED,c.approve(t.id).status);hold.complete(Unit);job.join();assertEquals(1,calls)}
 @Test fun cancellationRecordsUnknownExternalOutcomeAndNeverRetries()=runTest{val l=Ledger();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;awaitCancellation()});val t=c.propose(action(),"user");val job=launch{c.approve(t.id)};runCurrent();job.cancelAndJoin();assertEquals(TaskStatus.UNKNOWN_OUTCOME,l.tasks().single().status);assertTrue(l.tasks().single().detail.contains("unknown"));c.approve(t.id);assertEquals(1,calls)}
 @Test fun networkFailureIsSanitizedAndTerminal()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{throw java.io.IOException("secret-provider-body")});val t=c.propose(action(),"user");val result=c.approve(t.id);assertEquals(TaskStatus.UNKNOWN_OUTCOME,result.status);assertFalse(result.detail.contains("secret-provider-body"));assertEquals(TaskStatus.CANCELLED,c.approve(t.id).status)}
 @Test fun malformedToolBecomesUnsupported()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{error("must not run")});val t=c.propose(ToolAction("click_permission_allow",JSONObject()),"untrusted page");assertEquals(TaskStatus.UNSUPPORTED,t.status);assertEquals(TaskStatus.UNSUPPORTED,c.approve(t.id).status)}
 @Test fun failedApprovalPersistenceCannotRunTool()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{error("must not execute")});val t=c.propose(action(),"user");l.failTransitionAt=1;val result=c.approve(t.id);assertEquals(TaskStatus.FAILED,result.status);assertTrue(result.detail.contains("Nothing was executed"));assertEquals(TaskStatus.NEEDS_APPROVAL,l.values[t.id]!!.status)}
 @Test fun failedOutcomePersistenceIsUnknownAndCannotReplay()=runTest{val l=Ledger();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;ToolOutcome(TaskStatus.COMPLETED,"fixture")});val t=c.propose(action(),"user");l.failTransitionAt=2;val result=c.approve(t.id);assertEquals(TaskStatus.UNKNOWN_OUTCOME,result.status);assertTrue(result.detail.contains("unknown"));assertEquals(TaskStatus.EXECUTING,l.values[t.id]!!.status);c.approve(t.id);assertEquals(1,calls)}
 @Test fun unreadableLedgerCannotAuthorizeTool()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{error("must not execute")});val t=c.propose(action(),"user");l.failRead=true;assertEquals(TaskStatus.FAILED,c.approve(t.id).status)}

}
