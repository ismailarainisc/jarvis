package com.raaz.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raaz.assistant.background.*
import com.raaz.assistant.data.*
import com.raaz.assistant.gemini.*
import com.raaz.assistant.live.MeasuredWaveform
import com.raaz.assistant.voice.*

/** All voice data is supplied by the shared foreground coordinator/session or the explicit FGS. */
@Composable fun PremiumHomeScreen(
 ui:AssistantUiState,native:GeminiLiveUi,configuration:GeminiVoiceConfiguration,voice:HomeVoiceUi,
 background:Boolean,backgroundLive:GeminiLiveUi,wake:WakeState,
 onStart:()->Unit,onStop:()->Unit,onMute:()->Unit,onMode:(HomeVoiceMode)->Unit,
 onVoiceSettings:()->Unit,onHistory:()->Unit,onTools:()->Unit,onMemory:()->Unit,onSettings:()->Unit,
 onCamera:()->Unit,onRemovePhoto:()->Unit,onSend:(String)->Unit,onStopText:()->Unit
) {
 var showModes by rememberSaveable{mutableStateOf(false)}
 var showText by rememberSaveable{mutableStateOf(false)}
 var text by rememberSaveable{mutableStateOf("")}
 LaunchedEffect(ui.imageAttached){if(ui.imageAttached)showText=true}
 val nativeMode=voice.mode==HomeVoiceMode.GEMINI_LIVE
 val audio=if(background)backgroundLive else native
 val presentation=when {
  background&&backgroundLive.active->nativePresentation(backgroundLive)
  background->VoicePresentation(when(wake.phase){WakePhase.ARMED->UnifiedVoicePhase.LISTENING;WakePhase.ARMING,WakePhase.HANDOFF->UnifiedVoicePhase.CONNECTING;WakePhase.ERROR->UnifiedVoicePhase.ERROR;else->UnifiedVoicePhase.STOPPED},when(wake.phase){WakePhase.ARMED->AssistantState.LISTENING;WakePhase.ERROR->AssistantState.ERROR;WakePhase.ARMING,WakePhase.HANDOFF->AssistantState.CONNECTING;else->AssistantState.STOPPED},false,wake.message)
  nativeMode||native.phase==GeminiPhase.DISCONNECTING->nativePresentation(native)
  else->fallbackPresentation(ui.state,ui.voiceActive)
 }
 val active=background||presentation.active
 val busy=voice.switching||voice.permissionRequest!=null||native.phase==GeminiPhase.DISCONNECTING
 val nativeAudio=background||nativeMode
 val provider=if(background&&!backgroundLive.active)"Local wake · Jarvis" else  if(nativeAudio)"Google Gemini Live" else  "Android fallback · "+(ui.providers.find{it.id==ui.selectedProvider}?.name?:"text provider not configured")
 val error=voice.error?:if(nativeAudio)audio.error?:configuration.error else ui.error
 val colors=MaterialTheme.colorScheme
 BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background,colors.surface.copy(alpha=.55f))))) {
  val compact=maxHeight<600.dp
  Column(Modifier.fillMaxSize()) {
   LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("home-content"),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    item {
     Surface(shape=RoundedCornerShape(20.dp),color=colors.surfaceVariant.copy(alpha=.45f),modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
       Row(verticalAlignment=Alignment.CenterVertically) {
        Icon(if(nativeAudio)Icons.Default.GraphicEq else Icons.Default.RecordVoiceOver,null,tint=colors.primary,modifier=Modifier.size(20.dp))
        Text(provider,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f).padding(horizontal=8.dp))
        TextButton(onClick=onVoiceSettings){Text("Voice settings")}
       }
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        AssistantConnectionBadge(presentation.phase)
        if(nativeAudio)Text("Voice · ${configuration.voice.ifBlank{"not configured"}}",style=MaterialTheme.typography.labelMedium)
       }
       if(!nativeAudio)Text("SpeechRecognizer + device TTS · secondary mode",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
      }
     }
    }
    item {
     Column(Modifier.fillMaxWidth().padding(vertical=if(compact)0.dp else 8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(if(compact)170.dp else 224.dp).background(Brush.radialGradient(listOf(colors.primary.copy(alpha=.10f),colors.background.copy(alpha=0f)))),contentAlignment=Alignment.Center) {
       JarvisFace(presentation.visual,if(nativeAudio)audio.microphoneLevel else ui.microphoneLevel,if(nativeAudio)audio.outputLevel else 0f,nativeAudio&&audio.muted)
      }
      Text(presentation.summary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=if(presentation.phase==UnifiedVoicePhase.ERROR)colors.error else colors.onBackground)
      Text(if(background)"Explicit background mode" else  if(nativeMode)"Natural voice · continuous conversation" else  "Listening resumes after device speech",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
      if(voice.permissionRequest!=null)Text("Waiting for your microphone permission",style=MaterialTheme.typography.bodySmall)
      if(voice.switching)LinearProgressIndicator(Modifier.fillMaxWidth(.6f))
     }
    }
    if(nativeAudio&&audio.active) item {
     Surface(shape=RoundedCornerShape(18.dp),color=colors.surface.copy(alpha=.75f)) {
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       MeasuredWaveform("Microphone PCM",audio.microphoneLevel,audio.microphoneSamples,audio.capture&&audio.microphoneSamples>0,audio.muted)
       MeasuredWaveform("Playback PCM",audio.outputLevel,audio.outputSamples,audio.playback&&audio.outputSamples>0,false)
       if(audio.muted)Text("Capture may remain active; new microphone frames are not transmitted.",style=MaterialTheme.typography.bodySmall)
      }
     }
    }
    if(!nativeMode&&!background&&ui.voiceActive) item {
     Column {Text("Android recognition RMS estimate",style=MaterialTheme.typography.labelMedium);LinearProgressIndicator(progress={ui.microphoneLevel.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("Device TTS amplitude is not available. Interruption is unavailable in fallback mode.",style=MaterialTheme.typography.bodySmall)}
    }
    if(error!=null) item {AssistantMessageCard("Needs attention",error,true)}
    if(nativeAudio&&ui.error!=null&&ui.error!=error) item {AssistantMessageCard("Text / camera needs attention",ui.error,true)}
    if(!background&&nativeMode&&!configuration.configured) item {
     Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=colors.primaryContainer)) {
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text("Connect your natural voice",style=MaterialTheme.typography.titleMedium)
       Text("Add your Google AI Studio credential and an accessible Live model. JARVIS will not switch to device TTS automatically.",style=MaterialTheme.typography.bodyMedium)
       Button(onClick=onVoiceSettings){Text("Set up Gemini Live")}
      }
     }
    }
    if(nativeAudio) {
     item {TranscriptPreview("YOU",audio.interimText.ifBlank{audio.inputText},"Your transcript appears when the provider supplies it.")}
     item {TranscriptPreview("JARVIS",audio.outputText,"Your native Gemini reply appears here when transcription is available.")}
    }else if(ui.transcript.isNotBlank()) item {TranscriptPreview("YOU",ui.transcript,"")}
    if(ui.streamingText.isNotEmpty()||ui.messages.isNotEmpty()) item {
     val last=ui.messages.lastOrNull()
     TranscriptPreview(if(ui.streamingText.isNotEmpty())"TEXT REPLY · STREAMING" else  if(last?.role=="user")"YOU · TEXT" else  "JARVIS · TEXT",ui.streamingText.ifEmpty{last?.text.orEmpty()},"")
     if(ui.generating)TextButton(onClick=onStopText){Text("Stop text generation")}
    }
    item {
     Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
       HomeShortcut("Tools & background",Icons.Default.Apps,onTools,Modifier.weight(1f))
       HomeShortcut("Memory",Icons.Default.Psychology,onMemory,Modifier.weight(1f))
      }
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
       HomeShortcut("History",Icons.Default.History,onHistory,Modifier.weight(1f))
       HomeShortcut("Settings",Icons.Default.Settings,onSettings,Modifier.weight(1f))
      }
     }
    }
   }
   Surface(tonalElevation=4.dp,shadowElevation=6.dp,shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp)) {
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     if(showText) {
      if(ui.imageAttached)Row(verticalAlignment=Alignment.CenterVertically){Text("Photo attached to your next text message",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f));TextButton(onClick=onRemovePhoto){Text("Remove photo")}}
      OutlinedTextField(value=text,onValueChange={text=it},label={Text("Text chat")},placeholder={Text("Message your text provider")},maxLines=3,modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(enabled=text.isNotBlank()&&!active&&!ui.generating&&!busy,onClick={onSend(text);text=""}){Icon(Icons.AutoMirrored.Filled.Send,"Send message")}})
      if(active)Text("Stop the conversation to send a text request.",style=MaterialTheme.typography.bodySmall)
     }
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
      PrimaryConversationButton(active,busy,{if(active)onStop()else onStart()},Modifier.weight(1f))
      FilledTonalIconButton(onClick=onMute,enabled=nativeAudio&&audio.ready,modifier=Modifier.size(56.dp)) {
       Icon(if(audio.muted)Icons.Default.MicOff else Icons.Default.Mic,if(audio.muted)"Unmute microphone" else  "Mute microphone")
      }
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
      BottomHomeControl(if(showText)"Hide chat" else  "Text chat",Icons.Default.ChatBubbleOutline,{showText=!showText},Modifier.weight(1f))
      BottomHomeControl("Camera",Icons.Default.CameraAlt,onCamera,Modifier.weight(1f))
      BottomHomeControl("Voice mode",Icons.Default.Tune,{showModes=true},Modifier.weight(1f),enabled=!busy&&!background)
     }
    }
   }
  }
 }
 if(showModes)AlertDialog(onDismissRequest={showModes=false},title={Text("Choose voice mode")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  VoiceModeChoice("Gemini Live","Natural provider audio · default",voice.mode==HomeVoiceMode.GEMINI_LIVE){onMode(HomeVoiceMode.GEMINI_LIVE);showModes=false}
  VoiceModeChoice("Android fallback","Speech recognition + device TTS · turn-based",voice.mode==HomeVoiceMode.ANDROID_FALLBACK){onMode(HomeVoiceMode.ANDROID_FALLBACK);showModes=false}
  Text("Changing modes stops the current foreground conversation. Voice errors never trigger an automatic TTS fallback.",style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(onClick={showModes=false}){Text("Close")}})
}
@Composable fun AssistantConnectionBadge(phase:UnifiedVoicePhase) {
 val color=when(phase){UnifiedVoicePhase.ERROR->MaterialTheme.colorScheme.error;UnifiedVoicePhase.PROCESSING,UnifiedVoicePhase.CONNECTING,UnifiedVoicePhase.SETTING_UP->MaterialTheme.colorScheme.secondary;else->MaterialTheme.colorScheme.primary}
 Surface(color=color.copy(alpha=.12f),shape=RoundedCornerShape(8.dp),modifier=Modifier.semantics{contentDescription="Voice connection: ${phase.name.lowercase().replace('_',' ')}"}){Text(phase.name.replace('_',' '),modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=color)}
}
@Composable fun PrimaryConversationButton(active:Boolean,busy:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
 Button(onClick=onClick,enabled=active||!busy,shape=RoundedCornerShape(20.dp),modifier=modifier.heightIn(min=60.dp).semantics{contentDescription=if(active)"Stop conversation" else  "Start conversation";stateDescription=if(busy)"Voice operation pending" else  if(active)"Active" else  "Stopped"}) {
  Icon(if(active)Icons.Default.Stop else Icons.Default.Mic,null);Spacer(Modifier.width(10.dp));Text(if(active)"STOP" else  if(busy)"PLEASE WAIT" else  "START",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
 }
}
@Composable private fun HomeShortcut(title:String,icon:ImageVector,onClick:()->Unit,modifier:Modifier=Modifier) {
 OutlinedCard(onClick=onClick,modifier=modifier.heightIn(min=72.dp),shape=RoundedCornerShape(18.dp)) {
  Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp));Text(title,style=MaterialTheme.typography.labelLarge)}
 }
}
@Composable private fun TranscriptPreview(title:String,text:String,empty:String) {
 var expanded by rememberSaveable{mutableStateOf(false)}
 Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=.75f),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Text(title,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
   SelectionContainer{Text(text.ifBlank{empty},maxLines=if(expanded)Int.MAX_VALUE else 5,style=MaterialTheme.typography.bodyMedium,color=if(text.isBlank())MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)}
   if(text.length>250)TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Show less" else  "Expand")}
  }
 }
}
@Composable private fun AssistantMessageCard(title:String,message:String,error:Boolean) {
 Surface(color=if(error)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(14.dp)){Text(title,fontWeight=FontWeight.Bold);Text(message,style=MaterialTheme.typography.bodyMedium)}
 }
}
@Composable private fun VoiceModeChoice(title:String,detail:String,selected:Boolean,onClick:()->Unit) {
 OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected,onClick=null);Column(Modifier.padding(start=10.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(detail,style=MaterialTheme.typography.bodySmall)}}}
}

@Composable private fun BottomHomeControl(label:String,icon:ImageVector,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
 TextButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Icon(icon,null,Modifier.size(18.dp));Text(label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,maxLines=2)}}
}
