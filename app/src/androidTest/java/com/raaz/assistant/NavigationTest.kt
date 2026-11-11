package com.raaz.assistant
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class NavigationTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun settingsAndBack(){rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Preferences").assertIsDisplayed();rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithText("PERSONAL AI COMPANION").assertExists()}
}
