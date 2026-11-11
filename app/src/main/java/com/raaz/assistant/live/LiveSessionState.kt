package com.raaz.assistant.live

enum class LiveSessionState { DISCONNECTED, CONNECTING, NEGOTIATING, CONNECTED, SESSION_READY, DISCONNECTING, ERROR }
data class LiveSessionError(val message:String)
data class LiveUiState(val state:LiveSessionState=LiveSessionState.DISCONNECTED,val error:String?=null,val sessionId:String?=null,val muted:Boolean=false,
 val voiceActivity:LiveVoiceActivity=LiveVoiceActivity.STOPPED,
 val captureRunning:Boolean=false,val playbackRunning:Boolean=false,val microphoneAttached:Boolean=false,val remoteTrackAvailable:Boolean=false,
 val microphoneSamples:Long=0,val playbackSamples:Long=0,
 val microphoneLevel:Float=0f,val playbackLevel:Float=0f,val microphoneMeterAvailable:Boolean=false,val playbackMeterAvailable:Boolean=false,
 val inputActive:Boolean=false,val outputActive:Boolean=false,val outboundPackets:Long?=null,val inboundPackets:Long?=null,
 val captions:List<CaptionFragment> = emptyList(),val audioRoutes:List<LiveAudioRoute> = emptyList(),val selectedRoute:Int?=null,
 val routeNotice:String?=null)
/** Callback generation barrier shared by negotiation, media and data channel callbacks. */
class SessionEpoch { @Volatile private var epoch=0L;fun next():Long=++epoch;fun accepts(value:Long)=value==epoch }

fun connectionReadiness(transportConnected:Boolean,expectedSession:String?,startedSession:String?):LiveSessionState {
 if(expectedSession!=null && startedSession!=null && expectedSession!=startedSession)throw IllegalStateException("Session identity did not match the signaling response.")
 return when {transportConnected && expectedSession!=null && startedSession==expectedSession -> LiveSessionState.SESSION_READY;transportConnected -> LiveSessionState.CONNECTED;else -> LiveSessionState.NEGOTIATING}
}
