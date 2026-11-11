package com.raaz.assistant
import com.raaz.assistant.gemini.*
import com.raaz.assistant.data.AssistantState
import org.junit.Assert.*
import org.junit.Test
class AssistantVisualStateTest {
 @Test fun setupAndConnectNeverPretendToListen(){assertEquals(AssistantState.CONNECTING,GeminiPhase.CONNECTING.visualState());assertEquals(AssistantState.CONNECTING,GeminiPhase.SETTING_UP.visualState())}
 @Test fun foregroundAndBackgroundUseTheSamePhaseMapping(){assertEquals(AssistantState.PROCESSING,GeminiPhase.PROCESSING.visualState());assertEquals(AssistantState.SPEAKING,GeminiPhase.ASSISTANT_SPEAKING.visualState());assertEquals(AssistantState.IDLE,GeminiPhase.MUTED.visualState());assertEquals(AssistantState.ERROR,GeminiPhase.ERROR.visualState())}
}
