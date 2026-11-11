package com.raaz.assistant.live

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.data.AssistantState
import com.raaz.assistant.ui.JarvisFace

@Composable fun LiveConnectionScreen(controller:LiveSessionController){
 DisposableEffect(controller){onDispose{controller.stop(false)}}
 val ui by controller.state.collectAsStateWithLifecycle();var url by remember{mutableStateOf(controller.endpoint())};var token by remember{mutableStateOf("")}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)controller.start()else controller.permissionDenied()}
 val active=ui.state !in setOf(LiveSessionState.DISCONNECTED,LiveSessionState.ERROR)
 Column(Modifier.fillMaxSize()){
 Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("GPT-Live voice",style=MaterialTheme.typography.headlineSmall);Text("OpenAI gpt-live-1 · native WebRTC audio")
  Text("Connection: ${ui.state.name}",color=if(ui.state==LiveSessionState.ERROR)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
  val faceState=when{ui.state==LiveSessionState.ERROR->AssistantState.ERROR;!active->AssistantState.IDLE;ui.outputActive->AssistantState.SPEAKING;ui.state!=LiveSessionState.SESSION_READY->AssistantState.CONNECTING;else->AssistantState.LISTENING}
  Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){JarvisFace(faceState,ui.microphoneLevel,ui.playbackLevel,ui.muted)}
  Text(ui.voiceActivity.name.replace('_',' '),style=MaterialTheme.typography.titleMedium)
  if(ui.inputActive&&ui.outputActive)Text("Input and output audio are both active.")
  ui.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Text("Microphone and native audio are active only after Start. Stop or leave this screen to release them. Session creation may incur provider charges.",style=MaterialTheme.typography.bodySmall)
  if(active||ui.microphoneMeterAvailable||ui.playbackMeterAvailable){
   MeasuredWaveform("Microphone PCM",ui.microphoneLevel,ui.microphoneSamples,ui.microphoneMeterAvailable,ui.muted)
   MeasuredWaveform("Playback PCM",ui.playbackLevel,ui.playbackSamples,ui.playbackMeterAvailable,ui.state==LiveSessionState.DISCONNECTING)
   Text("Capture engine: ${if(ui.captureRunning)"running" else "not running"} · Renderer: ${if(ui.playbackRunning)"running" else "not running"}",style=MaterialTheme.typography.bodySmall)
   if(ui.muted)Text("Microphone input is disabled locally. The device's capture indicator may take time to update.",style=MaterialTheme.typography.bodySmall)
   Text("Audio-energy activity is measured PCM, not a provider speech/turn boundary. Renderer running does not mean audible speech.",style=MaterialTheme.typography.bodySmall)
   Text("Microphone sender: ${if(ui.microphoneAttached)"attached" else "not attached"} · Remote audio track: ${if(ui.remoteTrackAvailable)"received" else "not received"}",style=MaterialTheme.typography.bodySmall)
   Text("Audio RTP sent: ${ui.outboundPackets?:"not reported"} · received: ${ui.inboundPackets?:"not reported"}",style=MaterialTheme.typography.bodySmall)
   Text("RTP counters are local transport evidence; they do not prove the provider heard the microphone or that audio was heard on the device.",style=MaterialTheme.typography.bodySmall)
  }
  if(active){Text("Audio output",style=MaterialTheme.typography.titleSmall);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){ui.audioRoutes.forEach{route->FilterChip(enabled=ui.state!=LiveSessionState.DISCONNECTING,selected=ui.selectedRoute==route.id,onClick={controller.selectRoute(route.id)},label={Text(route.label)})}};ui.routeNotice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}}
  CaptionPanel("Your partial captions",ui.captions.filter{it.speaker=="user"})
  CaptionPanel("JARVIS partial captions",ui.captions.filter{it.speaker=="assistant"})
  Text("GPT-Live is full duplex and handles conversation timing internally. Transcript fragments have timestamps but no completed-turn event. No synthetic VAD commands or response timers are used.",style=MaterialTheme.typography.bodySmall)
  HorizontalDivider();Text("Backend configuration",style=MaterialTheme.typography.titleMedium)
  OutlinedTextField(url,{url=it},enabled=!active,label={Text("HTTPS backend session endpoint")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(token,{token=it},enabled=!active,label={Text(if(controller.configured())"Replace backend token (optional)" else "Backend user session token")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
  Text("Use your backend user token, never an OpenAI API key. Saved configuration is not proof of connectivity.",style=MaterialTheme.typography.bodySmall)
  Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(enabled=!active,onClick=controller::deleteToken){Text("Delete token")};Button(enabled=!active,onClick={controller.save(url,token);token=""}){Text("Save backend")}}
 }
 Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Button(enabled=ui.state!=LiveSessionState.DISCONNECTING,onClick={if(active)controller.stop()else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(if(active) "STOP" else "START")}
  if(active)OutlinedButton(enabled=ui.state==LiveSessionState.SESSION_READY,onClick=controller::mute,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(if(ui.muted) "Unmute mic" else "Mute mic")}
 }
 }
}
@Composable fun MeasuredWaveform(label:String,level:Float,sequence:Long,available:Boolean,disabled:Boolean){
 val samples=remember{mutableStateListOf<Float>()}
 LaunchedEffect(sequence,disabled,available){if(disabled||!available)samples.clear()else{samples.add(level.coerceIn(0f,1f));if(samples.size>32)samples.removeAt(0)}}
 val color=MaterialTheme.colorScheme.primary
 Column{Text("$label: "+when{disabled->"disabled";!available->"not available yet";else->"measured"},style=MaterialTheme.typography.labelMedium)
  Canvas(Modifier.fillMaxWidth().height(40.dp)){drawLine(color.copy(alpha=.2f),Offset(0f,size.height/2),Offset(size.width,size.height/2));if(!disabled&&available){val bar=size.width/32;for(i in samples.indices){val x=(32-samples.size+i+.5f)*bar;val amplitude=samples[i]*size.height/2;drawLine(color,Offset(x,size.height/2-amplitude),Offset(x,size.height/2+amplitude),bar*.55f)}}}
 }
}
@Composable private fun CaptionPanel(title:String,fragments:List<CaptionFragment>){Column{Text(title,style=MaterialTheme.typography.titleSmall);if(fragments.isEmpty())Text("No transcript events received.",style=MaterialTheme.typography.bodySmall)else{SelectionContainer{Text(fragments.sortedBy{it.startMs}.joinToString(""){it.delta},modifier=Modifier.heightIn(max=160.dp).verticalScroll(rememberScrollState()))};val last=fragments.last();Text("Last fragment: ${last.startMs}–${last.endMs} ms (session time)",style=MaterialTheme.typography.labelSmall)}}}
