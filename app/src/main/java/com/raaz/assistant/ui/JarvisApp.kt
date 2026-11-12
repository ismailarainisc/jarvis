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
import androidx.compose.ui.graphics.luminance
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
import com.raaz.assistant.voice.*
import com.raaz.assistant.setup.*

private val Cyan=Color(0xFF59E7FF);private val Navy=Color(0xFF03111D);private val SurfaceBlue=Color(0xFF0A2132);private val Violet=Color(0xFFB59AFF)
private enum class Screen { HOME, HISTORY, MEMORY, PROVIDERS, CAMERA, TASKS, SETTINGS, ABOUT, LIVE, HUB, SETUP, BACKGROUND }
@Composable fun JarvisApp(
 vm:AssistantViewModel,native:GeminiLiveUi,configuration:GeminiVoiceConfiguration,voice:HomeVoiceUi,
 onListen:()->Unit,onMode:(HomeVoiceMode)->Unit,onMute:()->Unit,onVoiceSurface:(Boolean)->Unit,setupRequest:Long,
 onCamera:()->Unit,onSend:(String)->Unit,onStop:()->Unit,onStopText:()->Unit,
 setupContent:@Composable (()->Unit,()->Unit,()->Unit,()->Unit)->Unit={_,_,_,_->},
 liveContent:@Composable ()->Unit={},featureContent:@Composable (String)->Unit={},
 featureNotice:String?=null,pendingTasks:Int=0,initialFeature:String?=null
){
 val backgroundRunning by BackgroundRuntime.running.collectAsStateWithLifecycle();val backgroundLive by BackgroundRuntime.live.collectAsStateWithLifecycle();val wake by BackgroundRuntime.wake.collectAsStateWithLifecycle()
 val appContext=LocalContext.current;val setup=remember(appContext){SetupProgress(AndroidSettingsStore(appContext))};val ui by vm.ui.collectAsStateWithLifecycle(); var pageName by rememberSaveable{mutableStateOf(if(setup.read().completed)Screen.HOME.name else Screen.SETUP.name)};val page=Screen.valueOf(pageName);LaunchedEffect(initialFeature){if(initialFeature!=null&&setup.read().completed)pageName=Screen.HUB.name};var setupReturn by rememberSaveable{mutableStateOf(false)};fun go(s:Screen){pageName=s.name};fun back(){if(setupReturn){setupReturn=false;go(Screen.SETUP)}else go(Screen.HOME)}; BackHandler(page!=Screen.HOME){back()}
 LaunchedEffect(page){onVoiceSurface(foregroundVoiceWorkspace(page.name))}
 LaunchedEffect(setupRequest){if(setupRequest>0)go(Screen.LIVE)}
 DisposableEffect(Unit){onDispose{onVoiceSurface(false)}}
 val colors=if(ui.settings.dark) darkColorScheme(primary=Cyan,secondary=Violet,background=Navy,surface=SurfaceBlue) else lightColorScheme(primary=Color(0xFF006878),secondary=Color(0xFF644DA1))
 MaterialTheme(colorScheme=colors){Scaffold(topBar={JarvisTopBar(if(page==Screen.LIVE)"Voice settings" else  page.name.replace('_',' '),page!=Screen.HOME,{back()},{go(Screen.SETTINGS)},{go(Screen.HISTORY)})}){pad-> Column(Modifier.fillMaxSize().padding(pad).imePadding().navigationBarsPadding()){
 if(featureNotice!=null||pendingTasks>0)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(featureNotice?:"$pendingTasks task(s) need approval",maxLines=2,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={go(Screen.TASKS)}){Text("Review")}}
 when(page){
 Screen.SETUP -> setupContent({setupReturn=false;go(Screen.HOME)},{setupReturn=true;go(Screen.PROVIDERS)},{setupReturn=true;go(Screen.BACKGROUND)},{setupReturn=true;go(Screen.LIVE)})
 Screen.BACKGROUND -> featureContent("Background")
 Screen.LIVE -> liveContent()
 Screen.HUB -> featureContent(initialFeature?:"Control")
 Screen.HOME -> PremiumHomeScreen(ui,native,configuration,voice,backgroundRunning,backgroundLive,wake,
  onStart=onListen,onStop=onStop,onMute=onMute,onMode=onMode,onVoiceSettings={go(Screen.LIVE)},
  onHistory={go(Screen.HISTORY)},onTools={go(Screen.HUB)},onMemory={go(Screen.MEMORY)},onSettings={go(Screen.SETTINGS)},
  onCamera=onCamera,onRemovePhoto=vm::removePhoto,onSend=onSend,onStopText=onStopText)
 Screen.PROVIDERS -> Providers(ui,vm::saveProfile,vm::selectProfile,vm::removeProfile,vm::deleteKey,vm::testProvider)
 Screen.HISTORY -> History(ui.messages,vm::clearHistory)
 Screen.SETTINGS -> SettingsScreen(ui.settings,{vm.saveSettings(it,null)},{go(Screen.PROVIDERS)},{go(Screen.ABOUT)},{go(Screen.LIVE)},{setup.revisit();go(Screen.SETUP)})
 Screen.MEMORY -> featureContent("Memory")
 Screen.CAMERA -> Column(Modifier.padding(20.dp)){Text("Camera & vision",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(12.dp));Text("Take a photo to attach to your next message. The selected model must support image input.");Spacer(Modifier.height(16.dp));Button(onClick=onCamera){Text("Take photo")};if(ui.imageAttached){Text("Photo attached");TextButton(onClick=vm::removePhoto){Text("Remove photo")}}}
 Screen.TASKS -> featureContent("Tasks")
 Screen.ABOUT -> featureContent("Diagnostics")
 }
 }}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JarvisTopBar(title:String,back:Boolean,onBack:()->Unit,onSettings:()->Unit,onHistory:()->Unit){TopAppBar(title={Column{Text(if(back) title else "JARVIS",fontWeight=FontWeight.Bold,letterSpacing=2.sp);if(!back)Text("PERSONAL AI COMPANION",style=MaterialTheme.typography.labelSmall)}},navigationIcon={if(back)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},actions={if(!back){IconButton(onClick=onHistory){Icon(Icons.Default.History,"Conversation history")};IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings")}}})}
@Composable fun JarvisFace(state:AssistantState,microphoneLevel:Float=0f,playbackLevel:Float=0f,muted:Boolean=false){
 val context=LocalContext.current
 val reduced=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 val phase=if(reduced)0f else jarvisOrbitPhase()
 val color=when(state){AssistantState.ERROR->MaterialTheme.colorScheme.error;AssistantState.THINKING,AssistantState.PROCESSING,AssistantState.CONNECTING->MaterialTheme.colorScheme.secondary;AssistantState.SPEAKING->if(MaterialTheme.colorScheme.surface.luminance()>.5f)Color(0xFF087653)else Color(0xFF65E8BD);else->MaterialTheme.colorScheme.primary}
 Canvas(Modifier.sizeIn(maxWidth=230.dp,maxHeight=230.dp).aspectRatio(1f).semantics{contentDescription="Assistant visual, ${state.name.lowercase()}"}){
  val r=size.minDimension*.43f
  val measured=if(reduced)0f else maxOf(if(muted)0f else microphoneLevel,playbackLevel).coerceIn(0f,1f)
  drawCircle(color.copy(alpha=.08f+measured*.12f),r*(.90f+measured*.10f))
  drawCircle(color.copy(alpha=.4f),r,style=Stroke(1.dp.toPx()))
  drawArc(color,360*phase,105f,false,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round),topLeft=center-androidx.compose.ui.geometry.Offset(r,r),size=androidx.compose.ui.geometry.Size(r*2,r*2))
  drawCircle(color.copy(alpha=.25f),r*.76f,style=Stroke(1.dp.toPx()))
  drawCircle(color.copy(alpha=.06f+measured*.08f),r*.62f)
  drawArc(color.copy(alpha=.20f),-360*phase+140f,76f,false,style=Stroke(1.dp.toPx()),topLeft=center-androidx.compose.ui.geometry.Offset(r*.87f,r*.87f),size=androidx.compose.ui.geometry.Size(r*1.74f,r*1.74f))
  for(index in 0 until 24){val angle=index*Math.PI/12;val outer=androidx.compose.ui.geometry.Offset((kotlin.math.cos(angle)*r*1.10).toFloat(),(kotlin.math.sin(angle)*r*1.10).toFloat());val inner=outer*.96f;drawLine(color.copy(alpha=.16f),center+inner,center+outer,1.dp.toPx())}
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
@Composable private fun SettingsScreen(s:Settings,save:(Settings)->Unit,providers:()->Unit,about:()->Unit,voiceSettings:()->Unit,setup:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Preferences",style=MaterialTheme.typography.headlineSmall);SettingsRow("Dark appearance",s.dark){save(s.copy(dark=it))};SettingsRow("Spoken text replies (Android fallback only)",s.voice){save(s.copy(voice=it))};Text("Fallback speech language",style=MaterialTheme.typography.titleSmall);Row{listOf("System","English","Urdu").forEach{FilterChip(selected=s.language==it,onClick={save(s.copy(language=it))},label={Text(it)},modifier=Modifier.padding(end=4.dp))}};Text("Foreground voice continues in Home, Voice settings and the approval/control workspace; other screens and backgrounding stop it. Optional background wake mode must be explicitly enabled in Tools & background and uses a persistent notification.");Text("Manage local memory and its separate cloud-context consent from Home → Memory. Chat history remains local; clear it from History.");TextButton(onClick=setup){Text("Revisit setup & permissions")};TextButton(onClick=voiceSettings){Text("Advanced voice settings")};TextButton(onClick=providers){Text("AI provider settings")};TextButton(onClick=about){Text("About & diagnostics")}}}
@Composable private fun SettingsRow(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(label,modifier=Modifier.weight(1f).padding(end=12.dp));Switch(checked=checked,onCheckedChange=onChange)}}
