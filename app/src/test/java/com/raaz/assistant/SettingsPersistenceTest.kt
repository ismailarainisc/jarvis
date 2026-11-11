package com.raaz.assistant

import com.raaz.assistant.data.*
import org.junit.Assert.*
import org.junit.Test

/** Fake cipher/store isolate repository migration logic; Android Keystore has a separate device test. */
class SettingsPersistenceTest {
 private class Store:SettingsStore {
  val data=mutableMapOf<String,Any>()
  override fun string(key:String,default:String?)=data[key] as? String?:default
  override fun boolean(key:String,default:Boolean)=data[key] as? Boolean?:default
  override fun contains(key:String)=data.containsKey(key)
  override fun edit(changes:Map<String,Any?>){changes.forEach{(k,v)->if(v==null)data.remove(k)else data[k]=v}}
 }
 private class Cipher:CredentialCipher {
  var fail=false
  override fun encrypt(value:String):String{if(fail)throw CredentialStorageException("isolated failure");return "test-cipher:$value"}
  override fun decrypt(value:String)=value.removePrefix("test-cipher:")
 }
 private fun profile(id:String="a",url:String="https://example.com/v1/chat/completions")=ProviderConfiguration(id,"Test",url,"test-model",true)
 @Test fun profilesKeysAndSelectionSurviveNewRepository(){val store=Store();val cipher=Cipher();val r=SettingsRepository(store,cipher);r.saveProfile(profile(),"first");r.saveProfile(profile("b"),"second");r.selectProfile("b");r.save(r.read().copy(dark=false,language="Urdu"),null);val restarted=SettingsRepository(store,cipher);assertEquals("second",restarted.key());assertFalse(restarted.read().dark);assertEquals("Urdu",restarted.read().language);assertEquals("b",restarted.selectedId());assertTrue(restarted.read().streaming)}
 @Test fun legacyCredentialIsReadableWithoutDestructiveMigration(){val s=Store();s.data["secret"]="test-cipher:old";val r=SettingsRepository(s,Cipher());assertEquals("old",r.key());r.save(r.read().copy(dark=false),null);assertEquals("test-cipher:old",s.data["secret"])}
 @Test fun missingCredentialGivesActionableError(){val r=SettingsRepository(Store(),Cipher());val e=assertThrows(CredentialStorageException::class.java){r.key()};assertTrue(e.message!!.contains("API key"))}
 @Test fun deletingLastLegacyProfileCannotResurrectSecret(){val s=Store();s.data["secret"]="test-cipher:old";val r=SettingsRepository(s,Cipher());r.removeProfile("legacy");assertTrue(r.profiles().isEmpty());assertFalse(s.contains("secret"));assertThrows(CredentialStorageException::class.java){r.key()};assertTrue(SettingsRepository(s,Cipher()).profiles().isEmpty())}
 @Test fun changingDestinationRequiresExplicitNewCredential(){val s=Store();val r=SettingsRepository(s,Cipher());r.saveProfile(profile(),"old");assertThrows(IllegalStateException::class.java){r.saveProfile(profile(url="https://other.example/v1"),"")};r.selectProfile("a");assertEquals("old",r.key());assertEquals(profile().url,r.read().url)}
 @Test fun failedEncryptionDoesNotChangeProfileOrKey(){val s=Store();val c=Cipher();val r=SettingsRepository(s,c);r.saveProfile(profile(),"old");val before=s.data.toMap();c.fail=true;assertThrows(CredentialStorageException::class.java){r.saveProfile(profile().copy(name="Changed"),"new")};assertEquals(before,s.data)}
 @Test fun corruptProfilesArePreservedRatherThanOverwritten(){val s=Store();s.data["providers"]="{broken";s.data["secret"]="test-cipher:old";val before=s.data.toMap();val r=SettingsRepository(s,Cipher());assertThrows(CredentialStorageException::class.java){r.profiles()};assertThrows(CredentialStorageException::class.java){r.saveProfile(profile(),"new")};assertEquals(before,s.data)}
 @Test fun keyRotationAndRemovalArePerProfile(){val s=Store();val r=SettingsRepository(s,Cipher());r.saveProfile(profile(),"first");r.saveProfile(profile("b"),"second");r.saveProfile(profile(),"rotated");r.selectProfile("a");assertEquals("rotated",r.key());r.deleteKey();assertThrows(CredentialStorageException::class.java){r.key()};r.selectProfile("b");assertEquals("second",r.key())}
 @Test fun geminiSettingsPersistWithoutChangingTextProvider(){val s=Store();val r=SettingsRepository(s,Cipher());r.saveProfile(profile(),"text");r.selectProfile("a");r.saveGeminiLive(" models/gemini-3.8-live ","Kore","isolated-google-key");val restarted=SettingsRepository(s,Cipher());assertEquals("gemini-3.8-live",restarted.geminiLiveModel());assertEquals("isolated-google-key",restarted.geminiLiveKey());assertEquals("text",restarted.key());restarted.removeGeminiLiveKey();assertFalse(restarted.hasGeminiLiveKey());assertEquals("text",restarted.key())}
}
