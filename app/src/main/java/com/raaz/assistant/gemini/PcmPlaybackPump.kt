package com.raaz.assistant.gemini

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** The real Android AudioTrack is adapted to this port; JVM tests drive exactly the same pump. */
interface PcmOutput {
 fun write(pcm:ByteArray,offset:Int,count:Int):Int
 fun headFrames():Long
 fun flush(restart:Boolean)
}
data class PlaybackObservation(val generation:Long,val active:Boolean,val pcm:ByteArray?=null)
class PcmPlaybackPump(private val output:PcmOutput,private val maxBytes:Int=MAX_PCM_BUFFER_BYTES,private val maxChunks:Int=128) {
 private class Chunk(val generation:Long,val pcm:ByteArray,var offset:Int=0)
 private val lock=Any();private val requestedGeneration=AtomicLong();private var appliedGeneration=0L
 private val queue=ArrayDeque<Chunk>();private var current:Chunk?=null;private var retained=0;private var active=false;private var closed=false
 private var frames=0L;private var baseline=output.headFrames();private var lastHead=0L;private var lastProgress:Long?=null;private var lastHeadProgress:Long?=null
 /** Invalidation is lock-free: an event/UI thread never waits on a native AudioTrack call. */
 fun invalidate(generation:Long){while(true){val previous=requestedGeneration.get();if(generation<=previous||requestedGeneration.compareAndSet(previous,generation))return}}
 private fun flushIfNeeded(){val generation=requestedGeneration.get();if(appliedGeneration!=generation){queue.clear();current=null;retained=0;output.flush(true);appliedGeneration=generation;active=false;frames=0;baseline=output.headFrames();lastHead=0;lastProgress=null;lastHeadProgress=null}}
 fun flushPending()=synchronized(lock){if(!closed)flushIfNeeded()}
 fun enqueue(pcm:ByteArray,generation:Long):Boolean=synchronized(lock){
  if(closed||generation!=requestedGeneration.get()||pcm.isEmpty()||pcm.size%2!=0)return@synchronized false
  flushIfNeeded()
  if(pcm.size>maxBytes-retained||queue.size+(if(current==null)0 else 1)>=maxChunks)return@synchronized false
  queue.addLast(Chunk(generation,pcm));retained+=pcm.size;true
 }
 fun retainedBytes():Int=synchronized(lock){retained}
 fun step(nowMs:Long):PlaybackObservation=synchronized(lock){
  if(closed)return@synchronized PlaybackObservation(requestedGeneration.get(),false)
  flushIfNeeded()
  if(current==null){current=queue.pollFirst();if(current!=null)lastProgress=nowMs}
  val chunk=current
  if(chunk==null){
   val played=(output.headFrames()-baseline) and 0xffffffffL
   if(played!=lastHead){lastHead=played;lastHeadProgress=nowMs}
   if(active&&played>=frames){active=false;lastHeadProgress=null}
   else if(active&&nowMs-(lastHeadProgress?:nowMs)>5000)throw GeminiFailure(GeminiErrorCode.PLAYBACK,"Audio playback stopped making progress. Check the output device and restart.")
   return@synchronized PlaybackObservation(appliedGeneration,active)
  }
  val requested=minOf(960,chunk.pcm.size-chunk.offset)
  val wrote=output.write(chunk.pcm,chunk.offset,requested)
  if(wrote<0||wrote>requested||wrote%2!=0)throw GeminiFailure(GeminiErrorCode.PLAYBACK,"The audio device returned an invalid PCM write. Restart using a supported audio route.")
  if(wrote==0){if(lastProgress==null)lastProgress=nowMs;if(nowMs-lastProgress!!>3000)throw GeminiFailure(GeminiErrorCode.PLAYBACK,"The output device is not accepting audio. Check the route and restart.");return@synchronized PlaybackObservation(appliedGeneration,active)}
  lastProgress=nowMs;lastHeadProgress=nowMs;active=true;val offset=chunk.offset;chunk.offset+=wrote;frames+=wrote/2
  val measured=chunk.pcm.copyOfRange(offset,offset+wrote)
  if(chunk.offset==chunk.pcm.size){retained-=chunk.pcm.size;current=null}
  PlaybackObservation(chunk.generation,true,measured)
 }
 fun close()=synchronized(lock){if(!closed){closed=true;requestedGeneration.incrementAndGet();queue.clear();current=null;retained=0;active=false;output.flush(false)}}
}
