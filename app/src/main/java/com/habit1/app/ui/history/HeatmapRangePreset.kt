package com.habit1.app.ui.history

/**
 * UI presets for analytics and heatmap date ranges.
 * Resolved in ViewModel/UI into concrete [com.habit1.app.domain.model.AnalyticsRange] values.
 */
enum class HeatmapRangePreset(val label: String) {
    THIS_WEEK("This Week"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");
}
