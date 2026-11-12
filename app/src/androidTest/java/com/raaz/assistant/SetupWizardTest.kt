package com.raaz.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.raaz.assistant.data.AndroidSettingsStore
import com.raaz.assistant.setup.*
import com.raaz.assistant.gemini.GeminiLiveController
import com.raaz.assistant.features.capabilitySnapshot
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

fun finishSetupForTest(rule:ComposeContentTestRule){
 repeat(10){if(rule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("Continue").performClick();rule.waitForIdle()}}
 if(rule.onAllNodesWithText("Complete setup and open Home").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("Complete setup and open Home").performClick();rule.waitForIdle()}
}
/** Device tests; compiling them is not evidence that these assertions executed. */
class SetupWizardTest {
 @get:Rule val rule=createComposeRule()
 @Test fun firstLaunchCanCompleteWithoutOptionalGrants(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-setup-${UUID.randomUUID()}";val p=SetupProgress(AndroidSettingsStore(context,name));val controller=GeminiLiveController(context){}
  val before=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO);var done=false
  try {
   rule.setContent{MaterialTheme{SetupWizard(controller,{done=true},{},{},{},p)}}
   rule.onNodeWithText("Welcome to JARVIS").assertIsDisplayed()
   finishSetupForTest(rule)
   rule.runOnIdle{assertTrue(done);assertTrue(p.read().completed);assertEquals(before,ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))}
  }finally{controller.close();context.getSharedPreferences(name,0).edit().clear().commit()}
 }
 @Test fun snapshotReportsActualCurrentPermission(){val c=InstrumentationRegistry.getInstrumentation().targetContext;val snapshot=capabilitySnapshot(c);assertEquals(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,snapshot.microphone);assertEquals(ContextCompat.checkSelfPermission(c,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED,snapshot.contacts)}
 @Test fun scopedAccessibilitySetupRequiresExplicitDisclosedConsent(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-disclosure-${UUID.randomUUID()}";val progress=SetupProgress(AndroidSettingsStore(context,name));progress.move(SetupStep.ACCESSIBILITY);val controller=GeminiLiveController(context){}
  try{rule.setContent{MaterialTheme{SetupWizard(controller,{},{},{},{},progress)}};rule.onNodeWithText("Open Android Accessibility Settings").performScrollTo().assertIsNotEnabled();rule.onNodeWithContentDescription("I understand limited screen-navigation access").performScrollTo().assertIsOff().performClick();rule.onNodeWithText("Open Android Accessibility Settings").assertIsEnabled()}finally{controller.close();context.getSharedPreferences(name,0).edit().clear().commit()}
 }

}
