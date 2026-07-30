package app.shared.core

data class LiveDashboardPayload(
    val metrics: List<DashboardMetric> = emptyList(),
    val cards: List<LiveDashboardCard> = emptyList(),
    val message: String = ""
)

data class LiveDashboardCard(
    val id: String,
    val title: String,
    val description: String,
    val details: String,
    val section: String,
    val badge: String = "",
    val actionId: String? = null,
    val actionLabel: String = "",
    val form: LiveActionForm? = null,
    val confirmationMessage: String = ""
)

data class LiveActionForm(
    val title: String,
    val submitLabel: String = "Отправить",
    val fields: List<LiveFormField>
)

data class LiveFormField(
    val key: String,
    val label: String,
    val type: LiveFormFieldType = LiveFormFieldType.TEXT,
    val required: Boolean = true,
    val defaultValue: String = "",
    val hint: String = "",
    val minimumValue: Double? = null
)

enum class LiveFormFieldType {
    TEXT,
    MULTILINE,
    DECIMAL,
    INTEGER
}

interface LiveWorkflowRepository {
    fun load(token: String, user: ApiUser): Pair<ApiResult, LiveDashboardPayload?>

    fun perform(
        token: String,
        user: ApiUser,
        card: LiveDashboardCard,
        values: Map<String, String> = emptyMap()
    ): ApiResult
}
