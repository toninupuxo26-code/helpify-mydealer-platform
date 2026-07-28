package app.helpsiffyy.mobile

import android.content.Intent
import app.shared.core.ApiUser
import app.shared.core.BaseDashboardActivity
import app.shared.core.DashboardCard
import app.shared.core.DemoAccount
import app.shared.core.ProductConfig
import app.shared.core.RoleOption

class DashboardActivity : BaseDashboardActivity() {
    override val productConfig = ProductConfig(
        "Helpify",
        "https://helpsiffyy.app/api",
        listOf(RoleOption("Заказчик", "customer"), RoleOption("Исполнитель", "contractor")),
        listOf(
            DemoAccount("Заказчик", "customer@example.test", "demo123"),
            DemoAccount("Исполнитель", "contractor@example.test", "demo123")
        )
    )

    override fun dashboardCards(user: ApiUser): List<DashboardCard> = if (user.role == "contractor") {
        listOf(
            DashboardCard("Лента заказов", "7 открытых задач рядом", "Открыта тестовая лента заказов"),
            DashboardCard("Мои предложения", "2 активных предложения", "Показаны демонстрационные предложения"),
            DashboardCard("Чаты", "3 новых сообщения", "Открыт демонстрационный чат"),
            DashboardCard("Рейтинг", "4.9 · 28 отзывов", "Показан тестовый рейтинг")
        )
    } else {
        listOf(
            DashboardCard("Создать задачу", "Опишите работу и бюджет", "Форма создания задачи будет подключена к API в следующем Android-патче"),
            DashboardCard("Предложения", "Получено 4 предложения", "Показан тестовый список предложений"),
            DashboardCard("Активная задача", "Сантехник · назначена", "Показана демонстрационная карточка задачи"),
            DashboardCard("Чат", "Исполнитель ответил", "Открыт демонстрационный чат")
        )
    }

    override fun returnToAuth() { startActivity(Intent(this, MainActivity::class.java)) }
}
