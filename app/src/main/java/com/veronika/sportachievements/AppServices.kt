package com.veronika.sportachievements

interface IProfileService {
    suspend fun get(): Profile?
    suspend fun save(profile: Profile)
}

interface IExerciseService {
    suspend fun list(): List<Exercise>
    suspend fun get(id: String): Exercise?
    suspend fun save(exercise: Exercise)
}

interface IGoalService {
    suspend fun list(): List<Goal>
    suspend fun save(goal: Goal)
    suspend fun delete(id: String)
}

class ProfileService(
    private val repository: IProfileRepository
) : IProfileService {

    override suspend fun get(): Profile? = repository.get()

    override suspend fun save(profile: Profile) {
        val name = profile.displayName.trim()

        if (name.isBlank()) {
            throw AppError.ValidationError("Введіть ім’я")
        }

        if (profile.id.isBlank()) {
            throw AppError.ValidationError(
                "Відсутній ідентифікатор профілю"
            )
        }

        repository.save(profile.copy(displayName = name))
    }
}

class ExerciseService(
    private val repository: IExerciseRepository
) : IExerciseService {

    override suspend fun list(): List<Exercise> = repository.list()

    override suspend fun get(id: String): Exercise? = repository.get(id)

    override suspend fun save(exercise: Exercise) {
        val name = exercise.name.trim()

        if (name.isBlank()) {
            throw AppError.ValidationError("Введіть назву вправи")
        }

        if (exercise.id.isBlank()) {
            throw AppError.ValidationError(
                "Відсутній ідентифікатор вправи"
            )
        }

        val expectedMetric =
            if (exercise.activityType == ActivityType.STRENGTH) {
                MetricType.WEIGHT_REPS
            } else {
                MetricType.DISTANCE_TIME
            }

        if (exercise.metricType != expectedMetric) {
            throw AppError.ValidationError(
                "Тип показників не відповідає виду активності"
            )
        }

        val existing = repository.get(exercise.id)

        if (
            existing != null &&
            (
                    existing.activityType != exercise.activityType ||
                            existing.metricType != exercise.metricType
                    )
        ) {
            throw AppError.ValidationError(
                "Для іншого виду активності створіть нову вправу"
            )
        }

        val duplicate = repository.list().any {
            it.id != exercise.id &&
                    it.activityType == exercise.activityType &&
                    it.name.trim().equals(name, ignoreCase = true)
        }

        if (duplicate) {
            throw AppError.ValidationError(
                "Вправа з такою назвою вже є"
            )
        }

        repository.save(exercise.copy(name = name))
    }
}

class GoalService(
    private val repository: IGoalRepository,
    private val exercises: IExerciseRepository
) : IGoalService {

    override suspend fun list(): List<Goal> = repository.list()

    override suspend fun save(goal: Goal) {
        if (goal.id.isBlank()) {
            throw AppError.ValidationError(
                "Відсутній ідентифікатор цілі"
            )
        }

        if (goal.deadline < goal.startedAt) {
            throw AppError.ValidationError(
                "Кінцева дата має бути не раніше початкової"
            )
        }

        if (!goal.targetValue.isFinite()) {
            throw AppError.ValidationError(
                "Введіть коректний цільовий результат"
            )
        }

        val exercise = exercises.get(goal.context.exerciseId)
            ?: throw AppError.NotFound("Вправу для цілі не знайдено")

        if (exercise.metricType != goal.context.metricType) {
            throw AppError.ValidationError(
                "Тип цілі не відповідає показникам вправи"
            )
        }

        when (goal.context.metricType) {
            MetricType.WEIGHT_REPS -> {
                val reps = goal.context.reps

                if (reps == null || reps <= 0) {
                    throw AppError.ValidationError(
                        "Повторення мають бути додатним цілим числом"
                    )
                }

                if (
                    goal.context.distanceM != null ||
                    goal.targetValue < 0
                ) {
                    throw AppError.ValidationError(
                        "Для силової цілі вкажіть вагу від 0 кг"
                    )
                }
            }

            MetricType.DISTANCE_TIME -> {
                val distance = goal.context.distanceM

                if (distance == null || distance <= 0) {
                    throw AppError.ValidationError(
                        "Дистанція має бути більшою за нуль"
                    )
                }

                if (
                    goal.context.reps != null ||
                    goal.targetValue <= 0 ||
                    goal.targetValue > Int.MAX_VALUE.toDouble() ||
                    goal.targetValue % 1.0 != 0.0
                ) {
                    throw AppError.ValidationError(
                        "Час має бути додатною цілою кількістю секунд"
                    )
                }
            }
        }

        repository.save(goal)
    }

    override suspend fun delete(id: String) {
        repository.delete(id)
    }
}