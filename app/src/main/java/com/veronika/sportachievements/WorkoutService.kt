package com.veronika.sportachievements

import java.time.LocalDate

interface IWorkoutService {
    suspend fun save(workout: Workout)
    suspend fun update(id: String, workout: Workout)
    suspend fun delete(id: String, confirmed: Boolean)
}

class WorkoutService(
    private val workouts: IWorkoutRepository,
    private val exercises: IExerciseRepository
) : IWorkoutService {

    override suspend fun save(workout: Workout) {
        validate(workout)
        workouts.saveAtomically(workout)
    }

    override suspend fun update(id: String, workout: Workout) {
        if (id != workout.id) {
            throw AppError.ValidationError(
                "Ідентифікатор тренування не відповідає запису"
            )
        }

        if (workouts.get(id) == null) {
            throw AppError.NotFound("Тренування не знайдено")
        }

        validate(workout)
        workouts.saveAtomically(workout)
    }

    override suspend fun delete(id: String, confirmed: Boolean) {
        if (!confirmed) {
            throw AppError.ValidationError(
                "Потрібно підтвердити видалення тренування"
            )
        }

        workouts.deleteAtomically(id)
    }

    private suspend fun validate(workout: Workout) {
        if (workout.id.isBlank()) {
            throw AppError.ValidationError(
                "Відсутній ідентифікатор тренування"
            )
        }

        if (workout.date > LocalDate.now()) {
            throw AppError.ValidationError(
                "Дата тренування не може бути в майбутньому"
            )
        }

        if (workout.results.isEmpty()) {
            throw AppError.ValidationError(
                "Додайте хоча б один результат"
            )
        }

        val resultIds = workout.results.map { it.id }

        if (
            resultIds.any { it.isBlank() } ||
            resultIds.distinct().size != resultIds.size
        ) {
            throw AppError.ValidationError(
                "Результати повинні мати різні ідентифікатори"
            )
        }

        val strength = workout.activityType == ActivityType.STRENGTH
        val expectedMetric = if (strength) {
            MetricType.WEIGHT_REPS
        } else {
            MetricType.DISTANCE_TIME
        }

        for ((index, result) in workout.results.withIndex()) {
            val prefix = "Результат ${index + 1}: "

            if (result.exerciseId.isBlank()) {
                throw AppError.ValidationError(
                    prefix + "оберіть вправу"
                )
            }

            if (strength) {
                val weight = result.weightKg
                val reps = result.reps

                if (weight == null || !weight.isFinite() || weight < 0) {
                    throw AppError.ValidationError(
                        prefix + "вага має бути числом від 0 кг"
                    )
                }

                if (reps == null || reps <= 0) {
                    throw AppError.ValidationError(
                        prefix + "повторення мають бути додатним цілим числом"
                    )
                }

                if (
                    result.distanceM != null ||
                    result.durationSec != null
                ) {
                    throw AppError.ValidationError(
                        prefix + "силовий підхід не повинен містити дистанцію і час"
                    )
                }
            } else {
                if (result.distanceM == null || result.distanceM <= 0) {
                    throw AppError.ValidationError(
                        prefix + "дистанція має бути більшою за нуль"
                    )
                }

                if (result.durationSec == null || result.durationSec <= 0) {
                    throw AppError.ValidationError(
                        prefix + "час має бути більшим за нуль"
                    )
                }

                if (result.weightKg != null || result.reps != null) {
                    throw AppError.ValidationError(
                        prefix + "дистанційна активність не повинна містити вагу і повторення"
                    )
                }
            }
        }

        // Одну вправу перевіряємо лише раз,
        // навіть якщо вона має кілька підходів.
        for (exerciseId in workout.results.map { it.exerciseId }.distinct()) {
            val exercise = exercises.get(exerciseId)
                ?: throw AppError.NotFound("Обрану вправу не знайдено")

            if (
                exercise.activityType != workout.activityType ||
                exercise.metricType != expectedMetric
            ) {
                throw AppError.ValidationError(
                    "Вправа «${exercise.name}» не відповідає виду тренування"
                )
            }
        }
    }
}