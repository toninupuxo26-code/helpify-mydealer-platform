package app.shared.core

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

abstract class BaseDashboardActivity : AppCompatActivity() {
    protected abstract val productConfig: ProductConfig
    protected abstract fun dashboardCards(user: ApiUser): List<DashboardCard>
    protected open fun dashboardMetrics(user: ApiUser): List<DashboardMetric> = emptyList()
    protected open fun liveWorkflowRepository(): LiveWorkflowRepository? = null
    protected abstract fun returnToAuth()

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionStore: SessionStore
    private lateinit var scenarioStore: ScenarioStore
    private lateinit var profileText: TextView
    private lateinit var cardsContainer: LinearLayout
    private var workflowRepository: LiveWorkflowRepository? = null
    private var currentUser: ApiUser? = null
    private var livePayload: LiveDashboardPayload? = null
    private var liveMessage: String = "Данные сервера ещё не загружены"
    private var liveLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        authRepository = AuthRepository(productConfig.apiBaseUrl)
        sessionStore = SessionStore(this, productConfig.productName)
        scenarioStore = ScenarioStore(this, productConfig.productName)
        workflowRepository = liveWorkflowRepository()

        profileText = findViewById(R.id.profileText)
        cardsContainer = findViewById(R.id.dashboardCards)

        findViewById<TextView>(R.id.dashboardTitle).text = productConfig.productName
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshAll() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }

        currentUser = sessionStore.user()
        if (sessionStore.token().isNullOrBlank() || currentUser == null) {
            returnToAuth()
            finish()
            return
        }

        render(currentUser!!)
        refreshLiveData()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun render(user: ApiUser) {
        currentUser = user
        profileText.text = "${user.name}\n${user.email}\nРоль: ${user.role}"
        cardsContainer.removeAllViews()

        renderLiveSection()

        val metrics = dashboardMetrics(user)
        if (metrics.isNotEmpty()) {
            addSectionTitle("Демо-обзор")
            metrics.forEach { metric ->
                cardsContainer.addView(metricView(metric), fullWidthParams())
            }
        }

        val resetButton = Button(this).apply {
            text = "Сбросить прогресс готовых сценариев"
            isAllCaps = false
            setOnClickListener {
                scenarioStore.resetAll()
                Toast.makeText(
                    this@BaseDashboardActivity,
                    "Прогресс сценариев сброшен",
                    Toast.LENGTH_SHORT
                ).show()
                render(user)
            }
        }
        cardsContainer.addView(resetButton, fullWidthParams(bottom = 18))

        dashboardCards(user)
            .groupBy { it.section }
            .forEach { (section, sectionCards) ->
                addSectionTitle(section)
                sectionCards.forEach { card ->
                    cardsContainer.addView(cardButton(card), fullWidthParams(bottom = 10))
                }
            }
    }

    private fun renderLiveSection() {
        if (workflowRepository == null) return

        addSectionTitle("Данные сервера")

        cardsContainer.addView(
            TextView(this).apply {
                text = if (liveLoading) "Загрузка…" else liveMessage
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(18, 8, 18, 12)
            },
            fullWidthParams()
        )

        val refreshButton = Button(this).apply {
            text = if (liveLoading) "Загрузка данных…" else "Обновить данные сервера"
            isAllCaps = false
            isEnabled = !liveLoading
            setOnClickListener { refreshLiveData() }
        }
        cardsContainer.addView(refreshButton, fullWidthParams(bottom = 12))

        val payload = livePayload ?: return

        payload.metrics.forEach { metric ->
            cardsContainer.addView(metricView(metric), fullWidthParams())
        }

        payload.cards
            .groupBy { it.section }
            .forEach { (section, sectionCards) ->
                addSectionTitle(section)
                sectionCards.forEach { card ->
                    cardsContainer.addView(
                        liveCardButton(card),
                        fullWidthParams(bottom = 10)
                    )
                }
            }
    }

    private fun metricView(metric: DashboardMetric): TextView = TextView(this).apply {
        text = "${metric.value} — ${metric.label}"
        textSize = 17f
        setTextColor(Color.DKGRAY)
        setPadding(18, 12, 18, 12)
    }

    private fun addSectionTitle(title: String) {
        cardsContainer.addView(
            TextView(this).apply {
                text = title
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.DKGRAY)
                setPadding(4, 18, 4, 10)
            },
            fullWidthParams()
        )
    }

    private fun liveCardButton(card: LiveDashboardCard): Button =
        Button(this).apply {
            text = buildString {
                append(card.title)
                if (card.badge.isNotBlank()) append(" · ${card.badge}")
                append("\n${card.description}")
            }
            isAllCaps = false
            setTextColor(Color.DKGRAY)
            setPadding(16, 18, 16, 18)
            setOnClickListener { openLiveCard(card) }
        }

    private fun openLiveCard(card: LiveDashboardCard) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(card.title)
            .setMessage(card.details)
            .setNegativeButton("Закрыть", null)

        if (!card.actionId.isNullOrBlank() && card.actionLabel.isNotBlank()) {
            dialog.setPositiveButton(card.actionLabel) { _, _ ->
                performLiveAction(card)
            }
        } else {
            dialog.setPositiveButton("Готово", null)
        }

        dialog.show()
    }

    private fun performLiveAction(card: LiveDashboardCard) {
        val repository = workflowRepository ?: return
        val token = sessionStore.token() ?: return
        val user = currentUser ?: return

        liveLoading = true
        liveMessage = "Выполняется действие…"
        render(user)

        executor.execute {
            val result = repository.perform(token, user, card)
            runOnUiThread {
                liveLoading = false
                Toast.makeText(
                    this,
                    result.message,
                    if (result.successful) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()

                if (result.statusCode == 401) {
                    sessionStore.clear()
                    returnToAuth()
                    finish()
                } else {
                    refreshLiveData()
                }
            }
        }
    }

    private fun cardButton(card: DashboardCard): Button {
        val step = scenarioStore.step(card.id)
        val progress = if (card.steps.isEmpty()) {
            ""
        } else {
            " · $step/${card.steps.size}"
        }

        return Button(this).apply {
            text = buildString {
                append(card.title)
                if (card.badge.isNotBlank()) append(" · ${card.badge}")
                append(progress)
                append("\n${card.description}")
            }
            isAllCaps = false
            setTextColor(Color.DKGRAY)
            setPadding(16, 18, 16, 18)
            setOnClickListener { openScenario(card) }
        }
    }

    private fun openScenario(card: DashboardCard) {
        val step = scenarioStore.step(card.id)
        val progressText = if (card.steps.isEmpty()) {
            card.actionMessage
        } else {
            val rows = card.steps.mapIndexed { index, item ->
                when {
                    index < step -> "✓ $item"
                    index == step -> "→ $item"
                    else -> "○ $item"
                }
            }
            "${card.actionMessage}\n\n${rows.joinToString("\n")}"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(card.title)
            .setMessage(progressText)
            .setNegativeButton("Закрыть", null)

        if (card.steps.isNotEmpty() && step < card.steps.size) {
            dialog.setPositiveButton("Следующий шаг") { _, _ ->
                val next = scenarioStore.advance(card.id, card.steps.size)
                val message = if (next >= card.steps.size) {
                    "Сценарий завершён"
                } else {
                    "Шаг $next из ${card.steps.size} сохранён"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                currentUser?.let { render(it) }
            }
        } else {
            dialog.setPositiveButton("Готово", null)
        }

        if (step > 0) {
            dialog.setNeutralButton("Сбросить") { _, _ ->
                scenarioStore.reset(card.id)
                currentUser?.let { render(it) }
            }
        }

        dialog.show()
    }

    private fun fullWidthParams(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = bottom
        }

    private fun refreshAll() {
        val token = sessionStore.token() ?: return
        val user = currentUser ?: return

        liveLoading = true
        liveMessage = "Проверка профиля и данных…"
        render(user)

        executor.execute {
            val (result, refreshed) = authRepository.me(token)
            runOnUiThread {
                if (refreshed != null) {
                    sessionStore.save(AuthSession(token, refreshed))
                    currentUser = refreshed
                    render(refreshed)
                    refreshLiveData()
                } else {
                    liveLoading = false
                    liveMessage = result.message
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    render(user)
                }
            }
        }
    }

    private fun refreshLiveData() {
        val repository = workflowRepository ?: return
        val token = sessionStore.token() ?: return
        val user = currentUser ?: return

        liveLoading = true
        liveMessage = "Загрузка данных сервера…"
        render(user)

        executor.execute {
            val (result, payload) = repository.load(token, user)
            runOnUiThread {
                liveLoading = false
                if (payload != null) {
                    livePayload = payload
                    liveMessage = payload.message.ifBlank { "Данные сервера обновлены" }
                } else {
                    liveMessage = result.message
                }

                if (result.statusCode == 401) {
                    sessionStore.clear()
                    returnToAuth()
                    finish()
                } else {
                    render(user)
                }
            }
        }
    }

    private fun logout() {
        val token = sessionStore.token()
        sessionStore.clear()
        if (token != null) {
            executor.execute { authRepository.logout(token) }
        }
        returnToAuth()
        finish()
    }
}

data class DashboardMetric(
    val label: String,
    val value: String
)

data class DashboardCard(
    val id: String,
    val title: String,
    val description: String,
    val actionMessage: String,
    val section: String = "Обзор",
    val badge: String = "",
    val steps: List<String> = emptyList()
)
