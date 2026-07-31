package app.shared.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {
    fun configure(
        context: Context,
        uniqueWorkName: String,
        workerClass: Class<out ListenableWorker>,
        enabled: Boolean,
        intervalMinutes: Int
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (!enabled) {
            workManager.cancelUniqueWork(uniqueWorkName)
            context.applicationContext
                .getSharedPreferences(SCHEDULER_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(intervalKey(uniqueWorkName))
                .apply()
            return
        }

        val normalizedInterval = intervalMinutes.coerceAtLeast(
            MIN_PERIODIC_INTERVAL_MINUTES
        )

        val schedulerPreferences = context.applicationContext
            .getSharedPreferences(SCHEDULER_PREFERENCES, Context.MODE_PRIVATE)
        val previousInterval = schedulerPreferences.getInt(
            intervalKey(uniqueWorkName),
            -1
        )

        val request = PeriodicWorkRequest.Builder(
            workerClass,
            normalizedInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints())
            .addTag(uniqueWorkName)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            if (previousInterval == normalizedInterval) {
                ExistingPeriodicWorkPolicy.KEEP
            } else {
                ExistingPeriodicWorkPolicy.REPLACE
            },
            request
        )

        schedulerPreferences.edit()
            .putInt(intervalKey(uniqueWorkName), normalizedInterval)
            .apply()
    }

    fun runNow(
        context: Context,
        uniqueWorkName: String,
        workerClass: Class<out ListenableWorker>
    ) {
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(networkConstraints())
            .addTag("$uniqueWorkName-now")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                "$uniqueWorkName-now",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    private fun intervalKey(uniqueWorkName: String): String =
        "interval_$uniqueWorkName"

    private const val MIN_PERIODIC_INTERVAL_MINUTES = 15
    private const val SCHEDULER_PREFERENCES = "background_sync_scheduler"
}
