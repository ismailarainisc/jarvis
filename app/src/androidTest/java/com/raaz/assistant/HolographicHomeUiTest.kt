package com.raaz.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.raaz.assistant.background.WakeState
import com.raaz.assistant.data.AssistantUiState
import com.raaz.assistant.data.Message
import com.raaz.assistant.gemini.*
import com.raaz.assistant.ui.PremiumHomeScreen
import com.raaz.assistant.voice.HomeVoiceUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Rendering/navigation only. These tests do not establish a Google or Android audio session. */
class HolographicHomeUiTest {
 @get:Rule val rule=createComposeRule()
 private fun home(native:GeminiLiveUi=GeminiLiveUi(),tasks:()->Unit={},memory:()->Unit={},tools:()->Unit={},ui:AssistantUiState=AssistantUiState()) {
  rule.setContent {MaterialTheme {PremiumHomeScreen(ui,native,GeminiVoiceConfiguration("fixture","Kore",true),HomeVoiceUi(),false,GeminiLiveUi(),WakeState(),onStart={},onStop={},onMute={},onMode={},onVoiceSettings={},onHistory={},onTools=tools,onMemory=memory,onSettings={},onCamera={},onRemovePhoto={},onSend={},onStopText={},onTasks=tasks)}}
 }
 @Test fun idleDoesNotPretendConnectedOrInventTranscript() {
  home()
  rule.onNodeWithText("Gemini Live · Connected").assertDoesNotExist()
  rule.onNodeWithText("Hello, sir. How can I help?").assertDoesNotExist()
  rule.onNodeWithContentDescription("Start conversation").assertIsDisplayed()
 }
 @Test fun bottomTabsInvokeDistinctRealNavigationCallbacks() {
  var tasks=0;var memory=0;var tools=0
  home(tasks={tasks++},memory={memory++},tools={tools++})
  rule.onNodeWithText("Tasks").performClick();rule.onNodeWithText("Memory").performClick();rule.onNodeWithText("Tools").performClick()
  rule.runOnIdle {assertEquals(1,tasks);assertEquals(1,memory);assertEquals(1,tools)}
 }
 @Test fun providerReadinessAndMuteAreActualState() {
  home(GeminiLiveUi(phase=GeminiPhase.MUTED,ready=true,muted=true))
  rule.onNodeWithTag("home-content").performScrollToNode(hasText("Gemini Live · Connected"))
  rule.onNodeWithText("Gemini Live · Connected").assertIsDisplayed()
  rule.onNodeWithContentDescription("Unmute microphone").assertIsDisplayed()
 }
 @Test fun finishedTextReplyRemainsVisibleInNativeMode() {
  home(ui=AssistantUiState(messages=listOf(Message("assistant","Completed text fixture response"))))
  rule.onNodeWithTag("home-content").performScrollToNode(hasText("Completed text fixture response"))
  rule.onNodeWithText("Completed text fixture response").assertIsDisplayed()
 }
 @Test fun shutdownDoesNotClaimConnectedEvenBeforeReadinessClears() {
  home(GeminiLiveUi(phase=GeminiPhase.DISCONNECTING,ready=true))
  rule.onNodeWithText("Gemini Live · Connected").assertDoesNotExist()
 }

}
