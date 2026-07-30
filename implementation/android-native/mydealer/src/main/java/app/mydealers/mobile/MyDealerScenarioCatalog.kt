package app.mydealers.mobile

import app.shared.core.DashboardCard
import app.shared.core.DashboardMetric

object MyDealerScenarioCatalog {
    fun metrics(role: String): List<DashboardMetric> = if (role == "vendor") {
        listOf(
            DashboardMetric("опубликовано товаров", "12"),
            DashboardMetric("на модерации", "3"),
            DashboardMetric("активных заказов", "5"),
            DashboardMetric("рейтинг", "4.9")
        )
    } else {
        listOf(
            DashboardMetric("товаров в избранном", "7"),
            DashboardMetric("позиций в корзине", "3"),
            DashboardMetric("активных заказов", "2"),
            DashboardMetric("проверенных вендоров", "18")
        )
    }

    fun cards(role: String): List<DashboardCard> =
        if (role == "vendor") vendorCards() else buyerCards()

    private fun buyerCards(): List<DashboardCard> = listOf(
        DashboardCard(
            id = "buyer-catalog-truffles",
            title = "Белый трюфель",
            description = "Пьемонт · 50 г · €145",
            actionMessage = "Карточка товара с происхождением, сезоном и данными вендора.",
            section = "Каталог",
            badge = "Лимитировано",
            steps = listOf(
                "Открыть карточку",
                "Проверить происхождение",
                "Выбрать количество",
                "Добавить в корзину"
            )
        ),
        DashboardCard(
            id = "buyer-catalog-cheese",
            title = "Фермерский сыр",
            description = "Выдержка 18 месяцев · €32/кг",
            actionMessage = "Товар локального производителя с ограниченным объёмом.",
            section = "Каталог",
            badge = "Фермерское",
            steps = listOf(
                "Открыть состав",
                "Посмотреть профиль вендора",
                "Добавить в избранное"
            )
        ),
        DashboardCard(
            id = "buyer-catalog-oysters",
            title = "Свежие устрицы",
            description = "Нормандия · набор 12 шт. · €38",
            actionMessage = "Карточка скоропортящегося товара с доступной датой доставки.",
            section = "Каталог",
            badge = "Свежая поставка",
            steps = listOf(
                "Выбрать дату доставки",
                "Проверить условия хранения",
                "Добавить набор в корзину"
            )
        ),
        DashboardCard(
            id = "buyer-favorites",
            title = "Избранные товары",
            description = "7 позиций от 5 вендоров",
            actionMessage = "Список сохранённых карточек с контролем наличия.",
            section = "Подборки",
            badge = "7 товаров",
            steps = listOf(
                "Открыть избранное",
                "Сравнить наличие",
                "Переместить товар в корзину"
            )
        ),
        DashboardCard(
            id = "buyer-selection",
            title = "Подборка недели",
            description = "8 сезонных продуктов",
            actionMessage = "Редакционная подборка товаров с ограниченной доступностью.",
            section = "Подборки",
            badge = "Новая",
            steps = listOf(
                "Открыть подборку",
                "Просмотреть рекомендации",
                "Сохранить понравившийся товар"
            )
        ),
        DashboardCard(
            id = "buyer-cart",
            title = "Корзина",
            description = "3 позиции · €214",
            actionMessage = "Корзина сохраняется между сеансами и группирует товары по вендорам.",
            section = "Покупка",
            badge = "3 позиции",
            steps = listOf(
                "Проверить количество",
                "Выбрать способ получения",
                "Добавить комментарий",
                "Перейти к оформлению"
            )
        ),
        DashboardCard(
            id = "buyer-checkout",
            title = "Оформить заказ",
            description = "2 вендора · доставка в субботу",
            actionMessage = "При оформлении создаются отдельные заказы для каждого вендора.",
            section = "Покупка",
            badge = "Оформление",
            steps = listOf(
                "Проверить адрес",
                "Подтвердить контактные данные",
                "Согласовать интервалы",
                "Разместить заказ"
            )
        ),
        DashboardCard(
            id = "buyer-active-order",
            title = "Заказ MD-1048",
            description = "Подтверждён · готовится к отправке",
            actionMessage = "Заказ содержит историю статусов и позиции на момент оформления.",
            section = "Заказы",
            badge = "Подтверждён",
            steps = listOf(
                "Открыть состав заказа",
                "Проверить статус",
                "Посмотреть данные вендора",
                "Подтвердить получение"
            )
        ),
        DashboardCard(
            id = "buyer-order-chat",
            title = "Чат с вендором",
            description = "1 новое сообщение по заказу MD-1048",
            actionMessage = "Диалог привязан к заказу и доступен обеим сторонам.",
            section = "Заказы",
            badge = "1 новое",
            steps = listOf(
                "Прочитать сообщение",
                "Уточнить время доставки",
                "Подтвердить договорённость"
            )
        ),
        DashboardCard(
            id = "buyer-order-history",
            title = "История покупок",
            description = "9 завершённых заказов",
            actionMessage = "Архив заказов с повторной покупкой отдельных позиций.",
            section = "Профиль",
            badge = "9 заказов",
            steps = listOf(
                "Выбрать прошлый заказ",
                "Открыть товар",
                "Повторить покупку"
            )
        ),
        DashboardCard(
            id = "buyer-review",
            title = "Оценить заказ",
            description = "Оливковое масло · ожидает отзыва",
            actionMessage = "Оценка товара и вендора после завершения заказа.",
            section = "Профиль",
            badge = "Ожидает",
            steps = listOf(
                "Поставить оценку товару",
                "Оценить вендора",
                "Добавить комментарий",
                "Опубликовать отзыв"
            )
        ),
        DashboardCard(
            id = "buyer-profile",
            title = "Профиль покупателя",
            description = "2 адреса · 3 предпочтения",
            actionMessage = "Управление контактами, адресами и продуктовыми предпочтениями.",
            section = "Профиль",
            badge = "Заполнен",
            steps = listOf(
                "Проверить контактные данные",
                "Обновить адрес",
                "Настроить предпочтения"
            )
        )
    )

    private fun vendorCards(): List<DashboardCard> = listOf(
        DashboardCard(
            id = "vendor-add-product",
            title = "Добавить товар",
            description = "Новая карточка для каталога",
            actionMessage = "Вендор заполняет происхождение, цену, единицу и описание.",
            section = "Товары",
            badge = "Создание",
            steps = listOf(
                "Выбрать категорию",
                "Добавить название и описание",
                "Указать цену и единицу",
                "Отправить на модерацию"
            )
        ),
        DashboardCard(
            id = "vendor-moderation",
            title = "Товары на модерации",
            description = "3 карточки ожидают проверки",
            actionMessage = "Очередь карточек со статусами проверки и комментариями.",
            section = "Товары",
            badge = "3 товара",
            steps = listOf(
                "Открыть карточку",
                "Проверить замечания",
                "Исправить описание",
                "Отправить повторно"
            )
        ),
        DashboardCard(
            id = "vendor-published",
            title = "Опубликованный каталог",
            description = "12 товаров · 4 категории",
            actionMessage = "Управление доступностью и содержанием опубликованных карточек.",
            section = "Товары",
            badge = "12 товаров",
            steps = listOf(
                "Выбрать товар",
                "Обновить наличие",
                "Изменить цену",
                "Сохранить карточку"
            )
        ),
        DashboardCard(
            id = "vendor-stock",
            title = "Остатки",
            description = "2 товара заканчиваются",
            actionMessage = "Контроль доступного количества и временного отключения продаж.",
            section = "Товары",
            badge = "Внимание",
            steps = listOf(
                "Открыть список остатков",
                "Обновить количество",
                "Скрыть отсутствующий товар"
            )
        ),
        DashboardCard(
            id = "vendor-new-orders",
            title = "Новые заказы",
            description = "2 заказа ожидают подтверждения",
            actionMessage = "Заказы группируются по покупателям и времени создания.",
            section = "Заказы",
            badge = "2 новых",
            steps = listOf(
                "Открыть новый заказ",
                "Проверить наличие",
                "Подтвердить заказ",
                "Указать срок готовности"
            )
        ),
        DashboardCard(
            id = "vendor-processing",
            title = "Заказ MD-1048",
            description = "3 позиции · €176 · подтверждён",
            actionMessage = "Рабочая карточка заказа с позициями и контактами покупателя.",
            section = "Заказы",
            badge = "В работе",
            steps = listOf(
                "Подготовить позиции",
                "Подтвердить упаковку",
                "Передать в доставку",
                "Завершить заказ"
            )
        ),
        DashboardCard(
            id = "vendor-order-chat",
            title = "Сообщения покупателей",
            description = "3 непрочитанных сообщения",
            actionMessage = "Переписка ведётся внутри конкретного заказа.",
            section = "Коммуникации",
            badge = "3 новых",
            steps = listOf(
                "Открыть диалог",
                "Ответить на вопрос",
                "Подтвердить договорённость"
            )
        ),
        DashboardCard(
            id = "vendor-catalog-question",
            title = "Вопрос о товаре",
            description = "Белый трюфель · условия хранения",
            actionMessage = "Предпродажный вопрос покупателя по карточке товара.",
            section = "Коммуникации",
            badge = "Вопрос",
            steps = listOf(
                "Открыть карточку товара",
                "Подготовить ответ",
                "Отправить рекомендации"
            )
        ),
        DashboardCard(
            id = "vendor-analytics",
            title = "Аналитика продаж",
            description = "€3,840 за месяц · 26 заказов",
            actionMessage = "Сводка по продажам, категориям и среднему заказу.",
            section = "Аналитика",
            badge = "Июль",
            steps = listOf(
                "Выбрать период",
                "Сравнить категории",
                "Открыть популярные товары"
            )
        ),
        DashboardCard(
            id = "vendor-reviews",
            title = "Отзывы",
            description = "Рейтинг 4.9 · 37 отзывов",
            actionMessage = "Отзывы покупателей связаны с завершёнными заказами.",
            section = "Аналитика",
            badge = "4.9",
            steps = listOf(
                "Открыть новый отзыв",
                "Посмотреть заказ",
                "Добавить ответ"
            )
        ),
        DashboardCard(
            id = "vendor-profile",
            title = "Профиль вендора",
            description = "История, регион, сертификаты",
            actionMessage = "Публичный профиль подтверждает происхождение товаров.",
            section = "Профиль",
            badge = "96% заполнено",
            steps = listOf(
                "Обновить историю бренда",
                "Проверить контакты",
                "Добавить сертификат",
                "Сохранить профиль"
            )
        ),
        DashboardCard(
            id = "vendor-settings",
            title = "Настройки работы",
            description = "Доставка, график, зоны обслуживания",
            actionMessage = "Настройки определяют доступность заказов и варианты получения.",
            section = "Профиль",
            badge = "Настройки",
            steps = listOf(
                "Настроить рабочие дни",
                "Указать способы получения",
                "Обновить зоны доставки"
            )
        )
    )
}
