package app.shared.core

data class RoleOption(val title: String, val value: String)

data class DemoAccount(val title: String, val email: String, val password: String)

data class ProductConfig(
    val productName: String,
    val apiBaseUrl: String,
    val roles: List<RoleOption>,
    val demoAccounts: List<DemoAccount>
)
