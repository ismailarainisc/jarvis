package com.raaz.assistant.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raaz.assistant.R
import com.raaz.assistant.gemini.GeminiLiveUi
import com.raaz.assistant.voice.UnifiedVoicePhase
import kotlin.math.min

/** Original artwork is decoration; live meters below are exclusively actual measured samples. */
@Composable internal fun HolographicFace(phase:UnifiedVoicePhase,microphone:Float,playback:Float,muted:Boolean,modifier:Modifier=Modifier) {
 val context=LocalContext.current
 val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 val measurement=when(phase){UnifiedVoicePhase.ASSISTANT_SPEAKING->playback;UnifiedVoicePhase.LISTENING,UnifiedVoicePhase.USER_SPEAKING->if(muted)0f else microphone;else->0f}.coerceIn(0f,1f)
 val level by animateFloatAsState(measurement,animationSpec=tween(if(reduced)0 else 100),label="Measured audio energy")
 val accent=when(phase){UnifiedVoicePhase.ERROR->MaterialTheme.colorScheme.error;UnifiedVoicePhase.PROCESSING,UnifiedVoicePhase.SETTING_UP,UnifiedVoicePhase.CONNECTING->Color(0xFFB28AFF);UnifiedVoicePhase.ASSISTANT_SPEAKING->Color(0xFF57F4C1);else->Color(0xFF19DFF8)}
 Box(modifier.semantics{contentDescription="JARVIS holographic face";stateDescription=phase.name.lowercase().replace('_',' ')},contentAlignment=Alignment.Center) {
  Image(painterResource(R.drawable.jarvis_hologram),contentDescription=null,modifier=Modifier.fillMaxSize())
  Canvas(Modifier.fillMaxSize()) {
   val radius=min(size.width,size.height)*.425f
   drawCircle(accent.copy(alpha=if(phase==UnifiedVoicePhase.ERROR).8f else .12f+level*.55f),radius,style=Stroke((1+level*2).dp.toPx()))
  }
 }
}

@Composable internal fun ReactorMark(modifier:Modifier=Modifier,color:Color=Color(0xFF17E6FF)) {
 Canvas(modifier) {
  val c=center;val radius=min(size.width,size.height)*.44f
  drawCircle(color.copy(alpha=.13f),radius);drawCircle(color,radius,style=Stroke(1.2.dp.toPx()));drawCircle(color.copy(alpha=.6f),radius*.79f,style=Stroke(.7.dp.toPx()))
  val p=Path().apply{moveTo(c.x-radius*.57f,c.y-radius*.30f);lineTo(c.x+radius*.57f,c.y-radius*.30f);lineTo(c.x,c.y+radius*.65f);close()}
  drawPath(p,color);drawArc(color,200f,140f,false,topLeft=Offset(c.x-radius*.64f,c.y-radius*.64f),size=androidx.compose.ui.geometry.Size(radius*1.28f,radius*1.28f),style=Stroke(1.dp.toPx()))
 }
}

/** History contains received energy values only: no sine/random animation masquerades as audio. */
@Composable internal fun HologramWaveform(audio:GeminiLiveUi?,fallbackLevel:Float,fallbackActive:Boolean,color:Color) {
 val output=audio?.playback==true&&audio.outputSamples>0
 val muted=audio?.muted==true&&!output
 val available=if(audio!=null)if(output)true else audio.capture&&audio.microphoneSamples>0&&!audio.muted else fallbackActive
 val sample=if(audio!=null)if(output)audio.outputLevel else audio.microphoneLevel else fallbackLevel
 val sequence=if(output)audio?.outputSamples else audio?.microphoneSamples
 val samples=remember{mutableStateListOf<Float>()}
 LaunchedEffect(sequence,if(audio==null)fallbackLevel else null,available,muted,output){
  if(!available||muted)samples.clear()else {samples.add(sample.coerceIn(0f,1f));while(samples.size>48)samples.removeAt(0)}
 }
 Canvas(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=16.dp).semantics{contentDescription=if(output)"Measured playback waveform" else "Microphone waveform";stateDescription=when{muted->"Muted";!available->"No measurements available";else->"Measured audio"}}) {
  val dx=size.width/48
  for(i in 0 until 48){val x=dx*(i+.5f);val j=i-(48-samples.size);val value=if(j in samples.indices)samples[j]else 0f;val h=maxOf(1.dp.toPx(),value*size.height*.45f);drawLine(color.copy(alpha=if(available).95f else .25f),Offset(x,center.y-h),Offset(x,center.y+h),2.dp.toPx(),StrokeCap.Round)}
 }
}

@Composable internal fun HologramTranscript(title:String,text:String,empty:String,assistant:Boolean,cyan:Color,border:Color,foreground:Color,subtext:Color) {
 var expanded by rememberSaveable{mutableStateOf(false)}
 val shape=remember{CutCornerShape()}
 val scheme=MaterialTheme.colorScheme
 Surface(shape=shape,color=scheme.surface.copy(alpha=.6f),border=androidx.compose.foundation.BorderStroke(1.dp,if(assistant)cyan.copy(alpha=.65f)else border.copy(alpha=.8f)),modifier=Modifier.fillMaxWidth()) {
  Row(Modifier.padding(13.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
   Box(Modifier.size(34.dp).background(cyan.copy(alpha=.10f),CircleShape),contentAlignment=Alignment.Center){if(assistant)ReactorMark(Modifier.size(30.dp),cyan)else Icon(Icons.Default.Person,null,tint=subtext,modifier=Modifier.size(24.dp))}
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
    Text(title,color=if(assistant)cyan else subtext,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
    SelectionContainer{Text(text.ifBlank{empty},color=if(text.isBlank())subtext else foreground,style=MaterialTheme.typography.bodyMedium,maxLines=if(expanded)Int.MAX_VALUE else 3)}
    if(text.length>180)TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Show less" else "Read more")}
   }
  }
 }
}

/** Chamfered sci-fi border remains a normal accessible Compose surface. */
private class CutCornerShape:Shape {
 override fun createOutline(size:androidx.compose.ui.geometry.Size,layoutDirection:androidx.compose.ui.unit.LayoutDirection,density:androidx.compose.ui.unit.Density):Outline {
  val c=with(density){10.dp.toPx()}.coerceAtMost(min(size.width,size.height)/3)
  return Outline.Generic(Path().apply{moveTo(c,0f);lineTo(size.width-c,0f);lineTo(size.width,c);lineTo(size.width,size.height-c);lineTo(size.width-c,size.height);lineTo(c,size.height);lineTo(0f,size.height-c);lineTo(0f,c);close()})
 }
}
