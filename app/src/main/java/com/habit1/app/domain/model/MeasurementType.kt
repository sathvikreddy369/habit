package com.habit1.app.domain.model

/**
 * Supported habit measurement types adhering to PRD Section 5.1.
 * Defines how progress is recorded, targeted, and verified.
 */
sealed interface MeasurementType {
    val typeName: String

    /**
     * Binary completion (e.g., Exercise, Meditate).
     * Completed when marked done (actualValue >= 1.0).
     */
    data object BooleanChoice : MeasurementType {
        override val typeName: String = TYPE_NAME
        const val TYPE_NAME = "BOOLEAN"
    }

    /**
     * Integer count measurement (e.g., Pushups: 50 reps, Pages read: 20).
     */
    data class Count(
        val target: Int,
        val unit: String? = null
    ) : MeasurementType {
        override val typeName: String = TYPE_NAME
        init {
            require(target > 0) { "Target count must be greater than 0" }
        }
        companion object {
            const val TYPE_NAME = "COUNT"
        }
    }

    /**
     * Duration in minutes (e.g., Study: 120 minutes).
     */
    data class Duration(
        val targetMinutes: Int
    ) : MeasurementType {
        override val typeName: String = TYPE_NAME
        init {
            require(targetMinutes > 0) { "Target duration must be greater than 0 minutes" }
        }
        companion object {
            const val TYPE_NAME = "DURATION"
        }
    }

    /**
     * Continuous decimal quantity (e.g., Water: 2.5 Liters, Running: 5.0 km).
     */
    data class Quantity(
        val target: Double,
        val unit: String
    ) : MeasurementType {
        override val typeName: String = TYPE_NAME
        init {
            require(target > 0.0) { "Target quantity must be greater than 0.0" }
        }
        companion object {
            const val TYPE_NAME = "QUANTITY"
        }
    }
}
