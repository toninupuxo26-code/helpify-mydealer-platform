package app.mydealers.mobile

import android.content.Context
import androidx.work.WorkerParameters
import app.shared.core.LiveDashboardBackgroundWorker
import app.shared.core.LiveWorkflowRepository

class MyDealerBackgroundSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : LiveDashboardBackgroundWorker(appContext, workerParameters) {
    override val productName: String = "MyDealer"
    override val apiBaseUrl: String = "https://mydealers.app/api"
    override val dashboardActivityClass: Class<*> = DashboardActivity::class.java

    override fun workflowRepository(): LiveWorkflowRepository =
        MyDealerLiveWorkflowRepository(apiBaseUrl)
}
