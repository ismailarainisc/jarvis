package com.raaz.assistant

import androidx.test.platform.app.InstrumentationRegistry
import com.raaz.assistant.data.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.util.UUID

/** Requires a real Android runtime; never touches the user's normal settings or encryption alias. */
class CredentialStorageTest {
 @Test fun encryptedCredentialRoundTripAndRemoval(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="test-settings-${UUID.randomUUID()}";val alias="test-key-${UUID.randomUUID()}"
  val store=AndroidSettingsStore(context,name);val cipher=AndroidCredentialCipher(alias)
  try{
   val repo=SettingsRepository(store,cipher)
   repo.saveGeminiLive("gemini-3.8-live","Kore","device-test-credential")
   assertFalse(store.string("gemini_live_key")!!.contains("device-test-credential"))
   val restored=SettingsRepository(AndroidSettingsStore(context,name),AndroidCredentialCipher(alias))
   assertEquals("device-test-credential",restored.geminiLiveKey())
   restored.removeGeminiLiveKey();assertFalse(restored.hasGeminiLiveKey())
  }finally{context.getSharedPreferences(name,0).edit().clear().commit();KeyStore.getInstance("AndroidKeyStore").apply{load(null);if(containsAlias(alias))deleteEntry(alias)}}
 }
 @Test fun missingEncryptionKeyDoesNotCreateReplacementDuringDecrypt(){
  val alias="missing-test-key-${UUID.randomUUID()}";val cipher=AndroidCredentialCipher(alias)
  try{cipher.decrypt(android.util.Base64.encodeToString(ByteArray(28),android.util.Base64.NO_WRAP));fail("missing key accepted")}catch(e:CredentialStorageException){assertTrue(e.message!!.contains("unavailable"))}
  assertFalse(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.containsAlias(alias))
 }
}
