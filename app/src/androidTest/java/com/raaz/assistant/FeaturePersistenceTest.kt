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
   val reopened=FeatureStore(context,database,preferences);assertEquals("Urdu",reopened.memories().single().value);reopened.recoverInterrupted();assertEquals(TaskStatus.UNKNOWN_OUTCOME,reopened.tasks().single().status);assertTrue(reopened.tasks().single().detail.contains("unknown"));reopened.deleteMemory("test-memory");assertTrue(reopened.memories().isEmpty());reopened.close()
  }finally{context.deleteDatabase(database);context.getSharedPreferences(preferences,0).edit().clear().commit()}
 }
 @Test fun finishedHistoryPruningNeverDeletesPendingApproval(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-retention-${UUID.randomUUID()}.db";val prefs="test-retention-${UUID.randomUUID()}";val store=FeatureStore(c,name,prefs)
  try{val pending=AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture",created=1L);store.insertTask(pending);repeat(150){n->store.insertTask(AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture",status=TaskStatus.VERIFIED,created=n.toLong()+2))};assertEquals(TaskStatus.NEEDS_APPROVAL,store.tasks().single{it.id==pending.id}.status);assertEquals(101,store.tasks().size);store.clearFinishedTasks();assertEquals(pending.id,store.tasks().single().id)}finally{store.close();c.deleteDatabase(name);c.getSharedPreferences(prefs,0).edit().clear().commit()}
 }
 @Test fun pendingCapacityRequiresExplicitReviewInsteadOfEviction(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-capacity-${UUID.randomUUID()}.db";val prefs="test-capacity-${UUID.randomUUID()}";val store=FeatureStore(c,name,prefs)
  try{repeat(100){store.insertTask(AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture"))};assertThrows(IllegalArgumentException::class.java){store.insertTask(AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture"))};assertEquals(100,store.tasks().size)}finally{store.close();c.deleteDatabase(name);c.getSharedPreferences(prefs,0).edit().clear().commit()}
 }

 @Test fun editedMemoryCannotBeDeletedUsingStaleApprovedPreview(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-delete-${UUID.randomUUID()}.db";val prefs="test-delete-${UUID.randomUUID()}";val store=FeatureStore(c,name,prefs)
  try{store.saveMemory(MemoryItem("memory-id","note","Tea","No sugar"));store.saveMemory(MemoryItem("memory-id","note","Tea","One sugar"));assertFalse(store.deleteMemoryIfUnchanged("memory-id","Tea","No sugar","note"));assertEquals("One sugar",store.memories().single().value);assertTrue(store.deleteMemoryIfUnchanged("memory-id","Tea","One sugar","note"));assertTrue(store.memories().isEmpty())}finally{store.close();c.deleteDatabase(name);c.getSharedPreferences(prefs,0).edit().clear().commit()}
 }

 @Test fun expiredVoiceApprovalIsCancelledOnRecoveryButManualRequestSurvives(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-expiry-${UUID.randomUUID()}.db";val prefs="test-expiry-${UUID.randomUUID()}";val store=FeatureStore(c,name,prefs)
  try{val voice=AgentTask(action=ToolAction("device_status",JSONObject()),source="Gemini proposal",created=1L);val manual=voice.copy(id=UUID.randomUUID().toString(),source="User tools screen");store.insertTask(voice);store.insertTask(manual);store.recoverInterrupted();assertEquals(TaskStatus.CANCELLED,store.tasks().single{it.id==voice.id}.status);assertEquals(TaskStatus.NEEDS_APPROVAL,store.tasks().single{it.id==manual.id}.status)}finally{store.close();c.deleteDatabase(name);c.getSharedPreferences(prefs,0).edit().clear().commit()}
 }
 @Test fun pendingLimitIsAtomicAcrossTwoDatabaseHelpers(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="test-concurrent-${UUID.randomUUID()}.db";val prefs="test-concurrent-${UUID.randomUUID()}";val first=FeatureStore(c,name,prefs);val second=FeatureStore(c,name,prefs);val workers=java.util.concurrent.Executors.newFixedThreadPool(2)
  try{repeat(99){first.insertTask(AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture"))};second.tasks();val gate=java.util.concurrent.CountDownLatch(1);val jobs=listOf(first,second).map{store->workers.submit<Boolean>{gate.await();try{store.insertTask(AgentTask(action=ToolAction("device_status",JSONObject()),source="fixture"));true}catch(e:IllegalArgumentException){false}}};gate.countDown();assertEquals(1,jobs.count{it.get(10,java.util.concurrent.TimeUnit.SECONDS)});assertEquals(100,first.tasks().size)}finally{workers.shutdownNow();first.close();second.close();c.deleteDatabase(name);c.getSharedPreferences(prefs,0).edit().clear().commit()}
 }

}
