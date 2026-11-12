package com.raaz.assistant.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raaz.assistant.data.SettingsRepository
import com.raaz.assistant.features.*
import com.raaz.assistant.background.BackgroundAssistantService

/** Current OS state, not persisted permission booleans or a fabricated provider connection. */
@Composable fun PermissionCenter(activity:Activity,onChanged:()->Unit={}) {
 var snapshot by remember{mutableStateOf<CapabilitySnapshot?>(null)};var error by remember{mutableStateOf<String?>(null)};var note by remember{mutableStateOf<String?>(null)}
 fun recheck(){try{snapshot=capabilitySnapshot(activity);error=null;onChanged()}catch(e:Exception){error="Could not query current Android capabilities. Recheck or open App settings."}}
 fun open(intent:Intent){try{activity.startActivity(intent)}catch(e:Exception){error="This system settings page is unavailable. Use Android App settings manually."}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->recheck();note=if(granted)"Access granted by Android; you can revoke it in App settings." else "Not granted. This optional capability remains unavailable. Retry or open App settings; nothing will execute automatically."}
 val role=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){recheck()}
 val owner=LocalLifecycleOwner.current
 DisposableEffect(owner){val listener=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)recheck()};owner.lifecycle.addObserver(listener);recheck();onDispose{owner.lifecycle.removeObserver(listener)}}
 Text("Permissions & readiness",style=MaterialTheme.typography.headlineSmall)
 Text("Optional grants are independent. Recheck after Settings. Readiness below means local prerequisites only; it does not prove a cloud connection, SDK license or physical audio.")
 error?.let{Text(it,color=MaterialTheme.colorScheme.error)};note?.let{Text(it)}
 val c=snapshot
 fun state(value:Boolean?)=when(value){true->"Granted / enabled";false->"Not granted / disabled";null->"Not checked / unavailable"}
 PermissionCard("Microphone",state(c?.microphone),"Needed for explicit voice START, not text chat or manual app actions."){Button(onClick={permission.launch(Manifest.permission.RECORD_AUDIO)}){Text("Request microphone")}}
 PermissionCard("Contacts",state(c?.contacts),"Optional local search and selected-contact revalidation. Manual numbers and dialer/composers do not need address-book access."){Button(onClick={permission.launch(Manifest.permission.READ_CONTACTS)}){Text("Request Contacts")}}
 PermissionCard("Notifications",state(c?.notifications),"Optional for foreground chat. Disclosed background mode also requires the JARVIS microphone channel to remain visible."){
  if(Build.VERSION.SDK_INT>=33)Button(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Request notifications")}
  Text("Microphone channel visible: ${state(c?.wakeNotificationVisible)}")
  TextButton(onClick={open(if(Build.VERSION.SDK_INT>=26)Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,BackgroundAssistantService.CHANNEL)else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}){Text("Open microphone notification settings")}
 }
 PermissionCard("Assistant role",state(c?.assistant),"Optional visible invocation. Android versions before 10 cannot be verified through RoleManager. No lock bypass."){TextButton(onClick={try{role.launch(assistantSetupIntent(activity))}catch(e:Exception){error="Assistant role selection unavailable. Use default-app settings."}}){Text("Open assistant selection")}}
 PermissionCard("Scoped accessibility",state(c?.accessibility),"Optional one-action Back/Scroll in separately enabled Chrome/YouTube. Use the disclosed wizard step to enable manually; no arbitrary taps or permission approval."){Text("Service connected: ${ScopedAutomationService.connected()}. Per-app opt-in and unlocked approval remain required.")}
 Text("Per-feature requirements",style=MaterialTheme.typography.titleMedium)
 Text("Native voice: saved accessible Google configuration + microphone. Current key status is not a connection test.\nLocal contact lookup: Contacts grant + action review.\nCalls/messages: exact recipient preview + unlock + manual Call/Send. Direct calling additionally requires its explicit opt-in and CALL_PHONE.\nMaps: destination intent, no JARVIS location grant.\nBackground wake: microphone + visible notification/channel + Picovoice configuration/license + explicit foreground start.\nScreen navigation: disclosed service + allowed app scope + one-action approval.")
 Button(onClick=::recheck){Text("Recheck all current statuses")}
 OutlinedButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}){Text("Open Android App settings")}
 Text("Skip any optional grant. Granting permission does not replay blocked tasks; prepare a new preview and approve it separately.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun PermissionCard(title:String,status:String,explanation:String,actions:@Composable ColumnScope.()->Unit){OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("$title · $status",style=MaterialTheme.typography.titleMedium);Text(explanation);actions()}}}
