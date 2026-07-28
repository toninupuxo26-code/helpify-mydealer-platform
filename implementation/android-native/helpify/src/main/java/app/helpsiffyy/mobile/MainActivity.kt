package app.helpsiffyy.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BaseAuthActivity
import app.shared.core.DemoAccount
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class MainActivity : BaseAuthActivity() {
    override val productConfig = ProductConfig(
        productName = "Helpify",
        apiBaseUrl = "https://helpsiffyy.app/api",
        roles = listOf(RoleOption("Заказчик", "customer"), RoleOption("Исполнитель", "contractor")),
        demoAccounts = listOf(
            DemoAccount("Заказчик", "customer@example.test", "demo123"),
            DemoAccount("Исполнитель", "contractor@example.test", "demo123")
        )
    )

    override fun openDashboard(user: ApiUser) {
        startActivity(Intent(this, DashboardActivity::class.java))
    }
}
