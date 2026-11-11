package com.raaz.assistant

import androidx.test.platform.app.InstrumentationRegistry
import com.raaz.assistant.features.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FeaturePersistenceTest {
 @Test fun additiveDatabasePersistsMemoryAndTasksAndDoesNotReplay(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val database="test-features-${UUID.randomUUID()}.db";val preferences="test-features-${UUID.randomUUID()}"
  try{
   val first=FeatureStore(context,database,preferences);first.setFlag("memory_enabled",true);first.saveMemory(MemoryItem("test-memory","preference","Reply language","Urdu"));val task=AgentTask(action=ToolAction("youtube_search",JSONObject().put("query","Kotlin")),source="instrumented fixture");first.insertTask(task);assertTrue(first.transition(task.id,TaskStatus.NEEDS_APPROVAL,TaskStatus.EXECUTING,"test"));assertFalse(first.transition(task.id,TaskStatus.NEEDS_APPROVAL,TaskStatus.EXECUTING,"duplicate"));first.close()
   val reopened=FeatureStore(context,database,preferences);assertEquals("Urdu",reopened.memories().single().value);reopened.recoverInterrupted();assertEquals(TaskStatus.FAILED,reopened.tasks().single().status);assertTrue(reopened.tasks().single().detail.contains("unknown"));reopened.deleteMemory("test-memory");assertTrue(reopened.memories().isEmpty());reopened.close()
  }finally{context.deleteDatabase(database);context.getSharedPreferences(preferences,0).edit().clear().commit()}
 }
}
