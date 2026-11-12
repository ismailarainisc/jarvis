package com.raaz.assistant.gemini

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.ui.AssistantConnectionBadge
import com.raaz.assistant.voice.*

/** Official Live guide links native voices to Google's speech-generation voice catalog.
 * Examples verified 2026-11-12. Actual access still depends on the chosen model/account. */
object DocumentedGeminiVoices {val examples=listOf("Kore","Puck","Charon","Fenrir","Aoede")}

/** Configuration and Home share the same controller. No screen-owned microphone or disposal hook. */
@Composable fun GeminiLiveScreen(
 controller:GeminiLiveController,voiceState:HomeVoiceUi,
 onStart:()->Unit,onStop:()->Unit,onMute:()->Unit,onSaved:()->Unit,backgroundRunning:Boolean
) {
 val ui by controller.state.collectAsStateWithLifecycle()
 val config by controller.configuration.collectAsStateWithLifecycle()
 var key by remember{mutableStateOf("")}
 var model by rememberSaveable(config.model){mutableStateOf(config.model)}
 var voice by rememberSaveable(config.voice){mutableStateOf(config.voice)}
 var voicesExpanded by remember{mutableStateOf(false)}
 val editable=!ui.active&&!voiceState.switching&&!backgroundRunning
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text("Advanced voice settings",style=MaterialTheme.typography.headlineSmall)
  Text("The same natural Gemini voice on Home",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
  AssistantConnectionBadge(nativePresentation(ui).phase)
  Text("Saved voice: ${config.voice.ifBlank{"not configured"}}\nSaved model: ${config.model.ifBlank{"not configured"}}")
  Text("Saving configuration is not a connection test. Home START and the control below use the same Gemini session, microphone and native audio playback. Device TTS is only used in the explicitly selected Android fallback mode.",style=MaterialTheme.typography.bodyMedium)
  ui.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  config.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  voiceState.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  ui.notice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
  if(backgroundRunning)Text("Stop background mode before editing voice configuration or starting a foreground conversation.",color=MaterialTheme.colorScheme.error)
  OutlinedTextField(model,{model=it},enabled=editable,label={Text("Gemini Live model ID")},supportingText={Text("Use a Live/audio model available to your Google project.")},modifier=Modifier.fillMaxWidth())
  Box {
   OutlinedButton(onClick={voicesExpanded=true},enabled=editable,modifier=Modifier.fillMaxWidth()){Text("Choose documented voice example")}
   DropdownMenu(expanded=voicesExpanded,onDismissRequest={voicesExpanded=false}) {
    DocumentedGeminiVoices.examples.forEach{name->DropdownMenuItem(text={Text(name)},onClick={voice=name;voicesExpanded=false})}
   }
  }
  OutlinedTextField(voice,{voice=it},enabled=editable,label={Text("Native Gemini voice name")},supportingText={Text("You may keep a supported custom name. Availability depends on model/account; your saved preference is never silently replaced.")},modifier=Modifier.fillMaxWidth())
  Text("Urdu, Hindi and English conversation follow your selected model's actual capabilities. No device-TTS substitution is performed for a native session.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(key,{key=it},enabled=editable,label={Text(if(config.configured)"Replace Google credential (blank keeps saved)" else  "Google AI Studio credential")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
  Button(enabled=editable,onClick={if(controller.save(model,voice,key)){key="";onSaved()}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Save native voice configuration")}
  TextButton(enabled=editable,onClick={controller.importSelected();onSaved()}){Text("Use selected Gemini provider key")}
  TextButton(enabled=editable,onClick={controller.deleteKey();onSaved()}){Text("Delete Gemini Live key")}
  HorizontalDivider()
  if(ui.active) {
   Text("Live audio diagnostics",style=MaterialTheme.typography.titleMedium)
   Text("Capture: ${if(ui.capture) "running" else "not running"} · PCM queued for Google: ${ui.sentBytes} bytes · Received PCM: ${ui.receivedBytes} bytes",style=MaterialTheme.typography.bodySmall)
   Text("Completed turns: ${ui.turnCount} · Server interruptions: ${ui.interruptionCount}",style=MaterialTheme.typography.bodySmall)
   if(ui.playback)Text("Native output has pending audio. Physical audibility requires device verification.",style=MaterialTheme.typography.bodySmall)
   Text("Audio output",style=MaterialTheme.typography.titleSmall)
   if(ui.routes.isEmpty())Text("No selectable communication routes reported by Android.",style=MaterialTheme.typography.bodySmall)
   ui.routes.forEach { route ->
    FilterChip(selected=route.id==ui.route,enabled=ui.ready,onClick={controller.selectRoute(route.id)},label={Text(route.label)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
   }
  }
  Button(enabled=ui.active||(!voiceState.switching&&voiceState.permissionRequest==null&&!backgroundRunning),onClick={if(ui.active)onStop()else onStart()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(ui.active)"STOP shared conversation" else  "START native Gemini")}
  OutlinedButton(enabled=ui.ready,onClick=onMute,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(ui.muted)"Unmute microphone" else  "Mute microphone")}
  Text("Home ↔ Voice settings preserves this shared session. Other screens and app backgrounding stop foreground audio. The opt-in background wake service remains a separate, mutually exclusive mode.",style=MaterialTheme.typography.bodySmall)
  Text("Microphone audio is sent to Google only after explicit START and actual setupComplete. Your credential remains encrypted on this device. Quota, pricing, model access and voice availability depend on Google. Shared-account distributed apps should use trusted short-lived credentials rather than embed developer keys.",style=MaterialTheme.typography.bodySmall)
 }
}
