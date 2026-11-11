package com.raaz.assistant.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.raaz.assistant.live.AudioLevel
import com.raaz.assistant.live.AudioResourceCleanup
import com.raaz.assistant.live.PcmLevelMeter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Native Gemini transport uses PCM, not WebRTC or Android TTS. Only this engine owns the microphone. */
class GeminiAudioEngine(private val context:Context,private val events:Events) {
 interface Events {fun microphone(data:ByteArray):Boolean;fun micLevel(level:AudioLevel);fun outputLevel(level:AudioLevel);fun capture(active:Boolean);fun playback(active:Boolean);fun error(message:String)}
 private data class Chunk(val epoch:Long,val bytes:ByteArray)
 private var record:AudioRecord?=null;private var output:AudioTrack?=null;private var aec:AcousticEchoCanceler?=null;private var ns:NoiseSuppressor?=null
 @Volatile private var running=false;@Volatile private var muted=false;private var reader:Thread?=null;private var player:Thread?=null
 private val queue=LinkedBlockingQueue<Chunk>(128);private val queuedBytes=AtomicInteger();private val epoch=AtomicLong();private val outputLock=Any();private val closeOnce=AudioResourceCleanup()
 private val micMeter=PcmLevelMeter();private val playbackMeter=PcmLevelMeter()
 private var framesWritten=0L;private var headBaseline=0L
 private fun head()=(output?.playbackHeadPosition?.toLong()?:0L) and 0xffffffffL
 fun start(){check(!running);check(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){"Microphone permission is required."}
  try{val inputBuffer=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(inputBuffer>0){"16 kHz microphone audio is unsupported on this device."}
   record=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION).setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(maxOf(inputBuffer*2,6400)).build()
   check(record!!.state==AudioRecord.STATE_INITIALIZED){"Microphone initialization failed."}
   val session=record!!.audioSessionId
   if(AcousticEchoCanceler.isAvailable())aec=runCatching{AcousticEchoCanceler.create(session)?.also{it.enabled=true}}.getOrNull()
   if(NoiseSuppressor.isAvailable())ns=runCatching{NoiseSuppressor.create(session)?.also{it.enabled=true}}.getOrNull()
   val outputBuffer=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);check(outputBuffer>0){"24 kHz audio playback is unsupported on this device."}
   output=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(outputBuffer*2,9600)).build()
   check(output!!.state==AudioTrack.STATE_INITIALIZED){"Audio output initialization failed."}
   running=true;record!!.startRecording();check(record!!.recordingState==AudioRecord.RECORDSTATE_RECORDING){"Microphone capture did not start."};output!!.play();headBaseline=head()
   reader=Thread({captureLoop()},"jarvis-gemini-capture").also{it.start()};player=Thread({playbackLoop()},"jarvis-gemini-playback").also{it.start()}
  }catch(e:Exception){close();throw e}
 }
 private fun captureLoop(){events.capture(true);try{val buffer=ByteArray(640);while(running){val count=record?.read(buffer,0,buffer.size,AudioRecord.READ_BLOCKING)?:break;if(!running)break;if(count<0)throw IllegalStateException();if(count==0)continue;if(!muted){val frame=buffer.copyOf(count-count%2);micMeter.sample(frame,SystemClock.elapsedRealtime())?.let{events.micLevel(it)};if(!events.microphone(frame))throw IllegalStateException()}}}catch(e:Exception){if(running)events.error("Microphone capture or audio transmission failed. Check permission, network and microphone access.")}finally{events.capture(false)}}
 fun enqueue(bytes:ByteArray):Boolean {if(!running)return false;if(bytes.size%2!=0||bytes.size>480000)return false;val total=queuedBytes.addAndGet(bytes.size);if(total>480000){queuedBytes.addAndGet(-bytes.size);return false};if(!queue.offer(Chunk(epoch.get(),bytes))){queuedBytes.addAndGet(-bytes.size);return false};return true}
 private fun playbackLoop(){var playing=false;var observedEpoch=epoch.get();var lastWrite=SystemClock.elapsedRealtime()
  try{while(running){if(observedEpoch!=epoch.get()){observedEpoch=epoch.get();playing=false};val chunk=queue.poll(20,TimeUnit.MILLISECONDS)
   if(chunk==null){synchronized(outputLock){val played=(head()-headBaseline) and 0xffffffffL;if(playing&&played>=framesWritten){playing=false;events.playback(false);events.outputLevel(AudioLevel(0f,0f))}};continue}
   synchronized(outputLock){if(chunk.epoch==epoch.get())queuedBytes.updateAndGet{(it-chunk.bytes.size).coerceAtLeast(0)}}
   var offset=0;lastWrite=SystemClock.elapsedRealtime()
   while(running&&offset<chunk.bytes.size&&chunk.epoch==epoch.get()){var stalled=false
    synchronized(outputLock){if(running&&chunk.epoch==epoch.get()){
     val size=minOf(960,chunk.bytes.size-offset);val wrote=output?.write(chunk.bytes,offset,size,AudioTrack.WRITE_NON_BLOCKING)?:-1
     if(wrote<0)throw IllegalStateException();if(wrote==0){stalled=true}else{lastWrite=SystemClock.elapsedRealtime();framesWritten+=wrote/2;offset+=wrote;if(!playing){playing=true;events.playback(true)};playbackMeter.sample(chunk.bytes.copyOfRange(offset-wrote,offset),SystemClock.elapsedRealtime())?.let{events.outputLevel(it)}}
    }}
    if(stalled){if(SystemClock.elapsedRealtime()-lastWrite>3000)throw IllegalStateException();Thread.sleep(5)}
   }
  }}catch(e:InterruptedException){}catch(e:Exception){if(running)events.error("Gemini audio playback failed. Check your output device.")}finally{events.playback(false)}
 }
 fun clearPlayback(){synchronized(outputLock){epoch.incrementAndGet();queue.clear();queuedBytes.set(0);output?.pause();output?.flush();output?.stop();output?.play();framesWritten=0;headBaseline=head()};events.playback(false);events.outputLevel(AudioLevel(0f,0f))}
 fun mute(value:Boolean){muted=value;if(value)events.micLevel(AudioLevel(0f,0f))}
 fun close():Boolean {running=false;muted=true;return closeOnce.close(listOf(
  {if(record?.recordingState==AudioRecord.RECORDSTATE_RECORDING)record?.stop()}, {synchronized(outputLock){epoch.incrementAndGet();queue.clear();queuedBytes.set(0);output?.pause();output?.flush();output?.stop()}},
  {reader?.interrupt();player?.interrupt()}, {if(Thread.currentThread()!=reader)reader?.join(1000)}, {if(Thread.currentThread()!=player)player?.join(1000)},
  {record?.release();record=null}, {synchronized(outputLock){output?.release();output=null}}, {aec?.release();aec=null}, {ns?.release();ns=null}
 ))}
}
