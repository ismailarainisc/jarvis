package com.raaz.assistant

import com.raaz.assistant.gemini.*
import org.junit.Assert.*
import org.junit.Test

class MicrophoneGateTest {
 private class Connection:GeminiConnection {
  val sent=mutableListOf<String>();var queue=0L;var accept=true
  override fun open(auth:GeminiSocketAuth){}
  override fun send(text:String):Boolean{sent.add(text);return accept}
  override fun queuedBytes()=queue
  override fun cancel(){}
 }
 @Test fun muteUnmuteInvalidatesFrameCapturedBeforeEitherTransition(){val c=Connection();val g=MicrophoneGate(c);val ticket=g.ticket()!!;g.setMuted(true);assertNull(g.ticket());g.setMuted(false);assertEquals(MicrophoneGate.Result.DROPPED,g.send(ticket,ByteArray(640)));assertEquals(listOf(geminiStreamEnd()),c.sent);assertEquals(MicrophoneGate.Result.SENT,g.send(g.ticket()!!,ByteArray(640)));assertEquals(640L,g.bytesQueued())}
 @Test fun stopPreventsOldCaptureSendingIntoAnySession(){val c=Connection();val old=MicrophoneGate(c);val ticket=old.ticket()!!;old.stop();val next=MicrophoneGate(c);assertEquals(MicrophoneGate.Result.CLOSED,old.send(ticket,ByteArray(2)));assertEquals(MicrophoneGate.Result.SENT,next.send(next.ticket()!!,ByteArray(2)));assertEquals(1,c.sent.size)}
 @Test fun microphoneFrameAndNetworkQueueAreBounded(){val c=Connection();val g=MicrophoneGate(c);for(size in listOf(0,1,642)){assertThrows(GeminiFailure::class.java){g.send(g.ticket()!!,ByteArray(size))}};c.queue=64001;assertEquals(GeminiErrorCode.BACKPRESSURE,assertThrows(GeminiFailure::class.java){g.send(g.ticket()!!,ByteArray(2))}.code)}
 @Test fun sendFailureDoesNotCountUnsentBytes(){val c=Connection();c.accept=false;val g=MicrophoneGate(c);assertThrows(GeminiFailure::class.java){g.send(g.ticket()!!,ByteArray(2))};assertEquals(0L,g.bytesQueued())}
 @Test fun rapidMuteIsIdempotentAndOrdersStreamEnd(){val c=Connection();val g=MicrophoneGate(c);repeat(50){val old=g.ticket()!!;g.send(old,ByteArray(2));g.setMuted(true);g.setMuted(true);assertEquals(MicrophoneGate.Result.DROPPED,g.send(old,ByteArray(2)));g.setMuted(false)};assertEquals(100,c.sent.size);assertEquals(50,c.sent.count{it==geminiStreamEnd()})}
}
