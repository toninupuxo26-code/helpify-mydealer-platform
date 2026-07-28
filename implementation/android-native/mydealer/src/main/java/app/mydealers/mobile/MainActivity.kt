package app.mydealers.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BaseAuthActivity
import app.shared.core.DemoAccount
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class MainActivity : BaseAuthActivity() {
    override val productConfig = ProductConfig(
        productName = "MyDealer",
        apiBaseUrl = "https://mydealers.app/api",
        roles = listOf(RoleOption("Покупатель", "buyer"), RoleOption("Вендор", "vendor")),
        demoAccounts = listOf(
            DemoAccount("Покупатель", "buyer@example.test", "demo123"),
            DemoAccount("Вендор", "vendor@example.test", "demo123")
        )
    )

    override fun openDashboard(user: ApiUser) {
        startActivity(Intent(this, DashboardActivity::class.java))
    }
}
