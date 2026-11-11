package com.raaz.assistant.gemini

import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.raaz.assistant.live.AudioLevel
import com.raaz.assistant.live.PcmLevelMeter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native handles are created, stopped, joined and released off the Android main thread. */
class GeminiAudioEngine(
 private val input:MicrophoneGate,
 private val events:GeminiAudioSession.Events,
 private val canRecord:()->Boolean,
 private val io:CoroutineDispatcher=Dispatchers.IO
):GeminiAudioSession {
 private val lifecycle=Mutex()
 @Volatile private var stopRequested=false
 @Volatile private var pump:PcmPlaybackPump?=null
 private var record:AudioRecord?=null;private var output:AudioTrack?=null
 private var aec:AcousticEchoCanceler?=null;private var ns:NoiseSuppressor?=null
 private var reader:Thread?=null;private var player:Thread?=null
 private var started=false;private var closed=false;private var cleanupSucceeded=true
 private val micMeter=PcmLevelMeter();private val outputMeter=PcmLevelMeter()
 override fun bufferedBytes():Int?=pump?.retainedBytes()
 override fun requestStop(){stopRequested=true}
 override suspend fun start()=withContext(io){lifecycle.withLock{
  check(!started&&!closed){"An audio engine cannot be reused."};started=true
  fun authorized(){ensureActive();if(stopRequested)throw CancellationException();if(!canRecord())throw GeminiFailure(GeminiErrorCode.PERMISSION,"Microphone permission or foreground access was lost before capture started.")}
  authorized()
  val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  if(minimum<=0)throw GeminiFailure(GeminiErrorCode.CAPTURE,"This device does not support the required 16 kHz microphone format.")
  record=try{AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
   .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
   .setBufferSizeInBytes(maxOf(minimum*2,6400)).build()}catch(e:SecurityException){throw GeminiFailure(GeminiErrorCode.PERMISSION,"Microphone permission was revoked during initialization.")}
  if(record!!.state!=AudioRecord.STATE_INITIALIZED)throw GeminiFailure(GeminiErrorCode.CAPTURE,"Microphone initialization failed. Check device audio support.")
  authorized();configureEffects(record!!.audioSessionId)
  val outputMinimum=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
  if(outputMinimum<=0)throw GeminiFailure(GeminiErrorCode.PLAYBACK,"This device does not support 24 kHz Gemini playback.")
  val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
   .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
   .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(outputMinimum*2,9600)).build()
  output=track
  if(track.state!=AudioTrack.STATE_INITIALIZED)throw GeminiFailure(GeminiErrorCode.PLAYBACK,"Native audio output initialization failed.")
  pump=PcmPlaybackPump(object:PcmOutput{
   override fun write(pcm:ByteArray,offset:Int,count:Int)=track.write(pcm,offset,count,AudioTrack.WRITE_NON_BLOCKING)
   override fun headFrames()=track.playbackHeadPosition.toLong() and 0xffffffffL
   override fun flush(restart:Boolean){if(track.playState==AudioTrack.PLAYSTATE_PLAYING)track.pause();track.flush();if(track.playState!=AudioTrack.PLAYSTATE_STOPPED)track.stop();if(restart&&!stopRequested)track.play()}
  })
  authorized();record!!.startRecording()
  if(record!!.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw GeminiFailure(GeminiErrorCode.CAPTURE,"Microphone recording could not start. Check other audio apps.")
  authorized();track.play()
  reader=Thread(::captureLoop,"jarvis-gemini-capture").also{it.start()}
  player=Thread(::playbackLoop,"jarvis-gemini-playback").also{it.start()}
 }}
 private fun configureEffects(session:Int){
  if(AcousticEchoCanceler.isAvailable())try{aec=AcousticEchoCanceler.create(session);if(aec==null||aec!!.setEnabled(true)!=AudioEffect.SUCCESS)events.warning("Hardware echo cancellation could not be enabled. A headset is recommended.")}catch(e:Exception){events.warning("Hardware echo cancellation is unavailable. A headset is recommended.")}
  if(NoiseSuppressor.isAvailable())try{ns=NoiseSuppressor.create(session);if(ns==null||ns!!.setEnabled(true)!=AudioEffect.SUCCESS)events.warning("Hardware noise suppression could not be enabled.")}catch(e:Exception){events.warning("Hardware noise suppression is unavailable.")}
 }
 private fun captureLoop(){events.capture(true);var lastRead=SystemClock.elapsedRealtime();var lastTicket:Long?=null
  try{val buffer=ByteArray(640);while(!stopRequested){
   val ticket=input.ticket() // Must precede blocking read: a mute/unmute pair invalidates this frame.
   val size=record?.read(buffer,0,buffer.size,AudioRecord.READ_BLOCKING)?:break
   if(stopRequested)break
   if(size<0||size>buffer.size||size%2!=0)throw GeminiFailure(GeminiErrorCode.CAPTURE,"The microphone returned invalid PCM data or lost access.")
   if(size==0){if(SystemClock.elapsedRealtime()-lastRead>3000)throw GeminiFailure(GeminiErrorCode.CAPTURE,"The microphone stopped providing audio. Check permission and hardware.");Thread.sleep(5);continue}
   lastRead=SystemClock.elapsedRealtime();if(ticket==null)continue
   if(ticket!=lastTicket){micMeter.reset();lastTicket=ticket}
   val frame=buffer.copyOf(size)
   if(input.send(ticket,frame)==MicrophoneGate.Result.SENT)micMeter.sample(frame,lastRead)?.let(events::microphone)
  }}catch(e:InterruptedException){if(!stopRequested)events.failed(GeminiFailure(GeminiErrorCode.CAPTURE,"Microphone audio thread was unexpectedly interrupted."))}
  catch(e:GeminiFailure){if(!stopRequested)events.failed(e)}catch(e:Exception){if(!stopRequested)events.failed(GeminiFailure(GeminiErrorCode.CAPTURE,"Microphone capture failed. Check permission and audio hardware."))}
  finally{events.capture(false)}
 }
 private fun playbackLoop(){var lastGeneration=-1L;var lastActive=false
  try{while(!stopRequested){val observation=pump?.step(SystemClock.elapsedRealtime())?:break
   if(observation.generation!=lastGeneration)outputMeter.reset()
   if(observation.generation!=lastGeneration||observation.active!=lastActive){lastGeneration=observation.generation;lastActive=observation.active;events.playback(observation.generation,observation.active);if(!observation.active)events.output(observation.generation,AudioLevel(0f,0f))}
   observation.pcm?.let{pcm->outputMeter.sample(pcm,SystemClock.elapsedRealtime())?.let{events.output(observation.generation,it)}}
   Thread.sleep(if(observation.pcm==null)5 else 1)
  }}catch(e:InterruptedException){if(!stopRequested)events.failed(GeminiFailure(GeminiErrorCode.PLAYBACK,"Playback thread was unexpectedly interrupted."))}
  catch(e:GeminiFailure){if(!stopRequested)events.failed(e)}catch(e:Exception){if(!stopRequested)events.failed(GeminiFailure(GeminiErrorCode.PLAYBACK,"Native playback failed. Check the output device."))}
 }
 override suspend fun enqueue(pcm:ByteArray,generation:Long):Boolean=withContext(io){!stopRequested&&pump?.enqueue(pcm,generation)==true}
 override suspend fun interrupt(generation:Long){pump?.invalidate(generation);withContext(io){if(!stopRequested){pump?.flushPending();outputMeter.reset()}}}
 override suspend fun close():Boolean {
  requestStop()
  return withContext(NonCancellable+io){lifecycle.withLock{
   if(closed)return@withLock cleanupSucceeded;closed=true
   fun release(action:()->Unit){try{action()}catch(e:Exception){cleanupSucceeded=false}}
   release{if(record?.recordingState==AudioRecord.RECORDSTATE_RECORDING)record?.stop()}
   release{pump?.close()};release{reader?.interrupt();player?.interrupt()}
   release{reader?.join(1000);if(reader?.isAlive==true)cleanupSucceeded=false}
   release{player?.join(1000);if(player?.isAlive==true)cleanupSucceeded=false}
   release{record?.release();record=null};release{output?.release();output=null};pump=null
   release{aec?.release();aec=null};release{ns?.release();ns=null}
   cleanupSucceeded
  }}
 }
}
