package com.raaz.assistant.features

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.raaz.assistant.data.*
import org.json.JSONObject
import java.util.UUID

/** Separate additive database: existing history, settings and keys are never migrated destructively. */
class FeatureDatabase(context:Context,name:String="jarvis_features.db"):SQLiteOpenHelper(context.applicationContext,name,null,1) {
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE memory(id TEXT PRIMARY KEY, kind TEXT NOT NULL, label TEXT NOT NULL, value TEXT NOT NULL, lookup TEXT NOT NULL, provenance TEXT NOT NULL, updated INTEGER NOT NULL)");db.execSQL("CREATE UNIQUE INDEX alias_name ON memory(label COLLATE NOCASE) WHERE kind='alias'");db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, action TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, detail TEXT NOT NULL, created INTEGER NOT NULL)")}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("No destructive migration is permitted. Add an explicit migration for this version.")}
}
class FeatureStore(context:Context,databaseName:String="jarvis_features.db",preferencesName:String="assistant_features"):TaskLedger {
 private val db=FeatureDatabase(context,databaseName)
 private val prefs=AndroidSettingsStore(context,preferencesName)
 private val cipher=AndroidCredentialCipher()
 fun flag(name:String,default:Boolean=false)=prefs.boolean(name,default)
 fun setFlag(name:String,value:Boolean)=prefs.edit(mapOf(name to value))
 fun secretPresent(name:String)=prefs.contains("key_$name")
 fun secret(name:String)=cipher.decrypt(prefs.string("key_$name")?:throw CredentialStorageException("Configure this service credential first."))
 fun saveSecret(name:String,value:String){require(value.isNotBlank()&&value.length<=8192);prefs.edit(mapOf("key_$name" to cipher.encrypt(value)))}
 fun deleteSecret(name:String)=prefs.edit(mapOf("key_$name" to null))
 fun config(name:String)=prefs.string(name,"")?:""
 fun config(name:String,value:String){require(value.length<=65536);prefs.edit(mapOf(name to value))}
 @Synchronized fun memories():List<MemoryItem> = db.readableDatabase.rawQuery("SELECT * FROM memory ORDER BY updated DESC LIMIT 200",null).use{c->buildList{while(c.moveToNext())add(MemoryItem(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6)))}}
 @Synchronized fun saveMemory(item:MemoryItem){require(item.kind in setOf("note","preference","alias"));require(safeMemory(item.label,item.value));require(item.id.matches(Regex("[A-Za-z0-9-]{1,100}")));if(item.kind=="alias")require(validPhone(item.value));require(memories().any{it.id==item.id}||memories().size<200){"Memory limit reached. Remove an item first."};val v=ContentValues().apply{put("id",item.id);put("kind",item.kind);put("label",if(item.kind=="alias")normalizeAlias(item.label)else item.label.trim());put("value",item.value);put("lookup",item.contactLookup);put("provenance",item.provenance);put("updated",System.currentTimeMillis())};db.writableDatabase.insertWithOnConflict("memory",null,v,SQLiteDatabase.CONFLICT_REPLACE)}
 @Synchronized fun deleteMemory(id:String){db.writableDatabase.delete("memory","id=?",arrayOf(id))}
 fun relevantContext(query:String):String {if(!flag("memory_enabled")||!flag("memory_cloud"))return "";return memoryContext(memories(),true,true,query)}
 fun saveMcpConfiguration(raw:String,credentialId:String?=null,token:String?=null){val changes=mutableMapOf<String,Any?>("mcp_servers" to raw);if(credentialId!=null&&!token.isNullOrBlank())changes["key_mcp_$credentialId"]=cipher.encrypt(token);prefs.edit(changes)}
 @Synchronized override fun tasks():List<AgentTask> = db.readableDatabase.rawQuery("SELECT * FROM tasks ORDER BY created DESC LIMIT 100",null).use{c->buildList{while(c.moveToNext())add(AgentTask(c.getString(0),ToolAction.decode(c.getString(1)),c.getString(2),TaskStatus.valueOf(c.getString(3)),c.getString(4),c.getLong(5)))}}
 @Synchronized override fun insertTask(task:AgentTask){val v=ContentValues().apply{put("id",task.id);put("action",task.action.encode());put("source",task.source.take(100));put("status",task.status.name);put("detail",task.detail.take(8000));put("created",task.created)};check(db.writableDatabase.insertOrThrow("tasks",null,v)>0);db.writableDatabase.execSQL("DELETE FROM tasks WHERE id NOT IN (SELECT id FROM tasks ORDER BY created DESC LIMIT 100)")}
 /** Compare-and-set prevents double approval/execution, including after process recreation. */
 @Synchronized override fun transition(id:String,from:TaskStatus,to:TaskStatus,detail:String):Boolean {val v=ContentValues().apply{put("status",to.name);put("detail",detail.take(8000))};return db.writableDatabase.update("tasks",v,"id=? AND status=?",arrayOf(id,from.name))==1}
 @Synchronized fun recoverInterrupted(){val v=ContentValues().apply{put("status",TaskStatus.FAILED.name);put("detail","Execution was interrupted by process termination. Outcome is unknown; no automatic retry.")};db.writableDatabase.update("tasks",v,"status=?",arrayOf(TaskStatus.EXECUTING.name))}
 @Synchronized fun clearFinishedTasks(){db.writableDatabase.delete("tasks","status NOT IN (?,?)",arrayOf(TaskStatus.NEEDS_APPROVAL.name,TaskStatus.EXECUTING.name))}
 fun close()=db.close()
}
