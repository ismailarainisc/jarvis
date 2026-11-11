package com.raaz.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raaz.assistant.AssistantViewModel
import com.raaz.assistant.data.*
import com.raaz.assistant.background.*
import com.raaz.assistant.gemini.*

private val Cyan=Color(0xFF59E7FF);private val Navy=Color(0xFF03111D);private val SurfaceBlue=Color(0xFF0A2132);private val Violet=Color(0xFFB59AFF)
private enum class Screen { HOME, HISTORY, MEMORY, PROVIDERS, CAMERA, TASKS, SETTINGS, ABOUT, LIVE, HUB }
@Composable fun JarvisApp(vm:AssistantViewModel,onListen:()->Unit,onCamera:()->Unit,onSend:(String)->Unit,onStop:()->Unit,liveContent:@Composable ()->Unit = {},featureContent:@Composable (String)->Unit = {},featureNotice:String?=null,pendingTasks:Int=0,initialFeature:String?=null){
 val backgroundRunning by BackgroundRuntime.running.collectAsStateWithLifecycle();val backgroundLive by BackgroundRuntime.live.collectAsStateWithLifecycle();val wake by BackgroundRuntime.wake.collectAsStateWithLifecycle()
 val ui by vm.ui.collectAsStateWithLifecycle(); var pageName by rememberSaveable{mutableStateOf(Screen.HOME.name)};val page=Screen.valueOf(pageName);LaunchedEffect(initialFeature){if(initialFeature!=null)pageName=Screen.HUB.name};fun go(s:Screen){pageName=s.name}; BackHandler(page!=Screen.HOME){go(Screen.HOME)}
 val colors=if(ui.settings.dark) darkColorScheme(primary=Cyan,secondary=Violet,background=Navy,surface=SurfaceBlue) else lightColorScheme(primary=Color(0xFF006878),secondary=Color(0xFF644DA1))
 MaterialTheme(colorScheme=colors){Scaffold(topBar={JarvisTopBar(page.name.replace('_',' '),page!=Screen.HOME,{go(Screen.HOME)},{go(Screen.SETTINGS)})}){pad-> Column(Modifier.fillMaxSize().padding(pad).imePadding().navigationBarsPadding()){
 if(featureNotice!=null||pendingTasks>0)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(featureNotice?:"$pendingTasks task(s) need approval",maxLines=2,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={go(Screen.TASKS)}){Text("Review")}}
 when(page){
 Screen.LIVE -> liveContent()
 Screen.HUB -> featureContent(initialFeature?:"Tools")
 Screen.HOME -> Home(ui,onListen,onCamera,onSend,onStop,{go(it)},backgroundRunning,backgroundLive,wake)
 Screen.PROVIDERS -> Providers(ui,vm::saveProfile,vm::selectProfile,vm::removeProfile,vm::deleteKey,vm::testProvider)
 Screen.HISTORY -> History(ui.messages,vm::clearHistory)
 Screen.SETTINGS -> SettingsScreen(ui.settings,{vm.saveSettings(it,null)},{go(Screen.PROVIDERS)},{go(Screen.ABOUT)})
 Screen.MEMORY -> featureContent("Memory")
 Screen.CAMERA -> Column(Modifier.padding(20.dp)){Text("Camera & vision",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(12.dp));Text("Take a photo to attach to your next message. The selected model must support image input.");Spacer(Modifier.height(16.dp));Button(onClick=onCamera){Text("Take photo")};if(ui.imageAttached){Text("Photo attached");TextButton(onClick=vm::removePhoto){Text("Remove photo")}}}
 Screen.TASKS -> featureContent("Tasks")
 Screen.ABOUT -> featureContent("Diagnostics")
 }
 }}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JarvisTopBar(title:String,back:Boolean,onBack:()->Unit,onSettings:()->Unit){TopAppBar(title={Column{Text(if(back) title else "JARVIS",fontWeight=FontWeight.Bold,letterSpacing=2.sp);if(!back)Text("PERSONAL AI COMPANION",style=MaterialTheme.typography.labelSmall)}},navigationIcon={if(back)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},actions={if(!back)IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings")}})}
@Composable private fun Home(ui:AssistantUiState,onListen:()->Unit,onCamera:()->Unit,onSend:(String)->Unit,onStop:()->Unit,go:(Screen)->Unit,background:Boolean,backgroundLive:GeminiLiveUi,wake:WakeState){var input by rememberSaveable{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){ConnectionStatus(ui.settings.hasKey);TextButton(onClick={go(Screen.PROVIDERS)}){Text("Providers")}}
 Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){val shown=if(!background)ui.state else if(backgroundLive.active)backgroundLive.phase.visualState()else when(wake.phase){WakePhase.ARMED->AssistantState.LISTENING;WakePhase.ARMING,WakePhase.HANDOFF->AssistantState.CONNECTING;WakePhase.ERROR->AssistantState.ERROR;else->AssistantState.STOPPED};JarvisFace(shown,if(background)backgroundLive.microphoneLevel else ui.microphoneLevel,if(background)backgroundLive.outputLevel else 0f,background&&backgroundLive.muted);Spacer(Modifier.height(8.dp));AssistantStatusIndicator(shown);Text(if(background)"Background: ${wake.phase} · Gemini: ${backgroundLive.phase}" else  if(ui.voiceActive)"Turn-based voice · interruption unavailable" else "Tap Start for a voice session",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(ui.voiceActive){LinearProgressIndicator(progress={ui.microphoneLevel},modifier=Modifier.fillMaxWidth(.6f).padding(vertical=8.dp),color=MaterialTheme.colorScheme.primary);if(ui.transcript.isNotBlank())Text(ui.transcript,style=MaterialTheme.typography.bodyMedium)};ui.error?.let{ErrorState(it)}}}
 if(ui.imageAttached) Row(verticalAlignment=Alignment.CenterVertically){Text("Photo attached",style=MaterialTheme.typography.labelMedium);TextButton(onClick={go(Screen.CAMERA)}){Text("Manage")}}
 if(ui.streamingText.isNotEmpty()) ConversationBubble(Message("assistant",ui.streamingText)) else ui.messages.lastOrNull()?.let{ConversationBubble(it)}
 if(ui.generating && !ui.voiceActive) TextButton(onClick=onStop){Text("Stop generating")}
 OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Message JARVIS")},maxLines=3,trailingIcon={IconButton(enabled=!ui.voiceActive && !ui.generating,onClick={if(input.isNotBlank()){onSend(input);input=""}}){Icon(Icons.AutoMirrored.Filled.Send,"Send message")}})
 Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){val active=ui.voiceActive||background;Button(onClick=if(active)onStop else onListen,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Icon(if(active)Icons.Default.Stop else Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text(if(background)"Stop background" else  if(active)"Stop" else "Fallback voice")};OutlinedButton(onClick=onCamera,modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Default.CameraAlt,"Take photo")}}
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Tools & background" to Screen.HUB,"Gemini Live" to Screen.LIVE,"History" to Screen.HISTORY,"Memory" to Screen.MEMORY,"Vision" to Screen.CAMERA,"Tasks" to Screen.TASKS,"Settings" to Screen.SETTINGS).forEach{(label,target)->TextButton(onClick={go(target)}){Text(label)}}}
 }}
@Composable fun JarvisFace(state:AssistantState,microphoneLevel:Float=0f,playbackLevel:Float=0f,muted:Boolean=false){
 val context=LocalContext.current
 val reduced=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 val phase=if(reduced)0f else jarvisOrbitPhase()
 val color=when(state){AssistantState.ERROR->Color(0xFFFF747C);AssistantState.THINKING,AssistantState.PROCESSING,AssistantState.CONNECTING->Violet;AssistantState.SPEAKING->Color(0xFF65E8BD);else->Cyan}
 Canvas(Modifier.sizeIn(maxWidth=230.dp,maxHeight=230.dp).aspectRatio(1f).semantics{contentDescription="Assistant visual, ${state.name.lowercase()}"}){
  val r=size.minDimension*.43f
  val measured=if(reduced)0f else maxOf(if(muted)0f else microphoneLevel,playbackLevel).coerceIn(0f,1f)
  drawCircle(color.copy(alpha=.08f+measured*.12f),r*(.90f+measured*.10f))
  drawCircle(color.copy(alpha=.4f),r,style=Stroke(1.dp.toPx()))
  drawArc(color,360*phase,105f,false,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round),topLeft=center-androidx.compose.ui.geometry.Offset(r,r),size=androidx.compose.ui.geometry.Size(r*2,r*2))
  drawCircle(color.copy(alpha=.25f),r*.76f,style=Stroke(1.dp.toPx()))
  val path=androidx.compose.ui.graphics.Path().apply{moveTo(center.x-r*.45f,center.y-r*.38f);lineTo(center.x-r*.40f,center.y+r*.20f);quadraticTo(center.x,center.y+r*.75f,center.x+r*.40f,center.y+r*.20f);lineTo(center.x+r*.45f,center.y-r*.38f)}
  drawPath(path,color,style=Stroke(2.dp.toPx()))
  drawLine(color,center+androidx.compose.ui.geometry.Offset(-r*.35f,-r*.1f),center+androidx.compose.ui.geometry.Offset(-r*.13f,-r*.05f),2.dp.toPx())
  drawLine(color,center+androidx.compose.ui.geometry.Offset(r*.35f,-r*.1f),center+androidx.compose.ui.geometry.Offset(r*.13f,-r*.05f),2.dp.toPx())
  drawLine(color,center+androidx.compose.ui.geometry.Offset(-r*.18f,r*.28f),center+androidx.compose.ui.geometry.Offset(r*.18f,r*.28f),2.dp.toPx())
 }
}
@Composable private fun jarvisOrbitPhase():Float {val infinite=rememberInfiniteTransition(label="decorative orbit");val phase by infinite.animateFloat(0f,1f,infiniteRepeatable(tween(4000,easing=LinearEasing)),label="decorative orbit");return phase}
@Composable private fun AssistantStatusIndicator(s:AssistantState){Text(s.name.replace('_',' '),color=if(s==AssistantState.ERROR)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}
@Composable private fun ConnectionStatus(hasKey:Boolean){Text(if(hasKey)"Provider configured · not tested" else "Provider not configured",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun ConversationBubble(m:Message){Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp)){Column(Modifier.padding(12.dp)){Text(if(m.role=="user")"YOU" else "JARVIS",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text(m.text)}}}
@Composable private fun ErrorState(message:String){Text(message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
@Composable private fun InfoScreen(title:String,description:String){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)){Text(title,style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(12.dp));Text(description)}}
@Composable private fun History(messages:List<Message>,clear:()->Unit){var confirm by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(16.dp)){if(messages.isEmpty())Text("No conversations yet.") else {TextButton(onClick={confirm=true}){Text("Clear history")};LazyColumn{items(messages){ConversationBubble(it)}}}};if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Clear chat history?")},text={Text("This removes locally stored messages.")},confirmButton={TextButton(onClick={clear();confirm=false}){Text("Clear")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})}
@Composable private fun Providers(ui:AssistantUiState,save:(String?,String,String,String,Boolean,String)->Unit,select:(String)->Unit,remove:(String)->Unit,deleteKey:()->Unit,test:()->Unit){
 var editing by remember{mutableStateOf<String?>(null)};var name by remember{mutableStateOf("")};var url by remember{mutableStateOf("")};var model by remember{mutableStateOf("")};var key by remember{mutableStateOf("")};var stream by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("AI providers",style=MaterialTheme.typography.headlineSmall);Text("OpenAI-compatible chat endpoints, including supported Gemini and NVIDIA endpoints. Custom endpoints receive your key and messages; only use services you trust.")
 ui.providers.forEach { p -> Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(p.name+(if(p.id==ui.selectedProvider)" · SELECTED" else ""),fontWeight=FontWeight.Bold);Text(p.url,style=MaterialTheme.typography.bodySmall);Text("Model: "+p.model+" · Key: "+if(p.hasKey)"saved" else "missing");Text("Streaming: "+if(p.streaming)"enabled (not verified)" else "off");Text("Audio input/output and realtime: not supported");val status=ui.providerStatus[p.id]?:ProviderStatus();Text(status.state.name+" · "+status.detail,style=MaterialTheme.typography.bodySmall);Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={select(p.id)}){Text("Select")};TextButton(onClick={editing=p.id;name=p.name;url=p.url;model=p.model;stream=p.streaming;key=""}){Text("Edit")};TextButton(onClick={remove(p.id)}){Text("Remove")}}}} }
 Text(if(editing==null)"Add provider" else "Edit provider",style=MaterialTheme.typography.titleMedium)
 Row(Modifier.horizontalScroll(rememberScrollState())) {listOf("OpenAI" to "https://api.openai.com/v1/chat/completions","Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions","NVIDIA" to "https://integrate.api.nvidia.com/v1/chat/completions").forEach{(n,u)->TextButton(onClick={name=n;url=u}){Text(n)}}}
 OutlinedTextField(name,{name=it},label={Text("Provider name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(url,{url=it},label={Text("HTTPS chat completions URL")},modifier=Modifier.fillMaxWidth());OutlinedTextField(model,{model=it},label={Text("Model ID")},modifier=Modifier.fillMaxWidth());OutlinedTextField(key,{key=it},label={Text("API key (blank keeps existing)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());SettingsRow("Enable SSE streaming",stream){stream=it};ui.error?.let{ErrorState(it)}
 Button(onClick={save(editing,name,url,model,stream,key);key=""}){Text("Save and select")};TextButton(onClick={editing=null;name="";url="";model="";key="";stream=false}){Text("New provider")};OutlinedButton(onClick=test){Text("Test selected provider")};OutlinedButton(onClick=deleteKey){Text("Delete selected key")}
 }}
@Composable private fun SettingsScreen(s:Settings,save:(Settings)->Unit,providers:()->Unit,about:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Preferences",style=MaterialTheme.typography.headlineSmall);SettingsRow("Dark appearance",s.dark){save(s.copy(dark=it))};SettingsRow("Spoken replies",s.voice){save(s.copy(voice=it))};Text("Speech language",style=MaterialTheme.typography.titleSmall);Row{listOf("System","English","Urdu").forEach{FilterChip(selected=s.language==it,onClick={save(s.copy(language=it))},label={Text(it)},modifier=Modifier.padding(end=4.dp))}};Text("Foreground voice stops when you leave it. Optional background wake mode must be explicitly enabled in Tools & background and uses a persistent notification.");Text("Manage local memory and its separate cloud-context consent from Home → Memory. Chat history remains local; clear it from History.");TextButton(onClick=providers){Text("AI provider settings")};TextButton(onClick=about){Text("About & diagnostics")}}}
@Composable private fun SettingsRow(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(label);Switch(checked=checked,onCheckedChange=onChange)}}
