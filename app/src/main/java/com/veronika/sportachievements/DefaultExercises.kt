package com.veronika.sportachievements

suspend fun ensureDefaultExercises(repository: IExerciseRepository) {
    val defaults = listOf(
        Exercise(
            id = "demo-exercise-running",
            name = "Біг",
            activityType = ActivityType.RUNNING,
            metricType = MetricType.DISTANCE_TIME
        ),
        Exercise(
            id = "default-exercise-cycling",
            name = "Велосипед",
            activityType = ActivityType.CYCLING,
            metricType = MetricType.DISTANCE_TIME
        ),
        Exercise(
            id = "demo-exercise-squat",
            name = "Присідання",
            activityType = ActivityType.STRENGTH,
            metricType = MetricType.WEIGHT_REPS
        )
    ) + listOf(
        "bench-press" to "Жим лежачи",
        "deadlift" to "Станова тяга",
        "lunges" to "Випади",
        "hip-thrust" to "Сідничний міст",
        "leg-press" to "Жим ногами",
        "shoulder-press" to "Жим над головою",
        "row" to "Тяга до пояса",
        "biceps" to "Згинання рук на біцепс",
        "pull-ups" to "Підтягування",
        "push-ups" to "Віджимання"
    ).map { (id, name) ->
        Exercise(
            id = "default-exercise-$id",
            name = name,
            activityType = ActivityType.STRENGTH,
            metricType = MetricType.WEIGHT_REPS
        )
    }

    for (exercise in defaults) {
        if (repository.get(exercise.id) == null) {
            repository.save(exercise)
        }
    }
}