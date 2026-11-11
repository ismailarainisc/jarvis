package com.raaz.assistant.data

enum class AssistantState { IDLE, CONNECTING, LISTENING, PROCESSING, THINKING, SPEAKING, INTERRUPTED, EXECUTING_TOOL, WAITING_FOR_APPROVAL, RECONNECTING, ERROR, STOPPED }
data class Message(val role: String, val text: String)
data class Settings(val url: String = "https://integrate.api.nvidia.com/v1/chat/completions", val model: String = "", val dark: Boolean = true, val language: String = "System", val voice: Boolean = true, val memory: Boolean = false, val hasKey: Boolean = false, val streaming:Boolean = false)
data class AssistantUiState(val state: AssistantState = AssistantState.IDLE, val settings: Settings = Settings(), val messages: List<Message> = emptyList(), val error: String? = null, val imageAttached: Boolean = false, val voiceActive:Boolean = false, val transcript:String = "", val microphoneLevel:Float = 0f, val providerTest:String? = null, val providers:List<ProviderConfiguration> = emptyList(), val selectedProvider:String = "", val streamingText:String = "", val generating:Boolean = false, val providerStatus:Map<String,ProviderStatus> = emptyMap())

fun providerValidation(url:String,model:String):String? = when {
    !validEndpoint(url) -> "Provider URL must use HTTPS."
    model.isBlank() -> "Add a model ID in AI Providers."
    else -> null
}
