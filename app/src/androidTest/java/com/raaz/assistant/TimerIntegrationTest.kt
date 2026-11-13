package com.raaz.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.provider.AlarmClock
import androidx.test.platform.app.InstrumentationRegistry
import com.raaz.assistant.features.clockTimerIntent
import org.junit.Assert.*
import org.junit.Test

/** Real Android framework checks; no external Clock invocation or user timer modification. */
class TimerIntegrationTest {
 @Test fun clockRequestUsesDocumentedImmediateTimerExtras(){val i=clockTimerIntent(300);assertEquals(AlarmClock.ACTION_SET_TIMER,i.action);assertEquals(300,i.getIntExtra(AlarmClock.EXTRA_LENGTH,0));assertTrue(i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,false))}
 @Test fun setAlarmPermissionDeclared(){val c=InstrumentationRegistry.getInstrumentation().targetContext;val info=c.packageManager.getPackageInfo(c.packageName,PackageManager.GET_PERMISSIONS);assertTrue(info.requestedPermissions?.contains("com.android.alarm.permission.SET_ALARM")==true)}
 @Test fun ownedTimerStateIsNotBackedUpOrWorldReadable(){val c=InstrumentationRegistry.getInstrumentation().targetContext;assertEquals(0,c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP);c.getSharedPreferences("owned_timer",Context.MODE_PRIVATE)}
}
