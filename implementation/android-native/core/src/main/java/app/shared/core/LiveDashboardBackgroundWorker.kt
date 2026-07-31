package app.shared.core

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

abstract class LiveDashboardBackgroundWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    protected abstract val productName: String
    protected abstract val apiBaseUrl: String
    protected abstract fun workflowRepository(): LiveWorkflowRepository

    override fun doWork(): Result {
        val syncStore = BackgroundSyncStore(applicationContext, productName)
        syncStore.recordRunning()

        return try {
            val sessionStore = SessionStore(applicationContext, productName)
            val token = sessionStore.token()
            val user = sessionStore.user()

            if (token.isNullOrBlank() || user == null) {
                syncStore.recordResult(
                    successful = false,
                    message = "Нет активной сессии",
                    changes = 0
                )
                return Result.success()
            }

            val (apiResult, payload) = workflowRepository().load(token, user)

            if (payload != null) {
                val updateStore = LiveUpdateStore(
                    applicationContext,
                    productName
                )
                val changes = updateStore.capture(user.role, payload)

                LiveDashboardCache(applicationContext, productName)
                    .save(user.role, payload)

                LiveUpdateNotifier(applicationContext, productName)
                    .notifyChanges(changes, updateStore.settings())

                syncStore.recordResult(
                    successful = true,
                    message = payload.message.ifBlank {
                        "Фоновое обновление завершено"
                    },
                    changes = changes.size
                )

                Result.success()
            } else {
                syncStore.recordResult(
                    successful = false,
                    message = apiResult.message,
                    changes = 0
                )

                when {
                    apiResult.statusCode == 401 -> Result.success()
                    runAttemptCount < MAX_RETRY_ATTEMPTS -> Result.retry()
                    else -> Result.failure()
                }
            }
        } catch (error: Exception) {
            syncStore.recordResult(
                successful = false,
                message = error.message ?: "Ошибка фонового обновления",
                changes = 0
            )

            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 2
    }
}
