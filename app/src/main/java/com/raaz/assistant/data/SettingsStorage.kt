package com.raaz.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SettingsStore {
 fun string(key:String,default:String?=null):String?
 fun boolean(key:String,default:Boolean):Boolean
 fun contains(key:String):Boolean
 fun edit(changes:Map<String,Any?>)
}
class AndroidSettingsStore(context:Context,name:String="settings"):SettingsStore {
 private val prefs=context.applicationContext.getSharedPreferences(name,Context.MODE_PRIVATE)
 override fun string(key:String,default:String?)=prefs.getString(key,default)
 override fun boolean(key:String,default:Boolean)=prefs.getBoolean(key,default)
 override fun contains(key:String)=prefs.contains(key)
 override fun edit(changes:Map<String,Any?>){val edit=prefs.edit();changes.forEach{(key,value)->when(value){null->edit.remove(key);is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);else->error("Unsupported settings type")}};edit.apply()}
}
interface CredentialCipher {fun encrypt(value:String):String;fun decrypt(value:String):String}
class CredentialStorageException(message:String):IllegalStateException(message)
/** Existing on-disk format remains Base64(12-byte IV + AES-GCM ciphertext/tag). */
class AndroidCredentialCipher(private val alias:String="jarvis_key"):CredentialCipher {
 companion object {private val keyLock=Any()}
 private fun key(create:Boolean):SecretKey=synchronized(keyLock){
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  if(!ks.containsAlias(alias)){
   if(!create)throw CredentialStorageException("The saved credential's device encryption key is unavailable. Re-enter the credential; stored data was not overwritten.")
   KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generateKey()}
  }
  ks.getKey(alias,null) as? SecretKey?:throw CredentialStorageException("Device credential encryption is unavailable.")
 }
 override fun encrypt(value:String):String=try{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)}catch(e:CredentialStorageException){throw e}catch(e:Exception){throw CredentialStorageException("Could not securely save the credential. Check Android device security.")}
 override fun decrypt(value:String):String=try{
  if(value.length>65536)throw CredentialStorageException("Saved credential is damaged. Re-enter it in settings.")
  val bytes=Base64.decode(value,Base64.NO_WRAP)
  if(bytes.size<28)throw CredentialStorageException("Saved credential is damaged. Re-enter it in settings.")
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
 }catch(e:CredentialStorageException){throw e}catch(e:Exception){throw CredentialStorageException("Saved credential cannot be decrypted. Re-enter it in settings; stored data was not overwritten.")}
}
