package com.raaz.assistant
import com.raaz.assistant.data.*
import org.junit.Assert.*
import org.junit.Test
class ProviderCoreTest {
 @Test fun endpointDisallowsCredentialsAndQuery(){assertFalse(validEndpoint("https://secret@example.com/v1"));assertFalse(validEndpoint("https://example.com/v1?key=secret"));assertTrue(validEndpoint("https://api.example.com/v1/chat/completions"))}
 @Test fun sseAccumulatesDataUntilBlankLine(){val p=SseEvents();assertNull(p.line(": keepalive"));assertNull(p.line("data: first"));assertNull(p.line("data: second"));assertEquals("first\nsecond",p.line(""));assertNull(p.line(""))}
 @Test fun errorStatusDoesNotInventSpecificQuotaReason(){assertTrue(httpError(429).contains("quota"));assertTrue(httpError(401).contains("Authentication"))}
}
