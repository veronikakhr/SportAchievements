package com.veronika.sportachievements

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate

class LocalWorkoutRepository(
    private val database: SportDatabase
) : IWorkoutRepository {

    override suspend fun find(
        filter: WorkoutFilter
    ): List<Workout> = queryWorkouts(filter)

    override suspend fun findPage(
        filter: WorkoutFilter,
        limit: Int,
        offset: Int
    ): List<Workout> {
        if (limit <= 0 || offset < 0) {
            throw AppError.ValidationError("Некоректні параметри сторінки")
        }

        return queryWorkouts(filter, limit, offset)
    }

    private suspend fun queryWorkouts(
        filter: WorkoutFilter,
        limit: Int? = null,
        offset: Int = 0
    ): List<Workout> = storageOperation {
        if (
            filter.dateFrom != null &&
            filter.dateTo != null &&
            filter.dateFrom > filter.dateTo
        ) {
            throw AppError.ValidationError(
                "Початкова дата має бути не пізніше кінцевої"
            )
        }

        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()

        filter.dateFrom?.let {
            conditions.add("w.workout_date >= ?")
            arguments.add(it.toString())
        }

        filter.dateTo?.let {
            conditions.add("w.workout_date <= ?")
            arguments.add(it.toString())
        }

        filter.activityType?.let {
            conditions.add("w.activity_type = ?")
            arguments.add(it.name)
        }

        filter.exerciseId?.let {
            conditions.add(
                """
                EXISTS (
                    SELECT 1 FROM results matching
                    WHERE matching.workout_id = w.id
                    AND matching.exercise_id = ?
                )
                """.trimIndent()
            )
            arguments.add(it)
        }

        readWorkouts(
            db = database.readableDatabase,
            conditions = conditions,
            arguments = arguments,
            limit = limit,
            offset = offset
        )
    }

    override suspend fun get(id: String): Workout? = storageOperation {
        readWorkouts(
            db = database.readableDatabase,
            conditions = listOf("w.id = ?"),
            arguments = listOf(id)
        ).firstOrNull()
    }

    override suspend fun saveAtomically(workout: Workout) {
        storageOperation {
            validateWorkout(workout)

            val db = database.writableDatabase

            db.beginTransaction()
            try {
                validateExercises(db, workout)

                val workoutValues = ContentValues().apply {
                    put("id", workout.id)
                    put("workout_date", workout.date.toString())
                    put("activity_type", workout.activityType.name)
                    put("notes", workout.notes.trim())
                }

                val updated = db.update(
                    "workouts",
                    workoutValues,
                    "id = ?",
                    arrayOf(workout.id)
                )

                if (updated == 0) {
                    db.insertOrThrow("workouts", null, workoutValues)
                }

                // Під час редагування замінюємо попередні підходи.
                // Якщо будь-яка операція не вдасться,
                // транзакція відновить попередні дані.
                db.delete(
                    "results",
                    "workout_id = ?",
                    arrayOf(workout.id)
                )

                workout.results.forEachIndexed { index, result ->
                    val values = ContentValues().apply {
                        put("id", result.id)
                        put("workout_id", workout.id)
                        put("exercise_id", result.exerciseId)
                        put("position", index)

                        result.weightKg?.let { put("weight_kg", it) }
                        result.reps?.let { put("reps", it) }
                        result.distanceM?.let { put("distance_m", it) }
                        result.durationSec?.let { put("duration_sec", it) }
                    }

                    db.insertOrThrow("results", null, values)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun deleteAtomically(id: String) {
        storageOperation {
            val db = database.writableDatabase

            db.beginTransaction()
            try {
                val deleted = db.delete(
                    "workouts",
                    "id = ?",
                    arrayOf(id)
                )

                if (deleted == 0) {
                    throw AppError.NotFound("Тренування не знайдено")
                }

                // Результати видаляються через ON DELETE CASCADE.
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun validateWorkout(workout: Workout) {
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

        val ids = workout.results.map { it.id }

        if (ids.any { it.isBlank() } || ids.distinct().size != ids.size) {
            throw AppError.ValidationError(
                "Підходи повинні мати різні ідентифікатори"
            )
        }

        workout.results.forEach { result ->
            if (result.exerciseId.isBlank()) {
                throw AppError.ValidationError("Оберіть вправу")
            }

            if (workout.activityType == ActivityType.STRENGTH) {
                val weight = result.weightKg
                val reps = result.reps

                if (
                    weight == null ||
                    !weight.isFinite() ||
                    weight < 0 ||
                    reps == null ||
                    reps <= 0 ||
                    result.distanceM != null ||
                    result.durationSec != null
                ) {
                    throw AppError.ValidationError(
                        "Вкажіть вагу від 0 кг і додатну кількість повторень"
                    )
                }
            } else {
                val distance = result.distanceM
                val duration = result.durationSec

                if (
                    distance == null ||
                    distance <= 0 ||
                    duration == null ||
                    duration <= 0 ||
                    result.weightKg != null ||
                    result.reps != null
                ) {
                    throw AppError.ValidationError(
                        "Дистанція та тривалість мають бути більші за нуль"
                    )
                }
            }
        }
    }

    private fun validateExercises(
        db: SQLiteDatabase,
        workout: Workout
    ) {
        val expectedMetric =
            if (workout.activityType == ActivityType.STRENGTH) {
                MetricType.WEIGHT_REPS
            } else {
                MetricType.DISTANCE_TIME
            }

        workout.results.map { it.exerciseId }.distinct().forEach { id ->
            db.query(
                "exercises",
                arrayOf("activity_type", "metric_type"),
                "id = ?",
                arrayOf(id),
                null,
                null,
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw AppError.NotFound("Обрану вправу не знайдено")
                }

                if (
                    cursor.text("activity_type") != workout.activityType.name ||
                    cursor.text("metric_type") != expectedMetric.name
                ) {
                    throw AppError.ValidationError(
                        "Вправа не відповідає виду тренування"
                    )
                }
            }
        }
    }

    private fun readWorkouts(
        db: SQLiteDatabase,
        conditions: List<String>,
        arguments: List<String>,
        limit: Int? = null,
        offset: Int = 0
    ): List<Workout> {
        val where = if (conditions.isEmpty()) {
            ""
        } else {
            "WHERE " + conditions.joinToString(" AND ")
        }
        val pagination = if (limit == null) {
            ""
        } else {
            "LIMIT ? OFFSET ?"
        }

        val queryArguments = arguments.toMutableList()

        if (limit != null) {
            queryArguments.add(limit.toString())
            queryArguments.add(offset.toString())
        }

        val sql = """
            SELECT
                w.id AS workout_id,
                w.workout_date,
                w.activity_type,
                w.notes,
                r.id AS result_id,
                r.exercise_id,
                r.weight_kg,
                r.reps,
                r.distance_m,
                r.duration_sec
            FROM (
                SELECT w.*, w.rowid AS sort_rowid
                FROM workouts w
                $where
                ORDER BY w.workout_date DESC, w.rowid DESC
                $pagination
            ) w
            LEFT JOIN results r ON r.workout_id = w.id
            ORDER BY w.workout_date DESC, w.sort_rowid DESC, r.position ASC
        """.trimIndent()

        val workouts = linkedMapOf<String, Workout>()
        val results = mutableMapOf<String, MutableList<Result>>()

        db.rawQuery(sql, queryArguments.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val workoutId = cursor.text("workout_id")

                if (workoutId !in workouts) {
                    workouts[workoutId] = Workout(
                        id = workoutId,
                        date = LocalDate.parse(cursor.text("workout_date")),
                        activityType = ActivityType.valueOf(
                            cursor.text("activity_type")
                        ),
                        notes = cursor.text("notes")
                    )
                    results[workoutId] = mutableListOf()
                }

                if (!cursor.isNull(cursor.getColumnIndexOrThrow("result_id"))) {
                    results.getValue(workoutId).add(
                        Result(
                            id = cursor.text("result_id"),
                            exerciseId = cursor.text("exercise_id"),
                            weightKg = cursor.optionalDouble("weight_kg"),
                            reps = cursor.optionalInt("reps"),
                            distanceM = cursor.optionalInt("distance_m"),
                            durationSec = cursor.optionalInt("duration_sec")
                        )
                    )
                }
            }
        }

        return workouts.values.map { workout ->
            workout.copy(results = results.getValue(workout.id).toList())
        }
    }

    private fun Cursor.optionalInt(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun Cursor.optionalDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }
}