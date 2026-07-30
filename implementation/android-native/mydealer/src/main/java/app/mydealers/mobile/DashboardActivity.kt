package app.mydealers.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BaseDashboardActivity
import app.shared.core.DashboardCard
import app.shared.core.DashboardMetric
import app.shared.core.DemoAccount
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class DashboardActivity : BaseDashboardActivity() {
    override val productConfig = ProductConfig(
        "MyDealer",
        "https://mydealers.app/api",
        listOf(
            RoleOption("Покупатель", "buyer"),
            RoleOption("Вендор", "vendor")
        ),
        listOf(
            DemoAccount("Покупатель", "buyer@example.test", "demo123"),
            DemoAccount("Вендор", "vendor@example.test", "demo123")
        )
    )

    override fun dashboardMetrics(user: ApiUser): List<DashboardMetric> =
        MyDealerScenarioCatalog.metrics(user.role)

    override fun dashboardCards(user: ApiUser): List<DashboardCard> =
        MyDealerScenarioCatalog.cards(user.role)

    override fun returnToAuth() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
