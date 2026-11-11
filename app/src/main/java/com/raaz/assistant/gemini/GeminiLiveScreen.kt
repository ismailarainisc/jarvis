package com.raaz.assistant.gemini

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.data.AssistantState
import com.raaz.assistant.live.MeasuredWaveform
import com.raaz.assistant.ui.JarvisFace

@Composable fun GeminiLiveScreen(controller:GeminiLiveController){
 DisposableEffect(controller){controller.setScreenVisible(true);onDispose{controller.setScreenVisible(false)}}
 val ui by controller.state.collectAsStateWithLifecycle();var key by remember{mutableStateOf("")};var model by remember{mutableStateOf(controller.model())};var voice by remember{mutableStateOf(controller.voice())}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)controller.start()else controller.permissionDenied()}
 Column(Modifier.fillMaxSize()){
 Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Gemini Live",style=MaterialTheme.typography.headlineSmall);Text("Google AI Studio · native Gemini audio")
  Text("Connection: ${ui.connection}",color=if(ui.connection=="ERROR")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
  val face=ui.phase.visualState()
  Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){JarvisFace(face,ui.microphoneLevel,ui.outputLevel,ui.muted)}
  Text(when(ui.phase){GeminiPhase.USER_SPEAKING->"INPUT AUDIO ACTIVITY";GeminiPhase.ASSISTANT_SPEAKING->"OUTPUT AUDIO ACTIVITY";else->ui.phase.name.replace('_',' ')},style=MaterialTheme.typography.titleMedium)
  if(ui.playback)Text("Native output has pending audio; physical audibility requires device verification.",style=MaterialTheme.typography.bodySmall)
  ui.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};ui.notice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
  Text("Start sends microphone audio directly to Google. Your key is encrypted on this device. No OpenAI account or backend is needed for personal API-key mode. Free-tier availability, model access and quota depend on Google and your account.",style=MaterialTheme.typography.bodySmall)
  if(ui.active){MeasuredWaveform("Microphone PCM",ui.microphoneLevel,ui.microphoneSamples,ui.microphoneSamples>0,ui.muted);MeasuredWaveform("Gemini playback PCM",ui.outputLevel,ui.outputSamples,ui.outputSamples>0,false)
   Text("Capture engine: ${if(ui.capture) "running" else "not running"} · PCM queued for Google: ${ui.sentBytes} bytes · Received PCM: ${ui.receivedBytes} bytes",style=MaterialTheme.typography.bodySmall)
   if(ui.muted)Text("Capture may remain active, but microphone frames are not transmitted while muted.",style=MaterialTheme.typography.bodySmall)
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){ui.routes.forEach{route->FilterChip(selected=route.id==ui.route,onClick={controller.selectRoute(route.id)},label={Text(route.label)})}}
   Text("Server completed turns: ${ui.turnCount} · Server interruptions: ${ui.interruptionCount}",style=MaterialTheme.typography.bodySmall)
  }
  Text("Your transcript",style=MaterialTheme.typography.titleSmall);SelectionContainer{Text(if(ui.inputText.isEmpty())"No transcript received." else ui.inputText)};if(ui.interimText.isNotEmpty())Text("Interim: ${ui.interimText}",style=MaterialTheme.typography.bodySmall)
  Text("Gemini transcript",style=MaterialTheme.typography.titleSmall);SelectionContainer{Text(if(ui.outputText.isEmpty())"No transcript received." else ui.outputText)}
  HorizontalDivider();Text("Gemini Live settings",style=MaterialTheme.typography.titleMedium)
  OutlinedTextField(model,{model=it},enabled=!ui.active,label={Text("Gemini Live model ID")},modifier=Modifier.fillMaxWidth())
  Text("Use a Live/audio model available in your AI Studio account. A text-only Gemini model will not work here.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(voice,{voice=it},enabled=!ui.active,label={Text("Voice name (for example Kore)")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(key,{key=it},enabled=!ui.active,label={Text(if(controller.configured())"Replace Google API key (optional)" else "Google AI Studio API key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
  Button(enabled=!ui.active,onClick={if(controller.save(model,voice,key))key=""}){Text("Save Gemini settings")}
  TextButton(enabled=!ui.active,onClick=controller::importSelected){Text("Use selected Gemini provider key")}
  TextButton(enabled=!ui.active,onClick=controller::deleteKey){Text("Delete Gemini Live key")}
  Text("For distributing a production app, provision short-lived Gemini tokens from a trusted backend instead of distributing a shared API key. This personal mode stores only the key you enter; no key is embedded in the APK.",style=MaterialTheme.typography.bodySmall)
 }
 Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Button(enabled=ui.phase!=GeminiPhase.DISCONNECTING,onClick={if(ui.active)controller.stop()else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(if(ui.active) "STOP" else "START")}
  if(ui.active)OutlinedButton(enabled=ui.ready,onClick=controller::mute,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(if(ui.muted) "Unmute" else "Mute")}
 }
 }
}
