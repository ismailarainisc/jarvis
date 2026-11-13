package com.raaz.assistant.timer

enum class TimerPhase { PREPARING, RUNNING, FINISHED, CANCELLED, STOPPED, FAILED, ALERT_FAILED, INTERRUPTED }
data class OwnedTimer(val id:String,val seconds:Int,val started:Long,val deadline:Long,val boot:Int,val phase:TimerPhase,val exact:Boolean) {
 fun remaining(now:Long)=((deadline-now).coerceAtLeast(0)+999)/1000
}
interface TimerPorts {
 fun read():OwnedTimer?
 fun save(timer:OwnedTimer)
 fun seen(id:String):Boolean
 fun remember(id:String)
 fun now():Long
 fun boot():Int
 fun schedule(timer:OwnedTimer)
 fun cancel(timer:OwnedTimer)
 fun show(timer:OwnedTimer)
 fun dismiss()
}
/** Caller serializes access across UI, tool workers and receivers. The Android facade uses one lock. */
class TimerMachine(private val p:TimerPorts) {
 fun start(id:String,seconds:Int,exact:Boolean):OwnedTimer {
  require(id.isNotBlank()&&id.length<=200);require(seconds in 1..86400)
  val old=p.read()
  if(p.seen(id)){check(old?.id==id){"Previously handled request; not repeated"};return requireNotNull(old)}
  if(old?.phase==TimerPhase.RUNNING)return old
  p.remember(id)
  val timer=OwnedTimer(id,seconds,p.now(),p.now()+seconds*1000L,p.boot(),TimerPhase.PREPARING,exact)
  p.save(timer)
  try {p.schedule(timer);p.show(timer.copy(phase=TimerPhase.RUNNING));return timer.copy(phase=TimerPhase.RUNNING).also(p::save)}
  catch(e:Exception){p.cancel(timer);p.dismiss();p.save(timer.copy(phase=TimerPhase.FAILED));throw e}
 }
 fun recover():OwnedTimer? {
  val t=p.read()?:return null
  if(t.phase==TimerPhase.PREPARING||t.phase==TimerPhase.RUNNING&&(t.boot!=p.boot()||p.now()<t.started)) {
   p.cancel(t);p.dismiss();return t.copy(phase=TimerPhase.INTERRUPTED).also(p::save)
  }
  return t
 }
 fun restoreSchedule():OwnedTimer? {val t=recover();if(t?.phase==TimerPhase.RUNNING&&p.now()<t.deadline)p.schedule(t);return t}
 fun finish(id:String):OwnedTimer? {
  val t=recover()?:return null
  if(t.id!=id||t.phase!=TimerPhase.RUNNING)return t
  if(p.now()<t.deadline){p.schedule(t);return t}
  p.cancel(t)
  val done=t.copy(phase=TimerPhase.FINISHED)
  p.save(done)
  return try{p.show(done);done}catch(e:Exception){done.copy(phase=TimerPhase.ALERT_FAILED).also(p::save)}
 }
 fun stop(id:String,cancel:Boolean):OwnedTimer? {
  val t=recover()?:return null
  if(t.id!=id)return t
  if(t.phase==TimerPhase.RUNNING||t.phase==TimerPhase.FINISHED||t.phase==TimerPhase.ALERT_FAILED){
   p.cancel(t);p.dismiss();return t.copy(phase=if(cancel)TimerPhase.CANCELLED else TimerPhase.STOPPED).also(p::save)
  }
  return t
 }
}
