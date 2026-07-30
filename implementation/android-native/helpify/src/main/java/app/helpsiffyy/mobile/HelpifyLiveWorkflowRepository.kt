package app.helpsiffyy.mobile

import app.shared.core.ApiClient
import app.shared.core.ApiResult
import app.shared.core.ApiUser
import app.shared.core.DashboardMetric
import app.shared.core.LiveActionForm
import app.shared.core.LiveDashboardCard
import app.shared.core.LiveDashboardPayload
import app.shared.core.LiveFormField
import app.shared.core.LiveFormFieldType
import app.shared.core.LiveWorkflowRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class HelpifyLiveWorkflowRepository(baseUrl: String) : LiveWorkflowRepository {
    private val api = ApiClient(baseUrl)

    override fun load(
        token: String,
        user: ApiUser
    ): Pair<ApiResult, LiveDashboardPayload?> {
        val result = api.get("/work/tasks", token)
        if (!result.successful) return result to null

        val tasks = result.body?.optJSONArray("tasks") ?: JSONArray()
        val cards = mutableListOf<LiveDashboardCard>()

        if (user.role == "customer") {
            cards += createTaskCard()
            cards += LiveDashboardCard(
                id = "helpify-seed-task-pack",
                title = "Создать набор из 6 задач",
                description = "Наполнить Helpify разнообразными серверными задачами",
                details = "Будут созданы задачи по сантехнике, электрике, уборке, " +
                    "сборке мебели, ремонту техники и доставке.",
                section = "Быстрые действия",
                badge = "Демо-данные",
                actionId = "seed-task-pack",
                actionLabel = "Создать набор",
                confirmationMessage = "Создать шесть новых задач в рабочей базе?"
            )
        }

        val openTaskIds = mutableListOf<Long>()
        var open = 0
        var assigned = 0
        var completed = 0
        var offersTotal = 0
        var firstAssignedTaskId: Long? = null

        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val id = task.optLong("id")
            val title = task.optString("title", "Задача #$id")
            val category = task.optString("category", "Без категории")
            val address = task.optString("address")
            val description = task.optString("description")
            val status = task.optString("status", "open")
            val budget = task.optDouble("budget", 0.0)
            val customerName = task.optString("customerName", "Заказчик")
            val offers = task.optJSONArray("offers") ?: JSONArray()

            offersTotal += offers.length()
            when (status) {
                "open" -> {
                    open++
                    openTaskIds += id
                }
                "assigned" -> {
                    assigned++
                    if (firstAssignedTaskId == null) firstAssignedTaskId = id
                }
                "completed" -> completed++
            }

            var actionId: String? = null
            var actionLabel = ""
            var form: LiveActionForm? = null
            var confirmationMessage = ""

            if (user.role == "customer" && status == "open" && offers.length() > 0) {
                val cheapest = cheapestOffer(offers)
                if (cheapest != null) {
                    actionId = "select-offer:$id:${cheapest.optLong("id")}"
                    actionLabel = "Выбрать предложение"
                    confirmationMessage =
                        "Назначить исполнителя с минимальной стоимостью?"
                }
            } else if (user.role == "contractor" && status == "open") {
                val ownOffer = findOfferByContractor(offers, user.id)
                if (ownOffer == null) {
                    val suggestedPrice = if (budget > 0) budget * 0.90 else 50.0
                    actionId = "create-offer:$id"
                    actionLabel = "Отправить предложение"
                    form = offerForm(suggestedPrice)
                }
            } else if (status == "assigned") {
                actionId = "complete-task:$id"
                actionLabel = "Завершить задачу"
                confirmationMessage = "Перевести задачу в статус «Завершена»?"
            }

            val offerRows = mutableListOf<String>()
            for (offerIndex in 0 until offers.length()) {
                val offer = offers.optJSONObject(offerIndex) ?: continue
                offerRows += buildString {
                    append(offer.optString("name", "Исполнитель"))
                    append(" · €${formatAmount(offer.optDouble("price", 0.0))}")
                    append(" · рейтинг ${offer.optDouble("rating", 0.0)}")
                    append(" · ${offer.optString("status", "pending")}")
                }
            }

            cards += LiveDashboardCard(
                id = "helpify-task-$id",
                title = title,
                description = "$category · €${formatAmount(budget)} · ${statusLabel(status)}",
                details = buildString {
                    append("Заказчик: $customerName\n")
                    append("Адрес: $address\n")
                    append("Статус: ${statusLabel(status)}\n\n")
                    append(description)
                    if (offerRows.isNotEmpty()) {
                        append("\n\nПредложения:\n")
                        append(offerRows.joinToString("\n"))
                    }
                },
                section = "Задачи из API",
                badge = statusLabel(status),
                actionId = actionId,
                actionLabel = actionLabel,
                form = form,
                confirmationMessage = confirmationMessage
            )
        }

        if (user.role == "contractor" && openTaskIds.isNotEmpty()) {
            cards.add(
                0,
                LiveDashboardCard(
                    id = "helpify-seed-offers",
                    title = "Предложения для открытых задач",
                    description = "Отправить до трёх предложений одним действием",
                    details = "Для первых открытых задач будут созданы предложения " +
                        "с разной стоимостью и расстоянием.",
                    section = "Быстрые действия",
                    badge = "Демо-данные",
                    actionId = "seed-offers:${openTaskIds.take(3).joinToString(",")}",
                    actionLabel = "Отправить набор",
                    confirmationMessage =
                        "Создать предложения для открытых задач?"
                )
            )
        }

        if (firstAssignedTaskId != null) {
            cards.add(
                0,
                LiveDashboardCard(
                    id = "helpify-send-message",
                    title = "Сообщение по активной задаче",
                    description = "Отправить собственный текст через серверный чат",
                    details = "Сообщение будет сохранено в контексте активной задачи.",
                    section = "Быстрые действия",
                    badge = "Чат",
                    actionId = "send-message:$firstAssignedTaskId",
                    actionLabel = "Написать",
                    form = messageForm(
                        "Здравствуйте! Уточняю детали по активной задаче."
                    )
                )
            )
        }

        val metrics = listOf(
            DashboardMetric("открытых задач в API", open.toString()),
            DashboardMetric("назначенных задач", assigned.toString()),
            DashboardMetric("завершённых задач", completed.toString()),
            DashboardMetric("предложений", offersTotal.toString())
        )

        return result to LiveDashboardPayload(
            metrics = metrics,
            cards = cards,
            message = "Получено задач: ${tasks.length()}"
        )
    }

    override fun perform(
        token: String,
        user: ApiUser,
        card: LiveDashboardCard,
        values: Map<String, String>
    ): ApiResult {
        val action = card.actionId ?: return localError("Действие отсутствует")
        val parts = action.split(":")

        return when (parts.firstOrNull()) {
            "create-task" -> api.post(
                "/work/tasks",
                JSONObject()
                    .put("title", values["title"].orEmpty())
                    .put("category", values["category"].orEmpty())
                    .put("address", values["address"].orEmpty())
                    .put("description", values["description"].orEmpty())
                    .put("budget", decimal(values["budget"], 50.0)),
                token
            )

            "seed-task-pack" -> createTaskPack(token)

            "create-offer" -> {
                if (parts.size < 2) return localError("Задача не найдена")
                api.post(
                    "/work/tasks/${parts[1]}/offers",
                    JSONObject()
                        .put("price", decimal(values["price"], 50.0))
                        .put("distance", values["distance"].orEmpty()),
                    token
                )
            }

            "seed-offers" -> {
                if (parts.size < 2) return localError("Задачи не найдены")
                createOfferPack(token, parts[1])
            }

            "select-offer" -> {
                if (parts.size < 3) return localError("Предложение не найдено")
                api.post(
                    "/work/tasks/${parts[1]}/offers/${parts[2]}/select",
                    JSONObject(),
                    token
                )
            }

            "complete-task" -> {
                if (parts.size < 2) return localError("Задача не найдена")
                api.post(
                    "/work/tasks/${parts[1]}/status",
                    JSONObject().put("status", "completed"),
                    token
                )
            }

            "send-message" -> {
                if (parts.size < 2) return localError("Задача не найдена")
                api.post(
                    "/work/tasks/${parts[1]}/messages",
                    JSONObject().put("text", values["text"].orEmpty()),
                    token
                )
            }

            else -> localError("Неизвестное действие")
        }
    }

    private fun createTaskCard(): LiveDashboardCard = LiveDashboardCard(
        id = "helpify-create-task",
        title = "Создать задачу на сервере",
        description = "Заполнить данные новой задачи Helpify",
        details = "Форма отправит задачу в рабочую базу через API.",
        section = "Быстрые действия",
        badge = "Новая задача",
        actionId = "create-task",
        actionLabel = "Заполнить",
        form = LiveActionForm(
            title = "Новая задача",
            submitLabel = "Создать",
            fields = listOf(
                LiveFormField(
                    key = "title",
                    label = "Название",
                    defaultValue = "Диагностика сантехники",
                    hint = "Кратко опишите задачу"
                ),
                LiveFormField(
                    key = "category",
                    label = "Категория",
                    defaultValue = "Сантехника"
                ),
                LiveFormField(
                    key = "address",
                    label = "Адрес",
                    defaultValue = "ул. Центральная, 12"
                ),
                LiveFormField(
                    key = "description",
                    label = "Описание",
                    type = LiveFormFieldType.MULTILINE,
                    defaultValue =
                        "Проверить смеситель, соединения и давление воды."
                ),
                LiveFormField(
                    key = "budget",
                    label = "Бюджет",
                    type = LiveFormFieldType.DECIMAL,
                    defaultValue = "65",
                    minimumValue = 1.0
                )
            )
        )
    )

    private fun offerForm(suggestedPrice: Double): LiveActionForm =
        LiveActionForm(
            title = "Новое предложение",
            submitLabel = "Отправить",
            fields = listOf(
                LiveFormField(
                    key = "price",
                    label = "Стоимость",
                    type = LiveFormFieldType.DECIMAL,
                    defaultValue = formatAmount(suggestedPrice),
                    minimumValue = 1.0
                ),
                LiveFormField(
                    key = "distance",
                    label = "Расстояние или время прибытия",
                    defaultValue = "2,5 км · 20 минут"
                )
            )
        )

    private fun messageForm(defaultText: String): LiveActionForm =
        LiveActionForm(
            title = "Сообщение",
            submitLabel = "Отправить",
            fields = listOf(
                LiveFormField(
                    key = "text",
                    label = "Текст сообщения",
                    type = LiveFormFieldType.MULTILINE,
                    defaultValue = defaultText
                )
            )
        )

    private fun createTaskPack(token: String): ApiResult {
        val tasks = listOf(
            listOf(
                "Устранить протечку под раковиной",
                "Сантехника",
                "ул. Озёрная, 7",
                "Проверить сифон и заменить уплотнения.",
                55.0
            ),
            listOf(
                "Установить два светильника",
                "Электрика",
                "пр. Мира, 41",
                "Монтаж потолочных светильников в гостиной.",
                90.0
            ),
            listOf(
                "Генеральная уборка квартиры",
                "Уборка",
                "ул. Парковая, 18",
                "Квартира 72 м², включая кухню и окна.",
                120.0
            ),
            listOf(
                "Собрать книжный шкаф",
                "Мебель",
                "ул. Садовая, 9",
                "Шкаф в упаковке, требуется сборка и крепление.",
                75.0
            ),
            listOf(
                "Диагностика стиральной машины",
                "Бытовая техника",
                "ул. Новая, 33",
                "Машина не завершает цикл отжима.",
                80.0
            ),
            listOf(
                "Доставить коробки на склад",
                "Доставка",
                "ул. Лесная, 5",
                "Шесть коробок, общий вес около 45 кг.",
                65.0
            )
        )

        var last: ApiResult? = null
        tasks.forEach { row ->
            last = api.post(
                "/work/tasks",
                JSONObject()
                    .put("title", row[0] as String)
                    .put("category", row[1] as String)
                    .put("address", row[2] as String)
                    .put("description", row[3] as String)
                    .put("budget", row[4] as Double),
                token
            )
            if (last?.successful != true) return last!!
        }

        return localSuccess("Создано задач: ${tasks.size}")
    }

    private fun createOfferPack(token: String, ids: String): ApiResult {
        val taskIds = ids.split(",").filter { it.isNotBlank() }
        var created = 0

        taskIds.forEachIndexed { index, taskId ->
            val result = api.post(
                "/work/tasks/$taskId/offers",
                JSONObject()
                    .put("price", 48.0 + index * 14.0)
                    .put("distance", "${index + 2},${index + 1} км"),
                token
            )
            if (!result.successful) return result
            created++
        }

        return localSuccess("Создано предложений: $created")
    }

    private fun cheapestOffer(offers: JSONArray): JSONObject? {
        var selected: JSONObject? = null
        var price = Double.MAX_VALUE

        for (index in 0 until offers.length()) {
            val offer = offers.optJSONObject(index) ?: continue
            val candidate = offer.optDouble("price", Double.MAX_VALUE)
            if (candidate < price) {
                price = candidate
                selected = offer
            }
        }

        return selected
    }

    private fun findOfferByContractor(
        offers: JSONArray,
        contractorId: Long
    ): JSONObject? {
        for (index in 0 until offers.length()) {
            val offer = offers.optJSONObject(index) ?: continue
            if (offer.optLong("contractorId") == contractorId) return offer
        }
        return null
    }

    private fun decimal(value: String?, fallback: Double): Double =
        value.orEmpty().replace(",", ".").toDoubleOrNull() ?: fallback

    private fun statusLabel(status: String): String = when (status) {
        "open" -> "Открыта"
        "assigned" -> "Назначена"
        "completed" -> "Завершена"
        else -> status
    }

    private fun formatAmount(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun localError(message: String): ApiResult =
        ApiResult(false, 0, null, message)

    private fun localSuccess(message: String): ApiResult =
        ApiResult(true, 200, JSONObject().put("message", message), message)
}
