package app.mydealers.mobile

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
        val publishedIds = mutableListOf<Long>()

        if (user.role == "vendor") {
            cards += createProductCard()
            cards += LiveDashboardCard(
                id = "mydealer-seed-products",
                title = "Создать набор из 6 товаров",
                description = "Наполнить каталог разнообразными карточками",
                details = "Будут добавлены фермерские продукты, напитки, сыр, " +
                    "оливковое масло, мёд и сезонный набор.",
                section = "Быстрые действия",
                badge = "Демо-данные",
                actionId = "seed-product-pack",
                actionLabel = "Создать набор",
                confirmationMessage = "Создать шесть товаров со статусом модерации?"
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
                actionLabel = "Оформить",
                confirmationMessage = "Создать заказы из текущей корзины?"
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

            if (status == "published") {
                published++
                publishedIds += id
            } else {
                moderation++
            }

            val actionId: String?
            val actionLabel: String
            val form: LiveActionForm?
            val confirmationMessage: String

            if (user.role == "buyer" && status == "published") {
                actionId = "add-cart:$id"
                actionLabel = "В корзину"
                form = quantityForm()
                confirmationMessage = ""
            } else if (user.role == "vendor" && status == "moderation") {
                actionId = "publish-product:$id"
                actionLabel = "Опубликовать"
                form = null
                confirmationMessage = "Опубликовать товар в общем каталоге?"
            } else {
                actionId = null
                actionLabel = ""
                form = null
                confirmationMessage = ""
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
                actionLabel = actionLabel,
                form = form,
                confirmationMessage = confirmationMessage
            )
        }

        if (user.role == "buyer" && publishedIds.isNotEmpty()) {
            cards.add(
                0,
                LiveDashboardCard(
                    id = "mydealer-seed-cart",
                    title = "Наполнить корзину",
                    description = "Добавить до трёх опубликованных товаров",
                    details = "В корзину будет добавлено по одной единице первых " +
                        "доступных товаров.",
                    section = "Быстрые действия",
                    badge = "Демо-данные",
                    actionId = "seed-cart:${publishedIds.take(3).joinToString(",")}",
                    actionLabel = "Добавить",
                    confirmationMessage = "Добавить выбранные товары в корзину?"
                )
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
            val form: LiveActionForm?
            val confirmationMessage: String

            if (user.role == "vendor" && status == "new") {
                actionId = "order-status:$id:confirmed"
                actionLabel = "Подтвердить"
                form = null
                confirmationMessage = "Подтвердить заказ MD-$id?"
            } else if (user.role == "vendor" && status == "confirmed") {
                actionId = "order-status:$id:completed"
                actionLabel = "Завершить"
                form = null
                confirmationMessage = "Отметить заказ MD-$id завершённым?"
            } else if (user.role == "buyer" && status != "completed") {
                actionId = "send-message:$id"
                actionLabel = "Написать вендору"
                form = messageForm()
                confirmationMessage = ""
            } else {
                actionId = null
                actionLabel = ""
                form = null
                confirmationMessage = ""
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
                actionLabel = actionLabel,
                form = form,
                confirmationMessage = confirmationMessage
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
        card: LiveDashboardCard,
        values: Map<String, String>
    ): ApiResult {
        val action = card.actionId ?: return localError("Действие отсутствует")
        val parts = action.split(":")

        return when (parts.firstOrNull()) {
            "create-product" -> api.post(
                "/market/products",
                JSONObject()
                    .put("name", values["name"].orEmpty())
                    .put("category", values["category"].orEmpty())
                    .put("price", decimal(values["price"], 10.0))
                    .put("unit", values["unit"].orEmpty())
                    .put("emoji", values["emoji"].orEmpty())
                    .put("description", values["description"].orEmpty()),
                token
            )

            "seed-product-pack" -> createProductPack(token)

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
                        .put("quantity", integer(values["quantity"], 1)),
                    token
                )
            }

            "seed-cart" -> {
                if (parts.size < 2) return localError("Товары не найдены")
                createCartPack(token, parts[1])
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
                    JSONObject().put("text", values["text"].orEmpty()),
                    token
                )
            }

            else -> localError("Неизвестное действие")
        }
    }

    private fun createProductCard(): LiveDashboardCard = LiveDashboardCard(
        id = "mydealer-create-product",
        title = "Создать товар на сервере",
        description = "Заполнить новую карточку MyDealer",
        details = "После создания товар получит статус модерации.",
        section = "Быстрые действия",
        badge = "Новый товар",
        actionId = "create-product",
        actionLabel = "Заполнить",
        form = LiveActionForm(
            title = "Новый товар",
            submitLabel = "Создать",
            fields = listOf(
                LiveFormField(
                    key = "name",
                    label = "Название",
                    defaultValue = "Фермерский сезонный набор"
                ),
                LiveFormField(
                    key = "category",
                    label = "Категория",
                    defaultValue = "Фермерские продукты"
                ),
                LiveFormField(
                    key = "price",
                    label = "Цена",
                    type = LiveFormFieldType.DECIMAL,
                    defaultValue = "24.90",
                    minimumValue = 0.01
                ),
                LiveFormField(
                    key = "unit",
                    label = "Единица",
                    defaultValue = "1 набор"
                ),
                LiveFormField(
                    key = "emoji",
                    label = "Значок",
                    defaultValue = "🧺"
                ),
                LiveFormField(
                    key = "description",
                    label = "Описание",
                    type = LiveFormFieldType.MULTILINE,
                    defaultValue = "Набор сезонных продуктов локального хозяйства."
                )
            )
        )
    )

    private fun quantityForm(): LiveActionForm = LiveActionForm(
        title = "Добавить в корзину",
        submitLabel = "Добавить",
        fields = listOf(
            LiveFormField(
                key = "quantity",
                label = "Количество",
                type = LiveFormFieldType.INTEGER,
                defaultValue = "1",
                minimumValue = 1.0
            )
        )
    )

    private fun messageForm(): LiveActionForm = LiveActionForm(
        title = "Сообщение вендору",
        submitLabel = "Отправить",
        fields = listOf(
            LiveFormField(
                key = "text",
                label = "Текст сообщения",
                type = LiveFormFieldType.MULTILINE,
                defaultValue =
                    "Здравствуйте! Подскажите, пожалуйста, актуальное время доставки."
            )
        )
    )

    private fun createProductPack(token: String): ApiResult {
        val products = listOf(
            ProductSeed(
                "Фермерский сыр 18 месяцев",
                "Сыры",
                32.0,
                "1 кг",
                "🧀",
                "Твёрдый сыр длительной выдержки."
            ),
            ProductSeed(
                "Липовый мёд",
                "Мёд",
                14.5,
                "500 г",
                "🍯",
                "Мёд текущего сезона от локальной пасеки."
            ),
            ProductSeed(
                "Оливковое масло первого отжима",
                "Масла",
                21.9,
                "750 мл",
                "🫒",
                "Нефильтрованное масло раннего урожая."
            ),
            ProductSeed(
                "Яблочный сок прямого отжима",
                "Напитки",
                6.8,
                "1 л",
                "🍎",
                "Сок без добавления сахара и концентрата."
            ),
            ProductSeed(
                "Сезонные овощи",
                "Овощи",
                18.0,
                "1 ящик",
                "🥕",
                "Набор свежих овощей из хозяйства."
            ),
            ProductSeed(
                "Дегустационный фермерский набор",
                "Наборы",
                39.0,
                "1 набор",
                "🧺",
                "Сыр, мёд, масло и сезонный продукт."
            )
        )

        products.forEach { product ->
            val result = api.post(
                "/market/products",
                JSONObject()
                    .put("name", product.name)
                    .put("category", product.category)
                    .put("price", product.price)
                    .put("unit", product.unit)
                    .put("emoji", product.emoji)
                    .put("description", product.description),
                token
            )
            if (!result.successful) return result
        }

        return localSuccess("Создано товаров: ${products.size}")
    }

    private fun createCartPack(token: String, ids: String): ApiResult {
        val productIds = ids.split(",").filter { it.isNotBlank() }
        var added = 0

        productIds.forEach { productId ->
            val result = api.post(
                "/market/cart/items",
                JSONObject()
                    .put("product_id", productId.toLongOrNull() ?: 0L)
                    .put("quantity", 1),
                token
            )
            if (!result.successful) return result
            added++
        }

        return localSuccess("Добавлено товаров: $added")
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

    private fun decimal(value: String?, fallback: Double): Double =
        value.orEmpty().replace(",", ".").toDoubleOrNull() ?: fallback

    private fun integer(value: String?, fallback: Int): Int =
        value.orEmpty().toIntOrNull() ?: fallback

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

    private fun localSuccess(message: String): ApiResult =
        ApiResult(true, 200, JSONObject().put("message", message), message)

    private data class ProductSeed(
        val name: String,
        val category: String,
        val price: Double,
        val unit: String,
        val emoji: String,
        val description: String
    )
}
