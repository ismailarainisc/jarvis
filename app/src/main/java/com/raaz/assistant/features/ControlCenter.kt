package com.raaz.assistant.features

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raaz.assistant.background.BackgroundRuntime
import com.raaz.assistant.gemini.GeminiLiveUi
import org.json.JSONObject

@Composable fun ControlCenter(vm:HubViewModel,s:HubState,activity:Activity,live:GeminiLiveUi,open:(String)->Unit){
 val backgroundRunning by BackgroundRuntime.running.collectAsStateWithLifecycle();val backgroundVoice by BackgroundRuntime.live.collectAsStateWithLifecycle()
 var snapshot by remember{mutableStateOf<CapabilitySnapshot?>(null)};var error by remember{mutableStateOf<String?>(null)}
 fun recheck(){try{snapshot=capabilitySnapshot(activity);error=null}catch(e:Exception){error="Android capability query failed. Recheck or open App settings."}}
 val lifecycle=LocalLifecycleOwner.current
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)recheck()};lifecycle.lifecycle.addObserver(observer);recheck();onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 Text("JARVIS Control Center",style=MaterialTheme.typography.headlineMedium)
 Text("${if(backgroundRunning)"Background voice: ${backgroundVoice.phase}" else "Foreground voice: ${live.phase}"} · approval-gated Android actions",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
 Row(Modifier.fillMaxWidth()){Text("Allow Gemini to propose tools (approval always required)",modifier=Modifier.weight(1f));Switch(s.flags["assistant_tools"]==true,{vm.flag("assistant_tools",it)},modifier=Modifier.semantics{contentDescription="Allow Gemini to propose tools with approval"})}
 Text("Enable tools before START. If already connected, STOP then START to advertise the declarations. Disabling revokes pending Gemini requests immediately and prevents approval.",style=MaterialTheme.typography.bodySmall)
 Text("Ask for a supported action on Home. Google proposes structured arguments; this device validates them and waits for your review. Private Contacts/Memory results stay in Tasks.")
 error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Actual device capabilities",style=MaterialTheme.typography.titleMedium)
  snapshot?.let{c->Text("Microphone: ${if(c.microphone)"granted" else "not granted"}\nContacts: ${if(c.contacts)"granted" else "not granted"}\nNotifications: ${if(c.notifications)"enabled" else "disabled"}\nAssistant role: ${when(c.assistant){true->"held";false->"not held";null->"not queryable before Android 10"}}\nAccessibility: ${if(c.accessibility)"enabled in Android" else "not enabled"}\nService connected: ${ScopedAutomationService.connected()}\nBattery: ${c.battery?.let{"$it%"}?:"not available"}\n${c.network}")}
  OutlinedButton(onClick=::recheck){Text("Recheck capabilities")}
  TextButton(onClick={try{activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}catch(e:Exception){error="Android App settings are unavailable."}}){Text("Permission / privacy settings")}
 }}
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
  ControlTile("Tasks & approvals",Icons.Default.Checklist,{open("Tasks")},Modifier.weight(1f))
  ControlTile("Apps & actions",Icons.Default.Apps,{open("Tools")},Modifier.weight(1f))
 }
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
  ControlTile("Contacts",Icons.Default.Contacts,{open("Contacts")},Modifier.weight(1f))
  ControlTile("Memory",Icons.Default.Psychology,{open("Memory")},Modifier.weight(1f))
 }
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
  ControlTile("MCP servers",Icons.Default.Hub,{open("MCP")},Modifier.weight(1f))
  ControlTile("Wake & background",Icons.Default.Hearing,{open("Background")},Modifier.weight(1f))
 }
 Button(onClick={vm.propose(activity,ToolAction("device_status",JSONObject()))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Prepare device-status task")}
 Text("Pending review: ${s.tasks.count{it.status in pendingTaskStates}} · executing: ${s.tasks.count{it.status==TaskStatus.EXECUTING}}",style=MaterialTheme.typography.labelLarge)
 Text("No hidden recording, arbitrary clicks, automatic WhatsApp/SMS sending, automatic permission approval, lock bypass or universal phone control. Permissions and per-app scopes remain revocable.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun ControlTile(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit,modifier:Modifier){OutlinedCard(onClick=onClick,modifier=modifier.heightIn(min=86.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.labelLarge)}}}
