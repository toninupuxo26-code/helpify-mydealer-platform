package app.helpsiffyy.mobile

import app.shared.core.ApiClient
import app.shared.core.ApiResult
import app.shared.core.ApiUser
import app.shared.core.DashboardMetric
import app.shared.core.LiveDashboardCard
import app.shared.core.LiveDashboardPayload
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
            cards += LiveDashboardCard(
                id = "helpify-create-task",
                title = "Создать задачу на сервере",
                description = "Добавить новую задачу в рабочую базу Helpify",
                details = "Будет создана тестовая задача по обслуживанию квартиры. " +
                    "После создания она появится у заказчика и в ленте исполнителя.",
                section = "Быстрые действия",
                badge = "POST /work/tasks",
                actionId = "create-task",
                actionLabel = "Создать"
            )
        }

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
                "open" -> open++
                "assigned" -> {
                    assigned++
                    if (firstAssignedTaskId == null) firstAssignedTaskId = id
                }
                "completed" -> completed++
            }

            var actionId: String? = null
            var actionLabel = ""

            if (user.role == "customer" && status == "open" && offers.length() > 0) {
                val cheapest = cheapestOffer(offers)
                if (cheapest != null) {
                    actionId = "select-offer:$id:${cheapest.optLong("id")}"
                    actionLabel = "Выбрать предложение"
                }
            } else if (user.role == "contractor" && status == "open") {
                val ownOffer = findOfferByContractor(offers, user.id)
                if (ownOffer == null) {
                    val suggestedPrice = if (budget > 0) budget * 0.90 else 50.0
                    actionId = "create-offer:$id:${formatAmount(suggestedPrice)}"
                    actionLabel = "Отправить предложение"
                }
            } else if (status == "assigned") {
                actionId = "complete-task:$id"
                actionLabel = "Завершить задачу"
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
                actionLabel = actionLabel
            )
        }

        if (firstAssignedTaskId != null) {
            cards.add(
                0,
                LiveDashboardCard(
                    id = "helpify-send-message",
                    title = "Сообщение по активной задаче",
                    description = "Отправить сообщение через серверный чат",
                    details = "Сообщение будет записано в task_messages и станет доступно " +
                        "обоим участникам активной задачи.",
                    section = "Быстрые действия",
                    badge = "Чат",
                    actionId = "send-message:$firstAssignedTaskId",
                    actionLabel = "Отправить"
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
        card: LiveDashboardCard
    ): ApiResult {
        val action = card.actionId ?: return localError("Действие отсутствует")
        val parts = action.split(":")

        return when (parts.firstOrNull()) {
            "create-task" -> {
                val suffix = (System.currentTimeMillis() / 1000L) % 100000L
                api.post(
                    "/work/tasks",
                    JSONObject()
                        .put("title", "Диагностика сантехники #$suffix")
                        .put("category", "Сантехника")
                        .put("address", "ул. Центральная, 12")
                        .put(
                            "description",
                            "Проверить смеситель, соединения и давление воды. " +
                                "Задача создана из Android-клиента."
                        )
                        .put("budget", 65),
                    token
                )
            }

            "create-offer" -> {
                if (parts.size < 3) return localError("Некорректное предложение")
                api.post(
                    "/work/tasks/${parts[1]}/offers",
                    JSONObject()
                        .put("price", parts[2].toDoubleOrNull() ?: 50.0)
                        .put("distance", "2,5 км"),
                    token
                )
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
                    JSONObject().put(
                        "text",
                        "Сообщение отправлено из Android-клиента Helpify."
                    ),
                    token
                )
            }

            else -> localError("Неизвестное действие")
        }
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
}
