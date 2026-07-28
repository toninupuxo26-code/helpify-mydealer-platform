package app.shared.core

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

abstract class BaseDashboardActivity : AppCompatActivity() {
    protected abstract val productConfig: ProductConfig
    protected abstract fun dashboardCards(user: ApiUser): List<DashboardCard>
    protected abstract fun returnToAuth()

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var repository: AuthRepository
    private lateinit var store: SessionStore
    private lateinit var profileText: TextView
    private lateinit var cards: LinearLayout
    private var user: ApiUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        repository = AuthRepository(productConfig.apiBaseUrl)
        store = SessionStore(this, productConfig.productName)
        profileText = findViewById(R.id.profileText)
        cards = findViewById(R.id.dashboardCards)
        findViewById<TextView>(R.id.dashboardTitle).text = productConfig.productName
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshProfile() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
        user = store.user()
        if (store.token().isNullOrBlank() || user == null) { returnToAuth(); finish(); return }
        render(user!!)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun render(current: ApiUser) {
        user = current
        profileText.text = "${current.name}
${current.email}
Роль: ${current.role}"
        cards.removeAllViews()
        dashboardCards(current).forEach { card ->
            val button = Button(this).apply {
                text = "${card.title}
${card.description}"
                isAllCaps = false
                setTextColor(Color.DKGRAY)
                setPadding(16, 18, 16, 18)
                setOnClickListener { Toast.makeText(this@BaseDashboardActivity, card.actionMessage, Toast.LENGTH_LONG).show() }
            }
            cards.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10
            })
        }
    }

    private fun refreshProfile() {
        val token = store.token() ?: return
        executor.execute {
            val (result, refreshed) = repository.me(token)
            runOnUiThread {
                if (refreshed != null) { store.save(AuthSession(token, refreshed)); render(refreshed) }
                else Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun logout() {
        val token = store.token()
        store.clear()
        if (token != null) executor.execute { repository.logout(token) }
        returnToAuth()
        finish()
    }
}

data class DashboardCard(val title: String, val description: String, val actionMessage: String)
