package com.veronika.sportachievements

import java.time.LocalDate
import java.util.UUID

enum class ActivityType(val title: String) {
    STRENGTH("Силове тренування"),
    RUNNING("Біг"),
    CYCLING("Велосипед")
}

enum class MetricType(val title: String) {
    WEIGHT_REPS("Вага та повторення"),
    DISTANCE_TIME("Дистанція та час")
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String
)

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val activityType: ActivityType,
    val metricType: MetricType
)

data class Workout(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val activityType: ActivityType,
    val notes: String = "",
    val results: List<Result> = emptyList()
)

// Один Result — один силовий підхід або результат дистанційної вправи.
data class Result(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceM: Int? = null,
    val durationSec: Int? = null
)

// Умови, за яких можна порівнювати результати.
data class ComparisonContext(
    val exerciseId: String,
    val metricType: MetricType,
    val reps: Int? = null,
    val distanceM: Int? = null
)

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val context: ComparisonContext,
    val targetValue: Double,
    val startedAt: LocalDate,
    val deadline: LocalDate
)

enum class GoalStatus(val title: String) {
    NO_DATA("Немає даних"),
    IN_PROGRESS("У процесі"),
    ACHIEVED("Досягнуто"),
    EXPIRED("Термін минув")
}

data class ChartPoint(
    val date: LocalDate,
    val value: Double
)