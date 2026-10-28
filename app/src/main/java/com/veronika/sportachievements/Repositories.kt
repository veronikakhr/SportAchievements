package com.veronika.sportachievements

import java.time.LocalDate

data class WorkoutFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val activityType: ActivityType? = null,
    val exerciseId: String? = null
)

interface IProfileRepository {
    suspend fun get(): Profile?
    suspend fun save(profile: Profile)
}

interface IExerciseRepository {
    suspend fun list(): List<Exercise>
    suspend fun get(id: String): Exercise?
    suspend fun save(exercise: Exercise)
}

interface IWorkoutRepository {
    suspend fun findPage(
        filter: WorkoutFilter,
        limit: Int,
        offset: Int
    ): List<Workout> {
        if (limit <= 0 || offset < 0) {
            throw AppError.ValidationError("Некоректні параметри сторінки")
        }

        return find(filter).drop(offset).take(limit)
    }
    suspend fun find(
        filter: WorkoutFilter = WorkoutFilter()
    ): List<Workout>

    suspend fun get(id: String): Workout?

    // Тренування та всі його результати зберігаються разом.
    // Наявний запис із таким id оновлюється.
    suspend fun saveAtomically(workout: Workout)

    // Видаляє тренування разом з його результатами.
    suspend fun deleteAtomically(id: String)
}

interface IGoalRepository {
    suspend fun list(): List<Goal>
    suspend fun save(goal: Goal)
    suspend fun delete(id: String)
}

sealed class AppError(message: String) : Exception(message) {
    class ValidationError(message: String) : AppError(message)

    class NotFound(
        message: String = "Запис не знайдено"
    ) : AppError(message)

    class StorageError(
        message: String = "Не вдалося виконати операцію з даними",
        cause: Throwable? = null
    ) : AppError(message) {
        init {
            if (cause != null) initCause(cause)
        }
    }
}