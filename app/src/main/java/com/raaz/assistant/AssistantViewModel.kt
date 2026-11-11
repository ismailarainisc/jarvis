package com.raaz.assistant
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raaz.assistant.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AssistantViewModel(app:Application): AndroidViewModel(app) {
 private val settingsRepo=SettingsRepository(app);private val repo=AssistantRepository(app);private var photo:Bitmap?=null;private var job:Job?=null;@Volatile private var sequence=0L
 private val _ui=MutableStateFlow(initialState());val ui=_ui.asStateFlow()
 private fun initialState():AssistantUiState {
  val messages=try{repo.load()}catch(e:Exception){return AssistantUiState(state=AssistantState.ERROR,error="Stored history could not be read. Existing data was preserved. Restore it or explicitly clear history before sending.")}
  return try{AssistantUiState(settings=settingsRepo.read(),messages=messages,providers=settingsRepo.profiles(),selectedProvider=settingsRepo.selectedId())}catch(e:Exception){AssistantUiState(messages=messages,state=AssistantState.ERROR,error="Stored settings could not be read. Existing data was preserved. Restore settings or repair provider profiles before editing.")}
 }
 private fun refresh(){try{val settings=settingsRepo.read();val profiles=settingsRepo.profiles();val id=settingsRepo.selectedId();_ui.update{it.copy(settings=settings,providers=profiles,selectedProvider=id)}}catch(e:Exception){fail("Stored settings could not be read. Existing data was preserved.")}}
 private fun changeSettings(action:()->Unit){try{action();refresh()}catch(e:Exception){fail("Could not update provider settings. Existing data was preserved.")}}
 fun status(state:AssistantState){_ui.update{it.copy(state=state,error=null)}}
 fun voiceActive(active:Boolean){_ui.update{it.copy(voiceActive=active)}}
 fun transcript(value:String){_ui.update{it.copy(transcript=value)}}
 fun level(value:Float){_ui.update{it.copy(microphoneLevel=value)}}
 fun fail(message:String){_ui.update{it.copy(state=AssistantState.ERROR,error=message)}}
 fun attach(bitmap:Bitmap){photo=bitmap;_ui.update{it.copy(imageAttached=true)}}
 fun removePhoto(){photo=null;_ui.update{it.copy(imageAttached=false)}}
 fun saveSettings(settings:Settings,key:String?=null){try{settingsRepo.save(settings,key);refresh()}catch(e:Exception){fail("Could not securely save settings.")}}
 fun saveProfile(id:String?,name:String,url:String,model:String,stream:Boolean,key:String){if(ui.value.generating||ui.value.voiceActive){fail("Stop the current conversation before changing providers.");return};try{val p=ProviderConfiguration(id?:UUID.randomUUID().toString(),name.trim(),url.trim(),model.trim(),stream);settingsRepo.saveProfile(p,key);settingsRepo.selectProfile(p.id);refresh();_ui.update{it.copy(providerStatus=it.providerStatus-p.id,error=null)}}catch(e:Exception){fail(if(e.message=="Enter a new API key when changing a saved endpoint.") e.message!! else "Enter a name, HTTPS URL without query or credentials, and model ID.")}}
 fun selectProfile(id:String){if(ui.value.generating||ui.value.voiceActive){fail("Stop the current conversation before changing providers.");return};changeSettings{settingsRepo.selectProfile(id)}}
 fun removeProfile(id:String){if(ui.value.generating||ui.value.voiceActive){fail("Stop the current conversation before changing providers.");return};changeSettings{settingsRepo.removeProfile(id)}}
 fun deleteKey(){changeSettings{settingsRepo.deleteProfileKey(settingsRepo.selectedId())}}
 fun clearHistory(){if(ui.value.generating||ui.value.voiceActive){fail("Stop the current conversation before clearing history.");return};repo.clear();_ui.update{it.copy(messages=emptyList(),state=AssistantState.IDLE,error=null)};refresh()}
 fun send(text:String,onReply:(String)->Unit,onFailure:(String)->Unit={}){if(text.isBlank()||job?.isActive==true){onFailure("Another request is already in progress.");return};val token=++sequence;val current=ui.value;val added=(current.messages+Message("user",text.trim())).takeLast(100);try{repo.save(added)}catch(e:Exception){val message="Could not save chat history. Existing data was preserved; restore it or explicitly clear history.";fail(message);onFailure(message);return};_ui.update{it.copy(messages=added,state=AssistantState.CONNECTING,error=null,generating=true,streamingText="")};val captured=photo
 job=viewModelScope.launch {try{val key=settingsRepo.key();status(AssistantState.THINKING);val answer=repo.reply(current.settings,key,added,captured){chunk->if(sequence==token)_ui.update{it.copy(streamingText=it.streamingText+chunk)}};if(sequence!=token)return@launch;val all=(ui.value.messages+Message("assistant",answer)).takeLast(100);repo.save(all);photo=null;_ui.update{it.copy(messages=all,imageAttached=false,state=if(it.voiceActive)AssistantState.PROCESSING else AssistantState.IDLE,generating=false,streamingText="")};onReply(answer)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException||sequence!=token)throw e;val msg=when(e){is javax.net.ssl.SSLException->"Secure connection failed. Check the provider certificate.";is java.net.SocketTimeoutException->"Provider request timed out.";is java.io.IOException->"Network connection failed. Check your internet and endpoint.";else->e.message?:"Could not contact the provider."};_ui.update{it.copy(generating=false,streamingText="")};fail(msg);onFailure(msg)}} }
 fun testProvider(){if(job?.isActive==true||ui.value.voiceActive){fail("Stop the current conversation before testing.");return};val id=settingsRepo.selectedId();_ui.update{it.copy(providerStatus=it.providerStatus+(id to ProviderStatus(ProviderConnectionState.TESTING,"Testing model inference…")))};job=viewModelScope.launch {try{val s=ui.value.settings.copy(streaming=false);repo.reply(s,settingsRepo.key(),listOf(Message("user","Reply with OK.")),null);_ui.update{it.copy(providerStatus=it.providerStatus+(id to ProviderStatus(ProviderConnectionState.CONNECTED,"Authenticated model inference succeeded.")))}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;val msg=if(e is java.io.IOException)"Network or TLS connection failed." else e.message?:"Request failed.";_ui.update{it.copy(providerStatus=it.providerStatus+(id to ProviderStatus(ProviderConnectionState.FAILED,msg)))}}}}
 override fun onCleared(){repo.close();super.onCleared()}
 fun stop(){sequence++;job?.cancel();repo.cancel();job=null;_ui.update{it.copy(generating=false,streamingText="")};status(AssistantState.STOPPED)}
}
