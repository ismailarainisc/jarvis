package com.raaz.assistant.live

import kotlin.math.log10
import kotlin.math.sqrt

enum class LiveVoiceActivity { CONNECTING, SESSION_READY, LISTENING, USER_SPEAKING, PROCESSING, ASSISTANT_SPEAKING, MUTED, DISCONNECTING, ERROR, STOPPED }
data class AudioLevel(val rms:Float,val display:Float)
/** Signed little-endian PCM16, measured before transport/device output; never saved. */
fun pcm16Rms(bytes:ByteArray):Float {
 if(bytes.size<2)return 0f
 var energy=0.0;var count=0;var i=0
 while(i+1<bytes.size){val sample=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toInt()/32768.0;energy+=sample*sample;count++;i+=2}
 return sqrt(energy/count).toFloat()
}
fun displayLevel(rms:Float):Float=if(rms<=0f)0f else ((20*log10(rms.coerceAtLeast(.000001f))+60)/60).coerceIn(0f,1f)
/** 10 Hz publish rate; smoothing applies only to genuine measured PCM. */
class PcmLevelMeter {
 private var last=Long.MIN_VALUE;private var energy=0.0;private var count=0;private var display=0f
 @Synchronized fun sample(bytes:ByteArray,nowMs:Long):AudioLevel? {val rms=pcm16Rms(bytes);energy+=rms*rms;count++;if(last!=Long.MIN_VALUE && nowMs-last<100)return null;last=nowMs;val mean=sqrt(energy/count).toFloat();energy=0.0;count=0;val next=displayLevel(mean);display=if(mean==0f)0f else display*.4f+next*.6f;return AudioLevel(mean,display)}
 @Synchronized fun reset(){last=Long.MIN_VALUE;energy=0.0;count=0;display=0f}
}
/** Audio-energy activity only; this is not a semantic speech recognizer or provider VAD. */
class AudioActivityDetector {
 private var active=false;private var lastLoud=0L
 fun update(rms:Float,now:Long):Boolean{if(rms>=.008f){active=true;lastLoud=now}else if(rms<.004f&&now-lastLoud>=250)active=false;return active}
 fun reset(){active=false;lastLoud=0L}
}
data class CaptionFragment(val speaker:String,val delta:String,val startMs:Long,val endMs:Long,val eventId:String)
data class LiveAudioRoute(val id:Int,val label:String)
fun voiceActivity(connection:LiveSessionState,muted:Boolean,capture:Boolean,inputActive:Boolean,outputActive:Boolean):LiveVoiceActivity=when(connection){
 LiveSessionState.ERROR->LiveVoiceActivity.ERROR
 LiveSessionState.DISCONNECTING->LiveVoiceActivity.DISCONNECTING
 LiveSessionState.DISCONNECTED->LiveVoiceActivity.STOPPED
 LiveSessionState.SESSION_READY->when{muted->LiveVoiceActivity.MUTED;inputActive->LiveVoiceActivity.USER_SPEAKING;outputActive->LiveVoiceActivity.ASSISTANT_SPEAKING;capture->LiveVoiceActivity.LISTENING;else->LiveVoiceActivity.SESSION_READY}
 else->LiveVoiceActivity.CONNECTING
}
