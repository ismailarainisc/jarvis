package com.raaz.assistant.features

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.background.*
import com.raaz.assistant.gemini.*
import org.json.JSONObject

@Composable fun HubScreen(vm:HubViewModel,initial:String="Control",foregroundLive:GeminiLiveUi,onStartBackground:()->Unit,onStopAll:()->Unit){
 val activity=LocalContext.current as Activity;val state by vm.state.collectAsStateWithLifecycle()
 var tab by rememberSaveable(initial){mutableStateOf(initial)}
 DisposableEffect(vm){vm.visible(true);vm.loadApps(activity);onDispose{vm.visible(false)}}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp)){listOf("Control","Tools","Contacts","Memory","MCP","Tasks","Background","Diagnostics").forEach{label->FilterChip(selected=tab==label,onClick={tab=label},label={Text(label)},modifier=Modifier.padding(3.dp).testTag("hub-tab-$label"))}}
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(tab,style=MaterialTheme.typography.headlineSmall)
   state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};state.notice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
   when(tab){
    "Control"->ControlCenter(vm,state,activity,foregroundLive,{tab=it})
    "Tools"->ToolsPanel(vm,state,activity,{tab="Tasks"})
    "Contacts"->ContactsPanel(vm,state,activity)
    "Memory"->MemoryPanel(vm,state)
    "MCP"->McpPanel(vm,state,activity)
    "Tasks"->TaskDashboard(vm,state,activity)
    "Background"->BackgroundPanel(vm,state,activity,onStartBackground,onStopAll)
    "Diagnostics"->DiagnosticsPanel(state,foregroundLive)
   }
  }
  OutlinedButton(onClick={vm.stopExecution();onStopAll()},modifier=Modifier.fillMaxWidth().padding(12.dp).heightIn(min=48.dp)){Text("EMERGENCY STOP · audio and active tool")}
 }
}
@Composable private fun FlagRow(label:String,checked:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,modifier=Modifier.weight(1f).padding(end=8.dp));Switch(checked,onCheckedChange=change,modifier=Modifier.semantics{contentDescription=label})}}
@Composable private fun ToolsPanel(vm:HubViewModel,s:HubState,a:Activity,tasks:()->Unit){
 var destination by rememberSaveable{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};var url by rememberSaveable{mutableStateOf("")};var seconds by rememberSaveable{mutableStateOf("60")};var hour by rememberSaveable{mutableStateOf("8")};var minute by rememberSaveable{mutableStateOf("0")}
 FlagRow("Allow Gemini to propose tools (approval always required)",s.flags["assistant_tools"]==true){vm.flag("assistant_tools",it)}
 Text("Enable before START; changing declarations needs a fresh voice session. Disabling immediately revokes pending AI proposals.",style=MaterialTheme.typography.bodySmall)
 Text("Actions are prepared first. Review the destination and arguments in Tasks. Launching an app does not prove its final result.")
 OutlinedTextField(query,{query=it},label={Text("YouTube / web search")},modifier=Modifier.fillMaxWidth())
 Row(Modifier.horizontalScroll(rememberScrollState())){Button(onClick={vm.propose(a,ToolAction("youtube_search",JSONObject().put("query",query)))} ){Text("Prepare YouTube search")};TextButton(onClick={vm.propose(a,ToolAction("web_search",JSONObject().put("query",query)))}){Text("Web")}}
 OutlinedTextField(url,{url=it},label={Text("HTTPS link")},modifier=Modifier.fillMaxWidth());TextButton(onClick={vm.propose(a,ToolAction("open_url",JSONObject().put("url",url)))}){Text("Prepare opening link")}
 OutlinedTextField(seconds,{seconds=it},label={Text("Timer seconds (1–86400)")},modifier=Modifier.fillMaxWidth());TextButton(onClick={vm.propose(a,ToolAction("set_timer",JSONObject().put("seconds",seconds.toIntOrNull()?:0)))}){Text("Prepare timer")}
 Row{OutlinedTextField(hour,{hour=it},label={Text("Hour 0–23")},modifier=Modifier.weight(1f));OutlinedTextField(minute,{minute=it},label={Text("Minute 0–59")},modifier=Modifier.weight(1f))};TextButton(onClick={vm.propose(a,ToolAction("set_alarm",JSONObject().put("hour",hour.toIntOrNull()?:-1).put("minute",minute.toIntOrNull()?:-1)))}){Text("Prepare alarm")}
 OutlinedTextField(destination,{destination=it},label={Text("Maps destination / place")},modifier=Modifier.fillMaxWidth())
 OutlinedButton(onClick={vm.propose(a,ToolAction("maps_search",JSONObject().put("query",destination)))}){Text("Prepare maps search")}
 OutlinedButton(onClick={vm.propose(a,ToolAction("navigate_maps",JSONObject().put("query",destination)))}){Text("Prepare directions")}
 Text("Installed launcher apps",style=MaterialTheme.typography.titleMedium)
 var filter by rememberSaveable{mutableStateOf("")};OutlinedTextField(filter,{filter=it},label={Text("Filter app names")},modifier=Modifier.fillMaxWidth())
 TextButton(onClick={vm.propose(a,ToolAction("search_apps",JSONObject().put("query",filter)))}){Text("Prepare local app-search task")}
 s.apps.filter{it.label.contains(filter,true)}.take(30).forEach{app->OutlinedButton(onClick={vm.propose(a,ToolAction("open_app",JSONObject().put("app",app.label).put("package",app.packageName)))},modifier=Modifier.fillMaxWidth()){Text("Open ${app.label}")}}
 Text("Scoped accessibility navigation is optional. It only scrolls or goes Back in explicitly enabled YouTube/Chrome; it cannot send messages, click arbitrary controls or approve permissions.")
 for((pkg,label) in listOf("com.google.android.youtube" to "YouTube","com.android.chrome" to "Chrome")){
  FlagRow("Allow $label scroll / Back",s.flags["automation_$pkg"]==true){vm.flag("automation_$pkg",it)}
  Row(Modifier.horizontalScroll(rememberScrollState())){for(direction in listOf("forward","backward","back"))TextButton(onClick={vm.propose(a,ToolAction(if(direction=="back")"back_app" else "scroll_app",JSONObject().put("package",pkg).put("direction",direction)))}){Text("$label $direction")}}
 }
 Row(Modifier.horizontalScroll(rememberScrollState())){for(page in listOf("app","wifi","bluetooth","accessibility","assistant","network","sound","location","notifications","battery"))TextButton(onClick={vm.propose(a,ToolAction("open_settings",JSONObject().put("page",page)))}){Text("Open $page settings")}}
 Button(onClick=tasks){Text("Review prepared tasks (${s.tasks.count{it.status==TaskStatus.NEEDS_APPROVAL}})")}
}
@Composable private fun ContactsPanel(vm:HubViewModel,s:HubState,a:Activity){
 var query by rememberSaveable{mutableStateOf("")};var alias by rememberSaveable{mutableStateOf("")};var recipient by rememberSaveable{mutableStateOf("")};var phone by rememberSaveable{mutableStateOf("")};var lookup by rememberSaveable{mutableStateOf("")};var message by rememberSaveable{mutableStateOf("")}
 val contacts=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)vm.searchContacts(a,query)else vm.fail("Contacts permission denied. You can still enter a phone number manually.")}
 val calls=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.flag("direct_calls",it);if(!it)vm.fail("Direct calls remain disabled. Use the dialer instead.")}
 Text("Contacts stay on this device. Choosing an alias never authorizes a call or message. WhatsApp needs an international +country-code number.")
 OutlinedTextField(query,{query=it},label={Text("Find contact")},modifier=Modifier.fillMaxWidth());Button(onClick={contacts.launch(Manifest.permission.READ_CONTACTS)}){Text("Grant / search contacts")}
 s.contacts.forEach{c->OutlinedButton(onClick={recipient=c.label;phone=c.phone;lookup=c.lookup},modifier=Modifier.fillMaxWidth()){Text("${c.label} · ${c.phone}")}}
 OutlinedTextField(recipient,{recipient=it;lookup=""},label={Text("Recipient name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(phone,{phone=it;lookup=""},label={Text("Phone number")},modifier=Modifier.fillMaxWidth())
 OutlinedTextField(alias,{alias=it},label={Text("Alias, e.g. Baba / Staff")},modifier=Modifier.fillMaxWidth());TextButton(onClick={vm.saveMemory(null,"alias",alias,phone,lookup)}){Text("Save alias locally (enable Memory first)")}
 OutlinedTextField(message,{message=it},label={Text("Message to compose")},modifier=Modifier.fillMaxWidth())
 for((name,label) in listOf("dial" to "Prepare SIM dialer","sms_compose" to "Prepare SMS composer","whatsapp_compose" to "Prepare WhatsApp composer"))Button(onClick={vm.propose(a,ToolAction(name,JSONObject().put("recipient",recipient.ifBlank{phone}).put("phone",phone).put("lookup",lookup).put("message",message)))},modifier=Modifier.fillMaxWidth()){Text(label)}
 FlagRow("Opt in to direct calls after explicit approval",s.flags["direct_calls"]==true){if(it)calls.launch(Manifest.permission.CALL_PHONE)else vm.flag("direct_calls",false)}
 if(s.flags["direct_calls"]==true)OutlinedButton(onClick={vm.propose(a,ToolAction("direct_call",JSONObject().put("recipient",recipient.ifBlank{phone}).put("phone",phone).put("lookup",lookup)))}){Text("Prepare direct call — review in Tasks")}
 Text("SMS and WhatsApp are composers: tap Send yourself. WhatsApp calling and automatic personal-account sending are not implemented. SIM choice is controlled by Android.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun MemoryPanel(vm:HubViewModel,s:HubState){
 var editing by rememberSaveable{mutableStateOf<String?>(null)};var kind by rememberSaveable{mutableStateOf("note")};var label by rememberSaveable{mutableStateOf("")};var value by rememberSaveable{mutableStateOf("")};var editingLookup by rememberSaveable{mutableStateOf("")};var remove by remember{mutableStateOf<String?>(null)}
 FlagRow("Enable local personal memory",s.flags["memory_enabled"]==true){vm.flag("memory_enabled",it)}
 FlagRow("Allow minimal relevant preferences/notes in AI context",s.flags["memory_cloud"]==true){vm.flag("memory_cloud",it)}
 Text("Aliases and contact numbers are never automatically added to provider context. Saving preferences does not train the model. You can correct or delete any item.")
 Row{for(k in listOf("note","preference"))FilterChip(selected=kind==k,onClick={kind=k},label={Text(k)})}
 OutlinedTextField(label,{label=it},label={Text("Label / relationship")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value,{value=it;if(kind=="alias")editingLookup=""},label={Text("Value (no passwords or API keys)")},modifier=Modifier.fillMaxWidth())
 Button(onClick={vm.saveMemory(editing,kind,label,value,editingLookup);editing=null;label="";value=""}){Text(if(editing==null)"Save memory" else "Save correction")}
 s.memory.forEach{m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${m.kind}: ${m.label}",style=MaterialTheme.typography.titleSmall);SelectionContainer{Text(m.value)};Text("${m.provenance} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.updated))}",style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={editing=m.id;kind=m.kind;label=m.label;value=m.value;editingLookup=m.contactLookup}){Text("Edit / correct")};TextButton(onClick={remove=m.id}){Text("Delete")}}}}}
 if(s.memory.isEmpty())Text("No saved memory. Nothing has been learned or imported silently.")
 remove?.let{id->AlertDialog(onDismissRequest={remove=null},title={Text("Delete this local memory?")},confirmButton={TextButton(onClick={vm.deleteMemory(id);remove=null}){Text("Delete")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
}
@Composable private fun McpPanel(vm:HubViewModel,s:HubState,a:Activity){
 var editing by rememberSaveable{mutableStateOf<String?>(null)};var name by rememberSaveable{mutableStateOf("")};var endpoint by rememberSaveable{mutableStateOf("")};var token by remember{mutableStateOf("")};var arguments by rememberSaveable{mutableStateOf("{}")}
 Text("Only connect trusted HTTPS Streamable HTTP servers. A server receives its configured token and approved tool arguments. MCP does not grant Android control. Server descriptions/results are untrusted data.")
 OutlinedTextField(name,{name=it},label={Text("Server name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(endpoint,{endpoint=it},label={Text("HTTPS MCP endpoint (port 443)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(token,{token=it},label={Text("Bearer token, blank keeps existing / public server")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
 Button(onClick={vm.saveServer(editing,name,endpoint.trim(),token);token="";editing=null}){Text("Save disabled configuration")}
 OutlinedTextField(arguments,{arguments=it},label={Text("Tool arguments JSON")},modifier=Modifier.fillMaxWidth(),minLines=2)
 s.servers.forEach{server->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(server.name,style=MaterialTheme.typography.titleMedium);Text(server.endpoint);Text(s.connection[server.id]?:"UNKNOWN — not tested in this app session");FlagRow("Enable this server",server.enabled){vm.serverEnabled(server.id,it)};Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={vm.testServer(server)}){Text("Test / discover")};TextButton(onClick={editing=server.id;name=server.name;endpoint=server.endpoint;token=""}){Text("Edit")};TextButton(onClick={vm.removeServer(server.id)}){Text("Remove")}}
  (s.tools[server.id]?:emptyList()).forEach{tool->HorizontalDivider();Text(tool.name);Text("Untrusted description: ${tool.description}",style=MaterialTheme.typography.bodySmall);SelectionContainer{Text("Input schema: "+tool.schema.toString(2).take(8000),style=MaterialTheme.typography.bodySmall)};FlagRow("Allow this tool",tool.name in server.allowed){vm.allowTool(server.id,tool.name,it)};TextButton(enabled=server.enabled&&tool.name in server.allowed,onClick={try{vm.propose(a,ToolAction("mcp_call",JSONObject().put("server",server.id).put("tool",tool.name).put("arguments",JSONObject(arguments))))}catch(e:Exception){vm.fail("Enter a valid JSON object for arguments.")}}){Text("Prepare approved call")}}
 }}}
 Text("OAuth login, legacy HTTP+SSE transport, stdio, server-initiated sampling/elicitation and complex JSON Schema constructs are not supported. Public endpoints or manually configured bearer authentication are supported.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun BackgroundPanel(vm:HubViewModel,s:HubState,a:Activity,start:()->Unit,stop:()->Unit){
 val wake by BackgroundRuntime.wake.collectAsStateWithLifecycle();val live by BackgroundRuntime.live.collectAsStateWithLifecycle();val running by BackgroundRuntime.running.collectAsStateWithLifecycle()
 var key by remember{mutableStateOf("")};var disclosure by remember{mutableStateOf(false)}
 val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){granted->if(granted.values.all{it})start()else vm.fail("Microphone and notification permission are required for this disclosed background mode.")}
 val role=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){vm.fail("Assistant role selection returned. Check Android default-assistant settings to confirm the selected app.")}
 Text("Service: ${if(running)"running" else "stopped"} · ${wake.phase}");Text(wake.message);Text("Background Gemini: ${live.phase}");live.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 if(live.active){com.raaz.assistant.ui.JarvisFace(live.phase.visualState(),live.microphoneLevel,live.outputLevel,live.muted);com.raaz.assistant.live.MeasuredWaveform("Background microphone PCM",live.microphoneLevel,live.microphoneSamples,live.microphoneSamples>0,live.muted);com.raaz.assistant.live.MeasuredWaveform("Background playback PCM",live.outputLevel,live.outputSamples,live.outputSamples>0,false);Row(Modifier.horizontalScroll(rememberScrollState())){live.routes.forEach{route->FilterChip(selected=live.route==route.id,onClick={a.startService(Intent(a,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.ROUTE).putExtra("route",route.id))},label={Text(route.label)})}}}
 Text("Local Porcupine detects the built-in ‘Jarvis’ keyword, including in ‘Hi Jarvis’. It requires your own Picovoice AccessKey and applicable license/activation. Wake detection does not send ambient audio to Gemini; after detection, microphone audio is sent to your configured Google account. A persistent microphone notification remains visible.")
 OutlinedTextField(key,{key=it},label={Text(if(s.wakeKey)"Replace Picovoice AccessKey" else "Picovoice AccessKey (console.picovoice.ai)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
 Button(enabled=!running,onClick={vm.saveWakeKey(key);key=""}){Text("Save wake credential")};TextButton(enabled=!running,onClick=vm::deleteWakeKey){Text("Delete wake credential")}
 Button(enabled=s.wakeKey,onClick={disclosure=true}){Text(if(running)"Resume local wake listening" else "Enable disclosed background mode")}
 if(running){TextButton(onClick={a.startService(Intent(a,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.PAUSE))}){Text("Pause all background audio")};TextButton(enabled=live.ready,onClick={a.startService(Intent(a,BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.MUTE))}){Text(if(live.muted)"Unmute Gemini microphone" else "Mute Gemini microphone")}}
 OutlinedButton(onClick=stop){Text("STOP microphone service")}
 FlagRow("Allow assistant recognition delegation while JARVIS is visible",s.flags["delegate_recognition"]==true){vm.flag("delegate_recognition",it)}
 TextButton(onClick={try{if(Build.VERSION.SDK_INT>=29){val manager=a.getSystemService(RoleManager::class.java);if(manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT))role.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))else role.launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))}else role.launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))}catch(e:Exception){vm.fail("Assistant selection is unavailable on this device. Use Android default-app settings.")}}){Text("Choose default Android assistant")}
 Text("The assistant role opens JARVIS visibly; it does not grant hardware hotword access or bypass the lock screen. All device actions require unlock and approval. OEM battery restrictions apply. No boot auto-start or silent microphone restart is implemented. Stop foreground voice before starting this service.",style=MaterialTheme.typography.bodySmall)
 if(disclosure)AlertDialog(onDismissRequest={disclosure=false},title={Text("Enable persistent microphone listening?")},text={Text("While enabled, local detection listens for Jarvis, including when the screen is locked where Android allows. Detection starts a real Google voice session using your account. Sensitive actions remain blocked until unlock and review. Picovoice license/account terms apply. You can pause or stop from the notification. No automatic restart after process death.")},confirmButton={TextButton(onClick={disclosure=false;permissions.launch(if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS)else arrayOf(Manifest.permission.RECORD_AUDIO))}){Text("I understand — enable")}},dismissButton={TextButton(onClick={disclosure=false}){Text("Cancel")}})
}
@Composable private fun DiagnosticsPanel(s:HubState,live:GeminiLiveUi){
 val background by BackgroundRuntime.live.collectAsStateWithLifecycle();val running by BackgroundRuntime.running.collectAsStateWithLifecycle()
 Text("JARVIS 7.0-actions · com.raaz.assistant · API ${Build.VERSION.SDK_INT}")
 Text("Foreground: ${live.phase}; background service: $running; background Gemini: ${background.phase}")
 AssistantVisibility.diagnostic?.let{Text(it)}
 Text("Audio lease occupied: ${AudioOwnership.process.occupied()}; cleanup quarantined: ${AudioOwnership.process.failed()}")
 Text("Foreground capture: ${live.capture}; playback pending: ${live.playback}; route ID: ${live.route?:"system"}")
 Text("Current socket backlog: ${live.socketBacklogBytes} bytes; playback buffer: ${live.bufferedPlaybackBytes?.toString()?:"not available"} bytes. Cumulative queued PCM: ${live.sentBytes}; received PCM: ${live.receivedBytes}. These do not prove physical audibility.")
 Text("Last sanitized error: ${live.errorCode?:"none"}")
 Text("Configured MCP servers: ${s.servers.size}; local memory: ${s.memory.size}; retained tasks: ${s.tasks.size}")
 Text("Capabilities: Gemini native audio/tools; foreground fallback (no barge-in); Android intents; optional local wake; explicit approved MCP. No arbitrary app clicking, personal WhatsApp calling, automatic SMS/WhatsApp sending, hidden recording or lock bypass.")
 Text("No keys, raw audio or private transcripts are included in this screen. Instrumented, real-device, account and battery verification must be recorded separately.",style=MaterialTheme.typography.bodySmall)
}
