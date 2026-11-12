package com.raaz.assistant.setup

import com.raaz.assistant.data.SettingsStore

enum class SetupStep(val title:String) {
 WELCOME("Welcome to JARVIS"), SCOPE("Your assistant, your control"), PROVIDER("Connect your AI"),
 MICROPHONE("Voice permission"), CONTACTS("Optional contacts"), NOTIFICATIONS("Optional notifications"),
 ASSISTANT("Optional assistant role"), ACCESSIBILITY("Optional screen navigation"), WAKE("Optional wake word"),
 VERIFY("Check enabled capabilities"), COMPLETE("Ready for Home")
}
data class SetupState(val step:SetupStep=SetupStep.WELCOME,val completed:Boolean=false,val error:String?=null)
/** Non-sensitive progress only; credentials remain in the existing Keystore-backed repository. */
class SetupProgress(private val settings:SettingsStore) {
 fun read():SetupState=try{SetupState(SetupStep.values().find{it.name==settings.string("setup7_step")}?:SetupStep.WELCOME,settings.boolean("setup7_completed",false))}catch(e:Exception){SetupState(error="Saved setup progress could not be read. Existing provider/settings data was not changed. Review setup again.")}
 fun move(step:SetupStep){settings.edit(mapOf("setup7_step" to step.name))}
 fun next():SetupStep {val step=SetupStep.values()[(read().step.ordinal+1).coerceAtMost(SetupStep.values().lastIndex)];move(step);return step}
 fun finish(){check(read().step==SetupStep.COMPLETE);settings.edit(mapOf("setup7_completed" to true,"setup7_step" to SetupStep.COMPLETE.name))}
 fun revisit(){if(read().completed)settings.edit(mapOf("setup7_step" to SetupStep.WELCOME.name,"setup7_completed" to false))}
}
