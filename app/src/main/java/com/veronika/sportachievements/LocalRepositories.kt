package com.veronika.sportachievements

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Усі операції з базою виконуються поза потоком інтерфейсу.
internal suspend fun <T> storageOperation(
    action: () -> T
): T = withContext(Dispatchers.IO) {
    try {
        action()
    } catch (error: AppError) {
        throw error
    } catch (error: android.database.SQLException) {
        throw AppError.StorageError(cause = error)
    }
}

internal fun Cursor.text(column: String): String =
    getString(getColumnIndexOrThrow(column))

class LocalProfileRepository(
    private val database: SportDatabase
) : IProfileRepository {

    override suspend fun get(): Profile? = storageOperation {
        database.readableDatabase.query(
            "profile",
            arrayOf("id", "display_name"),
            "singleton = ?",
            arrayOf("1"),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Profile(
                    id = cursor.text("id"),
                    displayName = cursor.text("display_name")
                )
            } else {
                null
            }
        }
    }

    override suspend fun save(profile: Profile) {
        storageOperation {
            val name = profile.displayName.trim()

            if (name.isBlank()) {
                throw AppError.ValidationError("Введіть ім’я")
            }

            val db = database.writableDatabase
            val values = ContentValues().apply {
                put("id", profile.id)
                put("display_name", name)
            }

            db.beginTransaction()
            try {
                val updated = db.update(
                    "profile",
                    values,
                    "singleton = ?",
                    arrayOf("1")
                )

                if (updated == 0) {
                    values.put("singleton", 1)
                    db.insertOrThrow("profile", null, values)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}

class LocalExerciseRepository(
    private val database: SportDatabase
) : IExerciseRepository {

    override suspend fun list(): List<Exercise> = storageOperation {
        database.readableDatabase.query(
            "exercises",
            null,
            null,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toExercise())
                }
            }
        }
    }

    override suspend fun get(id: String): Exercise? = storageOperation {
        database.readableDatabase.query(
            "exercises",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toExercise() else null
        }
    }

    override suspend fun save(exercise: Exercise) {
        storageOperation {
            val name = exercise.name.trim()

            if (name.isBlank()) {
                throw AppError.ValidationError("Введіть назву вправи")
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

            val db = database.writableDatabase

            db.beginTransaction()
            try {
                // Назву можна змінювати, а тип уже створеної вправи
                // залишається сталим для коректного порівняння результатів.
                db.query(
                    "exercises",
                    null,
                    "id = ?",
                    arrayOf(exercise.id),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val existing = cursor.toExercise()

                        if (
                            existing.activityType != exercise.activityType ||
                            existing.metricType != exercise.metricType
                        ) {
                            throw AppError.ValidationError(
                                "Для іншого виду активності створіть нову вправу"
                            )
                        }
                    }
                }

                val values = ContentValues().apply {
                    put("id", exercise.id)
                    put("name", name)
                    put("activity_type", exercise.activityType.name)
                    put("metric_type", exercise.metricType.name)
                }

                val updated = db.update(
                    "exercises",
                    values,
                    "id = ?",
                    arrayOf(exercise.id)
                )

                if (updated == 0) {
                    db.insertOrThrow("exercises", null, values)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun Cursor.toExercise() = Exercise(
        id = text("id"),
        name = text("name"),
        activityType = ActivityType.valueOf(text("activity_type")),
        metricType = MetricType.valueOf(text("metric_type"))
    )
}