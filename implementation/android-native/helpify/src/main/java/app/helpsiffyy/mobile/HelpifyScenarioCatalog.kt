package app.helpsiffyy.mobile

import app.shared.core.DashboardCard
import app.shared.core.DashboardMetric

object HelpifyScenarioCatalog {
    fun metrics(role: String): List<DashboardMetric> = if (role == "contractor") {
        listOf(
            DashboardMetric("заказов рядом", "9"),
            DashboardMetric("активных работ", "4"),
            DashboardMetric("рейтинг", "4.9"),
            DashboardMetric("завершено за месяц", "18")
        )
    } else {
        listOf(
            DashboardMetric("активных задач", "3"),
            DashboardMetric("получено предложений", "8"),
            DashboardMetric("завершено задач", "14"),
            DashboardMetric("средняя оценка", "4.8")
        )
    }

    fun cards(role: String): List<DashboardCard> =
        if (role == "contractor") contractorCards() else customerCards()

    private fun customerCards(): List<DashboardCard> = listOf(
        DashboardCard(
            id = "customer-create-plumbing",
            title = "Создать срочную задачу",
            description = "Протекает смеситель · сегодня · бюджет €60",
            actionMessage = "Мастер создания задачи с адресом, временем и фотографиями.",
            section = "Создание задач",
            badge = "Срочно",
            steps = listOf(
                "Выбрать категорию «Сантехник»",
                "Добавить описание и фотографии",
                "Указать адрес и доступное время",
                "Опубликовать задачу"
            )
        ),
        DashboardCard(
            id = "customer-create-cleaning",
            title = "Запланировать уборку",
            description = "Квартира 68 м² · пятница 11:00",
            actionMessage = "Плановая задача с повторением и дополнительными услугами.",
            section = "Создание задач",
            badge = "Плановая",
            steps = listOf(
                "Выбрать площадь помещения",
                "Добавить мытьё окон",
                "Выбрать дату и время",
                "Подтвердить публикацию"
            )
        ),
        DashboardCard(
            id = "customer-offers-electric",
            title = "Сравнить предложения",
            description = "Замена электрощита · 4 исполнителя",
            actionMessage = "Предложения отличаются ценой, рейтингом и временем прибытия.",
            section = "Предложения",
            badge = "4 предложения",
            steps = listOf(
                "Открыть список исполнителей",
                "Сравнить цену и рейтинг",
                "Просмотреть отзывы",
                "Выбрать исполнителя"
            )
        ),
        DashboardCard(
            id = "customer-negotiate-price",
            title = "Уточнить стоимость",
            description = "Сборка шкафа · предложение €85",
            actionMessage = "Обсуждение состава работ до назначения исполнителя.",
            section = "Предложения",
            badge = "Диалог",
            steps = listOf(
                "Открыть предложение",
                "Задать вопрос о материалах",
                "Получить обновлённую цену",
                "Подтвердить предложение"
            )
        ),
        DashboardCard(
            id = "customer-active-plumber",
            title = "Активная работа",
            description = "Сантехник в пути · прибытие через 18 минут",
            actionMessage = "Карточка назначенной задачи с текущим статусом и контактами.",
            section = "Активные задачи",
            badge = "В пути",
            steps = listOf(
                "Проверить профиль исполнителя",
                "Подтвердить доступ к адресу",
                "Отметить начало работ",
                "Принять результат"
            )
        ),
        DashboardCard(
            id = "customer-reschedule",
            title = "Перенести визит",
            description = "Установка светильников · завтра 16:00",
            actionMessage = "Изменение согласованного времени с подтверждением исполнителя.",
            section = "Активные задачи",
            badge = "Согласование",
            steps = listOf(
                "Предложить новое время",
                "Дождаться ответа исполнителя",
                "Подтвердить новый слот"
            )
        ),
        DashboardCard(
            id = "customer-task-chat",
            title = "Чат по задаче",
            description = "3 сообщения · прикреплена фотография",
            actionMessage = "Диалог хранится в контексте конкретной задачи.",
            section = "Коммуникации",
            badge = "3 новых",
            steps = listOf(
                "Прочитать сообщение",
                "Открыть вложение",
                "Отправить уточнение",
                "Подтвердить получение"
            )
        ),
        DashboardCard(
            id = "customer-complete-review",
            title = "Завершить и оценить",
            description = "Ремонт розетки · работа выполнена",
            actionMessage = "Закрытие задачи с оценкой качества и комментарием.",
            section = "Завершение",
            badge = "Ожидает оценки",
            steps = listOf(
                "Подтвердить выполнение",
                "Поставить оценку",
                "Добавить комментарий",
                "Закрыть задачу"
            )
        ),
        DashboardCard(
            id = "customer-history",
            title = "История заказов",
            description = "14 завершённых задач · 6 категорий",
            actionMessage = "Архив задач с фильтрами по датам, категориям и исполнителям.",
            section = "Профиль",
            badge = "14 задач",
            steps = listOf(
                "Выбрать период",
                "Открыть завершённую задачу",
                "Повторить похожий заказ"
            )
        ),
        DashboardCard(
            id = "customer-support",
            title = "Обращение в поддержку",
            description = "Вопрос по завершённой задаче",
            actionMessage = "Создание обращения с выбором причины и приложением материалов.",
            section = "Профиль",
            badge = "Поддержка",
            steps = listOf(
                "Выбрать задачу",
                "Указать причину обращения",
                "Добавить описание",
                "Отправить обращение"
            )
        )
    )

    private fun contractorCards(): List<DashboardCard> = listOf(
        DashboardCard(
            id = "contractor-nearby-feed",
            title = "Лента заказов рядом",
            description = "9 задач в радиусе 7 км",
            actionMessage = "Лента сортируется по расстоянию, времени и категории.",
            section = "Новые заказы",
            badge = "9 задач",
            steps = listOf(
                "Выбрать рабочие категории",
                "Настроить радиус поиска",
                "Открыть подходящую задачу"
            )
        ),
        DashboardCard(
            id = "contractor-offer-plumbing",
            title = "Отправить предложение",
            description = "Замена смесителя · 2.4 км",
            actionMessage = "Исполнитель указывает стоимость, время прибытия и комментарий.",
            section = "Новые заказы",
            badge = "€55",
            steps = listOf(
                "Оценить описание задачи",
                "Указать стоимость",
                "Выбрать время прибытия",
                "Отправить предложение"
            )
        ),
        DashboardCard(
            id = "contractor-offer-update",
            title = "Изменить предложение",
            description = "Сборка кухни · требуется уточнение",
            actionMessage = "Предложение можно обновить до выбора исполнителя заказчиком.",
            section = "Предложения",
            badge = "Активно",
            steps = listOf(
                "Открыть своё предложение",
                "Изменить комментарий",
                "Обновить стоимость",
                "Сохранить изменения"
            )
        ),
        DashboardCard(
            id = "contractor-assigned",
            title = "Назначенная задача",
            description = "Электромонтаж · сегодня 15:30",
            actionMessage = "Полный рабочий цикл от подтверждения до завершения.",
            section = "Активные работы",
            badge = "Назначена",
            steps = listOf(
                "Подтвердить выезд",
                "Отметить прибытие",
                "Начать работу",
                "Завершить работу"
            )
        ),
        DashboardCard(
            id = "contractor-route",
            title = "Маршрут к заказчику",
            description = "5.8 км · расчётное время 17 минут",
            actionMessage = "Адрес раскрывается после назначения исполнителя.",
            section = "Активные работы",
            badge = "Маршрут",
            steps = listOf(
                "Открыть адрес",
                "Построить маршрут",
                "Сообщить о выезде"
            )
        ),
        DashboardCard(
            id = "contractor-task-chat",
            title = "Чат с заказчиком",
            description = "2 новых сообщения · фото объекта",
            actionMessage = "Переписка и вложения привязаны к активной задаче.",
            section = "Коммуникации",
            badge = "2 новых",
            steps = listOf(
                "Прочитать сообщения",
                "Открыть фотографию",
                "Ответить заказчику",
                "Подтвердить детали"
            )
        ),
        DashboardCard(
            id = "contractor-calendar",
            title = "Рабочий календарь",
            description = "4 задачи на текущую неделю",
            actionMessage = "Календарь показывает подтверждённые и предварительные слоты.",
            section = "Организация работы",
            badge = "4 задачи",
            steps = listOf(
                "Открыть неделю",
                "Проверить пересечения",
                "Заблокировать свободное время"
            )
        ),
        DashboardCard(
            id = "contractor-earnings",
            title = "Статистика работ",
            description = "18 задач за месяц · €1,240",
            actionMessage = "Сводка количества работ, категорий и среднего чека.",
            section = "Организация работы",
            badge = "Июль",
            steps = listOf(
                "Выбрать период",
                "Просмотреть категории",
                "Открыть детализацию"
            )
        ),
        DashboardCard(
            id = "contractor-profile",
            title = "Профессиональный профиль",
            description = "6 услуг · 12 фотографий · рейтинг 4.9",
            actionMessage = "Профиль включает описание, категории, портфолио и рабочий радиус.",
            section = "Профиль",
            badge = "92% заполнено",
            steps = listOf(
                "Обновить описание",
                "Добавить фотографию работы",
                "Проверить категории",
                "Сохранить профиль"
            )
        ),
        DashboardCard(
            id = "contractor-review",
            title = "Новый отзыв",
            description = "5 звёзд · «Быстро и аккуратно»",
            actionMessage = "Отзывы формируют публичный рейтинг исполнителя.",
            section = "Профиль",
            badge = "5.0",
            steps = listOf(
                "Открыть отзыв",
                "Посмотреть связанную задачу",
                "Добавить ответ"
            )
        )
    )
}
