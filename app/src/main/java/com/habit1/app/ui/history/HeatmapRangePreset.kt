package com.habit1.app.ui.history

/**
 * UI presets for analytics and heatmap date ranges.
 * Resolved in ViewModel/UI into concrete [com.habit1.app.domain.model.AnalyticsRange] values.
 */
enum class HeatmapRangePreset(val label: String) {
    SEVEN_DAYS("7D"),
    THIRTY_DAYS("30D"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y");
}
