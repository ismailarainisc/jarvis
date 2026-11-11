package com.raaz.assistant.voice
/** Ensures a recognizer turn can complete only once and stale callbacks are ignored. */
class RecognitionTurnGate {
 private var next=0; private var current=0; private var pending=false
 fun begin():Int { current=++next;pending=true;return current }
 fun matches(token:Int)=pending && token==current
 fun consume(token:Int):Boolean {if(!matches(token))return false;pending=false;return true}
 fun cancel(){pending=false;current=++next}
}
