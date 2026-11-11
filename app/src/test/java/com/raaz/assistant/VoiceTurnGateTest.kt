package com.raaz.assistant
import com.raaz.assistant.voice.RecognitionTurnGate
import org.junit.Assert.*
import org.junit.Test
class VoiceTurnGateTest {
 @Test fun duplicateResultsAreIgnored(){val gate=RecognitionTurnGate();val token=gate.begin();assertTrue(gate.consume(token));assertFalse(gate.consume(token))}
 @Test fun stopInvalidatesOldCallbacks(){val gate=RecognitionTurnGate();val token=gate.begin();gate.cancel();assertFalse(gate.consume(token))}
 @Test fun successiveTurnsRejectPreviousCallbacks(){val gate=RecognitionTurnGate();val first=gate.begin();assertTrue(gate.consume(first));val second=gate.begin();assertFalse(gate.consume(first));assertTrue(gate.consume(second))}
}
