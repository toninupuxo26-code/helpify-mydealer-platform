package app.mydealers.mobile

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

class MyDealerLiveWorkflowRepository(baseUrl: String) : LiveWorkflowRepository {
    private val api = ApiClient(baseUrl)

    override fun load(
        token: String,
        user: ApiUser
    ): Pair<ApiResult, LiveDashboardPayload?> {
        val productsResult = api.get("/market/products", token)
        if (!productsResult.successful) return productsResult to null

        val ordersResult = api.get("/market/orders", token)
        if (!ordersResult.successful) return ordersResult to null

        val cartResult = if (user.role == "buyer") {
            api.get("/market/cart", token)
        } else {
            null
        }

        if (cartResult != null && !cartResult.successful) return cartResult to null

        val products = productsResult.body?.optJSONArray("products") ?: JSONArray()
        val orders = ordersResult.body?.optJSONArray("orders") ?: JSONArray()
        val cartItems = cartResult?.body?.optJSONArray("items") ?: JSONArray()
        val cartTotal = cartResult?.body?.optDouble("total", 0.0) ?: 0.0
        val cards = mutableListOf<LiveDashboardCard>()

        if (user.role == "vendor") {
            cards += LiveDashboardCard(
                id = "mydealer-create-product",
                title = "Создать товар на сервере",
                description = "Добавить карточку со статусом модерации",
                details = "Будет создан новый товар с названием, категорией, ценой, " +
                    "единицей измерения и описанием.",
                section = "Быстрые действия",
                badge = "POST /market/products",
                actionId = "create-product",
                actionLabel = "Создать"
            )
        } else if (cartItems.length() > 0) {
            cards += LiveDashboardCard(
                id = "mydealer-checkout",
                title = "Оформить корзину",
                description = "${cartItems.length()} поз. · €${formatAmount(cartTotal)}",
                details = cartDetails(cartItems, cartTotal),
                section = "Быстрые действия",
                badge = "Корзина",
                actionId = "checkout",
                actionLabel = "Оформить"
            )
        }

        var published = 0
        var moderation = 0

        for (index in 0 until products.length()) {
            val product = products.optJSONObject(index) ?: continue
            val id = product.optLong("id")
            val name = product.optString("name", "Товар #$id")
            val category = product.optString("category", "Без категории")
            val vendor = product.optString("vendorName", "Вендор")
            val description = product.optString("description")
            val unit = product.optString("unit")
            val emoji = product.optString("emoji", "🌿")
            val status = product.optString("status", "moderation")
            val price = product.optDouble("price", 0.0)

            if (status == "published") published++ else moderation++

            val actionId: String?
            val actionLabel: String

            if (user.role == "buyer" && status == "published") {
                actionId = "add-cart:$id"
                actionLabel = "В корзину"
            } else if (user.role == "vendor" && status == "moderation") {
                actionId = "publish-product:$id"
                actionLabel = "Опубликовать"
            } else {
                actionId = null
                actionLabel = ""
            }

            cards += LiveDashboardCard(
                id = "mydealer-product-$id",
                title = "$emoji $name",
                description = "$category · €${formatAmount(price)} / $unit",
                details = buildString {
                    append("Вендор: $vendor\n")
                    append("Статус: ${statusLabel(status)}\n")
                    append("Цена: €${formatAmount(price)} / $unit\n\n")
                    append(description)
                },
                section = "Товары из API",
                badge = statusLabel(status),
                actionId = actionId,
                actionLabel = actionLabel
            )
        }

        var newOrders = 0
        var confirmedOrders = 0
        var completedOrders = 0

        for (index in 0 until orders.length()) {
            val order = orders.optJSONObject(index) ?: continue
            val id = order.optLong("id")
            val status = order.optString("status", "new")
            val total = order.optDouble("total", 0.0)
            val buyer = order.optString("buyerName", "Покупатель")
            val vendor = order.optString("vendorName", "Вендор")
            val items = order.optJSONArray("items") ?: JSONArray()

            when (status) {
                "new" -> newOrders++
                "confirmed" -> confirmedOrders++
                "completed" -> completedOrders++
            }

            val actionId: String?
            val actionLabel: String

            if (user.role == "vendor" && status == "new") {
                actionId = "order-status:$id:confirmed"
                actionLabel = "Подтвердить"
            } else if (user.role == "vendor" && status == "confirmed") {
                actionId = "order-status:$id:completed"
                actionLabel = "Завершить"
            } else if (user.role == "buyer" && status != "completed") {
                actionId = "send-message:$id"
                actionLabel = "Написать вендору"
            } else {
                actionId = null
                actionLabel = ""
            }

            cards += LiveDashboardCard(
                id = "mydealer-order-$id",
                title = "Заказ MD-$id",
                description = "${items.length()} поз. · €${formatAmount(total)} · " +
                    statusLabel(status),
                details = buildString {
                    append("Покупатель: $buyer\n")
                    append("Вендор: $vendor\n")
                    append("Статус: ${statusLabel(status)}\n")
                    append("Сумма: €${formatAmount(total)}\n\n")
                    append(orderItemsDetails(items))
                },
                section = "Заказы из API",
                badge = statusLabel(status),
                actionId = actionId,
                actionLabel = actionLabel
            )
        }

        val metrics = if (user.role == "buyer") {
            listOf(
                DashboardMetric("опубликованных товаров", published.toString()),
                DashboardMetric("позиций в корзине", cartItems.length().toString()),
                DashboardMetric("сумма корзины", "€${formatAmount(cartTotal)}"),
                DashboardMetric("заказов", orders.length().toString())
            )
        } else {
            listOf(
                DashboardMetric("опубликованных товаров", published.toString()),
                DashboardMetric("товаров на модерации", moderation.toString()),
                DashboardMetric("новых заказов", newOrders.toString()),
                DashboardMetric(
                    "в работе / завершено",
                    "$confirmedOrders / $completedOrders"
                )
            )
        }

        return productsResult to LiveDashboardPayload(
            metrics = metrics,
            cards = cards,
            message = "Товаров: ${products.length()}, заказов: ${orders.length()}"
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
            "create-product" -> {
                val suffix = (System.currentTimeMillis() / 1000L) % 100000L
                api.post(
                    "/market/products",
                    JSONObject()
                        .put("name", "Фермерский набор #$suffix")
                        .put("category", "Фермерские продукты")
                        .put("price", 24.90)
                        .put("unit", "1 набор")
                        .put("emoji", "🧺")
                        .put(
                            "description",
                            "Набор сезонных продуктов. Карточка создана " +
                                "из Android-клиента MyDealer."
                        ),
                    token
                )
            }

            "publish-product" -> {
                if (parts.size < 2) return localError("Товар не найден")
                api.post(
                    "/market/products/${parts[1]}/publish",
                    JSONObject(),
                    token
                )
            }

            "add-cart" -> {
                if (parts.size < 2) return localError("Товар не найден")
                api.post(
                    "/market/cart/items",
                    JSONObject()
                        .put("product_id", parts[1].toLongOrNull() ?: 0L)
                        .put("quantity", 1),
                    token
                )
            }

            "checkout" -> api.post(
                "/market/orders/checkout",
                JSONObject(),
                token
            )

            "order-status" -> {
                if (parts.size < 3) return localError("Заказ не найден")
                api.post(
                    "/market/orders/${parts[1]}/status",
                    JSONObject().put("status", parts[2]),
                    token
                )
            }

            "send-message" -> {
                if (parts.size < 2) return localError("Заказ не найден")
                api.post(
                    "/market/orders/${parts[1]}/messages",
                    JSONObject().put(
                        "text",
                        "Сообщение отправлено из Android-клиента MyDealer."
                    ),
                    token
                )
            }

            else -> localError("Неизвестное действие")
        }
    }

    private fun cartDetails(items: JSONArray, total: Double): String {
        val rows = mutableListOf<String>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val product = item.optJSONObject("product") ?: JSONObject()
            rows += "${product.optString("name", "Товар")} × " +
                "${item.optInt("quantity", 1)} · " +
                "€${formatAmount(item.optDouble("lineTotal", 0.0))}"
        }
        return rows.joinToString("\n") + "\n\nИтого: €${formatAmount(total)}"
    }

    private fun orderItemsDetails(items: JSONArray): String {
        val rows = mutableListOf<String>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            rows += "${item.optString("name", "Товар")} × " +
                "${item.optInt("quantity", 1)} · " +
                "€${formatAmount(item.optDouble("lineTotal", 0.0))}"
        }
        return if (rows.isEmpty()) "Позиции отсутствуют" else rows.joinToString("\n")
    }

    private fun statusLabel(status: String): String = when (status) {
        "published" -> "Опубликован"
        "moderation" -> "На модерации"
        "new" -> "Новый"
        "confirmed" -> "Подтверждён"
        "completed" -> "Завершён"
        else -> status
    }

    private fun formatAmount(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun localError(message: String): ApiResult =
        ApiResult(false, 0, null, message)
}
