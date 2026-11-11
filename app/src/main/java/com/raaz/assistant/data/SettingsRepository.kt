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

class SettingsRepository(context: Context) {
    // Same preference file, fields and keystore alias as the Java prototype.
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun read():Settings { val base=Settings(prefs.getString("url", "https://integrate.api.nvidia.com/v1/chat/completions") ?: "", prefs.getString("model", "") ?: "", prefs.getBoolean("dark", true), prefs.getString("language", "System") ?: "System", prefs.getBoolean("voice", true), prefs.getBoolean("memory", false), prefs.contains("secret")); val selected=profiles().find{it.id==selectedId()}; return if(selected==null) base else base.copy(url=selected.url,model=selected.model,hasKey=selected.hasKey,streaming=selected.streaming) }
    fun save(s: Settings, key: String?) { val encrypted = key?.takeIf { it.isNotBlank() }?.let(::encrypt); prefs.edit().putString("url",s.url).putString("model",s.model).putBoolean("dark",s.dark).putString("language",s.language).putBoolean("voice",s.voice).putBoolean("memory",s.memory).apply { if (encrypted != null) putString("secret", encrypted) }.apply() }
    fun deleteKey() { prefs.edit().remove("secret").apply() }

    fun selectedId():String = prefs.getString("selected_provider", "legacy") ?: "legacy"
    fun profiles():List<ProviderConfiguration> {
        val raw=prefs.getString("providers",null)
        if(raw==null) return listOf(ProviderConfiguration("legacy","Existing provider",prefs.getString("url","") ?: "",prefs.getString("model","") ?: "",false,prefs.contains("secret")))
        return try { val arr=org.json.JSONArray(raw);(0 until arr.length()).map { val x=arr.getJSONObject(it);val id=x.getString("id");ProviderConfiguration(id,x.getString("name"),x.getString("url"),x.getString("model"),x.optBoolean("streaming"),prefs.contains("provider_key_"+id)||(id=="legacy"&&prefs.contains("secret"))) } } catch(e:Exception){ emptyList() }
    }
    private fun writeProfiles(list:List<ProviderConfiguration>,edit:android.content.SharedPreferences.Editor){val arr=org.json.JSONArray();list.forEach {arr.put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("url",it.url).put("model",it.model).put("streaming",it.streaming))};edit.putString("providers",arr.toString())}
    fun saveProfile(profile:ProviderConfiguration,key:String?){ require(validEndpoint(profile.url) && profile.model.isNotBlank() && profile.name.isNotBlank()); val old=profiles().find{it.id==profile.id};if(old!=null && old.hasKey && old.url!=profile.url && key.isNullOrBlank()) error("Enter a new API key when changing a saved endpoint."); val list=profiles().filterNot{it.id==profile.id}+profile;val e=prefs.edit();writeProfiles(list,e);if(!key.isNullOrBlank()) e.putString("provider_key_"+profile.id,encrypt(key));e.apply() }
    fun selectProfile(id:String){require(profiles().any{it.id==id});prefs.edit().putString("selected_provider",id).apply()}
    fun removeProfile(id:String){val e=prefs.edit().remove("provider_key_"+id);if(id=="legacy")e.remove("secret");val remaining=profiles().filterNot{it.id==id};writeProfiles(remaining,e);if(selectedId()==id)e.putString("selected_provider",remaining.firstOrNull()?.id ?: "");e.apply()}
    fun deleteProfileKey(id:String){val e=prefs.edit().remove("provider_key_"+id);if(id=="legacy")e.remove("secret");e.apply()}
    fun liveEndpoint():String=prefs.getString("live_backend","")?:""
    fun hasLiveToken()=prefs.contains("live_backend_token")
    fun saveLive(url:String,token:String){require(validEndpoint(url));require(token.isNotBlank()||hasLiveToken());if(liveEndpoint()!=url)require(token.isNotBlank());val e=prefs.edit().putString("live_backend",url);if(token.isNotBlank())e.putString("live_backend_token",encrypt(token));e.apply()}
    fun liveToken():String{val value=prefs.getString("live_backend_token",null)?:error("Configure your authenticated backend session token first.");return decryptValue(value)}
    fun removeLiveToken(){prefs.edit().remove("live_backend_token").apply()}
    private fun decryptValue(value:String):String{val bytes=Base64.decode(value,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));return String(c.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)}
    fun geminiLiveModel():String=prefs.getString("gemini_live_model",com.raaz.assistant.gemini.DEFAULT_GEMINI_LIVE_MODEL)?:com.raaz.assistant.gemini.DEFAULT_GEMINI_LIVE_MODEL
    fun geminiLiveVoice():String=prefs.getString("gemini_live_voice","Kore")?:"Kore"
    fun hasGeminiLiveKey()=prefs.contains("gemini_live_key")
    fun geminiLiveKey():String=decryptValue(prefs.getString("gemini_live_key",null)?:error("Save your Google AI Studio API key in Gemini Live settings first."))
    fun saveGeminiLive(model:String,voice:String,key:String){com.raaz.assistant.gemini.geminiModelName(model);require(voice.matches(Regex("[A-Za-z0-9_-]{1,60}")));val encrypted=key.takeIf{it.isNotBlank()}?.let(::encrypt);require(encrypted!=null||hasGeminiLiveKey()){ "Enter a Google AI Studio API key." };prefs.edit().putString("gemini_live_model",model.removePrefix("models/").trim()).putString("gemini_live_voice",voice).apply{if(encrypted!=null)putString("gemini_live_key",encrypted)}.apply()}
    fun removeGeminiLiveKey(){prefs.edit().remove("gemini_live_key").apply()}
    fun importSelectedGeminiKey(){require(java.net.URI(read().url).host=="generativelanguage.googleapis.com"){"Select a Google Gemini provider in AI Providers first."};prefs.edit().putString("gemini_live_key",encrypt(key())).apply()}
    private fun secret(): SecretKey { val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}; if(!ks.containsAlias("jarvis_key")) { val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore"); generator.init(KeyGenParameterSpec.Builder("jarvis_key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey() }; return ks.getKey("jarvis_key",null) as SecretKey }
    private fun encrypt(value:String):String { val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,secret()); return Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP) }
    fun key():String { val value=prefs.getString("provider_key_"+selectedId(),null) ?: if(selectedId()=="legacy") prefs.getString("secret",null) else null ?: error("Add an API key in AI Providers."); val bytes=Base64.decode(value,Base64.NO_WRAP); val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8) }
}
