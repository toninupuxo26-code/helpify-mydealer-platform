package app.shared.core

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private lateinit var liveCache: LiveDashboardCache
    private lateinit var actionHistory: ActionHistoryStore
    private lateinit var libraryStore: DashboardLibraryStore
    private lateinit var profileText: TextView
    private lateinit var cardsContainer: LinearLayout
    private var workflowRepository: LiveWorkflowRepository? = null
    private var currentUser: ApiUser? = null
    private var livePayload: LiveDashboardPayload? = null
    private var liveMessage: String = "Данные сервера ещё не загружены"
    private var liveLoading = false
    private var showingCachedData = false
    private var searchQuery = ""
    private var activeSection = ALL_SECTIONS
    private var actionableOnly = false
    private var favoritesOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        authRepository = AuthRepository(productConfig.apiBaseUrl)
        sessionStore = SessionStore(this, productConfig.productName)
        scenarioStore = ScenarioStore(this, productConfig.productName)
        liveCache = LiveDashboardCache(this, productConfig.productName)
        actionHistory = ActionHistoryStore(this, productConfig.productName)
        libraryStore = DashboardLibraryStore(this, productConfig.productName)
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

        restoreCachedLiveData(currentUser!!)
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

        renderNavigation(user)
        renderLiveSection()
        renderHistorySection()
        renderRecentSection()

        val demoCards = dashboardCards(user)
            .filter { matchesDashboardCard(it) }

        if (showOverviewMetrics()) {
            val metrics = dashboardMetrics(user)
            if (metrics.isNotEmpty()) {
                addSectionTitle("Демо-обзор")
                metrics.forEach { metric ->
                    cardsContainer.addView(metricView(metric), fullWidthParams())
                }
            }
        }

        if (
            activeSection == ALL_SECTIONS &&
            searchQuery.isBlank() &&
            !actionableOnly &&
            !favoritesOnly
        ) {
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
        }

        if (demoCards.isNotEmpty()) {
            demoCards
                .groupBy { it.section }
                .forEach { (section, sectionCards) ->
                    addSectionTitle(section)
                    sectionCards.forEach { card ->
                        cardsContainer.addView(
                            cardButton(card),
                            fullWidthParams(bottom = 10)
                        )
                    }
                }
        }

        if (!hasVisibleCards(user)) {
            cardsContainer.addView(
                TextView(this).apply {
                    text = "По выбранным условиям ничего не найдено"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(18, 28, 18, 28)
                },
                fullWidthParams()
            )
        }
    }

    private fun renderNavigation(user: ApiUser) {
        addSectionTitle("Поиск и фильтры")

        val searchInput = EditText(this).apply {
            hint = "Название, описание, раздел или статус"
            setText(searchQuery)
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        cardsContainer.addView(searchInput, fullWidthParams(bottom = 8))

        val searchActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        searchActions.addView(
            Button(this).apply {
                text = "Найти"
                isAllCaps = false
                setOnClickListener {
                    searchQuery = searchInput.text.toString().trim()
                    render(user)
                }
            },
            weightedParams()
        )

        searchActions.addView(
            Button(this).apply {
                text = "Сбросить"
                isAllCaps = false
                setOnClickListener {
                    searchQuery = ""
                    activeSection = ALL_SECTIONS
                    actionableOnly = false
                    favoritesOnly = false
                    render(user)
                }
            },
            weightedParams()
        )

        cardsContainer.addView(searchActions, fullWidthParams(bottom = 8))

        val sectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        availableSections(user).forEach { section ->
            sectionRow.addView(
                Button(this).apply {
                    text = if (section == activeSection) "✓ $section" else section
                    isAllCaps = false
                    setOnClickListener {
                        activeSection = section
                        render(user)
                    }
                }
            )
        }

        cardsContainer.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(sectionRow)
            },
            fullWidthParams(bottom = 8)
        )

        cardsContainer.addView(
            CheckBox(this).apply {
                text = "Только карточки с доступным действием"
                isChecked = actionableOnly
                setOnCheckedChangeListener { _, checked ->
                    if (actionableOnly != checked) {
                        actionableOnly = checked
                        render(user)
                    }
                }
            },
            fullWidthParams(bottom = 4)
        )

        cardsContainer.addView(
            CheckBox(this).apply {
                text = "Только избранные карточки"
                isChecked = favoritesOnly
                setOnCheckedChangeListener { _, checked ->
                    if (favoritesOnly != checked) {
                        favoritesOnly = checked
                        render(user)
                    }
                }
            },
            fullWidthParams(bottom = 4)
        )

        cardsContainer.addView(
            TextView(this).apply {
                text = "Удерживайте карточку, чтобы добавить или удалить её из избранного"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                setPadding(18, 0, 18, 14)
            },
            fullWidthParams()
        )
    }

    private fun renderLiveSection() {
        if (workflowRepository == null) return
        if (!sectionVisible(SERVER_SECTION)) return

        addSectionTitle(SERVER_SECTION)

        cardsContainer.addView(
            TextView(this).apply {
                text = buildString {
                    if (showingCachedData) append("Офлайн-кэш · ")
                    append(if (liveLoading) "Загрузка…" else liveMessage)
                }
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(18, 8, 18, 12)
            },
            fullWidthParams()
        )

        val liveControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        liveControls.addView(
            Button(this).apply {
                text = if (liveLoading) "Загрузка…" else "Обновить"
                isAllCaps = false
                isEnabled = !liveLoading
                setOnClickListener { refreshLiveData() }
            },
            weightedParams()
        )

        liveControls.addView(
            Button(this).apply {
                text = "Очистить кэш"
                isAllCaps = false
                setOnClickListener {
                    currentUser?.let { user ->
                        liveCache.clear(user.role)
                        if (showingCachedData) {
                            livePayload = null
                            showingCachedData = false
                            liveMessage = "Офлайн-кэш очищен"
                        }
                        render(user)
                    }
                }
            },
            weightedParams()
        )

        cardsContainer.addView(liveControls, fullWidthParams(bottom = 12))

        val payload = livePayload ?: return

        if (showOverviewMetrics()) {
            payload.metrics.forEach { metric ->
                cardsContainer.addView(metricView(metric), fullWidthParams())
            }
        }

        val liveCards = payload.cards.filter { matchesLiveCard(it) }

        liveCards
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

    private fun renderHistorySection() {
        if (!sectionVisible(HISTORY_SECTION)) return

        val entries = actionHistory.entries()
            .filter { matchesHistoryEntry(it) }

        if (
            entries.isEmpty() &&
            activeSection != HISTORY_SECTION &&
            searchQuery.isBlank()
        ) {
            return
        }

        addSectionTitle(HISTORY_SECTION)

        if (entries.isEmpty()) {
            cardsContainer.addView(
                TextView(this).apply {
                    text = "История серверных действий пока пуста"
                    setPadding(18, 10, 18, 10)
                },
                fullWidthParams()
            )
            return
        }

        cardsContainer.addView(
            Button(this).apply {
                text = "Очистить историю действий"
                isAllCaps = false
                setOnClickListener {
                    actionHistory.clear()
                    currentUser?.let { render(it) }
                }
            },
            fullWidthParams(bottom = 8)
        )

        entries.forEach { entry ->
            cardsContainer.addView(
                historyEntryView(entry),
                fullWidthParams(bottom = 8)
            )
        }
    }

    private fun renderRecentSection() {
        if (
            activeSection != RECENT_SECTION &&
            searchQuery.isBlank()
        ) {
            return
        }

        val role = currentUser?.role ?: return
        val entries = libraryStore.recent(role)
            .filter { matchesRecentEntry(it) }

        if (entries.isEmpty() && activeSection != RECENT_SECTION) return

        addSectionTitle(RECENT_SECTION)

        if (entries.isEmpty()) {
            cardsContainer.addView(
                TextView(this).apply {
                    text = "Недавно просмотренных карточек пока нет"
                    setPadding(18, 10, 18, 10)
                },
                fullWidthParams()
            )
            return
        }

        cardsContainer.addView(
            Button(this).apply {
                text = "Очистить недавно просмотренные"
                isAllCaps = false
                setOnClickListener {
                    libraryStore.clearRecent(role)
                    currentUser?.let { render(it) }
                }
            },
            fullWidthParams(bottom = 8)
        )

        entries.forEach { entry ->
            cardsContainer.addView(
                recentEntryButton(entry),
                fullWidthParams(bottom = 8)
            )
        }
    }

    private fun recentEntryButton(entry: DashboardLibraryEntry): Button =
        Button(this).apply {
            text = buildString {
                if (libraryStore.isFavorite(entry.role, entry.key)) append("★ ")
                append(entry.title)
                if (entry.badge.isNotBlank()) append(" · ${entry.badge}")
                append("\n${entry.description}")
                append("\nПросмотрено ${formatTimestamp(entry.viewedAtMillis)}")
            }
            isAllCaps = false
            setTextColor(Color.DKGRAY)
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                AlertDialog.Builder(this@BaseDashboardActivity)
                    .setTitle(entry.title)
                    .setMessage(entry.details)
                    .setNegativeButton("Закрыть", null)
                    .setNeutralButton("Поделиться") { _, _ ->
                        shareDashboardItem(
                            entry.title,
                            entry.description,
                            entry.details
                        )
                    }
                    .setPositiveButton("Готово", null)
                    .show()
            }
            setOnLongClickListener {
                toggleFavorite(entry.key, entry.title, entry.role)
                true
            }
        }

    private fun historyEntryView(entry: ActionHistoryEntry): TextView =
        TextView(this).apply {
            val marker = if (entry.successful) "✓" else "!"
            text = "$marker ${entry.title}\n" +
                "${formatTimestamp(entry.timestampMillis)} · ${entry.message}"
            textSize = 15f
            setTextColor(if (entry.successful) Color.DKGRAY else Color.RED)
            setPadding(18, 12, 18, 12)
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
            val key = liveFavoriteKey(card)
            val role = currentUser?.role.orEmpty()
            text = buildString {
                if (libraryStore.isFavorite(role, key)) append("★ ")
                append(card.title)
                if (card.badge.isNotBlank()) append(" · ${card.badge}")
                append("\n${card.description}")
                if (showingCachedData) append("\nСохранённая копия · действия отключены")
            }
            isAllCaps = false
            setTextColor(Color.DKGRAY)
            setPadding(16, 18, 16, 18)
            setOnClickListener { openLiveCard(card) }
            setOnLongClickListener {
                toggleFavorite(key, card.title, role)
                true
            }
        }

    private fun openLiveCard(card: LiveDashboardCard) {
        currentUser?.let { user ->
            libraryStore.recordRecent(
                user.role,
                DashboardLibraryEntry(
                    key = liveFavoriteKey(card),
                    role = user.role,
                    title = card.title,
                    description = card.description,
                    details = card.details,
                    section = card.section,
                    badge = card.badge,
                    viewedAtMillis = System.currentTimeMillis()
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(card.title)
            .setMessage(card.details)
            .setNegativeButton("Закрыть", null)
            .setNeutralButton("Поделиться") { _, _ ->
                shareDashboardItem(
                    card.title,
                    card.description,
                    card.details
                )
            }

        if (
            !showingCachedData &&
            !card.actionId.isNullOrBlank() &&
            card.actionLabel.isNotBlank()
        ) {
            dialog.setPositiveButton(card.actionLabel) { _, _ ->
                prepareLiveAction(card)
            }
        } else {
            dialog.setPositiveButton("Готово", null)
        }

        dialog.show()
    }

    private fun prepareLiveAction(card: LiveDashboardCard) {
        when {
            card.form != null -> openLiveActionForm(card)
            card.confirmationMessage.isNotBlank() -> {
                AlertDialog.Builder(this)
                    .setTitle(card.actionLabel)
                    .setMessage(card.confirmationMessage)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Подтвердить") { _, _ ->
                        performLiveAction(card, emptyMap())
                    }
                    .show()
            }
            else -> performLiveAction(card, emptyMap())
        }
    }

    private fun openLiveActionForm(card: LiveDashboardCard) {
        val form = card.form ?: return
        val entries = linkedMapOf<String, EditText>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 8)
        }

        form.fields.forEach { field ->
            content.addView(
                TextView(this).apply {
                    text = field.label
                    textSize = 15f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, 12, 0, 4)
                },
                fullWidthParams()
            )

            val entry = EditText(this).apply {
                hint = field.hint
                setText(field.defaultValue)
                inputType = inputTypeFor(field.type)
                if (field.type == LiveFormFieldType.MULTILINE) {
                    minLines = 3
                    maxLines = 6
                }
            }

            entries[field.key] = entry
            content.addView(entry, fullWidthParams())
        }

        val scroll = ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(form.title)
            .setView(scroll)
            .setNegativeButton("Отмена", null)
            .setPositiveButton(form.submitLabel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = entries.mapValues { it.value.text.toString().trim() }
                val error = validateLiveForm(form, values)

                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    performLiveAction(card, values)
                }
            }
        }

        dialog.show()
    }

    private fun inputTypeFor(type: LiveFormFieldType): Int = when (type) {
        LiveFormFieldType.TEXT ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        LiveFormFieldType.MULTILINE ->
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE

        LiveFormFieldType.DECIMAL ->
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        LiveFormFieldType.INTEGER ->
            InputType.TYPE_CLASS_NUMBER
    }

    private fun validateLiveForm(
        form: LiveActionForm,
        values: Map<String, String>
    ): String? {
        form.fields.forEach { field ->
            val value = values[field.key].orEmpty()

            if (field.required && value.isBlank()) {
                return "Заполните поле «${field.label}»"
            }

            if (value.isNotBlank() && field.type == LiveFormFieldType.DECIMAL) {
                val number = value.replace(",", ".").toDoubleOrNull()
                    ?: return "Поле «${field.label}» должно быть числом"

                if (field.minimumValue != null && number < field.minimumValue) {
                    return "Поле «${field.label}» должно быть не меньше " +
                        field.minimumValue
                }
            }

            if (value.isNotBlank() && field.type == LiveFormFieldType.INTEGER) {
                val number = value.toIntOrNull()
                    ?: return "Поле «${field.label}» должно быть целым числом"

                if (
                    field.minimumValue != null &&
                    number.toDouble() < field.minimumValue
                ) {
                    return "Поле «${field.label}» должно быть не меньше " +
                        field.minimumValue.toInt()
                }
            }
        }

        return null
    }

    private fun performLiveAction(
        card: LiveDashboardCard,
        values: Map<String, String>
    ) {
        val repository = workflowRepository ?: return
        val token = sessionStore.token() ?: return
        val user = currentUser ?: return

        liveLoading = true
        liveMessage = "Выполняется действие…"
        render(user)

        executor.execute {
            val result = repository.perform(token, user, card, values)
            actionHistory.add(card.title, result)

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
            val key = demoFavoriteKey(card)
            val role = currentUser?.role.orEmpty()
            text = buildString {
                if (libraryStore.isFavorite(role, key)) append("★ ")
                append(card.title)
                if (card.badge.isNotBlank()) append(" · ${card.badge}")
                append(progress)
                append("\n${card.description}")
            }
            isAllCaps = false
            setTextColor(Color.DKGRAY)
            setPadding(16, 18, 16, 18)
            setOnClickListener { openScenario(card) }
            setOnLongClickListener {
                toggleFavorite(key, card.title, role)
                true
            }
        }
    }

    private fun openScenario(card: DashboardCard) {
        currentUser?.let { user ->
            libraryStore.recordRecent(
                user.role,
                DashboardLibraryEntry(
                    key = demoFavoriteKey(card),
                    role = user.role,
                    title = card.title,
                    description = card.description,
                    details = card.actionMessage,
                    section = card.section,
                    badge = card.badge,
                    viewedAtMillis = System.currentTimeMillis()
                )
            )
        }

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

    private fun restoreCachedLiveData(user: ApiUser) {
        val cached = liveCache.load(user.role) ?: return
        livePayload = cached.payload
        showingCachedData = true
        liveMessage = "сохранено ${formatTimestamp(cached.savedAtMillis)}"
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
                    liveCache.save(user.role, payload)
                    showingCachedData = false
                    liveMessage = payload.message.ifBlank {
                        "Данные сервера обновлены"
                    }
                } else {
                    val cached = liveCache.load(user.role)
                    if (cached != null) {
                        livePayload = cached.payload
                        showingCachedData = true
                        liveMessage = "${result.message} · кэш " +
                            formatTimestamp(cached.savedAtMillis)
                    } else {
                        liveMessage = result.message
                    }
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

    private fun availableSections(user: ApiUser): List<String> {
        val sections = linkedSetOf(
            ALL_SECTIONS,
            SERVER_SECTION,
            HISTORY_SECTION,
            RECENT_SECTION
        )

        livePayload?.cards?.forEach { sections += it.section }
        dashboardCards(user).forEach { sections += it.section }

        return sections.toList()
    }

    private fun sectionVisible(section: String): Boolean =
        activeSection == ALL_SECTIONS || activeSection == section

    private fun matchesLiveCard(card: LiveDashboardCard): Boolean {
        if (!sectionVisible(card.section) && activeSection != SERVER_SECTION) return false
        if (actionableOnly && card.actionId.isNullOrBlank()) return false
        if (
            favoritesOnly &&
            !libraryStore.isFavorite(
                currentUser?.role.orEmpty(),
                liveFavoriteKey(card)
            )
        ) {
            return false
        }

        return matchesQuery(
            card.title,
            card.description,
            card.details,
            card.section,
            card.badge
        )
    }

    private fun matchesDashboardCard(card: DashboardCard): Boolean {
        if (!sectionVisible(card.section)) return false
        if (actionableOnly && card.steps.isEmpty()) return false
        if (
            favoritesOnly &&
            !libraryStore.isFavorite(
                currentUser?.role.orEmpty(),
                demoFavoriteKey(card)
            )
        ) {
            return false
        }

        return matchesQuery(
            card.title,
            card.description,
            card.actionMessage,
            card.section,
            card.badge
        )
    }

    private fun matchesHistoryEntry(entry: ActionHistoryEntry): Boolean {
        if (activeSection != ALL_SECTIONS && activeSection != HISTORY_SECTION) {
            return false
        }

        return matchesQuery(entry.title, entry.message)
    }

    private fun matchesRecentEntry(entry: DashboardLibraryEntry): Boolean {
        if (activeSection != ALL_SECTIONS && activeSection != RECENT_SECTION) {
            return false
        }
        if (actionableOnly) return false
        if (favoritesOnly && !libraryStore.isFavorite(entry.role, entry.key)) {
            return false
        }

        return matchesQuery(
            entry.title,
            entry.description,
            entry.details,
            entry.section,
            entry.badge
        )
    }

    private fun matchesQuery(vararg values: String): Boolean {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        if (query.isBlank()) return true

        return values.any { value ->
            value.lowercase(Locale.getDefault()).contains(query)
        }
    }

    private fun showOverviewMetrics(): Boolean =
        activeSection == ALL_SECTIONS &&
            searchQuery.isBlank() &&
            !actionableOnly &&
            !favoritesOnly

    private fun hasVisibleCards(user: ApiUser): Boolean {
        val hasLive = sectionVisible(SERVER_SECTION) &&
            livePayload?.cards?.any { matchesLiveCard(it) } == true
        val hasHistory = sectionVisible(HISTORY_SECTION) &&
            actionHistory.entries().any { matchesHistoryEntry(it) }
        val hasRecent = sectionVisible(RECENT_SECTION) &&
            libraryStore.recent(user.role).any { matchesRecentEntry(it) }
        val hasDemo = dashboardCards(user).any { matchesDashboardCard(it) }

        return hasLive || hasHistory || hasRecent || hasDemo
    }

    private fun toggleFavorite(key: String, title: String, role: String) {
        val favorite = libraryStore.toggleFavorite(role, key)
        Toast.makeText(
            this,
            if (favorite) "Добавлено в избранное: $title"
            else "Удалено из избранного: $title",
            Toast.LENGTH_SHORT
        ).show()
        currentUser?.let { render(it) }
    }

    private fun liveFavoriteKey(card: LiveDashboardCard): String =
        "live:${card.id}"

    private fun demoFavoriteKey(card: DashboardCard): String =
        "demo:${card.id}"

    private fun shareDashboardItem(
        title: String,
        description: String,
        details: String
    ) {
        val text = buildString {
            append(title)
            if (description.isNotBlank()) append("\n$description")
            if (details.isNotBlank()) append("\n\n$details")
            append("\n\n${productConfig.productName}")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }

        startActivity(Intent.createChooser(intent, "Поделиться карточкой"))
    }

    private fun formatTimestamp(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "неизвестное время"
        return SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(timestampMillis))
    }

    private fun fullWidthParams(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = bottom
        }

    private fun weightedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

    private fun logout() {
        val token = sessionStore.token()
        sessionStore.clear()
        if (token != null) {
            executor.execute { authRepository.logout(token) }
        }
        returnToAuth()
        finish()
    }

    private companion object {
        const val ALL_SECTIONS = "Все"
        const val SERVER_SECTION = "Данные сервера"
        const val HISTORY_SECTION = "История действий"
        const val RECENT_SECTION = "Недавние"
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
