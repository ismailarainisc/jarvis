package com.raaz.assistant

import com.raaz.assistant.timer.*
import com.raaz.assistant.features.ToolRegistry
import org.junit.Assert.*
import org.junit.Test

/** Isolated ports exercise the production machine. No Android or real notification delivery claim. */
class OwnedTimerTest {
 private class Ports:TimerPorts {
  var record:OwnedTimer?=null;val ids=mutableSetOf<String>();var time=10000L;var bootId=3
  var schedules=0;var cancels=0;var alerts=0;var dismisses=0;var failSchedule=false;var failShow=false
  override fun read()=record
  override fun save(timer:OwnedTimer){record=timer}
  override fun seen(id:String)=id in ids
  override fun remember(id:String){ids.add(id)}
  override fun now()=time
  override fun boot()=bootId
  override fun schedule(timer:OwnedTimer){if(failSchedule)error("isolated scheduler failure");schedules++}
  override fun cancel(timer:OwnedTimer){cancels++}
  override fun show(timer:OwnedTimer){if(failShow)error("isolated notification failure");if(timer.phase==TimerPhase.FINISHED)alerts++}
  override fun dismiss(){dismisses++}
 }
 @Test fun realDeadlineAndCountdownFromMonotonicClock(){val p=Ports();val m=TimerMachine(p);val t=m.start("a",300,true);assertEquals(310000,t.deadline);assertEquals(300,t.remaining(p.time));p.time+=1250;assertEquals(299,t.remaining(p.time));assertEquals(TimerPhase.RUNNING,t.phase);assertEquals(1,p.schedules)}
 @Test fun restoringAlarmKeepsOriginalDeadlineAndIdentity(){val p=Ports();val t=TimerMachine(p).start("a",300,false);p.time+=60000;assertEquals(t,TimerMachine(p).restoreSchedule());assertEquals(2,p.schedules);assertEquals(240,p.record!!.remaining(p.time))}
 @Test fun secondsAndUpperBoundary(){for(n in listOf(1,30,86400)){val p=Ports();assertEquals(n,TimerMachine(p).start("a",n,false).seconds)}}
 @Test fun invalidDurationsHaveNoSideEffects(){for(n in listOf(0,-1,86401,Int.MAX_VALUE)){val p=Ports();try{TimerMachine(p).start("a",n,false);fail()}catch(e:IllegalArgumentException){};assertNull(p.record);assertEquals(0,p.schedules)}}
 @Test fun duplicateRequestDoesNotRestart(){val p=Ports();val m=TimerMachine(p);val a=m.start("a",300,false);p.time+=5000;assertEquals(a,m.start("a",300,false));assertEquals(1,p.schedules)}
 @Test fun newCommandDoesNotReplaceRunningTimer(){val p=Ports();val m=TimerMachine(p);val a=m.start("a",300,false);assertEquals(a,m.start("b",30,false));assertEquals(1,p.schedules)}
 @Test fun cancellationPreventsLateAlert(){val p=Ports();val m=TimerMachine(p);m.start("a",30,false);m.stop("a",true);p.time+=40000;m.finish("a");assertEquals(TimerPhase.CANCELLED,p.record!!.phase);assertEquals(0,p.alerts)}
 @Test fun staleCancelCannotAffectNewTimer(){val p=Ports();val m=TimerMachine(p);m.start("a",30,false);m.stop("a",true);m.start("b",30,false);m.stop("a",true);assertEquals("b",p.record!!.id);assertEquals(TimerPhase.RUNNING,p.record!!.phase)}
 @Test fun elapsedTimerPostsOnce(){val p=Ports();val m=TimerMachine(p);m.start("a",30,false);p.time+=30000;m.finish("a");m.finish("a");assertEquals(1,p.alerts);assertEquals(TimerPhase.FINISHED,p.record!!.phase)}
 @Test fun earlyReceiverDoesNotCompleteTimer(){val p=Ports();val m=TimerMachine(p);m.start("a",30,false);m.finish("a");assertEquals(0,p.alerts);assertEquals(TimerPhase.RUNNING,p.record!!.phase);assertEquals(2,p.schedules)}
 @Test fun processRecreationRetainsCountdown(){val p=Ports();TimerMachine(p).start("a",300,true);p.time+=60000;assertEquals(240,TimerMachine(p).recover()!!.remaining(p.time))}
 @Test fun rebootNeverClaimsOldElapsedDeadlineFinished(){val p=Ports();TimerMachine(p).start("a",300,true);p.bootId++;assertEquals(TimerPhase.INTERRUPTED,TimerMachine(p).recover()!!.phase);assertEquals(0,p.alerts)}
 @Test fun partialCreationIsInterruptedOnRecovery(){val p=Ports();p.record=OwnedTimer("a",30,10000,40000,3,TimerPhase.PREPARING,false);assertEquals(TimerPhase.INTERRUPTED,TimerMachine(p).recover()!!.phase)}
 @Test fun failedNotificationDoesNotClaimDelivered(){val p=Ports();val m=TimerMachine(p);m.start("a",30,true);p.failShow=true;p.time+=30000;assertEquals(TimerPhase.ALERT_FAILED,m.finish("a")!!.phase)}
 @Test fun scheduleFailureCannotClaimRunning(){val p=Ports();p.failSchedule=true;try{TimerMachine(p).start("a",30,true);fail()}catch(e:IllegalStateException){};assertEquals(TimerPhase.FAILED,p.record!!.phase);assertTrue(p.cancels>0)}
 @Test fun startNotificationFailureRollsBack(){val p=Ports();p.failShow=true;try{TimerMachine(p).start("a",30,true);fail()}catch(e:IllegalStateException){};assertEquals(TimerPhase.FAILED,p.record!!.phase)}
 @Test fun stopDismissesFinishedAlert(){val p=Ports();val m=TimerMachine(p);m.start("a",30,true);p.time+=30000;m.finish("a");m.stop("a",false);assertEquals(TimerPhase.STOPPED,p.record!!.phase);assertTrue(p.dismisses>0)}
 @Test fun oldFinishedRequestCannotStartAnotherTimer(){val p=Ports();val m=TimerMachine(p);m.start("a",30,true);m.stop("a",true);assertEquals(TimerPhase.CANCELLED,m.start("a",30,true).phase);assertEquals(1,p.schedules)}
 @Test fun staleCompletionCannotFinishNewTimer(){val p=Ports();val m=TimerMachine(p);m.start("a",30,true);m.stop("a",true);m.start("b",30,true);p.time+=30000;m.finish("a");assertEquals(0,p.alerts);assertEquals(TimerPhase.RUNNING,p.record!!.phase)}
 @Test fun englishUrduHindiDurations(){for(text in listOf("Start a timer for five minutes.","پانچ منٹ کا ٹائمر لگاؤ","पाँच मिनट का टाइमर लगाओ","۵ منٹ کا ٹائمر لگاؤ","५ मिनट का टाइमर लगाओ")){val a=parseTimerCommand(text)!!;assertEquals(300,a.args.getInt("seconds"));assertNull(ToolRegistry.validate(a))}}
 @Test fun thirtySeconds(){for(text in listOf("Set a timer for thirty seconds","تیس سیکنڈ کا ٹائمر شروع کرو","तीस सेकंड का टाइमर शुरू करो"))assertEquals(30,parseTimerCommand(text)!!.args.getInt("seconds"))}
 @Test fun statusAndCancelInThreeLanguages(){for(text in listOf("How much time is left?","کتنا وقت باقی ہے؟","कितना समय बाकी है?"))assertEquals("timer_status",parseTimerCommand(text)!!.name);for(text in listOf("Cancel my timer","ٹائمر منسوخ کرو","टाइमर रद्द करो"))assertEquals("cancel_timer",parseTimerCommand(text)!!.name)}
 @Test fun invalidParsedDurationStillFailsRegistry(){assertNotNull(ToolRegistry.validate(parseTimerCommand("set a timer for 0 seconds")!!));assertNull(parseTimerCommand("set a timer for lots of seconds"))}
 @Test fun timerSchemasRejectExtraProviderArguments(){val a=parseTimerCommand("start a timer for five minutes")!!;a.args.put("recipient","private");assertNotNull(ToolRegistry.validateProvider(a))}
}
