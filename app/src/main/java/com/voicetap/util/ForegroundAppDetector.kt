package com.voicetap.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Returns the package name of the currently active app, or null if Usage Access
     * permission has not been granted.
     */
    fun getForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val stats = usageStats.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 5_000,
            now,
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
