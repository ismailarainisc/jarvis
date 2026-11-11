package com.raaz.assistant
import com.raaz.assistant.background.selectRecognitionComponent
import org.junit.Assert.*
import org.junit.Test
class RecognitionSelectionTest {
 @Test fun ownAssistantRecognizerIsNeverSelectedRecursively(){assertNull(selectRecognitionComponent(listOf("com.raaz.assistant/.Recognition"),"com.raaz.assistant/.Recognition","com.raaz.assistant"))}
 @Test fun preferredExternalProviderIsPreserved(){assertEquals("other/.Speech",selectRecognitionComponent(listOf("first/.Speech","other/.Speech"),"other/.Speech","com.raaz.assistant"))}
 @Test fun missingPreferenceUsesAnActualExternalComponent(){assertEquals("external/.Speech",selectRecognitionComponent(listOf("com.raaz.assistant/.Recognition","external/.Speech"),"com.raaz.assistant/.Recognition","com.raaz.assistant"))}
}
