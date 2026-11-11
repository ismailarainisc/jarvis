package com.raaz.assistant.live

import android.content.Context
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.SystemClock
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One native microphone owner and WebRTC-rendered remote audio. No TTS or separate AudioRecord. */
class AndroidWebRtcClient(context:Context,private val events:Events) {
 interface Events {
  fun transport(state:PeerConnection.PeerConnectionState);fun message(value:String);fun channelClosed()
  fun capture(running:Boolean);fun playback(running:Boolean);fun microphoneAttached();fun remoteTrack()
  fun microphoneLevel(level:AudioLevel);fun playbackLevel(level:AudioLevel);fun audioError(message:String)
  fun rtp(outbound:Long?,inbound:Long?)
 }
 companion object { @Volatile private var initialized=false;private fun initialize(context:Context){synchronized(this){if(!initialized){PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions());initialized=true}}} }
 private val audioModule:JavaAudioDeviceModule
 private val factory:PeerConnectionFactory
 private val mediaLock=Any();private val remoteTracks=mutableListOf<AudioTrack>()
 private var source:AudioSource?=null;private var track:AudioTrack?=null;private var channel:DataChannel?=null;private var peer:PeerConnection?=null
 @Volatile private var closed=false;@Volatile private var muted=false;@Volatile private var stopping=false
 private val cleanup=AudioResourceCleanup();private var cleanupSucceeded=true
 private val micMeter=PcmLevelMeter();private val outputMeter=PcmLevelMeter()
 init {
  initialize(context)
  audioModule=JavaAudioDeviceModule.builder(context.applicationContext)
   .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
   .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
   .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
   .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
   .setSamplesReadyCallback {samples->if(!closed&&!stopping&&!muted&&samples.audioFormat==AudioFormat.ENCODING_PCM_16BIT)micMeter.sample(samples.data,SystemClock.elapsedRealtime())?.let{events.microphoneLevel(it)}}
   .setPlaybackSamplesReadyCallback {samples->if(!closed&&!stopping&&samples.audioFormat==AudioFormat.ENCODING_PCM_16BIT)outputMeter.sample(samples.data,SystemClock.elapsedRealtime())?.let{events.playbackLevel(it)}}
   .setAudioRecordStateCallback(object:JavaAudioDeviceModule.AudioRecordStateCallback{override fun onWebRtcAudioRecordStart(){if(!closed)events.capture(true)};override fun onWebRtcAudioRecordStop(){if(!closed)events.capture(false)}})
   .setAudioTrackStateCallback(object:JavaAudioDeviceModule.AudioTrackStateCallback{override fun onWebRtcAudioTrackStart(){if(!closed)events.playback(true)};override fun onWebRtcAudioTrackStop(){if(!closed)events.playback(false)}})
   .setAudioRecordErrorCallback(object:JavaAudioDeviceModule.AudioRecordErrorCallback{
    override fun onWebRtcAudioRecordInitError(error:String){report("Microphone initialization failed. Check permission and microphone availability.")}
    override fun onWebRtcAudioRecordStartError(code:JavaAudioDeviceModule.AudioRecordStartErrorCode,error:String){report("Microphone capture could not start. Close other recording apps and try again.")}
    override fun onWebRtcAudioRecordError(error:String){report("Microphone capture stopped unexpectedly. Check permission and audio hardware.")}
   })
   .setAudioTrackErrorCallback(object:JavaAudioDeviceModule.AudioTrackErrorCallback{
    override fun onWebRtcAudioTrackInitError(error:String){report("Audio playback initialization failed. Check the output device.")}
    override fun onWebRtcAudioTrackStartError(code:JavaAudioDeviceModule.AudioTrackStartErrorCode,error:String){report("Native audio playback could not start.")}
    override fun onWebRtcAudioTrackError(error:String){report("Native audio playback stopped unexpectedly. Check the output device.")}
   }).createAudioDeviceModule()
  try{factory=PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()}catch(e:Exception){audioModule.release();throw e}
  try {
   val config=PeerConnection.RTCConfiguration(emptyList()).apply{sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN}
   peer=factory.createPeerConnection(config,object:PeerConnection.Observer{
    override fun onSignalingChange(s:PeerConnection.SignalingState){}
    override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){}
    override fun onConnectionChange(s:PeerConnection.PeerConnectionState){if(!closed)events.transport(s)}
    override fun onIceConnectionReceivingChange(receiving:Boolean){}
    override fun onIceGatheringChange(s:PeerConnection.IceGatheringState){}
    override fun onIceCandidate(candidate:IceCandidate){}
    override fun onIceCandidatesRemoved(candidates:Array<out IceCandidate>){}
    override fun onAddStream(stream:MediaStream){}
    override fun onRemoveStream(stream:MediaStream){}
    override fun onDataChannel(dc:DataChannel){}
    override fun onRenegotiationNeeded(){}
    override fun onRemoveTrack(receiver:RtpReceiver){if(!closed&&!stopping)report("The provider removed its remote audio track. Restart the conversation.")}
    override fun onAddTrack(receiver:RtpReceiver,streams:Array<out MediaStream>){synchronized(mediaLock){if(!closed){(receiver.track() as? AudioTrack)?.let{if(it.setEnabled(!stopping)){remoteTracks.add(it);events.remoteTrack()}else report("Remote audio track could not be enabled. Restart the conversation.")}}}}
   })?:error("Unable to create a WebRTC PeerConnection.")
   source=factory.createAudioSource(MediaConstraints())
   track=factory.createAudioTrack("jarvis-microphone",source).also{local->check(local.setEnabled(true)&&local.enabled()){"Microphone track could not be enabled."};val sender=peer!!.addTrack(local,listOf("jarvis-audio"));check(sender.track()?.id()==local.id()){ "Microphone track attachment failed." };events.microphoneAttached()}
   peer!!.setAudioRecording(true);peer!!.setAudioPlayout(true)
   channel=peer!!.createDataChannel("oai-events",DataChannel.Init()).also{dc->dc.registerObserver(object:DataChannel.Observer{
    override fun onBufferedAmountChange(previousAmount:Long){}
    override fun onStateChange(){if(!closed&&dc.state()==DataChannel.State.CLOSED)events.channelClosed()}
    override fun onMessage(buffer:DataChannel.Buffer){if(!closed&&!buffer.binary){val bytes=ByteArray(buffer.data.remaining());buffer.data.get(bytes);events.message(String(bytes,Charsets.UTF_8))}}
   })}
  }catch(e:Exception){close();throw e}
 }
 private fun report(message:String){if(!closed&&!stopping)events.audioError(message)}
 private fun observer(success:()->Unit,failure:(String)->Unit)=object:SdpObserver{override fun onCreateSuccess(s:SessionDescription){};override fun onSetSuccess()=success();override fun onCreateFailure(error:String)=failure(error);override fun onSetFailure(error:String)=failure(error)}
 suspend fun offer():SessionDescription=suspendCancellableCoroutine{c->peer!!.createOffer(object:SdpObserver{override fun onCreateSuccess(s:SessionDescription){if(c.isActive)c.resume(s)};override fun onSetSuccess(){};override fun onCreateFailure(error:String){if(c.isActive)c.resumeWithException(IllegalStateException("Could not create an SDP offer."))};override fun onSetFailure(error:String){}},MediaConstraints())}
 suspend fun local(sdp:SessionDescription)=suspendCancellableCoroutine<Unit>{c->peer!!.setLocalDescription(observer({if(c.isActive)c.resume(Unit)},{if(c.isActive)c.resumeWithException(IllegalStateException("Could not set local SDP."))}),sdp)}
 suspend fun remote(sdp:String)=suspendCancellableCoroutine<Unit>{c->peer!!.setRemoteDescription(observer({if(c.isActive)c.resume(Unit)},{if(c.isActive)c.resumeWithException(IllegalStateException("Provider SDP negotiation failed."))}),SessionDescription(SessionDescription.Type.ANSWER,sdp))}
 fun iceComplete()=peer?.iceGatheringState()==PeerConnection.IceGatheringState.COMPLETE
 fun localSdp()=peer?.localDescription?.description?:error("Local SDP is missing.")
 fun mute(value:Boolean){if(closed||stopping)return;muted=value;audioModule.setMicrophoneMute(value);check(track?.setEnabled(!value)==true){"Microphone track could not be updated."};peer?.setAudioRecording(!value)}
 /** Stop native capture/playout immediately while leaving the event channel for graceful finalization. */
 fun silence(){if(closed||stopping)return;stopping=true;muted=true;audioModule.setMicrophoneMute(true);audioModule.setSpeakerMute(true);synchronized(mediaLock){track?.setEnabled(false);remoteTracks.forEach{it.setEnabled(false)};peer?.setAudioRecording(false);peer?.setAudioPlayout(false)}}
 fun pollStats(){if(closed||stopping)return;peer?.getStats {report->if(!closed&&!stopping){val rows=report.statsMap.values;fun total(type:String,field:String):Long?{val audio=rows.filter{it.type==type&&(it.members["kind"]=="audio"||it.members["mediaType"]=="audio")};val numbers=audio.mapNotNull{it.members[field] as? Number};return if(numbers.isEmpty())null else numbers.sumOf{it.toLong()}};events.rtp(total("outbound-rtp","packetsSent"),total("inbound-rtp","packetsReceived"))}}}
 fun requestClose():Boolean {val dc=channel?:return false;return dc.state()==DataChannel.State.OPEN&&dc.send(DataChannel.Buffer(ByteBuffer.wrap("{\"type\":\"session.close\"}".toByteArray()),false))}
 fun close():Boolean {if(closed)return cleanupSucceeded;closed=true;stopping=true;muted=true
  synchronized(mediaLock){cleanupSucceeded=cleanup.close(listOf(
   {audioModule.setMicrophoneMute(true)},{audioModule.setSpeakerMute(true)},
   {track?.setEnabled(false)},{remoteTracks.forEach{it.setEnabled(false)}},
   {peer?.setAudioRecording(false)},{peer?.setAudioPlayout(false)},
   {channel?.unregisterObserver()},{channel?.close()},{channel?.dispose();channel=null},
   {peer?.close()},{peer?.dispose();peer=null},{remoteTracks.clear()},
   {track?.dispose();track=null},{source?.dispose();source=null},{factory.dispose()},{audioModule.release()}
  ))};return cleanupSucceeded
 }
}
