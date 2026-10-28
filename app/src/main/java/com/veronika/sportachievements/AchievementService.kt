package com.veronika.sportachievements

import java.time.LocalDate

data class GoalProgress(
    val goal: Goal,
    val currentValue: Double?,
    val status: GoalStatus
)

data class PersonalRecord(
    val context: ComparisonContext,
    val value: Double,
    val date: LocalDate,
    val workoutId: String
)

class AchievementService {

    // Вага порівнюється лише для однакової кількості повторень.
    // Час порівнюється лише для однакової дистанції.
    private fun matchingValue(
        result: Result,
        context: ComparisonContext
    ): Double? {
        if (result.exerciseId != context.exerciseId) return null

        return when (context.metricType) {
            MetricType.WEIGHT_REPS -> {
                if (
                    context.reps == null ||
                    result.reps != context.reps
                ) {
                    null
                } else {
                    result.weightKg?.takeIf {
                        it.isFinite() && it >= 0
                    }
                }
            }

            MetricType.DISTANCE_TIME -> {
                if (
                    context.distanceM == null ||
                    result.distanceM != context.distanceM
                ) {
                    null
                } else {
                    result.durationSec?.takeIf { it > 0 }?.toDouble()
                }
            }
        }
    }

    private fun isBetter(
        candidate: Double,
        current: Double,
        metricType: MetricType
    ): Boolean = when (metricType) {
        MetricType.WEIGHT_REPS -> candidate > current
        MetricType.DISTANCE_TIME -> candidate < current
    }

    fun personalRecord(
        workouts: List<Workout>,
        context: ComparisonContext
    ): PersonalRecord? {
        var record: PersonalRecord? = null

        // При однакових результатах зберігаємо перше досягнення.
        val ordered = workouts.sortedWith(
            compareBy<Workout> { it.date }.thenBy { it.id }
        )

        for (workout in ordered) {
            for (result in workout.results) {
                val value = matchingValue(result, context) ?: continue
                val previous = record

                if (
                    previous == null ||
                    isBetter(value, previous.value, context.metricType)
                ) {
                    record = PersonalRecord(
                        context = context,
                        value = value,
                        date = workout.date,
                        workoutId = workout.id
                    )
                }
            }
        }

        return record
    }

    fun chartPoints(
        workouts: List<Workout>,
        context: ComparisonContext,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null
    ): List<ChartPoint> {
        if (
            dateFrom != null &&
            dateTo != null &&
            dateFrom > dateTo
        ) {
            throw AppError.ValidationError(
                "Початкова дата має бути не пізніше кінцевої"
            )
        }

        val bestByDate = sortedMapOf<LocalDate, Double>()

        for (workout in workouts) {
            if (dateFrom != null && workout.date < dateFrom) continue
            if (dateTo != null && workout.date > dateTo) continue

            for (result in workout.results) {
                val value = matchingValue(result, context) ?: continue
                val previous = bestByDate[workout.date]

                // На графіку — найкращий порівнюваний результат за день.
                if (
                    previous == null ||
                    isBetter(value, previous, context.metricType)
                ) {
                    bestByDate[workout.date] = value
                }
            }
        }

        return bestByDate.map { (date, value) ->
            ChartPoint(date = date, value = value)
        }
    }

    fun goalProgress(
        goal: Goal,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): GoalProgress {
        // Обидві межі періоду включені.
        // Результати після сьогоднішньої дати не враховуємо.
        val eligible = workouts.filter {
            it.date >= goal.startedAt &&
                    it.date <= goal.deadline &&
                    it.date <= today
        }

        val currentValue = personalRecord(
            workouts = eligible,
            context = goal.context
        )?.value

        val achieved = currentValue?.let { value ->
            when (goal.context.metricType) {
                MetricType.WEIGHT_REPS -> value >= goal.targetValue
                MetricType.DISTANCE_TIME -> value <= goal.targetValue
            }
        } ?: false

        val status = when {
            achieved -> GoalStatus.ACHIEVED
            today > goal.deadline -> GoalStatus.EXPIRED
            currentValue == null -> GoalStatus.NO_DATA
            else -> GoalStatus.IN_PROGRESS
        }

        return GoalProgress(
            goal = goal,
            currentValue = currentValue,
            status = status
        )
    }

    fun allGoalProgress(
        goals: List<Goal>,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): List<GoalProgress> = goals.map { goal ->
        goalProgress(goal, workouts, today)
    }
}