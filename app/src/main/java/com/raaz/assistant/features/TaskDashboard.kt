package com.raaz.assistant.features

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun TaskDashboard(vm:HubViewModel,s:HubState,a:Activity) {
 var review by remember{mutableStateOf<AgentTask?>(null)};var clear by remember{mutableStateOf(false)};var visibleCount by remember{mutableIntStateOf(20)}
 Text("Tasks & approvals",style=MaterialTheme.typography.headlineSmall)
 Text("Every record is real. LAUNCHED means Android accepted an intent/action; VERIFIED is reserved for a checked local result. External sending, calls and navigation are never inferred.",style=MaterialTheme.typography.bodyMedium)
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
  TaskMetric("Awaiting review",s.tasks.count{it.status in pendingTaskStates},Modifier.weight(1f))
  TaskMetric("Executing",s.tasks.count{it.status==TaskStatus.EXECUTING},Modifier.weight(1f))
 }
 TextButton(onClick={clear=true}){Text("Clear finished task records")}
 if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("Remove finished records?")},text={Text("Pending, permission-blocked and executing requests remain. Local arguments/results of finished requests are deleted.")},confirmButton={TextButton(onClick={vm.clearFinishedTasks();clear=false}){Text("Clear finished")}},dismissButton={TextButton(onClick={clear=false}){Text("Cancel")}})
 if(s.tasks.isEmpty())OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Icon(Icons.Default.Checklist,null);Text("No actions requested yet",style=MaterialTheme.typography.titleMedium);Text("Use a supported voice command or prepare an action in Control Center. Nothing runs silently.")}}
 s.tasks.take(visibleCount).forEach { task ->
  val tone=when(task.status){TaskStatus.FAILED,TaskStatus.UNSUPPORTED,TaskStatus.UNKNOWN_OUTCOME->MaterialTheme.colorScheme.error;TaskStatus.NEEDS_APPROVAL,TaskStatus.NEEDS_PERMISSION->MaterialTheme.colorScheme.secondary;else->MaterialTheme.colorScheme.primary}
  OutlinedCard(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(task.action.name.replace('_',' '),style=MaterialTheme.typography.titleMedium)
   Surface(color=tone.copy(alpha=.12f),shape=MaterialTheme.shapes.small){Text(taskStatusLabel(task.status),color=tone,modifier=Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium)}
   if(task.status==TaskStatus.EXECUTING)LinearProgressIndicator(Modifier.fillMaxWidth())
   Text("Requested by: ${task.source}",style=MaterialTheme.typography.labelMedium)
   Text("Required access: ${requiredAccess(task.action)}",style=MaterialTheme.typography.bodySmall)
   SelectionContainer{Text(task.action.args.toString(2),style=MaterialTheme.typography.bodyMedium)}
   Text(task.detail,style=MaterialTheme.typography.bodyMedium)
   Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(task.created)),style=MaterialTheme.typography.bodySmall)
   when(task.status){
    TaskStatus.NEEDS_APPROVAL->Button(enabled=!s.busy,onClick={review=task},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Preview and approve once")}
    TaskStatus.NEEDS_PERMISSION->{Text("Grant only the relevant capability in Control Center / Setup. A new preview and approval are required after re-preparing.",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={vm.propose(a,task.action,"User re-prepared permission-blocked request")}){Text("Recheck & prepare a new request")}}
    else->Unit
   }
   if(task.status in pendingTaskStates||task.status==TaskStatus.EXECUTING)TextButton(onClick={vm.cancel(task.id)}){Text("Cancel this request")}
  }}
 }
 if(s.tasks.size>visibleCount)OutlinedButton(onClick={visibleCount+=20}){Text("Load more task history")}
 review?.let{task->AlertDialog(onDismissRequest={review=null},title={Text("Approve this action once?")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  Text(task.action.name.replace('_',' '),style=MaterialTheme.typography.titleMedium)
  Text(requiredAccess(task.action));SelectionContainer{Text(task.action.args.toString(2))}
  Text("Check the exact target, number, message or server. A direct call can initiate a real call; memory deletion removes local data; MCP tools can have external effects. Only approve if you requested this exact action. Launch does not prove final completion.")
 }},confirmButton={TextButton(onClick={vm.approve(a,task.id);review=null}){Text("Approve once")}},dismissButton={TextButton(onClick={review=null}){Text("Keep pending")}})}
}
@Composable private fun TaskMetric(label:String,count:Int,modifier:Modifier){Surface(modifier=modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.padding(14.dp)){Text(count.toString(),style=MaterialTheme.typography.headlineSmall);Text(label,style=MaterialTheme.typography.labelMedium)}}}
