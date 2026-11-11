package com.raaz.assistant

import com.raaz.assistant.gemini.*
import org.junit.Assert.*
import org.junit.Test

class PcmPlaybackPumpTest {
 private class Output:PcmOutput {
  var result:Int?=null;var head=0L;var flushes=0;val writes=mutableListOf<ByteArray>()
  override fun write(pcm:ByteArray,offset:Int,count:Int):Int{val n=result?:count;if(n>0&&n<=count)writes.add(pcm.copyOfRange(offset,offset+n));return n}
  override fun headFrames()=head
  override fun flush(restart:Boolean){flushes++;head=0}
 }
 @Test fun inFlightArrayStillCountsAgainstBufferLimit(){val o=Output();val p=PcmPlaybackPump(o,maxBytes=8);o.result=2;assertTrue(p.enqueue(ByteArray(8),0));p.step(0);assertEquals(8,p.retainedBytes());assertFalse(p.enqueue(ByteArray(2),0))}
 @Test fun partialWritesPreservePcmInOrder(){val o=Output();o.result=2;val p=PcmPlaybackPump(o);val pcm=byteArrayOf(1,2,3,4,5,6);p.enqueue(pcm,0);repeat(3){p.step(it.toLong())};assertArrayEquals(pcm,o.writes.fold(ByteArray(0)){a,b->a+b});assertEquals(0,p.retainedBytes());o.head=3;assertFalse(p.step(4).active)}
 @Test fun oddOrNegativeOrOversizeWritesFail(){for(n in listOf(-1,1,6)){val o=Output();o.result=n;val p=PcmPlaybackPump(o);p.enqueue(ByteArray(4),0);try{p.step(0);fail("invalid driver write $n")}catch(e:GeminiFailure){assertEquals(GeminiErrorCode.PLAYBACK,e.code)}}}
 @Test fun stalledWriteFailsBoundedly(){val o=Output();o.result=0;val p=PcmPlaybackPump(o);p.enqueue(ByteArray(4),0);p.step(0);assertThrows(GeminiFailure::class.java){p.step(3001)}}
 @Test fun stalledPlaybackHeadIsNotShownAsPlayingForever(){val o=Output();val p=PcmPlaybackPump(o);p.enqueue(ByteArray(4),0);p.step(0);assertThrows(GeminiFailure::class.java){p.step(5001)}}
 @Test fun interruptionDiscardsActiveAndQueuedChunks(){val o=Output();o.result=2;val p=PcmPlaybackPump(o);p.enqueue(ByteArray(8),0);p.enqueue(ByteArray(4),0);p.step(0);p.invalidate(1);p.flushPending();assertEquals(0,p.retainedBytes());assertFalse(p.enqueue(ByteArray(2),0));assertTrue(p.enqueue(byteArrayOf(9,8),1));assertEquals(1L,p.step(1).generation);assertArrayEquals(byteArrayOf(9,8),o.writes.last());assertEquals(1,o.flushes)}
 @Test fun invalidPcmAndChunkCountAreBounded(){val p=PcmPlaybackPump(Output(),maxBytes=8,maxChunks=2);assertFalse(p.enqueue(ByteArray(0),0));assertFalse(p.enqueue(ByteArray(3),0));assertTrue(p.enqueue(ByteArray(2),0));assertTrue(p.enqueue(ByteArray(2),0));assertFalse(p.enqueue(ByteArray(2),0))}
 @Test fun closeIsIdempotentAndRejectsFutureAudio(){val o=Output();val p=PcmPlaybackPump(o);p.enqueue(ByteArray(4),0);p.close();p.close();assertEquals(1,o.flushes);assertEquals(0,p.retainedBytes());assertFalse(p.enqueue(ByteArray(4),0));assertFalse(p.step(100).active)}
 @Test fun repeatedGenerationsDoNotResurrectOldAudio(){val o=Output();val p=PcmPlaybackPump(o);repeat(20){i->p.invalidate(i.toLong());assertTrue(p.enqueue(ByteArray(2),i.toLong()));assertFalse(p.enqueue(ByteArray(2),i.toLong()-1));p.step(i.toLong())};assertEquals(19,o.flushes)}
}
