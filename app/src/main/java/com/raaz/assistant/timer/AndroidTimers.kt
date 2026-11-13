package com.raaz.assistant.timer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raaz.assistant.MainActivity
import com.raaz.assistant.R
import com.raaz.assistant.features.*
import org.json.JSONObject

/** One process-wide lock, persistent state, no microphone or background service. */
class AndroidTimers(context:Context):TimerPorts {
 private val app=context.applicationContext
 private val prefs=app.getSharedPreferences("owned_timer",Context.MODE_PRIVATE)
 private val alarms=app.getSystemService(AlarmManager::class.java)
 companion object {private val lock=Any();const val CHANNEL="jarvis_timer_alerts";const val NOTICE=7300}
 private val machine=TimerMachine(this)
 private var restoredId:String?=null
 override fun now()=SystemClock.elapsedRealtime()
 override fun boot()=if(Build.VERSION.SDK_INT>=24)Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT,-1)else ((System.currentTimeMillis()-now())/60000L).toInt()
 override fun read():OwnedTimer? {
  val raw=prefs.getString("timer",null)?:return null
  val j=JSONObject(raw);return OwnedTimer(j.getString("id"),j.getInt("seconds"),j.getLong("started"),j.getLong("deadline"),j.getInt("boot"),TimerPhase.valueOf(j.getString("phase")),j.getBoolean("exact"))
 }
 override fun save(timer:OwnedTimer){check(prefs.edit().putString("timer",JSONObject().put("id",timer.id).put("seconds",timer.seconds).put("started",timer.started).put("deadline",timer.deadline).put("boot",timer.boot).put("phase",timer.phase.name).put("exact",timer.exact).toString()).commit()){"Timer state could not be saved"}}
 override fun seen(id:String)=prefs.getStringSet("requests",emptySet())!!.contains(id)
 override fun remember(id:String){val ids=prefs.getStringSet("requests",emptySet())!!.toMutableSet();check(ids.size<256){"Timer request journal full. Clear it in timer controls when no timer is running."};ids.add(id);check(prefs.edit().putStringSet("requests",ids).commit())}
 fun clearJournal()=synchronized(lock){check(machine.recover()?.phase!=TimerPhase.RUNNING);check(prefs.edit().remove("requests").commit())}
 fun exactAvailable()=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()
 private fun pending(id:String,action:String):PendingIntent=PendingIntent.getBroadcast(app,0,Intent(app,TimerReceiver::class.java).setAction(action).setData(Uri.Builder().scheme("jarvis-timer").authority(action).appendPath(id).build()).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 override fun schedule(timer:OwnedTimer){val pi=pending(timer.id,"finish");if(timer.exact){if(Build.VERSION.SDK_INT>=31&&!alarms.canScheduleExactAlarms())throw SecurityException("Precise alarm access was revoked");alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,timer.deadline,pi)}else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,timer.deadline,pi)}
 override fun cancel(timer:OwnedTimer){alarms.cancel(pending(timer.id,"finish"))}
 override fun dismiss(){NotificationManagerCompat.from(app).cancel(NOTICE)}
 private fun channel(){if(Build.VERSION.SDK_INT>=26){val manager=app.getSystemService(NotificationManager::class.java);if(manager.getNotificationChannel(CHANNEL)==null)manager.createNotificationChannel(NotificationChannel(CHANNEL,"Timer completion alerts",NotificationManager.IMPORTANCE_HIGH).apply{description="User-started JARVIS timer alerts";setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());enableVibration(true)})}}
 fun notificationsAllowed():Boolean {channel();return NotificationManagerCompat.from(app).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&(Build.VERSION.SDK_INT<26||app.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE)}
 override fun show(timer:OwnedTimer){
  check(notificationsAllowed()){"Enable timer notifications before starting"}
  val finished=timer.phase==TimerPhase.FINISHED
  val open=PendingIntent.getActivity(app,0,Intent(app,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_jarvis).setContentTitle(if(finished)"JARVIS timer finished" else "JARVIS timer running").setContentText(if(finished)"Your timer has elapsed." else if(timer.exact)"Timer scheduled; tap to view." else "Approximate background alert; Android may delay delivery.").setContentIntent(open).setCategory(if(finished)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_PROGRESS).setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(!finished).setAutoCancel(finished).setSilent(!finished).setWhen(System.currentTimeMillis()+timer.remaining(now())*1000).addAction(0,if(finished)"Stop" else "Cancel",pending(timer.id,if(finished)"stop" else "cancel"))
  if(!finished&&Build.VERSION.SDK_INT>=24)n.setUsesChronometer(true).setChronometerCountDown(true)
  if(finished)n.setDefaults(NotificationCompat.DEFAULT_ALL)
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)throw SecurityException("Notifications revoked")
  NotificationManagerCompat.from(app).notify(NOTICE,n.build())
 }
 fun snapshot():OwnedTimer?=synchronized(lock){val t=machine.recover();if(t?.phase==TimerPhase.RUNNING&&t.exact&&!exactAvailable()){cancel(t);dismiss();return@synchronized t.copy(phase=TimerPhase.INTERRUPTED).also(::save)};if(t?.phase==TimerPhase.RUNNING&&now()>=t.deadline)machine.finish(t.id)else {if(t?.phase==TimerPhase.RUNNING&&restoredId!=t.id){machine.restoreSchedule();restoredId=t.id};t}}
 fun stop(id:String,cancel:Boolean)=synchronized(lock){machine.stop(id,cancel)}
 fun finish(id:String)=synchronized(lock){machine.finish(id)}
 fun reboot()=synchronized(lock){read()?.let{if(it.phase==TimerPhase.RUNNING||it.phase==TimerPhase.PREPARING){cancel(it);dismiss();save(it.copy(phase=TimerPhase.INTERRUPTED))}}}
 fun execute(action:ToolAction,request:String):ToolOutcome=synchronized(lock){
  ToolRegistry.validate(action)?.let{return@synchronized ToolOutcome(TaskStatus.UNSUPPORTED,it)}
  try {
   val prior=snapshot()
   if(action.name in setOf("cancel_timer","stop_timer")){
    if(seen(request))return@synchronized ToolOutcome(TaskStatus.VERIFIED,"Duplicate timer command ignored. No timer was changed.")
    remember(request)
   }
   val t=when(action.name){"set_timer"->{if(!notificationsAllowed())return@synchronized ToolOutcome(TaskStatus.NEEDS_PERMISSION,"Enable JARVIS timer notifications in timer controls. No timer started.");machine.recover();machine.start(request,action.args.getInt("seconds"),exactAvailable())};"timer_status"->snapshot();"cancel_timer","stop_timer"->{val old=snapshot();if(old==null)null else machine.stop(old.id,action.name=="cancel_timer")};else->return@synchronized ToolOutcome(TaskStatus.UNSUPPORTED,"Not a local timer action")}
   ToolOutcome(if(action.name=="set_timer"&&t?.phase!=TimerPhase.RUNNING)TaskStatus.FAILED else TaskStatus.VERIFIED,(if(action.name=="set_timer"&&prior?.phase==TimerPhase.RUNNING)"Existing timer retained; no new timer created. " else "")+if(t==null)"No JARVIS timer exists." else "JARVIS timer ${t.phase.name.lowercase()}; remaining ${if(t.phase==TimerPhase.RUNNING)t.remaining(now())else 0} seconds; duration ${t.seconds} seconds. "+if(t.exact)"Precise alarm registered; physical alert delivery not verified." else "Background alert is approximate and may be delayed by Android.")
  }catch(e:Exception){ToolOutcome(TaskStatus.FAILED,"Timer operation failed; inspect timer controls and notification permissions. No successful start is claimed.")}
 }
}
class TimerReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val timers=AndroidTimers(context);if(intent.action==Intent.ACTION_BOOT_COMPLETED){timers.reboot();return};val id=intent.getStringExtra("id")?:return;when(intent.action){"finish"->timers.finish(id);"cancel"->timers.stop(id,true);"stop"->timers.stop(id,false)}}}
val timerActions=setOf("set_timer","timer_status","cancel_timer","stop_timer")
