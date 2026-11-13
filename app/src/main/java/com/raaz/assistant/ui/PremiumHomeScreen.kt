package com.raaz.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raaz.assistant.background.*
import com.raaz.assistant.data.*
import com.raaz.assistant.gemini.*
import com.raaz.assistant.voice.*

/** All voice data is supplied by the shared foreground coordinator/session or the explicit FGS. */
@Composable fun PremiumHomeScreen(
 ui:AssistantUiState,native:GeminiLiveUi,configuration:GeminiVoiceConfiguration,voice:HomeVoiceUi,
 background:Boolean,backgroundLive:GeminiLiveUi,wake:WakeState,
 onStart:()->Unit,onStop:()->Unit,onMute:()->Unit,onMode:(HomeVoiceMode)->Unit,
 onVoiceSettings:()->Unit,onHistory:()->Unit,onTools:()->Unit,onMemory:()->Unit,onSettings:()->Unit,
 onCamera:()->Unit,onRemovePhoto:()->Unit,onSend:(String)->Unit,onStopText:()->Unit,
 onTasks:()->Unit=onTools
) {
 var showModes by rememberSaveable{mutableStateOf(false)}
 var text by rememberSaveable{mutableStateOf("")}
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
 val error=voice.error?:if(nativeAudio)audio.error?:configuration.error else ui.error
 val colors=MaterialTheme.colorScheme
 val cyan=Color(0xFF11DDF3)
 val dark=colors.background.luminance()<.5f
 val ink=if(dark)Color(0xFF020C15)else colors.background
 val foreground=if(dark)Color(0xFFE2F5FF)else colors.onBackground
 val subtext=if(dark)Color(0xFF91B4D0)else colors.onSurfaceVariant
 val border=if(dark)Color(0xFF468AA8)else colors.outline
 val connected=nativeAudio&&audio.ready&&presentation.phase !in setOf(UnifiedVoicePhase.DISCONNECTING,UnifiedVoicePhase.ERROR,UnifiedVoicePhase.STOPPED)
 val statusColor=when{error!=null->colors.error;connected->Color(0xFF20E6A0);else->cyan}
 val status=when {
  background&&!audio.active->"Local wake · ${wake.phase.name.lowercase().replace('_',' ')}"
  connected->"Gemini Live · Connected"
  nativeAudio->"Gemini Live · ${presentation.phase.name.lowercase().replace('_',' ')}"
  else->"Android fallback · ${presentation.phase.name.lowercase().replace('_',' ')}"
 }
 val heading=when(presentation.phase){
  UnifiedVoicePhase.LISTENING,UnifiedVoicePhase.USER_SPEAKING->"Listening to you"
  UnifiedVoicePhase.ASSISTANT_SPEAKING->"Speaking to you"
  UnifiedVoicePhase.IDLE,UnifiedVoicePhase.STOPPED->"Ready when you are"
  UnifiedVoicePhase.PROCESSING->"Processing your request"
  UnifiedVoicePhase.MUTED->"Microphone muted"
  else->presentation.summary
 }
 val userText=if(nativeAudio)audio.interimText.ifBlank{audio.inputText}else ui.transcript
 val assistantText=if(nativeAudio)audio.outputText else ui.streamingText.ifBlank{ui.messages.lastOrNull{it.role=="assistant"}?.text.orEmpty()}
 BoxWithConstraints(Modifier.fillMaxSize().background(ink)) {
  val compact=maxHeight<740.dp
  Column(Modifier.fillMaxSize()) {
   LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("home-content"),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    item {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      IconButton(onClick=onHistory){Icon(Icons.Default.History,"Conversation history",tint=foreground)}
      Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        ReactorMark(Modifier.size(30.dp),cyan)
        Text("JARVIS",fontSize=29.sp,letterSpacing=5.sp,fontWeight=FontWeight.Medium,color=foreground)
       }
       Text("PERSONAL AI ASSISTANT",fontSize=9.sp,letterSpacing=2.sp,color=subtext,modifier=Modifier.padding(top=5.dp))
      }
      IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings",tint=foreground)}
     }
     HorizontalDivider(Modifier.padding(top=12.dp),color=border.copy(alpha=.35f))
    }
    item {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
      Surface(onClick=onVoiceSettings,modifier=Modifier.weight(1f),color=statusColor.copy(alpha=.06f),border=BorderStroke(1.dp,statusColor.copy(alpha=.7f)),shape=RoundedCornerShape(24.dp)) {
       Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).background(statusColor,CircleShape))
        Text(status,style=MaterialTheme.typography.labelLarge,color=statusColor,fontWeight=FontWeight.SemiBold)
       }
      }
      IconButton(onClick={showModes=true},enabled=!busy&&!background){Icon(Icons.Default.Tune,"Voice mode",tint=subtext)}
     }
    }
    item {
     Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
      HolographicFace(presentation.phase,if(nativeAudio)audio.microphoneLevel else ui.microphoneLevel,if(nativeAudio)audio.outputLevel else 0f,audio.muted,Modifier.fillMaxWidth().height(if(compact)268.dp else 350.dp))
      Text(heading,fontSize=if(compact)24.sp else 28.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,color=foreground)
      Text(when{voice.switching->"Preparing your voice session…";voice.permissionRequest!=null->"Waiting for microphone permission";presentation.phase==UnifiedVoicePhase.LISTENING->"Speak naturally. I’m here.";!nativeAudio->"Android recognition + device TTS";audio.muted->"Microphone audio is not transmitted";!active->"Tap Start to begin a conversation";else->"Voice · ${configuration.voice}"},style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center,color=subtext,modifier=Modifier.padding(top=4.dp))
      HologramWaveform(if(nativeAudio)audio else null,ui.microphoneLevel,!nativeAudio&&ui.voiceActive&&ui.state==AssistantState.LISTENING,cyan)
     }
    }
    item {com.raaz.assistant.timer.TimerPanel()}
    if(error!=null) item {AssistantMessageCard("Needs attention",error,true)}
    if(nativeAudio&&ui.error!=null&&ui.error!=error) item {AssistantMessageCard("Text / camera",ui.error,true)}
    if(!background&&nativeMode&&!configuration.configured) item {
     OutlinedCard(onClick=onVoiceSettings,modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.padding(14.dp)) {Text("Connect your natural voice",fontWeight=FontWeight.Bold);Text("Use your saved Google AI Studio key and an accessible Live model.",style=MaterialTheme.typography.bodySmall);TextButton(onClick=onVoiceSettings){Text("Set up Gemini Live")}}
     }
    }
    item {HologramTranscript("YOU",userText,"Your words appear when transcription is available.",false,cyan,border,foreground,subtext)}
    item {HologramTranscript("JARVIS",assistantText,"Your assistant’s transcript appears here when available.",true,cyan,border,foreground,subtext)}
    if(nativeAudio&&(ui.messages.isNotEmpty()||ui.streamingText.isNotBlank())) item {
     val last=ui.messages.lastOrNull()
     TranscriptPreview(if(ui.streamingText.isNotBlank())"TEXT REPLY · STREAMING" else if(last?.role=="user")"YOU · TEXT" else "JARVIS · TEXT",ui.streamingText.ifBlank{last?.text.orEmpty()},"")
    }
    if(ui.generating) item {TextButton(onClick=onStopText){Text("Stop text generation")}}
   }
   Column(Modifier.fillMaxWidth().background(ink).padding(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
     OutlinedIconButton(onClick=onMute,enabled=nativeAudio&&audio.ready,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,border),modifier=Modifier.size(52.dp)) {Icon(if(audio.muted)Icons.Default.MicOff else Icons.Default.Mic,if(audio.muted)"Unmute microphone" else "Mute microphone",tint=if(nativeAudio&&audio.ready)foreground else subtext.copy(alpha=.4f))}
     PrimaryConversationButton(active,busy,{if(active)onStop()else onStart()},Modifier.weight(1f))
     OutlinedIconButton(onClick=onCamera,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,border),modifier=Modifier.size(52.dp)){Icon(Icons.Default.CameraAlt,"Camera",tint=foreground)}
    }
    if(ui.imageAttached)Row(verticalAlignment=Alignment.CenterVertically){Text("Photo attached",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f));TextButton(onClick=onRemovePhoto){Text("Remove photo")}}
    OutlinedTextField(value=text,onValueChange={text=it},placeholder={Text("Type a message…",color=subtext)},shape=RoundedCornerShape(15.dp),maxLines=3,modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=border,focusedBorderColor=cyan),trailingIcon={IconButton(enabled=text.isNotBlank()&&!active&&!ui.generating&&!busy,onClick={onSend(text);text=""}){Icon(Icons.AutoMirrored.Filled.Send,"Send message",tint=if(text.isNotBlank()&&!active)cyan else subtext)}})
    if(text.isNotBlank()&&active)Text("Stop voice to send through your text provider.",style=MaterialTheme.typography.labelSmall,color=subtext)

   }
   HorizontalDivider(color=border.copy(alpha=.35f))
   NavigationBar(containerColor=ink,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
    listOf(Triple("Home",Icons.Default.Home,{}),Triple("Tasks",Icons.Default.TaskAlt,onTasks),Triple("Memory",Icons.Default.Layers,onMemory),Triple("Tools",Icons.Default.Build,onTools)).forEachIndexed {index,item->
     NavigationBarItem(selected=index==0,onClick=item.third,icon={Icon(item.second,null)},label={Text(item.first)},colors=NavigationBarItemDefaults.colors(selectedIconColor=cyan,selectedTextColor=cyan,indicatorColor=cyan.copy(alpha=.10f),unselectedIconColor=foreground,unselectedTextColor=foreground))
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
 Button(onClick=onClick,enabled=active||!busy,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF11DDF3),contentColor=Color(0xFF02131C)),contentPadding=PaddingValues(horizontal=10.dp),shape=RoundedCornerShape(20.dp),modifier=modifier.heightIn(min=60.dp).semantics{contentDescription=if(active)"Stop conversation" else  "Start conversation";stateDescription=if(busy)"Voice operation pending" else  if(active)"Active" else  "Stopped"}) {
  Icon(if(active)Icons.Default.Stop else Icons.Default.Mic,null);Spacer(Modifier.width(10.dp));Text(if(active)"Stop conversation" else if(busy)"Please wait" else "Start conversation",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
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
