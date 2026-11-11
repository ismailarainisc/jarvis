package com.raaz.assistant.features

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** No arbitrary tapping, text extraction, login, sending or permission approval. */
class ScopedAutomationService:AccessibilityService() {
 companion object {
  @Volatile private var current:ScopedAutomationService?=null
  suspend fun perform(activity:Activity,target:String,direction:String,stillAllowed:()->Boolean):ToolOutcome {
   val service=current?:return ToolOutcome(TaskStatus.NEEDS_PERMISSION,"Enable JARVIS scoped accessibility service manually in Android settings.")
   if(target !in setOf("com.google.android.youtube","com.android.chrome")||direction !in setOf("forward","backward","back"))return ToolOutcome(TaskStatus.UNSUPPORTED,"Unsupported scope.")
   val launch=activity.packageManager.getLaunchIntentForPackage(target)?:return ToolOutcome(TaskStatus.UNSUPPORTED,"Target app is not installed.")
   activity.startActivity(launch)
   repeat(12){
    if(!stillAllowed())return ToolOutcome(TaskStatus.CANCELLED,"App automation permission was revoked before dispatch.")
    if((activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked)return ToolOutcome(TaskStatus.FAILED,"Device locked; automation stopped.")
    if(current!==service)return ToolOutcome(TaskStatus.CANCELLED,"Accessibility service disconnected.")
    val root=service.rootInActiveWindow
    if(root?.packageName?.toString()==target){
     val accepted=if(direction=="back")service.performGlobalAction(GLOBAL_ACTION_BACK)else service.scroll(root,direction)
     return ToolOutcome(if(accepted)TaskStatus.LAUNCHED else TaskStatus.FAILED,if(accepted)"One approved navigation action dispatched. Final screen was not verified." else "No supported scrollable node accepted the action.")
    }
    delay(150)
   }
   return ToolOutcome(TaskStatus.FAILED,"Target app did not become the active accessible window. No navigation action performed.")
  }
 }
 private fun scroll(root:AccessibilityNodeInfo,direction:String):Boolean {
  val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root);var count=0
  while(queue.isNotEmpty()&&count++<200){val node=queue.removeFirst();if(node.isScrollable&&node.performAction(if(direction=="forward")AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;for(i in 0 until minOf(node.childCount,30))node.getChild(i)?.let{queue.add(it)}};return false
 }
 override fun onServiceConnected(){current=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){if(current===this)current=null}
 override fun onDestroy(){if(current===this)current=null;super.onDestroy()}
}
