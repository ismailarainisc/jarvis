package com.raaz.assistant

import com.raaz.assistant.gemini.*
import com.raaz.assistant.live.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiSessionTest {
 private class Connection(val events:GeminiConnection.Listener):GeminiConnection {
  val messages=mutableListOf<String>();var cancelCount=0;var backlog=0L;var accepts=true
  override fun open(auth:GeminiSocketAuth){}
  override fun send(text:String):Boolean {messages.add(text);return accepts}
  override fun queuedBytes()=backlog
  override fun cancel(){cancelCount++}
 }
 private class Audio(val input:MicrophoneGate,val events:GeminiAudioSession.Events):GeminiAudioSession {
  var starts=0;var stops=0;var closes=0;var clean=true;var holdStart:CompletableDeferred<Unit>?=null;var holdClose:CompletableDeferred<Unit>?=null
  val interruptions=mutableListOf<Long>();val packets=mutableListOf<Pair<Long,ByteArray>>()
  override fun requestStop(){stops++}
  override suspend fun start(){starts++;holdStart?.await();events.capture(true)}
  override suspend fun enqueue(pcm:ByteArray,generation:Long):Boolean{packets.add(generation to pcm);return true}
  override suspend fun interrupt(generation:Long){interruptions.add(generation)}
  override suspend fun close():Boolean{closes++;holdClose?.await();return clean}
 }
 private class Platform:GeminiPlatform {
  var allowed=true;var releases=0;var acquires=0;val selected=mutableListOf<Int>();var routeFailure=false
  override fun canRecord()=allowed
  override fun acquire(){acquires++}
  override fun selectRoute(id:Int){if(routeFailure)throw IllegalStateException("fixture route failure");selected.add(id)}
  override fun release():Boolean{releases++;return true}
 }
 private class Fixture(test:TestScope) {
  val dispatcher=StandardTestDispatcher(test.testScheduler);val scope=CoroutineScope(SupervisorJob()+dispatcher);val ownership=AudioOwnership();val platform=Platform()
  val connections=mutableListOf<Connection>();val audios=mutableListOf<Audio>();var platformFailure:((GeminiFailure)->Unit)?=null
  var startHold:CompletableDeferred<Unit>?=null
  val session=GeminiSession(scope,object:GeminiConnectionFactory{override fun create(listener:GeminiConnection.Listener)=Connection(listener).also{connections.add(it)}},
   GeminiAudioFactory{input,events->Audio(input,events).also{it.holdStart=startHold;audios.add(it)}},
   object:GeminiPlatformFactory{override fun create(onError:(GeminiFailure)->Unit,onRoutes:(List<LiveAudioRoute>,Int?,String?)->Unit):GeminiPlatform{platformFailure=onError;return platform}},ownership,clock={test.testScheduler.currentTime},parserDispatcher=dispatcher)
  fun start(){session.start(GeminiConfiguration(DEFAULT_GEMINI_LIVE_MODEL,"Kore","isolated-test-key"))}
 }
 private fun scenario(block:suspend TestScope.(Fixture)->Unit)=runTest {
  val f=Fixture(this)
  try{block(f)}finally{f.audios.forEach{it.holdStart?.complete(Unit);it.holdClose?.complete(Unit)};f.session.stopAndAwait();runCurrent();f.scope.cancel()}
 }
 private fun TestScope.ready(f:Fixture){f.start();f.connections.last().events.opened();runCurrent();f.connections.last().events.message("{\"setupComplete\":{}}");runCurrent()}
 @Test fun genuineReadinessStartsExactlyOneAudioEngine()=scenario{f->
  f.start();assertEquals(GeminiPhase.CONNECTING,f.session.state.value.phase);assertTrue(f.audios.isEmpty())
  f.connections[0].events.opened();runCurrent();assertEquals(GeminiPhase.SETTING_UP,f.session.state.value.phase);assertTrue(f.audios.isEmpty())
  f.connections[0].events.message("{\"setupComplete\":{}}");runCurrent();assertEquals(1,f.audios.size);assertEquals(GeminiPhase.LISTENING,f.session.state.value.phase)
  f.connections[0].events.message("{\"setupComplete\":{}}");runCurrent();assertEquals(1,f.audios.size)
 }
 @Test fun permissionDeniedDoesNotConnectOrCapture()=scenario{f->f.platform.allowed=false;f.start();runCurrent();assertTrue(f.connections.isEmpty());assertTrue(f.audios.isEmpty());assertEquals(GeminiPhase.ERROR,f.session.state.value.phase);assertFalse(f.ownership.occupied())}
 @Test fun stopBeforeReadinessRejectsOldCallbacks()=scenario{f->f.start();val old=f.connections[0];f.session.stopAndAwait();old.events.opened();old.events.message("{\"setupComplete\":{}}");runCurrent();assertEquals(GeminiPhase.STOPPED,f.session.state.value.phase);assertTrue(f.audios.isEmpty());assertEquals(1,old.cancelCount)}
 @Test fun repeatedStartStopReleasesEverySessionOnce()=scenario{f->repeat(3){ready(f);f.session.stopAndAwait();f.session.stop();runCurrent()};assertEquals(3,f.audios.size);assertTrue(f.audios.all{it.closes==1});assertTrue(f.connections.all{it.cancelCount==1});assertFalse(f.ownership.occupied())}
 @Test fun staleSessionCannotChangeNewConnection()=scenario{f->ready(f);val old=f.connections[0];val oldAudio=f.audios[0];f.session.stopAndAwait();ready(f);old.events.failed(providerFailure(401));oldAudio.events.capture(false);runCurrent();assertEquals(GeminiPhase.LISTENING,f.session.state.value.phase);assertNull(f.session.state.value.error)}
 @Test fun muteUnmuteRejectsFramesReadAcrossBothTransitions()=scenario{f->ready(f);val input=f.audios[0].input;val old=input.ticket()!!;f.session.mute();assertEquals(GeminiPhase.MUTED,f.session.state.value.phase);assertNull(input.ticket());f.session.mute();assertEquals(MicrophoneGate.Result.DROPPED,input.send(old,byteArrayOf(0,0)));assertEquals(MicrophoneGate.Result.SENT,input.send(input.ticket()!!,byteArrayOf(0,0)))}
 @Test fun muteDuringConnectionDoesNotSendControlData()=scenario{f->f.start();f.session.mute();assertTrue(f.connections[0].messages.isEmpty());assertFalse(f.session.state.value.muted)}
 @Test fun largeQueuedInputStopsRatherThanPretendingToMute()=scenario{f->ready(f);f.connections[0].backlog=8000;f.session.mute();runCurrent();assertEquals(GeminiErrorCode.BACKPRESSURE,f.session.state.value.errorCode);assertFalse(f.session.state.value.active)}
 @Test fun interruptionInvalidatesPlaybackCallbacksAndKeepsSession()=scenario{f->ready(f);val c=f.connections[0];val a=f.audios[0];a.events.playback(0,true);a.events.output(0,AudioLevel(.1f,.8f));runCurrent();assertEquals(GeminiPhase.ASSISTANT_SPEAKING,f.session.state.value.phase)
  c.events.message("{\"serverContent\":{\"interrupted\":true}}");runCurrent();assertEquals(listOf(1L),a.interruptions);assertEquals(GeminiPhase.INTERRUPTED,f.session.state.value.phase)
  a.events.output(0,AudioLevel(.5f,1f));a.events.playback(0,true);runCurrent();assertFalse(f.session.state.value.outputActive);assertFalse(f.session.state.value.playback)
  a.events.playback(1,true);a.events.output(1,AudioLevel(.2f,.9f));runCurrent();assertTrue(f.session.state.value.outputActive);assertEquals(0,c.cancelCount)
 }
 @Test fun oldAudioInSameInterruptionMessageIsNotQueued()=scenario{f->ready(f);f.connections[0].events.message("""{"serverContent":{"interrupted":true,"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAA="}}]}}}""");runCurrent();assertTrue(f.audios[0].packets.isEmpty());f.connections[0].events.message("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAA="}}]}}}""");runCurrent();assertEquals(1L,f.audios[0].packets.single().first)}
 @Test fun backgroundDuringSetupNeverStartsCapture()=scenario{f->f.start();f.platform.allowed=false;f.connections[0].events.opened();f.connections[0].events.message("{\"setupComplete\":{}}");runCurrent();assertTrue(f.audios.isEmpty());assertEquals(GeminiPhase.ERROR,f.session.state.value.phase)}
 @Test fun stopWhileAudioStartsWaitsForCleanupAndBlocksRestart()=scenario{f->f.startHold=CompletableDeferred();f.start();f.connections[0].events.opened();f.connections[0].events.message("{\"setupComplete\":{}}");runCurrent();val a=f.audios.single();a.holdClose=CompletableDeferred();f.session.stop();runCurrent();assertEquals(GeminiPhase.DISCONNECTING,f.session.state.value.phase);f.start();assertEquals(1,f.connections.size);assertTrue(f.ownership.occupied());a.holdClose!!.complete(Unit);runCurrent();assertEquals(GeminiPhase.STOPPED,f.session.state.value.phase);assertFalse(f.ownership.occupied())}
 @Test fun failedCleanupQuarantinesAudioOwnership()=scenario{f->ready(f);f.audios[0].clean=false;f.session.stopAndAwait();assertEquals(GeminiErrorCode.CLEANUP,f.session.state.value.errorCode);assertTrue(f.ownership.failed());assertFalse(f.ownership.acquire("fallback"));f.start();assertEquals(1,f.connections.size)}
 @Test fun focusLossCancelsNetworkAndReleasesResources()=scenario{f->ready(f);f.platformFailure!!(GeminiFailure(GeminiErrorCode.AUDIO_BUSY,"test interruption"));runCurrent();assertEquals(GeminiPhase.ERROR,f.session.state.value.phase);assertEquals(1,f.audios[0].closes);assertEquals(1,f.connections[0].cancelCount);assertFalse(f.ownership.occupied())}
 @Test fun revokedPermissionIsDetectedWithoutMoreInput()=scenario{f->ready(f);f.platform.allowed=false;advanceTimeBy(201);runCurrent();assertEquals(GeminiErrorCode.PERMISSION,f.session.state.value.errorCode);assertEquals(1,f.audios[0].closes)}
 @Test fun goAwayHonorsServerGracePeriod()=scenario{f->ready(f);f.connections[0].events.message("{\"goAway\":{\"timeLeft\":\"1s\"}}");runCurrent();assertTrue(f.session.state.value.active);advanceTimeBy(900);runCurrent();assertTrue(f.session.state.value.active);advanceTimeBy(101);runCurrent();assertEquals(GeminiErrorCode.EXPIRED,f.session.state.value.errorCode);assertEquals(1,f.connections.size)}
 @Test fun inputAndOutputStatesDoNotInventProcessing()=scenario{f->ready(f);f.audios[0].events.microphone(AudioLevel(.2f,.8f));runCurrent();assertEquals(GeminiPhase.USER_SPEAKING,f.session.state.value.phase);f.session.mute();f.audios[0].events.playback(0,true);f.audios[0].events.output(0,AudioLevel(.2f,.8f));runCurrent();assertEquals(GeminiPhase.MUTED,f.session.state.value.phase);assertTrue(f.session.state.value.outputActive)}
 @Test fun processingRequiresProviderEvidence()=scenario{f->ready(f);advanceTimeBy(400);runCurrent();assertEquals(GeminiPhase.LISTENING,f.session.state.value.phase);f.connections[0].events.message("{\"serverContent\":{\"turnComplete\":true,\"interactionStatus\":\"IN_PROGRESS\"}}");runCurrent();assertEquals(GeminiPhase.PROCESSING,f.session.state.value.phase)}
 @Test fun malformedMessagesFailSafelyAndStopCapture()=scenario{f->ready(f);f.connections[0].events.message("{bad-json");runCurrent();assertEquals(GeminiErrorCode.PROTOCOL,f.session.state.value.errorCode);assertEquals(1,f.audios[0].closes)}
 @Test fun duplicateFailuresCannotDoubleClose()=scenario{f->ready(f);repeat(4){f.connections[0].events.failed(providerFailure(429))};runCurrent();assertEquals(1,f.audios[0].closes);assertEquals(1,f.connections[0].cancelCount)}
 @Test fun setupTimeoutDoesNotRetryBillableSession()=scenario{f->f.start();f.connections[0].events.opened();runCurrent();advanceTimeBy(25001);runCurrent();assertEquals(GeminiErrorCode.TIMEOUT,f.session.state.value.errorCode);assertEquals(1,f.connections.size);assertTrue(f.audios.isEmpty())}
 @Test fun boundedInboxRejectsUnprocessedFlood()=scenario{f->f.start();repeat(100){f.connections[0].events.message("{}")};runCurrent();assertEquals(GeminiErrorCode.BACKPRESSURE,f.session.state.value.errorCode);assertEquals(1,f.connections.size)}
 @Test fun routeSelectionUsesActivePlatformAndStopsOnFailure()=scenario{f->ready(f);f.session.selectRoute(7);assertEquals(listOf(7),f.platform.selected);f.platform.routeFailure=true;f.session.selectRoute(8);runCurrent();assertEquals(GeminiErrorCode.PLAYBACK,f.session.state.value.errorCode);assertEquals(1,f.audios[0].closes)}
 @Test fun invalidConfigurationDoesNotAcquireAudioOrConnect()=scenario{f->f.session.start(GeminiConfiguration("bad/model","Kore","fixture"));runCurrent();assertEquals(GeminiErrorCode.CONFIGURATION,f.session.state.value.errorCode);assertTrue(f.connections.isEmpty());assertEquals(0,f.platform.acquires);assertFalse(f.ownership.occupied())}
 @Test fun nativeCaptureFailureReleasesPlatformAndSocket()=scenario{f->ready(f);f.audios[0].events.failed(GeminiFailure(GeminiErrorCode.CAPTURE,"fixture capture error"));runCurrent();assertEquals(GeminiErrorCode.CAPTURE,f.session.state.value.errorCode);assertEquals(1,f.platform.releases);assertEquals(1,f.connections[0].cancelCount);assertFalse(f.ownership.occupied())}
 @Test fun disconnectedSocketRequiresManualRestart()=scenario{f->ready(f);f.connections[0].events.closed(1006);runCurrent();assertEquals(GeminiErrorCode.SESSION_ENDED,f.session.state.value.errorCode);advanceTimeBy(30000);runCurrent();assertEquals(1,f.connections.size);assertEquals(1,f.audios[0].closes)}

}
