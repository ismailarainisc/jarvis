package com.raaz.assistant.background

/** Actual service reservation. Rejected starts revoke it, so foreground audio is never permanently blocked. */
class BackgroundAuthorization {
 @Volatile var authorized=false
 fun accept(startCommand:Boolean,alreadyStarted:Boolean,microphone:Boolean,visibleNotification:Boolean,stopping:Boolean=false):Boolean {
  if(stopping||!authorized||(!alreadyStarted&&!startCommand)||!microphone||!visibleNotification){authorized=false;return false}
  return true
 }
}

/** Cleanup must not hide the actual failed start with a generic stopped/paused message. */
fun visibleWakeState(actual:WakeState,failure:String?)=if(failure==null)actual else WakeState(WakePhase.ERROR,failure)
