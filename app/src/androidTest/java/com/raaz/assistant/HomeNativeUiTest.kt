package com.raaz.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.raaz.assistant.background.WakeState
import com.raaz.assistant.data.AssistantUiState
import com.raaz.assistant.gemini.*
import com.raaz.assistant.ui.PremiumHomeScreen
import com.raaz.assistant.voice.HomeVoiceUi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated rendering/click tests, not native audio, microphone or Gemini compatibility tests. */
class HomeNativeUiTest {
 @get:Rule val rule=createComposeRule()
 @Test fun nativeHomeStartStopAndMuteUseSuppliedSessionCommands(){
  val actual=mutableStateOf(GeminiLiveUi());var starts=0;var stops=0;var mutes=0
  rule.setContent{MaterialTheme{PremiumHomeScreen(AssistantUiState(),actual.value,GeminiVoiceConfiguration("fixture-model","Kore",true),HomeVoiceUi(),false,GeminiLiveUi(),WakeState(),
   onStart={starts++;actual.value=GeminiLiveUi(phase=GeminiPhase.LISTENING,ready=true,capture=true)},onStop={stops++;actual.value=GeminiLiveUi(phase=GeminiPhase.STOPPED)},onMute={mutes++},onMode={},onVoiceSettings={},onHistory={},onTools={},onMemory={},onSettings={},onCamera={},onRemovePhoto={},onSend={},onStopText={})}}
  rule.onNodeWithContentDescription("Start conversation").performClick();rule.runOnIdle{assertEquals(1,starts)}
  rule.onNodeWithContentDescription("Mute microphone").performClick();rule.runOnIdle{assertEquals(1,mutes)}
  rule.onNodeWithContentDescription("Stop conversation").performClick();rule.runOnIdle{assertEquals(1,stops)}
 }
 @Test fun missingCredentialShowsSetupWithoutFallback(){var setup=0
  rule.setContent{MaterialTheme{PremiumHomeScreen(AssistantUiState(),GeminiLiveUi(),GeminiVoiceConfiguration("fixture-model","Kore",false),HomeVoiceUi(),false,GeminiLiveUi(),WakeState(),onStart={},onStop={},onMute={},onMode={},onVoiceSettings={setup++},onHistory={},onTools={},onMemory={},onSettings={},onCamera={},onRemovePhoto={},onSend={},onStopText={})}}
  rule.onNodeWithTag("home-content").performScrollToNode(hasText("Set up Gemini Live"))
  rule.onNodeWithText("Set up Gemini Live").performClick();rule.runOnIdle{assertEquals(1,setup)}
  rule.onNodeWithText("Fallback voice").assertDoesNotExist()
 }
 @Test fun textOrCameraFailureIsVisibleWithEmptyHistory(){
  rule.setContent{MaterialTheme{PremiumHomeScreen(AssistantUiState(error="Camera application is unavailable."),GeminiLiveUi(),GeminiVoiceConfiguration("fixture-model","Kore",true),HomeVoiceUi(),false,GeminiLiveUi(),WakeState(),onStart={},onStop={},onMute={},onMode={},onVoiceSettings={},onHistory={},onTools={},onMemory={},onSettings={},onCamera={},onRemovePhoto={},onSend={},onStopText={})}}
  rule.onNodeWithTag("home-content").performScrollToNode(hasText("Camera application is unavailable."))
  rule.onNodeWithText("Camera application is unavailable.").assertIsDisplayed()
 }
}
