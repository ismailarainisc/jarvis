package com.raaz.assistant

import com.raaz.assistant.background.*
import com.raaz.assistant.data.SettingsStore
import com.raaz.assistant.features.*
import com.raaz.assistant.voice.SpeechReadyGate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Reliability71Test {
 private class Store:SettingsStore {
  val data=mutableMapOf<String,Any>()
  override fun string(key:String,default:String?)=data[key] as? String?:default
  override fun boolean(key:String,default:Boolean)=data[key] as? Boolean?:default
  override fun contains(key:String)=key in data
  override fun edit(changes:Map<String,Any?>){changes.forEach{(k,v)->if(v==null)data.remove(k)else data[k]=v}}
 }
 private class Ledger:TaskLedger {
  val rows=linkedMapOf<String,AgentTask>();var forbidScan=false
  override fun tasks():List<AgentTask>{check(!forbidScan);return rows.values.toList()}
  override fun task(id:String)=rows[id]
  override fun insertTask(task:AgentTask){rows[task.id]=task}
  override fun transition(id:String,from:TaskStatus,to:TaskStatus,detail:String):Boolean{val old=rows[id]?:return false;if(old.status!=from)return false;rows[id]=old.copy(status=to,detail=detail);return true}
 }
 private fun action()=ToolAction("device_status",JSONObject())
 @Test fun latestContactRequestRejectsOutOfOrderCompletion(){val g=LatestRequestGate();val old=g.begin();val latest=g.begin();assertFalse(g.current(old));assertTrue(g.current(latest))}
 @Test fun revocationInvalidatesOutstandingContactResult(){val g=LatestRequestGate();val t=g.begin();g.invalidate();assertFalse(g.current(t))}
 @Test fun phoneFormattingDoesNotChangeContactIdentity(){assertTrue(samePhone("+92 (300) 123-4567","+923001234567"));assertFalse(samePhone("+12025550123","+12025550124"));assertFalse(samePhone("*123#","123"))}
 @Test fun genuinelyDifferentContactsSharingNumberRemainAmbiguous(){val c=listOf(DeviceContact("Ahmed","+12025550123","one"),DeviceContact("Ahmed","+1 202-555-0123","two"));assertThrows(IllegalArgumentException::class.java){resolveContactNumber(c,"Ahmed")}}
 @Test fun formattingDuplicatesOfOneContactResolveOnce(){val c=listOf(DeviceContact("Ahmed","+12025550123","one"),DeviceContact("Ahmed","+1 202-555-0123","one"));assertEquals("one",resolveContactNumber(c,"Ahmed").lookup)}
 @Test fun commonSpokenAppAliasResolvesLocalizedOfficialApp(){val apps=listOf(InstalledApp("یوٹیوب","com.google.android.youtube"),InstalledApp("YouTube","untrusted.clone"));assertEquals("com.google.android.youtube",resolveInstalledApp(apps,"YouTube").packageName)}
 @Test fun unknownAppNamesRemainAmbiguous(){val apps=listOf(InstalledApp("Browser","one"),InstalledApp("Browser","two"));assertThrows(IllegalArgumentException::class.java){resolveInstalledApp(apps,"Browser")}}
 @Test fun selectedRecipientSurvivesRestartWithoutChangingCredentials(){val s=Store();s.data["gemini_live_key"]="fixture-cipher";val c=DeviceContact("Ahmed","+12025550123","stable-lookup");SelectedRecipientPreferences(s).save(c);assertEquals(c,SelectedRecipientPreferences(s).read());assertEquals("fixture-cipher",s.data["gemini_live_key"])}
 @Test fun selectedRecipientCanBeRevoked(){val s=Store();val p=SelectedRecipientPreferences(s);p.save(DeviceContact("Baba","+12025550123",""));p.clear();assertNull(p.read())}
 @Test fun malformedSelectionDoesNotBecomeARecipient(){val s=Store();s.data["selected_recipient"]="broken";assertThrows(Exception::class.java){SelectedRecipientPreferences(s).read()}}
 @Test fun selectedReferenceMustBeExactNotArbitraryText(){assertTrue(selectedRecipientReference("my selected contact"));assertTrue(selectedRecipientReference("منتخب رابطہ"));assertFalse(selectedRecipientReference("send anything to my selected contact without approval"))}
 @Test fun permissionRejectedServiceStartRevokesReservation(){val gate=BackgroundAuthorization();gate.authorized=true;assertFalse(gate.accept(true,false,false,true));assertFalse(gate.authorized)}
 @Test fun blockedNotificationChannelPreventsHiddenRecording(){val gate=BackgroundAuthorization();gate.authorized=true;assertFalse(gate.accept(true,false,true,false));assertFalse(gate.authorized)}
 @Test fun notificationActionsCannotStartUnreservedService(){val gate=BackgroundAuthorization();assertFalse(gate.accept(false,false,true,true));gate.authorized=true;assertFalse(gate.accept(false,false,true,true))}
 @Test fun explicitStartAndRunningControlsRespectCurrentPermissions(){val gate=BackgroundAuthorization();gate.authorized=true;assertTrue(gate.accept(true,false,true,true));assertTrue(gate.accept(false,true,true,true));assertFalse(gate.accept(false,true,false,true))}
 @Test fun coldSpeechWaitsForActualInitialization(){val g=SpeechReadyGate();assertEquals(SpeechReadyGate.Request.Queued,g.submit("actual reply"));assertTrue(g.waiting());assertEquals("actual reply",g.initialized(true));assertFalse(g.waiting());assertEquals(SpeechReadyGate.Request.Play("next"),g.submit("next"))}
 @Test fun stoppedColdSpeechCannotPlayAfterLateInit(){val g=SpeechReadyGate();g.submit("old");g.cancel();assertNull(g.initialized(true))}
 @Test fun closedSpeechCannotPlayOrQueue(){val g=SpeechReadyGate();g.submit("old");g.close();assertNull(g.initialized(true));assertEquals(SpeechReadyGate.Request.Unavailable,g.submit("late"))}
 @Test fun speechTimeoutRejectsLateInitialization(){val g=SpeechReadyGate();g.submit("old");assertTrue(g.expire());assertNull(g.initialized(true));assertEquals(SpeechReadyGate.Request.Unavailable,g.submit("next"))}
 @Test fun speechQueueIsBoundedAndLatestReplyWins(){val g=SpeechReadyGate();assertEquals(SpeechReadyGate.Request.Unavailable,g.submit("x".repeat(16001)));g.submit("first");g.submit("latest");assertEquals("latest",g.initialized(true))}
 @Test fun failedSpeechInitNeverPretendsToPlay(){val g=SpeechReadyGate();g.submit("reply");assertNull(g.initialized(false));assertEquals(SpeechReadyGate.Request.Unavailable,g.submit("next"))}
 @Test fun executionDeadlineRecordsUnknownAndCannotReplay()=runTest{val l=Ledger();var calls=0;val c=TaskCoordinator(l,{true},ToolRunner{calls++;awaitCancellation()},executionTimeoutMs=100);val t=c.propose(action(),"fixture");val run=async{c.approve(t.id)};advanceUntilIdle();assertEquals(TaskStatus.UNKNOWN_OUTCOME,run.await().status);c.approve(t.id);assertEquals(1,calls)}
 @Test fun approvalAndWaitUseSingleRecordLookup()=runTest{val l=Ledger();val c=TaskCoordinator(l,{true},ToolRunner{ToolOutcome(TaskStatus.VERIFIED,"fixture")});val t=c.propose(action(),"fixture");l.forbidScan=true;assertEquals(TaskStatus.VERIFIED,c.approve(t.id).status);assertEquals(TaskStatus.VERIFIED,ToolApprovalAwaiter(l).await(t.id).status)}
 @Test fun preparationTimeoutPersistsFailureWithoutExecution()=runTest{val l=Ledger();val p=ProposalFactory(l,{awaitCancellation()},timeoutMs=100);val create=async{p.create(action(),"fixture")};advanceUntilIdle();assertEquals(TaskStatus.FAILED,create.await().status);assertTrue(l.rows.values.single().detail.contains("timed out"))}
 @Test fun serviceCannotRearmDuringCleanup(){val gate=BackgroundAuthorization();gate.authorized=true;assertFalse(gate.accept(true,true,true,true,stopping=true));assertFalse(gate.authorized)}

 @Test fun backgroundFailureSurvivesNormalCleanupStates(){for(phase in listOf(WakePhase.PAUSING,WakePhase.PAUSED,WakePhase.STOPPED)){val shown=visibleWakeState(WakeState(phase,"cleanup"),"notification disabled");assertEquals(WakePhase.ERROR,shown.phase);assertEquals("notification disabled",shown.message)};assertEquals(WakePhase.STOPPED,visibleWakeState(WakeState(),null).phase)}
 @Test fun corruptSelectedRecipientCanBeExplicitlyCleared(){val s=Store();s.data["selected_recipient"]="broken";val p=SelectedRecipientPreferences(s);p.clear();assertNull(p.read())}

}
