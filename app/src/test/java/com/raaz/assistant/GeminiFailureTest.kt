package com.raaz.assistant
import com.raaz.assistant.gemini.*
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
class GeminiFailureTest {
 @Test fun structuredProviderErrorsAreDistinct(){for((status,expected) in listOf(401 to GeminiErrorCode.AUTHENTICATION,403 to GeminiErrorCode.AUTHENTICATION,404 to GeminiErrorCode.CONFIGURATION,429 to GeminiErrorCode.QUOTA,504 to GeminiErrorCode.TIMEOUT,503 to GeminiErrorCode.NETWORK)){assertEquals(expected,providerFailure(status).code)}}
 @Test fun transportErrorsNeverExposeTheirRawMessage(){for(error in listOf(SocketTimeoutException("secret"),SSLException("secret"),java.io.IOException("secret"))){assertFalse(networkFailure(error).message!!.contains("secret"))};assertEquals(GeminiErrorCode.TLS,networkFailure(SSLException("secret")).code);assertEquals(GeminiErrorCode.TIMEOUT,networkFailure(SocketTimeoutException("secret")).code)}
 @Test fun closeCodesAreClassifiedWithoutUntrustedReasonText(){assertEquals(GeminiErrorCode.PROTOCOL,closedFailure(1009).code);assertEquals(GeminiErrorCode.CONFIGURATION,closedFailure(1008).code);assertEquals(GeminiErrorCode.SESSION_ENDED,closedFailure(1000).code)}
 @Test fun ownershipCannotBeReleasedByPreviousActivity(){val o=AudioOwnership();assertTrue(o.acquire("first"));assertFalse(o.acquire("second"));assertFalse(o.release("second"));assertTrue(o.release("first"));assertTrue(o.acquire("second"));assertFalse(o.release("first"));o.quarantine("second");assertFalse(o.acquire("second"));assertFalse(o.acquire("third"))}
}
