package com.veronika.sportachievements

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PerformanceTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: SportDatabase
    private lateinit var repository: LocalWorkoutRepository

    private val exerciseId = "performance-squat"

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        databaseName = "performance-${UUID.randomUUID()}.db"
        database = SportDatabase(context, databaseName)
        repository = LocalWorkoutRepository(database)

        LocalExerciseRepository(database).save(
            Exercise(
                id = exerciseId,
                name = "Тестові присідання",
                activityType = ActivityType.STRENGTH,
                metricType = MetricType.WEIGHT_REPS
            )
        )

        seedDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }

        if (::context.isInitialized && ::databaseName.isInitialized) {
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun seedDatabase() = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        val today = LocalDate.now()

        // Підготовка даних не входить у вимірювання.
        db.beginTransaction()

        try {
            repeat(1000) { workoutIndex ->
                val workoutId = "performance-workout-$workoutIndex"

                val workoutValues = ContentValues().apply {
                    put("id", workoutId)
                    put(
                        "workout_date",
                        today.minusDays((workoutIndex % 365).toLong())
                            .toString()
                    )
                    put("activity_type", ActivityType.STRENGTH.name)
                    put("notes", "")
                }

                db.insertOrThrow("workouts", null, workoutValues)

                repeat(10) { setIndex ->
                    val resultValues = ContentValues().apply {
                        put(
                            "id",
                            "performance-result-$workoutIndex-$setIndex"
                        )
                        put("workout_id", workoutId)
                        put("exercise_id", exerciseId)
                        put("position", setIndex)
                        put(
                            "weight_kg",
                            20.0 + (workoutIndex % 50) + setIndex
                        )
                        put("reps", 10)
                    }

                    db.insertOrThrow("results", null, resultValues)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Test
    fun operationsMeetTwoSecondTarget() = runBlocking {
        val allWorkouts = repository.find(WorkoutFilter())

        assertEquals(1000, allWorkouts.size)
        assertEquals(10000, allWorkouts.sumOf { it.results.size })

        val failures = mutableListOf<String>()

        suspend fun measure(
            name: String,
            operation: suspend () -> Unit
        ) {
            // Окремий прогрів перед 10 вимірюваннями.
            operation()

            val durations = mutableListOf<Double>()

            repeat(10) {
                val started = SystemClock.elapsedRealtimeNanos()

                operation()

                val elapsedMs =
                    (SystemClock.elapsedRealtimeNanos() - started) /
                            1_000_000.0

                durations.add(elapsedMs)
            }

            val passed = durations.count { it <= 2000.0 }

            val formatted = durations.joinToString(", ") {
                String.format(java.util.Locale.US, "%.1f", it)
            }

            println("PERF | $name | ms: $formatted")
            println("PERF | $name | within 2000 ms: $passed/10")

            if (passed < 9) {
                failures.add("$name: лише $passed із 10 вимірювань до 2 с")
            }
        }

        measure("History page: 20 workouts") {
            val page = repository.findPage(
                filter = WorkoutFilter(),
                limit = 20,
                offset = 0
            )

            assertEquals(20, page.size)
            assertTrue(page.all { it.results.size == 10 })
        }

        // Поточний застосунок також читає повну історію
        // для статистики, тому вимірюємо й цю операцію.
        measure("Full history: 1000 workouts") {
            val data = repository.find(WorkoutFilter())
            assertEquals(1000, data.size)
        }

        val filter = WorkoutFilter(
            dateFrom = LocalDate.now().minusDays(30),
            dateTo = LocalDate.now(),
            activityType = ActivityType.STRENGTH,
            exerciseId = exerciseId
        )

        measure("Combined filters") {
            val filtered = repository.find(filter)
            assertTrue(filtered.isNotEmpty())
            assertTrue(
                filtered.all {
                    it.date >= filter.dateFrom!! &&
                            it.date <= filter.dateTo!!
                }
            )
        }

        val original = repository.get("performance-workout-0")
            ?: error("Тестове тренування не знайдено")

        var editNumber = 0

        measure("Save workout: 10 sets") {
            editNumber++

            repository.saveAtomically(
                original.copy(
                    notes = "Тестова зміна $editNumber",
                    results = original.results.map {
                        it.copy(weightKg = 40.0 + editNumber)
                    }
                )
            )
        }

        val achievementService = AchievementService()
        val comparison = ComparisonContext(
            exerciseId = exerciseId,
            metricType = MetricType.WEIGHT_REPS,
            reps = 10
        )

        measure("Chart: read data and calculate points") {
            val data = repository.find(WorkoutFilter())

            val points = withContext(Dispatchers.Default) {
                achievementService.chartPoints(
                    workouts = data,
                    context = comparison,
                    dateFrom = LocalDate.now().minusDays(364),
                    dateTo = LocalDate.now()
                )
            }

            assertTrue(points.isNotEmpty())
        }

        assertEquals(
            1000,
            repository.find(WorkoutFilter()).size
        )

        println(
            "PERF | Dataset: 1000 workouts, 10000 results"
        )

        assertTrue(
            failures.joinToString("\n"),
            failures.isEmpty()
        )
    }
}