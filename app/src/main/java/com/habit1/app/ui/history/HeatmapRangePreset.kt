package com.habit1.app.ui.history

/**
 * Standardized rolling UI presets for analytics and heatmap date ranges (Decision 5.1.2).
 * Resolved in ViewModel/UI into concrete [com.habit1.app.domain.model.AnalyticsRange] values.
 */
enum class HeatmapRangePreset(val label: String) {
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    LAST_365_DAYS("Last 365 Days"),
    ALL_TIME("All Time");

    companion object {
        // Backward-compatibility aliases for legacy code and tests
        val THIS_WEEK = LAST_7_DAYS
        val THIS_MONTH = LAST_30_DAYS
        val THIS_YEAR = LAST_365_DAYS
    }
}
