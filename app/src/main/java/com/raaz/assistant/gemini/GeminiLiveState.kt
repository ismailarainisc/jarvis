package com.raaz.assistant.gemini

import com.raaz.assistant.live.LiveAudioRoute

enum class GeminiPhase { IDLE, CONNECTING, SETTING_UP, READY, LISTENING, USER_SPEAKING, PROCESSING, ASSISTANT_SPEAKING, INTERRUPTED, MUTED, DISCONNECTING, ERROR, STOPPED }
data class GeminiLiveUi(
 val phase:GeminiPhase=GeminiPhase.IDLE,val error:String?=null,val errorCode:GeminiErrorCode?=null,val notice:String?=null,
 val ready:Boolean=false,val muted:Boolean=false,val capture:Boolean=false,val playback:Boolean=false,
 val microphoneLevel:Float=0f,val outputLevel:Float=0f,val microphoneSamples:Long=0,val outputSamples:Long=0,
 val inputActive:Boolean=false,val outputActive:Boolean=false,val inputText:String="",val outputText:String="",val interimText:String="",
 val sentBytes:Long=0,val receivedBytes:Long=0,val turnCount:Long=0,val interruptionCount:Long=0,
 val routes:List<LiveAudioRoute> = emptyList(),val route:Int?=null
) {
 val active:Boolean get()=phase !in setOf(GeminiPhase.IDLE,GeminiPhase.STOPPED,GeminiPhase.ERROR)
 val connection:String get()=when{phase==GeminiPhase.DISCONNECTING->"DISCONNECTING";ready->"READY";else->phase.name}
}
