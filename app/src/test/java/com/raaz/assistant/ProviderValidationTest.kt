package com.raaz.assistant
import com.raaz.assistant.data.providerValidation
import org.junit.Assert.*
import org.junit.Test
class ProviderValidationTest {
 @Test fun rejectsCleartext(){assertNotNull(providerValidation("http://example.org", "model"))}
 @Test fun rejectsBlankModel(){assertNotNull(providerValidation("https://example.org", "  "))}
 @Test fun acceptsValidProvider(){assertNull(providerValidation("https://example.org/v1/chat/completions", "model"))}
}
