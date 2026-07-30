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
    val actionLabel: String = ""
)

interface LiveWorkflowRepository {
    fun load(token: String, user: ApiUser): Pair<ApiResult, LiveDashboardPayload?>
    fun perform(token: String, user: ApiUser, card: LiveDashboardCard): ApiResult
}
