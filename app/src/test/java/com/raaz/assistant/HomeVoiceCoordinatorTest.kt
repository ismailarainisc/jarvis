package com.raaz.assistant

import com.raaz.assistant.data.*
import com.raaz.assistant.gemini.*
import com.raaz.assistant.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Tests the production Home/settings command owner; fake audio ports are not provider/device proof. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeVoiceCoordinatorTest {
 private class Store:SettingsStore {
  val data=mutableMapOf<String,Any>();var failSave=false
  override fun string(key:String,default:String?)=data[key] as? String?:default
  override fun boolean(key:String,default:Boolean)=data[key] as? Boolean?:default
  override fun contains(key:String)=data.containsKey(key)
  override fun edit(changes:Map<String,Any?>){if(failSave)throw IllegalStateException("fixture storage failure");changes.forEach{(k,v)->if(v==null)data.remove(k)else data[k]=v}}
 }
 private class Native:HomeNativeVoice {
  val mutable=MutableStateFlow(GeminiLiveUi());override val state=mutable.asStateFlow()
  var starts=0;var stops=0;var mutes=0;var denied=0;var key=true;var holdClose:CompletableDeferred<Unit>?=null
  override fun configured()=key
  override fun setForeground(value:Boolean){}
  override fun setVisible(value:Boolean){}
  override fun start(){starts++;mutable.value=GeminiLiveUi(phase=GeminiPhase.CONNECTING)}
  override fun stop(){stops++;if(state.value.active)mutable.value=GeminiLiveUi(phase=GeminiPhase.DISCONNECTING)}
  override suspend fun stopAndAwait(){stop();holdClose?.await();if(state.value.active)mutable.value=GeminiLiveUi(phase=GeminiPhase.STOPPED)}
  override fun mute(){mutes++}
  override fun permissionDenied(){denied++;mutable.value=GeminiLiveUi(phase=GeminiPhase.ERROR,error="permission denied")}
 }
 private class Fallback:HomeFallbackVoice {var starts=0;var stops=0;var running=false;override fun start(){starts++;running=true};override fun stop(){stops++;running=false};override fun active()=running}
 private class Fixture(test:TestScope){
  val store=Store();val native=Native();val fallback=Fallback();var background=false;var cancelledText=0
  val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(test.testScheduler))
  val coordinator=HomeVoiceCoordinator(scope,native,fallback,VoicePreferences(store),{background},{cancelledText++})
  init{coordinator.setForeground(true);coordinator.setSurfaceVisible(true)}
 }
 private fun scenario(block:suspend TestScope.(Fixture)->Unit)=runTest{val f=Fixture(this);try{block(f)}finally{f.native.holdClose?.complete(Unit);f.coordinator.close();f.scope.cancel()}}
 @Test fun homeStartDefaultsToNativeWithoutStartingFallback()=scenario{f->assertEquals(HomeVoiceMode.GEMINI_LIVE,f.coordinator.state.value.mode);assertEquals(VoiceStartResult.Accepted,f.coordinator.start(true));runCurrent();assertEquals(1,f.native.starts);assertEquals(0,f.fallback.starts);assertFalse(f.coordinator.mayUseDeviceSpeech())}
 @Test fun homeAndSettingsShareOneNativeSession()=scenario{f->f.coordinator.start(true);runCurrent();f.coordinator.setSurfaceVisible(true);assertEquals(VoiceStartResult.Ignored,f.coordinator.start(true));runCurrent();assertEquals(1,f.native.starts)}
 @Test fun noKeyOpensSetupInsteadOfRoboticFallback()=scenario{f->f.native.key=false;assertEquals(VoiceStartResult.SetupRequired,f.coordinator.start(true));runCurrent();assertTrue(f.coordinator.state.value.needsSetup);assertEquals(0,f.native.starts);assertEquals(0,f.fallback.starts);assertNull(f.coordinator.state.value.permissionRequest)}
 @Test fun microphoneDenialNeverStartsEitherEngine()=scenario{f->val result=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.permissionResult(result.ticket,false);runCurrent();assertEquals(1,f.native.denied);assertEquals(0,f.native.starts);assertEquals(0,f.fallback.starts)}
 @Test fun grantedPermissionStartsExactlyOneNativeSession()=scenario{f->val result=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.permissionResult(result.ticket,true);f.coordinator.permissionResult(result.ticket,true);runCurrent();assertEquals(1,f.native.starts)}
 @Test fun stopInvalidatesOutstandingPermissionResult()=scenario{f->val result=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.stop();f.coordinator.permissionResult(result.ticket,true);runCurrent();assertEquals(0,f.native.starts)}
 @Test fun leavingVoiceWorkspaceRejectsDelayedPermission()=scenario{f->val result=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.setSurfaceVisible(false);f.coordinator.permissionResult(result.ticket,true);runCurrent();assertEquals(0,f.native.starts)}
 @Test fun backgroundingRejectsPermissionAndReleasesVoice()=scenario{f->val result=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.setForeground(false);f.coordinator.permissionResult(result.ticket,true);runCurrent();assertEquals(0,f.native.starts);assertFalse(f.coordinator.mayUseDeviceSpeech())}
 @Test fun explicitFallbackSelectionPersistsAndUsesOnlyFallback()=scenario{f->f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.coordinator.start(true);runCurrent();assertEquals(1,f.fallback.starts);assertEquals(0,f.native.starts);assertEquals(HomeVoiceMode.ANDROID_FALLBACK,VoicePreferences(f.store).read());assertTrue(f.coordinator.mayUseDeviceSpeech())}
 @Test fun repeatedFallbackStartDoesNotRestartTheActiveRecognizer()=scenario{f->f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.coordinator.start(true);runCurrent();assertEquals(VoiceStartResult.Ignored,f.coordinator.start(true));runCurrent();assertEquals(1,f.fallback.starts)}
 @Test fun nativeSelectionStopsFallbackAndSuppressesLateTts()=scenario{f->f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.coordinator.start(true);runCurrent();assertTrue(f.fallback.running);f.coordinator.select(HomeVoiceMode.GEMINI_LIVE);assertFalse(f.fallback.running);assertFalse(f.coordinator.mayUseDeviceSpeech());f.coordinator.start(true);runCurrent();assertEquals(1,f.native.starts)}
 @Test fun modeSwitchInvalidatesOldPermissionTicket()=scenario{f->val permission=f.coordinator.start(false) as VoiceStartResult.PermissionRequired;f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.coordinator.permissionResult(permission.ticket,true);runCurrent();assertEquals(0,f.native.starts);assertEquals(0,f.fallback.starts)}
 @Test fun backgroundServicePreventsAnotherForegroundOwner()=scenario{f->f.background=true;assertEquals(VoiceStartResult.Ignored,f.coordinator.start(true));f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);runCurrent();assertEquals(0,f.native.starts);assertEquals(0,f.fallback.starts);assertEquals(HomeVoiceMode.GEMINI_LIVE,f.coordinator.state.value.mode)}
 @Test fun startWaitsForNativeCleanupBeforeFallback()=scenario{f->f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.native.holdClose=CompletableDeferred();f.coordinator.start(true);runCurrent();assertEquals(0,f.fallback.starts);assertTrue(f.coordinator.state.value.switching);f.native.holdClose!!.complete(Unit);runCurrent();assertEquals(1,f.fallback.starts)}
 @Test fun stopDuringCleanupCannotStartAnObsoleteEngine()=scenario{f->f.native.holdClose=CompletableDeferred();f.coordinator.start(true);runCurrent();f.coordinator.stop();f.native.holdClose!!.complete(Unit);runCurrent();assertEquals(0,f.native.starts);assertEquals(0,f.fallback.starts)}
 @Test fun repeatedStartStopDoesNotDuplicateSessions()=scenario{f->repeat(4){f.coordinator.start(true);runCurrent();f.coordinator.stopAndAwait()};assertEquals(4,f.native.starts);assertEquals(0,f.fallback.starts)}
 @Test fun connectionErrorDoesNotAutomaticallyStartDeviceTts()=scenario{f->f.coordinator.start(true);runCurrent();f.native.mutable.value=GeminiLiveUi(phase=GeminiPhase.ERROR,error="API rejected");runCurrent();assertEquals(HomeVoiceMode.GEMINI_LIVE,f.coordinator.state.value.mode);assertEquals(0,f.fallback.starts);assertFalse(f.coordinator.mayUseDeviceSpeech())}
 @Test fun muteOnlyTargetsActualReadyNativeSession()=scenario{f->f.coordinator.mute();assertEquals(0,f.native.mutes);f.native.mutable.value=GeminiLiveUi(phase=GeminiPhase.LISTENING,ready=true);f.coordinator.mute();assertEquals(1,f.native.mutes)}
 @Test fun switchingAwayFromNativeWaitsForCleanup()=scenario{f->f.coordinator.start(true);runCurrent();f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);assertEquals(VoiceStartResult.Ignored,f.coordinator.start(true));assertEquals(0,f.fallback.starts);f.native.stopAndAwait();f.coordinator.start(true);runCurrent();assertEquals(1,f.fallback.starts)}
 @Test fun fallbackBackgroundingCancelsPendingChatAndTts()=scenario{f->f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);f.coordinator.start(true);runCurrent();val before=f.cancelledText;f.coordinator.setForeground(false);assertTrue(f.cancelledText>before);assertFalse(f.fallback.running);assertFalse(f.coordinator.mayUseDeviceSpeech())}
 @Test fun failedPreferenceWritePreservesThePriorMode()=scenario{f->f.store.failSave=true;f.coordinator.select(HomeVoiceMode.ANDROID_FALLBACK);assertEquals(HomeVoiceMode.GEMINI_LIVE,f.coordinator.state.value.mode);assertNotNull(f.coordinator.state.value.error)}
 @Test fun closedCoordinatorCannotReactivateMicrophone()=scenario{f->f.coordinator.close();f.coordinator.start(true);runCurrent();assertEquals(0,f.native.starts)}
 @Test fun nativePresentationCannotBeOverwrittenByFallbackStates(){val model=nativePresentation(GeminiLiveUi(phase=GeminiPhase.ASSISTANT_SPEAKING,ready=true));assertEquals(UnifiedVoicePhase.ASSISTANT_SPEAKING,model.phase);assertEquals(AssistantState.SPEAKING,model.visual);assertFalse(nativePresentation(GeminiLiveUi()).active)}
 @Test fun reconnectingIsOnlyReportedForActualFallbackRetry(){assertEquals(UnifiedVoicePhase.RECONNECTING,fallbackPresentation(AssistantState.RECONNECTING,true).phase);assertEquals(UnifiedVoicePhase.CONNECTING,nativePresentation(GeminiLiveUi(phase=GeminiPhase.CONNECTING)).phase)}
 @Test fun oldSettingsDoNotSilentlySelectFallback(){val store=Store();store.data["voice"]=true;store.data["model"]="old-text-model";store.data["language"]="Urdu";val before=store.data.toMap();assertEquals(HomeVoiceMode.GEMINI_LIVE,VoicePreferences(store).read());assertEquals(before,store.data)}
 @Test fun actualTypedFallbackSpeechHasAStopControl(){assertTrue(fallbackPresentation(AssistantState.SPEAKING,false).active);assertFalse(fallbackPresentation(AssistantState.IDLE,false).active)}

}
