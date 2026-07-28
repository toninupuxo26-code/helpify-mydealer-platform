package app.mydealers.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BaseDashboardActivity
import app.shared.core.DashboardCard
import app.shared.core.DemoAccount
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class DashboardActivity : BaseDashboardActivity() {
    override val productConfig = ProductConfig(
        "MyDealer",
        "https://mydealers.app/api",
        listOf(RoleOption("Покупатель", "buyer"), RoleOption("Вендор", "vendor")),
        listOf(
            DemoAccount("Покупатель", "buyer@example.test", "demo123"),
            DemoAccount("Вендор", "vendor@example.test", "demo123")
        )
    )

    override fun dashboardCards(user: ApiUser): List<DashboardCard> = if (user.role == "vendor") {
        listOf(
            DashboardCard("Добавить товар", "Новая карточка на модерацию", "Форма товара будет подключена к API в следующем Android-патче"),
            DashboardCard("Мои товары", "5 опубликовано · 1 на модерации", "Показан тестовый список товаров"),
            DashboardCard("Заказы", "2 новых заказа", "Показаны демонстрационные заказы"),
            DashboardCard("Сообщения", "Покупатель задал вопрос", "Открыт демонстрационный чат заказа")
        )
    } else {
        listOf(
            DashboardCard("Каталог", "18 отборных продуктов", "Открыт тестовый каталог"),
            DashboardCard("Корзина", "2 товара · 3 450 ₽", "Показана демонстрационная корзина"),
            DashboardCard("Мои заказы", "1 заказ подтверждён", "Показан тестовый заказ"),
            DashboardCard("Чат с вендором", "Новое сообщение", "Открыт демонстрационный чат заказа")
        )
    }

    override fun returnToAuth() { startActivity(Intent(this, MainActivity::class.java)) }
}
