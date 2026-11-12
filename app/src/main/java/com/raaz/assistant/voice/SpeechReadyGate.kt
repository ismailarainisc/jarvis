package com.raaz.assistant.voice

/** One bounded pending reply while Android's real TTS initialization callback is outstanding. */
class SpeechReadyGate {
 enum class State { WAITING, READY, FAILED, CLOSED }
 sealed class Request {object Queued:Request();data class Play(val text:String):Request();object Unavailable:Request()}
 private var state=State.WAITING;private var pending:String?=null
 fun submit(text:String):Request {
  if(text.isBlank()||text.length>16000)return Request.Unavailable
  return when(state){State.READY->Request.Play(text);State.WAITING->{pending=text;Request.Queued};else->Request.Unavailable}
 }
 fun initialized(success:Boolean):String? {if(state!=State.WAITING)return null;state=if(success)State.READY else State.FAILED;val text=if(success)pending else null;pending=null;return text}
 fun waiting()=pending!=null&&state==State.WAITING
 fun cancel(){pending=null}
 fun expire():Boolean {if(!waiting())return false;pending=null;state=State.FAILED;return true}
 fun close(){pending=null;state=State.CLOSED}
}
