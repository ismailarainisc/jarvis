package com.raaz.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Compiles in CI; execution needs an attached Android runtime. No calls/messages are triggered. */
class AssistantHubNavigationTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun memoryToolsAndDiagnosticsHaveRealScreens(){
  rule.onNodeWithText("Tools & background").performClick()
  rule.onNodeWithText("Allow Gemini to propose tools (approval always required)").assertExists()
  rule.onNodeWithText("Memory").performClick()
  rule.onNodeWithContentDescription("Enable local personal memory").assertExists()
  rule.onNodeWithContentDescription("Back").performClick()
  rule.onNodeWithContentDescription("Settings").performClick()
  rule.onNodeWithText("About & diagnostics").performScrollTo().performClick()
  rule.onNodeWithText("No keys, raw audio or private transcripts are included in this screen. Instrumented, real-device, account and battery verification must be recorded separately.").assertExists()
 }
}
