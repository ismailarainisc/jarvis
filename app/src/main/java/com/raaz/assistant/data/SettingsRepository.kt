package com.raaz.assistant.data

import android.content.Context
import com.raaz.assistant.gemini.*
import org.json.JSONArray
import org.json.JSONObject

/** Non-destructive compatibility with the original `settings` file, `secret` and `jarvis_key` alias. */
class SettingsRepository internal constructor(private val prefs:SettingsStore,private val cipher:CredentialCipher) {
 constructor(context:Context):this(AndroidSettingsStore(context),AndroidCredentialCipher())
 private val defaultUrl="https://integrate.api.nvidia.com/v1/chat/completions"
 fun read():Settings {
  val selected=profiles().find{it.id==selectedId()}
  return Settings(url=selected?.url?:"",model=selected?.model?:"",dark=prefs.boolean("dark",true),language=prefs.string("language","System")?:"System",voice=prefs.boolean("voice",true),memory=prefs.boolean("memory",false),hasKey=selected?.hasKey==true,streaming=selected?.streaming==true)
 }
 fun save(s:Settings,key:String?){
  val changes=mutableMapOf<String,Any?>("dark" to s.dark,"language" to s.language,"voice" to s.voice,"memory" to s.memory)
  if(!key.isNullOrBlank()){
   val id=selectedId();val selected=profiles().find{it.id==id}?:throw CredentialStorageException("Select a provider before saving its credential.")
   require(selected.url==s.url){"Enter a new API key through the provider editor when changing endpoints."}
   changes["provider_key_$id"]=cipher.encrypt(key);if(id=="legacy")changes["secret"]=null
  }
  prefs.edit(changes)
 }
 fun selectedId()=prefs.string("selected_provider","legacy")?:"legacy"
 private fun hasCredential(id:String)=prefs.contains("provider_key_$id")||(id=="legacy"&&prefs.contains("secret"))
 fun profiles():List<ProviderConfiguration>{
  val raw=prefs.string("providers")?:return listOf(ProviderConfiguration("legacy","Existing provider",prefs.string("url",defaultUrl)?:defaultUrl,prefs.string("model","")?:"",false,hasCredential("legacy")))
  try{
   val arr=JSONArray(raw);val result=(0 until arr.length()).map{val x=arr.getJSONObject(it);val id=x.getString("id");ProviderConfiguration(id,x.getString("name"),x.getString("url"),x.getString("model"),x.optBoolean("streaming"),hasCredential(id))}
   if(result.any{it.id.isBlank()}||result.map{it.id}.distinct().size!=result.size)throw IllegalArgumentException()
   return result
  }catch(e:Exception){throw CredentialStorageException("Stored provider profiles are damaged. Existing data was preserved; repair or restore the profiles before editing.")}
 }
 private fun encode(list:List<ProviderConfiguration>):String=JSONArray().also{arr->list.forEach{arr.put(JSONObject().put("id",it.id).put("name",it.name).put("url",it.url).put("model",it.model).put("streaming",it.streaming))}}.toString()
 fun saveProfile(profile:ProviderConfiguration,key:String?){
  require(profile.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))&&validEndpoint(profile.url)&&profile.model.isNotBlank()&&profile.name.isNotBlank())
  val existing=profiles();val old=existing.find{it.id==profile.id}
  if(old?.hasKey==true&&old.url!=profile.url&&key.isNullOrBlank())error("Enter a new API key when changing a saved endpoint.")
  val changes=mutableMapOf<String,Any?>("providers" to encode(existing.filterNot{it.id==profile.id}+profile))
  if(!key.isNullOrBlank()){changes["provider_key_${profile.id}"]=cipher.encrypt(key);if(profile.id=="legacy")changes["secret"]=null}
  prefs.edit(changes)
 }
 fun selectProfile(id:String){require(profiles().any{it.id==id});prefs.edit(mapOf("selected_provider" to id))}
 fun removeProfile(id:String){val remaining=profiles().filterNot{it.id==id};val changes=mutableMapOf<String,Any?>("providers" to encode(remaining),"provider_key_$id" to null);if(id=="legacy")changes["secret"]=null;if(selectedId()==id)changes["selected_provider"]=remaining.firstOrNull()?.id?:"";prefs.edit(changes)}
 fun deleteProfileKey(id:String){val changes=mutableMapOf<String,Any?>("provider_key_$id" to null);if(id=="legacy")changes["secret"]=null;prefs.edit(changes)}
 fun deleteKey()=deleteProfileKey(selectedId())
 fun key():String {
  val id=selectedId();if(profiles().none{it.id==id})throw CredentialStorageException("Select a provider and add its API key in settings.")
  val value=prefs.string("provider_key_$id")?:if(id=="legacy")prefs.string("secret")else null
  if(value==null)throw CredentialStorageException("Add an API key for the selected provider in settings.")
  return cipher.decrypt(value)
 }
 fun liveEndpoint()=prefs.string("live_backend","")?:""
 fun hasLiveToken()=prefs.contains("live_backend_token")
 fun saveLive(url:String,token:String){require(validEndpoint(url));require(token.isNotBlank()||hasLiveToken());if(liveEndpoint()!=url)require(token.isNotBlank());val changes=mutableMapOf<String,Any?>("live_backend" to url);if(token.isNotBlank())changes["live_backend_token"]=cipher.encrypt(token);prefs.edit(changes)}
 fun liveToken()=cipher.decrypt(prefs.string("live_backend_token")?:throw CredentialStorageException("Configure your authenticated backend session token first."))
 fun removeLiveToken()=prefs.edit(mapOf("live_backend_token" to null))
 fun geminiLiveModel()=prefs.string("gemini_live_model",DEFAULT_GEMINI_LIVE_MODEL)?:DEFAULT_GEMINI_LIVE_MODEL
 fun geminiLiveVoice()=prefs.string("gemini_live_voice","Kore")?:"Kore"
 fun hasGeminiLiveKey()=prefs.contains("gemini_live_key")
 fun geminiLiveKey()=cipher.decrypt(prefs.string("gemini_live_key")?:throw CredentialStorageException("Save your Google AI Studio credential in Gemini Live settings first."))
 fun saveGeminiLive(model:String,voice:String,key:String){
  val normalized=geminiModelName(model).removePrefix("models/");validateGeminiVoice(voice)
  val changes=mutableMapOf<String,Any?>("gemini_live_model" to normalized,"gemini_live_voice" to voice)
  if(key.isNotBlank()){geminiSocketAuth(key);changes["gemini_live_key"]=cipher.encrypt(key)}else require(hasGeminiLiveKey()){"Enter a Google credential."}
  prefs.edit(changes)
 }
 fun removeGeminiLiveKey()=prefs.edit(mapOf("gemini_live_key" to null))
 fun importSelectedGeminiKey(){val s=read();require(validEndpoint(s.url)&&java.net.URI(s.url).host=="generativelanguage.googleapis.com");val credential=key();geminiSocketAuth(credential);prefs.edit(mapOf("gemini_live_key" to cipher.encrypt(credential)))}
}
