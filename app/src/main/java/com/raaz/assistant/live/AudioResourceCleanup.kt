package com.raaz.assistant.live

/** Runs every release step even if one fails; a second close cannot dispose native objects twice. */
class AudioResourceCleanup {
 private var closed=false;private var success=true
 @Synchronized fun close(steps:List<()->Unit>):Boolean {if(closed)return success;closed=true;steps.forEach{try{it()}catch(e:Exception){success=false}};return success}
}
fun selectedRouteWasRemoved(selected:Int?,removed:List<Int>):Boolean=selected!=null&&selected in removed
