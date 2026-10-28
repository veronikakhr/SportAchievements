package com.veronika.sportachievements

import android.content.ContentValues

class LocalGoalRepository(
    private val database: SportDatabase
) : IGoalRepository {

    override suspend fun list(): List<Goal> = storageOperation {
        database.readableDatabase.query(
            "goals",
            null,
            null,
            null,
            null,
            null,
            "deadline ASC, id ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val repsIndex = cursor.getColumnIndexOrThrow("reps")
                    val distanceIndex =
                        cursor.getColumnIndexOrThrow("distance_m")

                    add(
                        Goal(
                            id = cursor.text("id"),
                            context = ComparisonContext(
                                exerciseId = cursor.text("exercise_id"),
                                metricType = MetricType.valueOf(
                                    cursor.text("metric_type")
                                ),
                                reps = if (cursor.isNull(repsIndex)) {
                                    null
                                } else {
                                    cursor.getInt(repsIndex)
                                },
                                distanceM = if (cursor.isNull(distanceIndex)) {
                                    null
                                } else {
                                    cursor.getInt(distanceIndex)
                                }
                            ),
                            targetValue = cursor.getDouble(
                                cursor.getColumnIndexOrThrow("target_value")
                            ),
                            startedAt = java.time.LocalDate.parse(
                                cursor.text("started_at")
                            ),
                            deadline = java.time.LocalDate.parse(
                                cursor.text("deadline")
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun save(goal: Goal) {
        storageOperation {
            validate(goal)

            val db = database.writableDatabase

            db.beginTransaction()
            try {
                db.query(
                    "exercises",
                    arrayOf("metric_type"),
                    "id = ?",
                    arrayOf(goal.context.exerciseId),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw AppError.NotFound("Вправу для цілі не знайдено")
                    }

                    if (
                        cursor.text("metric_type") !=
                        goal.context.metricType.name
                    ) {
                        throw AppError.ValidationError(
                            "Тип цілі не відповідає показникам вправи"
                        )
                    }
                }

                val values = ContentValues().apply {
                    put("id", goal.id)
                    put("exercise_id", goal.context.exerciseId)
                    put("metric_type", goal.context.metricType.name)

                    val reps = goal.context.reps
                    if (reps == null) {
                        putNull("reps")
                    } else {
                        put("reps", reps)
                    }

                    val distance = goal.context.distanceM
                    if (distance == null) {
                        putNull("distance_m")
                    } else {
                        put("distance_m", distance)
                    }

                    put("target_value", goal.targetValue)
                    put("started_at", goal.startedAt.toString())
                    put("deadline", goal.deadline.toString())
                }

                val updated = db.update(
                    "goals",
                    values,
                    "id = ?",
                    arrayOf(goal.id)
                )

                if (updated == 0) {
                    db.insertOrThrow("goals", null, values)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun delete(id: String) {
        storageOperation {
            val deleted = database.writableDatabase.delete(
                "goals",
                "id = ?",
                arrayOf(id)
            )

            if (deleted == 0) {
                throw AppError.NotFound("Ціль не знайдено")
            }
        }
    }

    private fun validate(goal: Goal) {
        if (goal.id.isBlank() || goal.context.exerciseId.isBlank()) {
            throw AppError.ValidationError("Оберіть вправу для цілі")
        }

        if (goal.deadline < goal.startedAt) {
            throw AppError.ValidationError(
                "Кінцева дата має бути не раніше початкової"
            )
        }

        if (!goal.targetValue.isFinite()) {
            throw AppError.ValidationError("Введіть коректний цільовий результат")
        }

        when (goal.context.metricType) {
            MetricType.WEIGHT_REPS -> {
                val reps = goal.context.reps

                if (
                    reps == null ||
                    reps <= 0 ||
                    goal.context.distanceM != null ||
                    goal.targetValue < 0
                ) {
                    throw AppError.ValidationError(
                        "Вкажіть додатну кількість повторень і вагу від 0 кг"
                    )
                }
            }

            MetricType.DISTANCE_TIME -> {
                val distance = goal.context.distanceM

                if (
                    distance == null ||
                    distance <= 0 ||
                    goal.context.reps != null ||
                    goal.targetValue <= 0 ||
                    goal.targetValue > Int.MAX_VALUE.toDouble() ||
                    goal.targetValue % 1.0 != 0.0
                ) {
                    throw AppError.ValidationError(
                        "Вкажіть додатну дистанцію та час у цілих секундах"
                    )
                }
            }
        }
    }
}