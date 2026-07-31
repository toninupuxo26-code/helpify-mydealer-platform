package app.helpsiffyy.mobile

import android.content.Context
import androidx.work.WorkerParameters
import app.shared.core.LiveDashboardBackgroundWorker
import app.shared.core.LiveWorkflowRepository

class HelpifyBackgroundSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : LiveDashboardBackgroundWorker(appContext, workerParameters) {
    override val productName: String = "Helpify"
    override val apiBaseUrl: String = "https://helpsiffyy.app/api"

    override fun workflowRepository(): LiveWorkflowRepository =
        HelpifyLiveWorkflowRepository(apiBaseUrl)
}
