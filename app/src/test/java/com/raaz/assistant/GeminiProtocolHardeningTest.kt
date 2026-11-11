package com.raaz.assistant

import com.raaz.assistant.gemini.*
import org.junit.Assert.*
import org.junit.Test

class GeminiProtocolHardeningTest {
 @Test fun pcmRejectsOddNoncanonicalAndMalformedBase64(){for(value in listOf("AAAA","AB==","@#==","AA=A","AA\n=","A")){assertThrows(GeminiFailure::class.java){decodeGeminiPcm(value)}}}
 @Test fun mimeMustBeExactPcmAndNegotiatedRate(){for(mime in listOf("audio/pcmbogus","audio/pcm;rate=16000","audio/pcm;rate=24000;channels=2")){assertThrows(GeminiFailure::class.java){decodeGeminiEvent("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"$mime","data":"AAA="}}]}}}""")}}}
 @Test fun eventAndPcmBoundsAreEnforced(){assertThrows(GeminiFailure::class.java){decodeGeminiEvent(" ".repeat(MAX_GEMINI_MESSAGE_CHARS+1))};assertThrows(GeminiFailure::class.java){decodeGeminiPcm("A".repeat(MAX_PCM_BUFFER_BYTES*4/3+8))}}
 @Test fun incorrectlyTypedFieldsFailTruthfully(){for(data in listOf("""{"setupComplete":true}""","""{"serverContent":{"interrupted":"yes"}}""","""{"serverContent":{"modelTurn":{"parts":{}}}}""")){assertThrows(GeminiFailure::class.java){decodeGeminiEvent(data)}}}
 @Test fun providerBodyAndCredentialNeverAppearInErrors(){val e=decodeGeminiEvent("""{"error":{"code":401,"status":"UNAUTHENTICATED","message":"secret-value"}}""");assertFalse(e.failure!!.message!!.contains("secret-value"));assertFalse(geminiSocketAuth("isolated-secret").toString().contains("isolated-secret"))}
 @Test fun goAwayCarriesRealGracePeriod(){val e=decodeGeminiEvent("""{"goAway":{"timeLeft":"12.5s"}}""");assertTrue(e.goAway);assertEquals(12500L,e.expiresInMs)}
 @Test fun modelPrefixIsNormalizedAfterWhitespace(){assertEquals("models/gemini-3.8-live",geminiModelName(" models/gemini-3.8-live "))}
 @Test fun invalidAuthCannotInjectHeadersOrUrl(){for(key in listOf("","bad\r\nHeader:value","value with spaces")){assertThrows(IllegalArgumentException::class.java){geminiSocketAuth(key)}}}
}
