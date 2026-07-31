package app.helpsiffyy.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BackgroundSyncScheduler
import app.shared.core.BaseDashboardActivity
import app.shared.core.DashboardCard
import app.shared.core.DashboardMetric
import app.shared.core.DemoAccount
import app.shared.core.LiveWorkflowRepository
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class DashboardActivity : BaseDashboardActivity() {
    override val productConfig = ProductConfig(
        "Helpify",
        "https://helpsiffyy.app/api",
        listOf(
            RoleOption("Заказчик", "customer"),
            RoleOption("Исполнитель", "contractor")
        ),
        listOf(
            DemoAccount("Заказчик", "customer@example.test", "demo123"),
            DemoAccount("Исполнитель", "contractor@example.test", "demo123")
        )
    )

    override fun dashboardMetrics(user: ApiUser): List<DashboardMetric> =
        HelpifyScenarioCatalog.metrics(user.role)

    override fun dashboardCards(user: ApiUser): List<DashboardCard> =
        HelpifyScenarioCatalog.cards(user.role)

    override fun configureBackgroundSync(
        enabled: Boolean,
        intervalMinutes: Int
    ) {
        BackgroundSyncScheduler.configure(
            this,
            "helpify-live-background-sync",
            HelpifyBackgroundSyncWorker::class.java,
            enabled,
            intervalMinutes
        )
    }

    override fun requestBackgroundSyncNow() {
        BackgroundSyncScheduler.runNow(
            this,
            "helpify-live-background-sync",
            HelpifyBackgroundSyncWorker::class.java
        )
    }

    override fun dashboardActivityClass(): Class<out BaseDashboardActivity> =
        DashboardActivity::class.java

    override fun liveWorkflowRepository(): LiveWorkflowRepository =
        HelpifyLiveWorkflowRepository(productConfig.apiBaseUrl)

    override fun returnToAuth() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
