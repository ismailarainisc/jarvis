package com.raaz.assistant
import com.raaz.assistant.live.*
import org.junit.Assert.*
import org.junit.Test
class LiveSessionTest {
 @Test fun sdpSuccessDoesNotImplyConnection(){assertEquals(LiveSessionState.NEGOTIATING,connectionReadiness(false,"session",null))}
 @Test fun connectedTransportIsNotSessionReady(){assertEquals(LiveSessionState.CONNECTED,connectionReadiness(true,"session",null))}
 @Test fun startedBeforeTransportRemainsNegotiating(){assertEquals(LiveSessionState.NEGOTIATING,connectionReadiness(false,"session","session"))}
 @Test fun matchingRealEventsEnableReadiness(){assertEquals(LiveSessionState.SESSION_READY,connectionReadiness(true,"session","session"))}
 @Test(expected=IllegalStateException::class) fun sessionMismatchRejected(){connectionReadiness(true,"current","stale")}
 @Test fun stopAndRestartRejectStaleEvents(){val e=SessionEpoch();val first=e.next();assertTrue(e.accepts(first));e.next();assertFalse(e.accepts(first));val next=e.next();assertTrue(e.accepts(next));assertFalse(e.accepts(first))}
}
