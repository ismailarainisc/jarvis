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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.data.AndroidSettingsStore
import com.raaz.assistant.features.*
import com.raaz.assistant.gemini.GeminiLiveController

@Composable fun SetupWizard(controller:GeminiLiveController,onDone:()->Unit,onProviders:()->Unit,onBackground:()->Unit,onVoiceTest:()->Unit,progressOverride:SetupProgress?=null) {
 val activity=LocalContext.current as Activity
 val progress=remember(progressOverride){progressOverride?:SetupProgress(AndroidSettingsStore(activity))}
 var step by remember{mutableStateOf(progress.read().step)}
 var snapshot by remember{mutableStateOf<CapabilitySnapshot?>(null)}
 var error by remember{mutableStateOf(progress.read().error)}
 var notice by remember{mutableStateOf<String?>(null)}
 val configuration by controller.configuration.collectAsStateWithLifecycle()
 var key by remember{mutableStateOf("")};var model by rememberSaveable(configuration.model){mutableStateOf(configuration.model)};var voice by rememberSaveable(configuration.voice){mutableStateOf(configuration.voice)}
 var disclosure by rememberSaveable{mutableStateOf(false)}
 fun recheck(){try{snapshot=capabilitySnapshot(activity);error=progress.read().error}catch(e:Exception){error="Android capability checks failed. Recheck or open system app settings; setup progress was preserved."}}
 fun launch(intent:Intent){try{activity.startActivity(intent)}catch(e:Exception){error="This setup page is unavailable on your device. Use system App settings or skip this optional capability."}}
 fun move(target:SetupStep){try{progress.move(target);step=target;notice=null}catch(e:Exception){error="Could not save setup progress. Existing settings were preserved."}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->recheck();notice=if(granted)"Android granted access. You can revoke it in system app settings." else "Permission denied. This capability remains unavailable; you can skip it or open App settings."}
 val role=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){recheck()}
 val lifecycle=LocalLifecycleOwner.current
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)recheck()};lifecycle.lifecycle.addObserver(observer);recheck();onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 val c=snapshot
 val permissionName=when(step){SetupStep.MICROPHONE->Manifest.permission.RECORD_AUDIO;SetupStep.CONTACTS->Manifest.permission.READ_CONTACTS;SetupStep.NOTIFICATIONS->if(Build.VERSION.SDK_INT>=33)Manifest.permission.POST_NOTIFICATIONS else null;else->null}
 Column(Modifier.fillMaxSize()) {
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Text("JARVIS · PERSONAL CONTROL",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
   Text("Step ${step.ordinal+1} of ${SetupStep.values().size}",style=MaterialTheme.typography.labelMedium)
   Text(step.title,style=MaterialTheme.typography.headlineMedium)
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)};notice?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
   when(step) {
    SetupStep.WELCOME->Text("Set up natural Gemini voice and only the Android capabilities you choose. Optional steps may be skipped. No microphone or contact access is requested automatically.")
    SetupStep.SCOPE->Text("JARVIS can prepare approved app launches, searches, dialer/SMS/WhatsApp composition, alarms, maps and local memory operations. It cannot control every app, bypass locks or security dialogs, automatically approve Android permissions, or prove that a message was sent just because a composer opened. Consequential actions require unlock and review.")
    SetupStep.PROVIDER->{
     Text("Home uses native Gemini Live by default. Existing credentials and text providers are preserved. Saving configuration is not a connection test.")
     Text("Native credential: ${if(configuration.configured)"saved (not connectivity proof)" else "not configured"}")
     OutlinedTextField(model,{model=it},label={Text("Accessible Gemini Live model ID")},modifier=Modifier.fillMaxWidth())
     OutlinedTextField(voice,{voice=it},label={Text("Native voice name, e.g. Kore")},modifier=Modifier.fillMaxWidth())
     OutlinedTextField(key,{key=it},label={Text("Google credential (blank preserves saved)")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
     Button(onClick={if(controller.save(model,voice,key)){key="";notice="Saved securely on this device. No network test performed."}else error="Configuration could not be saved. Check model, voice and credential."}){Text("Save native provider")}
     OutlinedButton(onClick=onProviders){Text("Manage text providers / fallback")}
    }
    SetupStep.MICROPHONE->{Text("Microphone access enables explicit START on Home. In a native conversation, audio is sent to your configured Google account only after session setup. Nothing starts recording during this wizard.");CapabilityLine("Microphone",c?.microphone)}
    SetupStep.CONTACTS->{Text("Optional. Search local contacts and choose exact recipients. Contacts/aliases never authorize a call or message; private lookup results stay in local Tasks.");CapabilityLine("Contacts",c?.contacts)}
    SetupStep.NOTIFICATIONS->{Text("Optional for ordinary foreground chat. Notifications support the disclosed background microphone service with Pause/Stop controls. Denying this does not prevent text chat.");CapabilityLine("Notifications",c?.notifications)}
    SetupStep.ASSISTANT->{Text("Optional. Choose JARVIS using Android's own assistant-role screen. This enables supported visible invocation; it grants no lock bypass or universal hardware wake-word access.");CapabilityLine("Default assistant",c?.assistant);Button(onClick={try{role.launch(assistantSetupIntent(activity))}catch(e:Exception){error="Assistant role selection is unavailable. Use default-app settings."}}){Text("Open assistant-role setup")}}
    SetupStep.ACCESSIBILITY->{
     Text("Optional. JARVIS can dispatch one user-approved Back or Scroll in separately enabled Chrome/YouTube. The service inspects semantic nodes to find a scrollable element. It does not extract screen text, tap arbitrary controls, approve permissions, enter passwords or independently plan actions. Access can be revoked in Android settings. Store-distribution policy approval is not guaranteed.")
     CapabilityLine("Scoped accessibility enabled in Android",c?.accessibility)
     Row {Checkbox(disclosure,{disclosure=it},modifier=Modifier.semantics{contentDescription="I understand limited screen-navigation access"});Text("I understand this limited screen-navigation access",modifier=Modifier.weight(1f))}
     Button(enabled=disclosure,onClick={launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Open Android Accessibility Settings")}
     Text("After enabling the service manually, opt into each supported app in Control Center. JARVIS cannot enable it for you.")
    }
    SetupStep.WAKE->{Text("Optional. Local wake detection requires your own Picovoice credential/license, microphone access and a visible foreground-service notification. It does not silently restart after process death or reboot. Google receives audio only after activation. OEM restrictions and battery costs apply.");OutlinedButton(onClick=onBackground){Text("Configure optional background wake")}}
    SetupStep.VERIFY->{
     Text("These are actual OS/configuration checks, not a claim of provider inference, audibility or successful actions.")
     CapabilityLine("Microphone",c?.microphone);CapabilityLine("Contacts",c?.contacts);CapabilityLine("Notifications",c?.notifications);CapabilityLine("Assistant role",c?.assistant);CapabilityLine("Accessibility",c?.accessibility)
     Text("Battery: ${c?.battery?.let{"$it%"}?:"Not available"}\nNetwork: ${c?.network?:"Not checked"}\nGoogle configuration: ${if(configuration.configured)"credential saved; live connection not tested here" else "not configured"}")
     OutlinedButton(onClick=onVoiceTest){Text("Open native voice test controls")}
     PermissionCenter(activity)
     Text("Test START/STOP yourself with legitimate credentials. Every tool proposal still needs separate review in Tasks.")
    }
    SetupStep.COMPLETE->Text("Your choices remain revocable. Home is ready even if optional permissions or AI setup were skipped. Missing capabilities show setup guidance; no permission is granted by finishing this wizard.")
   }
   if(permissionName!=null)Button(onClick={permission.launch(permissionName)}){Text("Grant this permission")}
   if(step in setOf(SetupStep.MICROPHONE,SetupStep.CONTACTS,SetupStep.NOTIFICATIONS,SetupStep.ASSISTANT,SetupStep.ACCESSIBILITY,SetupStep.WAKE,SetupStep.VERIFY)) {
    OutlinedButton(onClick=::recheck){Text("Recheck current status")}
    TextButton(onClick={launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}){Text("Open Android app settings")}
   }
  }
  Surface(tonalElevation=4.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){
   if(step!=SetupStep.WELCOME)TextButton(onClick={move(SetupStep.values()[step.ordinal-1])}){Text("Previous step")}
   Button(onClick={if(step==SetupStep.COMPLETE){try{progress.finish();onDone()}catch(e:Exception){error="Setup could not be saved. Please retry."}}else move(SetupStep.values()[step.ordinal+1])},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(step==SetupStep.COMPLETE)"Complete setup and open Home" else "Continue")}
   if(step !in setOf(SetupStep.WELCOME,SetupStep.COMPLETE))TextButton(onClick={move(SetupStep.values()[step.ordinal+1])}){Text("Skip this step")}
  }}
 }
}
@Composable private fun CapabilityLine(label:String,available:Boolean?){Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){Text("$label · ${when(available){true->"Enabled";false->"Not granted / not configured";null->"Not checked"}}",modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}}
