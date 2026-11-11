package com.raaz.assistant.features

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.raaz.assistant.mcp.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.util.Locale

class AndroidTools(private val activity:Activity,private val store:FeatureStore,private val mcp:McpClient):ToolRunner {
 fun unlocked()=!(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
 private val resolver=ToolResolver(activity,store)
 fun apps()=resolver.apps()
 suspend fun contacts(query:String)=resolver.contacts(query)
 suspend fun prepare(action:ToolAction)=resolver.prepare(action)
 private fun launch(intent:Intent,detail:String):ToolOutcome {
  if(!unlocked())return ToolOutcome(TaskStatus.NEEDS_APPROVAL,"Unlock the device first.")
  return try{activity.startActivity(intent);ToolOutcome(TaskStatus.LAUNCHED,detail)}catch(e:android.content.ActivityNotFoundException){ToolOutcome(TaskStatus.UNSUPPORTED,"No installed application can handle this action.")}catch(e:SecurityException){ToolOutcome(TaskStatus.NEEDS_PERMISSION,"Android denied this action. Review app permissions.")}
 }
 override suspend fun run(action:ToolAction):ToolOutcome=withContext(Dispatchers.Main){
  if(activity is androidx.lifecycle.LifecycleOwner&&!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return@withContext ToolOutcome(TaskStatus.CANCELLED,"Approving screen is no longer in the foreground.")
  if(!unlocked())return@withContext ToolOutcome(TaskStatus.NEEDS_APPROVAL,"Device locked. No action executed.")
  ToolRegistry.validate(action)?.let{return@withContext ToolOutcome(TaskStatus.UNSUPPORTED,it)}
  val a=action.args
  when(action.name){
   "open_app"->{val selected=a.optString("package");val valid=apps().any{it.packageName==selected&&it.label.equals(a.getString("app"),true)};val intent=if(valid)activity.packageManager.getLaunchIntentForPackage(selected)else null;if(intent==null)ToolOutcome(TaskStatus.UNSUPPORTED,"App changed or is no longer installed. Propose it again.")else launch(intent,"App launch requested. Screen content was not verified.")}
   "youtube_search"->{val uri=Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query",a.getString("query")).build();val native=Intent(Intent.ACTION_VIEW,uri).setPackage("com.google.android.youtube");if(native.resolveActivity(activity.packageManager)!=null)launch(native,"YouTube search opened; no video playback claimed.")else launch(Intent(Intent.ACTION_VIEW,uri),"YouTube web search opened; no video playback claimed.")}
   "youtube_open","open_url"->launch(Intent(Intent.ACTION_VIEW,Uri.parse(a.getString("url"))),"Link launch requested. Page load or playback was not verified.")
   "web_search"->launch(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q",a.getString("query")).build()),"Browser search launch requested.")
   "open_settings"->{val page=a.getString("page");val intent=when(page){"app"->Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}"));"wifi"->Intent(Settings.ACTION_WIFI_SETTINGS);"bluetooth"->Intent(Settings.ACTION_BLUETOOTH_SETTINGS);"accessibility"->Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);else->Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)};launch(intent,"Android settings launch requested.")}
   "set_alarm"->launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,a.getInt("hour")).putExtra(AlarmClock.EXTRA_MINUTES,a.getInt("minute")).putExtra(AlarmClock.EXTRA_SKIP_UI,false),"Alarm app opened for confirmation; alarm creation was not independently verified.")
   "set_timer"->launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,a.getInt("seconds")).putExtra(AlarmClock.EXTRA_SKIP_UI,false),"Timer app opened for confirmation; timer creation was not independently verified.")
   "dial","direct_call","sms_compose","whatsapp_compose"->{
    val phone=a.optString("phone");if(!validPhone(phone))return@withContext ToolOutcome(TaskStatus.FAILED,"Recipient needs to be resolved again.")
    val lookup=a.optString("lookup")
    if(lookup.isNotBlank()){
     val current=try{withContext(Dispatchers.IO){resolver.contactStillMatches(lookup,phone)}}catch(e:Exception){return@withContext ToolOutcome(TaskStatus.NEEDS_PERMISSION,"Contacts access is required to verify this saved recipient.")}
     if(!current)return@withContext ToolOutcome(TaskStatus.FAILED,"Contact changed. Select the current number and approve a new request.")
    }
    when(action.name){
     "dial"->launch(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null)),"Dialer opened. No call placement or connection claimed; choose SIM in the dialer.")
     "direct_call"->{if(!store.flag("direct_calls")||ContextCompat.checkSelfPermission(activity,Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)ToolOutcome(TaskStatus.NEEDS_PERMISSION,"Enable direct calls and grant CALL_PHONE first, or use the dialer.")else launch(Intent(Intent.ACTION_CALL,Uri.fromParts("tel",phone,null)),"Call initiation requested after approval. Connection is not verified; Android selects or asks for SIM.")}
     "sms_compose"->launch(Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",phone,null)).putExtra("sms_body",a.getString("message")),"SMS composer opened. Message was not sent by JARVIS.")
     else->{val uri=Uri.parse("https://wa.me/${whatsappNumber(phone)}").buildUpon().appendQueryParameter("text",a.getString("message")).build();val native=Intent(Intent.ACTION_VIEW,uri).setPackage("com.whatsapp");if(native.resolveActivity(activity.packageManager)!=null)launch(native,"WhatsApp composer launch requested. Message not sent; calls are not supported by this integration.")else launch(Intent(Intent.ACTION_VIEW,uri),"WhatsApp web link opened. App availability and message sending are not verified.")}
    }
   }
   "save_memory"->{if(!store.flag("memory_enabled"))ToolOutcome(TaskStatus.UNSUPPORTED,"Enable local memory before saving.")else{withContext(Dispatchers.IO){store.saveMemory(MemoryItem(java.util.UUID.randomUUID().toString(),"note",a.getString("label"),a.getString("value"),provenance="User-approved assistant proposal"))};ToolOutcome(TaskStatus.COMPLETED,"Memory saved locally after approval. No model training occurred.")}}
   "mcp_call"->{val server=readServers(store).find{it.id==a.getString("server")}?:return@withContext ToolOutcome(TaskStatus.UNSUPPORTED,"MCP server not configured.");if(a.optString("endpoint")!=server.endpoint)return@withContext ToolOutcome(TaskStatus.FAILED,"MCP destination changed. Prepare and approve a new request.");val token=if(store.secretPresent("mcp_${server.id}"))store.secret("mcp_${server.id}")else "";val result=mcp.invoke(server,token,a.getString("tool"),a.getJSONObject("arguments"),authorized={unlocked()&&readServers(store).any{it.id==server.id&&it.endpoint==server.endpoint&&it.enabled&&a.getString("tool") in it.allowed}});ToolOutcome(if(result.isError)TaskStatus.FAILED else TaskStatus.COMPLETED,"MCP tool returned ${if(result.isError)"an error" else "a result"}. This is not independent confirmation of external effects.\n${result.text}"+(if(result.cleanupConfirmed)"" else "\nSession closure could not be confirmed."))}
   "scroll_app","back_app"->{if(!store.flag("automation_${a.getString("package")}"))return@withContext ToolOutcome(TaskStatus.UNSUPPORTED,"Enable scoped automation for this app first.");ScopedAutomationService.perform(activity,a.getString("package"),if(action.name=="back_app")"back" else a.getString("direction"),stillAllowed={store.flag("automation_${a.getString("package")}")})}
   else->ToolOutcome(TaskStatus.UNSUPPORTED,"Tool not implemented.")
  }
 }
}
fun readServers(store:FeatureStore):List<McpServer>{val raw=store.config("mcp_servers");if(raw.isBlank())return emptyList();val arr=JSONArray(raw);require(arr.length()<=20);return (0 until arr.length()).map{val x=arr.getJSONObject(it);val allowed=x.optJSONArray("allowed")?:JSONArray();McpServer(x.getString("id"),x.getString("name"),x.getString("endpoint"),x.optBoolean("enabled"),(0 until allowed.length()).map{allowed.getString(it)}.toSet())}}
fun writeServers(store:FeatureStore,servers:List<McpServer>,credentialId:String?=null,token:String?=null){require(servers.size<=20);store.saveMcpConfiguration(JSONArray().also{arr->servers.forEach{s->arr.put(org.json.JSONObject().put("id",s.id).put("name",s.name).put("endpoint",s.endpoint).put("enabled",s.enabled).put("allowed",JSONArray(s.allowed.toList())))}}.toString(),credentialId,token)}

class ToolResolver(private val context:Context,private val store:FeatureStore) {
 fun apps():List<InstalledApp> {val pm=context.packageManager;return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).map{InstalledApp(it.loadLabel(pm).toString(),it.activityInfo.packageName)}.distinctBy{it.packageName}.sortedBy{it.label.lowercase(Locale.ROOT)}.take(300)}
 suspend fun contacts(query:String):List<DeviceContact> = withContext(Dispatchers.IO){
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)throw SecurityException("Contacts permission is not granted.")
  if(query.isBlank())return@withContext emptyList()
  val uri=Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,Uri.encode(query.take(100)))
  context.contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY),null,null,null)?.use{c->buildList{while(size<30&&c.moveToNext()){val phone=c.getString(1).orEmpty();if(validPhone(phone))add(DeviceContact(c.getString(0).orEmpty(),phone,c.getString(2).orEmpty()))}}}?:emptyList()
 }
 suspend fun prepare(action:ToolAction):ToolAction=withContext(Dispatchers.IO){
  val copy=ToolAction.decode(action.encode());val a=copy.args
  if(copy.name=="open_app"&&!a.has("package")){
   val selected=resolveInstalledApp(apps(),a.getString("app"))
   a.put("package",selected.packageName);a.put("app",selected.label)
  }
  if(copy.name in setOf("dial","direct_call","sms_compose","whatsapp_compose")&&!a.has("phone")){
   val recipient=a.getString("recipient").trim()
   if(validPhone(recipient))a.put("phone",recipient)else {
    val alias=if(store.flag("memory_enabled"))store.memories().find{it.kind=="alias"&&normalizeAlias(it.label)==normalizeAlias(recipient)}else null
    if(alias!=null){a.put("phone",alias.value);a.put("lookup",alias.contactLookup)}else{
     val selected=resolveContactNumber(contacts(recipient),recipient)
     a.put("phone",selected.phone);a.put("lookup",selected.lookup)
    }
   }
  }
  if(copy.name=="mcp_call"){val server=readServers(store).filter{it.id==a.getString("server")||it.name.equals(a.getString("server"),true)}.singleOrNull()?:error("Select an unambiguous configured MCP server.");a.put("server",server.id);a.put("endpoint",server.endpoint);a.put("serverName",server.name)}
  if(copy.name=="whatsapp_compose")whatsappNumber(a.getString("phone"))
  copy
 }

 fun contactStillMatches(lookup:String,phone:String):Boolean {
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)throw SecurityException()
  return context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY+"=?",arrayOf(lookup),null)?.use{c->var found=false;while(c.moveToNext())if(c.getString(0)==phone)found=true;found}?:false
 }
}
