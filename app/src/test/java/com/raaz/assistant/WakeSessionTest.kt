package com.raaz.assistant

import com.raaz.assistant.background.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeSessionTest {
 private class Capture:WakeCapture {var wake:(()->Unit)?=null;var failure:(()->Unit)?=null;var starts=0;var closes=0;var clean=true;var hold:CompletableDeferred<Unit>?=null;override suspend fun start(onWake:()->Unit,onFailure:()->Unit){wake=onWake;failure=onFailure;starts++;hold?.await()};override fun requestStop(){};override suspend fun close():Boolean{closes++;return clean}}
 private class Voice:BackgroundVoice{var starts=0;var stops=0;var verify:()->Unit={};override fun start(){verify();starts++};override suspend fun stop(){stops++}}
 private fun scenario(block:suspend TestScope.(WakeSession,MutableList<Capture>,Voice,(Boolean)->Unit)->Unit)=runTest{val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));val captures=mutableListOf<Capture>();val voice=Voice();var allowed=true;val session=WakeSession(scope,WakeFactory{Capture().also{captures.add(it)}},voice,{allowed});try{block(session,captures,voice,{allowed=it})}finally{captures.forEach{it.hold?.complete(Unit)};session.stop();scope.cancel()}}
 @Test fun explicitArmIsRequiredAndDoesNotStartGemini()=scenario{s,c,v,_->assertEquals(WakePhase.STOPPED,s.state.value.phase);assertTrue(c.isEmpty());s.arm();runCurrent();assertEquals(WakePhase.ARMED,s.state.value.phase);assertEquals(0,v.starts)}
 @Test fun wakeClosesCaptureBeforeStartingGemini()=scenario{s,c,v,_->s.arm();runCurrent();v.verify={assertEquals(1,c.single().closes)};c.single().wake!!();runCurrent();assertEquals(1,v.starts);assertEquals(WakePhase.CONVERSATION,s.state.value.phase)}
 @Test fun duplicateDetectionsCannotStartTwoSessions()=scenario{s,c,v,_->s.arm();runCurrent();repeat(5){c.single().wake!!()};runCurrent();assertEquals(1,v.starts)}
 @Test fun staleWakeAndFailureCannotAffectRearmedSession()=scenario{s,c,v,_->s.arm();runCurrent();val old=c.single();s.pause();runCurrent();s.arm();runCurrent();old.wake!!();old.failure!!();runCurrent();assertEquals(WakePhase.ARMED,s.state.value.phase);assertEquals(0,v.starts)}
 @Test fun pauseAndStopRejectPendingDetections()=scenario{s,c,v,_->s.arm();runCurrent();val callback=c.single().wake!!;s.pause();callback();runCurrent();assertEquals(WakePhase.PAUSED,s.state.value.phase);assertEquals(0,v.starts);s.stop();callback();runCurrent();assertEquals(WakePhase.STOPPED,s.state.value.phase)}
 @Test fun revokedPermissionBlocksHandoff()=scenario{s,c,v,allow->s.arm();runCurrent();allow(false);c.single().wake!!();runCurrent();assertEquals(0,v.starts);assertEquals(1,c.single().closes)}
 @Test fun permissionDeniedNeverCreatesRecorder()=scenario{s,c,v,allow->allow(false);s.arm();runCurrent();assertTrue(c.isEmpty());assertEquals(0,v.starts);assertEquals(WakePhase.ERROR,s.state.value.phase)}
 @Test fun failedCaptureCleanupQuarantinesFutureStarts()=scenario{s,c,v,_->s.arm();runCurrent();c.single().clean=false;c.single().wake!!();runCurrent();assertEquals(WakePhase.ERROR,s.state.value.phase);s.arm();runCurrent();assertEquals(1,c.size);assertEquals(0,v.starts)}
 @Test fun repeatedArmPauseDoesNotLeakCaptures()=scenario{s,c,_,_->repeat(10){s.arm();runCurrent();s.pause();runCurrent()};assertEquals(10,c.size);assertTrue(c.all{it.closes==1})}
 @Test fun actualCaptureFailurePausesWithoutAutomaticRetry()=scenario{s,c,v,_->s.arm();runCurrent();c.single().failure!!();runCurrent();assertEquals(WakePhase.ERROR,s.state.value.phase);advanceTimeBy(30000);runCurrent();assertEquals(1,c.size);assertEquals(0,v.starts)}
}
