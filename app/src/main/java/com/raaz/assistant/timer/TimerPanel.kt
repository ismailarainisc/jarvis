package com.raaz.assistant.timer

import android.Manifest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.raaz.assistant.features.ToolAction
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable fun TimerPanel(){
 val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 val timers=remember(context){AndroidTimers(context)}
 var timer by remember{mutableStateOf<OwnedTimer?>(null)};var seconds by remember{mutableLongStateOf(0)}
 var allowed by remember{mutableStateOf(false)};var exact by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=timers.notificationsAllowed()}
 LaunchedEffect(owner,timers){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){try{timer=timers.snapshot();seconds=timer?.remaining(timers.now())?:0;allowed=timers.notificationsAllowed();exact=timers.exactAvailable()}catch(e:Exception){error="Timer state is unavailable; no running/finished state is claimed."};delay(1000)}}}
 OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text("JARVIS timer",style=MaterialTheme.typography.titleSmall)
  Text(timer?.let{if(it.phase==TimerPhase.RUNNING)"${seconds/60}:${(seconds%60).toString().padStart(2,'0')} remaining" else it.phase.name.replace('_',' ')}?:"No timer")
  if(!exact||timer?.let{it.phase==TimerPhase.RUNNING&&!it.exact}==true)Text("Background alerts may be delayed by Android. Precise timing is optional special access; enable before starting a new timer.",style=MaterialTheme.typography.bodySmall)
  if(!allowed)TextButton(onClick={if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("Enable timer notifications")}
  TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("Notification settings")}
  if(Build.VERSION.SDK_INT>=31&&!exact)TextButton(onClick={try{context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}catch(e:Exception){error="Precise timer access is unavailable on this device."}}){Text("Enable precise alerts (optional)")}
  Row {
   if(timer?.phase==TimerPhase.RUNNING){TextButton(onClick={timer?.let{timer=timers.stop(it.id,false)}}){Text("Stop")};TextButton(onClick={timer?.let{timer=timers.stop(it.id,true)}}){Text("Cancel")}}
   else if(timer?.phase in setOf(TimerPhase.FINISHED,TimerPhase.ALERT_FAILED))TextButton(onClick={timer?.let{timer=timers.stop(it.id,false)}}){Text("Stop alert")}
   else TextButton(onClick={val result=timers.execute(ToolAction("set_timer",JSONObject().put("seconds",300)),java.util.UUID.randomUUID().toString());error=if(result.status==com.raaz.assistant.features.TaskStatus.VERIFIED)null else result.detail;timer=timers.snapshot()}){Text("Start 5 minutes")}
  }
  if(timer?.phase!=TimerPhase.RUNNING)TextButton(onClick={try{timers.clearJournal();error=null}catch(e:Exception){error="Request journal could not be cleared."}}){Text("Clear old timer request journal")}
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }}
}
