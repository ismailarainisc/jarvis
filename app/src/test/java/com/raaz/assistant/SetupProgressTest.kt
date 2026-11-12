package com.raaz.assistant

import com.raaz.assistant.data.SettingsStore
import com.raaz.assistant.setup.*
import org.junit.Assert.*
import org.junit.Test

class SetupProgressTest {
 private class Store:SettingsStore {
  val values=mutableMapOf<String,Any>();var fail=false;var badRead=false
  override fun string(key:String,default:String?):String?{if(badRead)error("corrupt fixture");return values[key] as? String?:default}
  override fun boolean(key:String,default:Boolean)=values[key] as? Boolean?:default
  override fun contains(key:String)=key in values
  override fun edit(changes:Map<String,Any?>){if(fail)error("fixture storage failure");changes.forEach{(k,v)->if(v==null)values.remove(k)else values[k]=v}}
 }
 @Test fun firstLaunchStartsWithWelcomeAndNoGrant(){val s=Store();assertEquals(SetupState(),SetupProgress(s).read());assertTrue(s.values.isEmpty())}
 @Test fun resumesPersistedStepAfterRecreation(){val s=Store();SetupProgress(s).move(SetupStep.CONTACTS);assertEquals(SetupStep.CONTACTS,SetupProgress(s).read().step);assertFalse(SetupProgress(s).read().completed)}
 @Test fun finishingOnlyAllowedOnFinalStep(){val p=SetupProgress(Store());assertThrows(IllegalStateException::class.java){p.finish()}}
 @Test fun completionSurvivesRestart(){val s=Store();val p=SetupProgress(s);p.move(SetupStep.COMPLETE);p.finish();assertTrue(SetupProgress(s).read().completed)}
 @Test fun revisitKeepsExistingProviderAndMode(){val s=Store();s.values["gemini_live_key"]="isolated-cipher";s.values["home_voice_mode"]="ANDROID_FALLBACK";val p=SetupProgress(s);p.move(SetupStep.COMPLETE);p.finish();p.revisit();assertEquals(SetupStep.WELCOME,p.read().step);assertFalse(p.read().completed);assertEquals("isolated-cipher",s.values["gemini_live_key"]);assertEquals("ANDROID_FALLBACK",s.values["home_voice_mode"])}
 @Test fun revisitingIncompleteSetupDoesNotLoseProgress(){val p=SetupProgress(Store());p.move(SetupStep.ACCESSIBILITY);p.revisit();assertEquals(SetupStep.ACCESSIBILITY,p.read().step)}
 @Test fun skippingAllOptionalStepsDoesNotGrantAnyPermission(){val s=Store();val p=SetupProgress(s);repeat(10){p.next()};p.finish();assertEquals(setOf("setup7_step","setup7_completed"),s.values.keys)}
 @Test fun corruptMetadataIsVisibleAndNonDestructive(){val s=Store();s.values["gemini_live_key"]="isolated-cipher";s.badRead=true;val result=SetupProgress(s).read();assertNotNull(result.error);assertFalse(result.completed);assertEquals("isolated-cipher",s.values["gemini_live_key"])}
 @Test fun failedSaveDoesNotPretendSetupCompleted(){val s=Store();val p=SetupProgress(s);p.move(SetupStep.COMPLETE);s.fail=true;assertThrows(IllegalStateException::class.java){p.finish()};assertFalse(p.read().completed)}
 @Test fun nextCannotAdvanceBeyondComplete(){val p=SetupProgress(Store());p.move(SetupStep.COMPLETE);assertEquals(SetupStep.COMPLETE,p.next())}
}
