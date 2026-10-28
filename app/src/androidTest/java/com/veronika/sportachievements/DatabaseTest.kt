package com.veronika.sportachievements

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DatabaseTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: SportDatabase
    private lateinit var workouts: LocalWorkoutRepository

    private val squat = Exercise(
        id = "test-squat",
        name = "Присідання",
        activityType = ActivityType.STRENGTH,
        metricType = MetricType.WEIGHT_REPS
    )

    private val press = Exercise(
        id = "test-press",
        name = "Жим лежачи",
        activityType = ActivityType.STRENGTH,
        metricType = MetricType.WEIGHT_REPS
    )

    private val running = Exercise(
        id = "test-running",
        name = "Біг",
        activityType = ActivityType.RUNNING,
        metricType = MetricType.DISTANCE_TIME
    )

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        databaseName = "test-${UUID.randomUUID()}.db"
        database = SportDatabase(context, databaseName)
        workouts = LocalWorkoutRepository(database)

        val exercises = LocalExerciseRepository(database)
        exercises.save(squat)
        exercises.save(press)
        exercises.save(running)
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

    private fun strengthWorkout(
        exerciseId: String = squat.id,
        date: LocalDate = LocalDate.now(),
        resultId: String = UUID.randomUUID().toString()
    ) = Workout(
        date = date,
        activityType = ActivityType.STRENGTH,
        notes = "Тестове тренування",
        results = listOf(
            Result(
                id = resultId,
                exerciseId = exerciseId,
                weightKg = 30.0,
                reps = 10
            )
        )
    )

    @Test
    fun workoutSurvivesDatabaseReopening() = runBlocking {
        val original = strengthWorkout()
        workouts.saveAtomically(original)

        database.close()

        database = SportDatabase(context, databaseName)
        workouts = LocalWorkoutRepository(database)

        assertEquals(original, workouts.get(original.id))
    }

    @Test
    fun dateActivityAndExerciseFiltersWorkTogether() = runBlocking {
        val today = LocalDate.now()

        val matching = strengthWorkout(
            date = today.minusDays(1)
        )

        val wrongDate = strengthWorkout(
            date = today.minusDays(10)
        )

        val wrongExercise = strengthWorkout(
            exerciseId = press.id,
            date = today.minusDays(1)
        )

        val wrongActivity = Workout(
            date = today.minusDays(1),
            activityType = ActivityType.RUNNING,
            results = listOf(
                Result(
                    exerciseId = running.id,
                    distanceM = 5000,
                    durationSec = 1800
                )
            )
        )

        listOf(
            matching,
            wrongDate,
            wrongExercise,
            wrongActivity
        ).forEach {
            workouts.saveAtomically(it)
        }

        val found = workouts.find(
            WorkoutFilter(
                dateFrom = today.minusDays(2),
                dateTo = today,
                activityType = ActivityType.STRENGTH,
                exerciseId = squat.id
            )
        )

        assertEquals(listOf(matching.id), found.map { it.id })
    }

    @Test
    fun deletingWorkoutAlsoDeletesItsResults() = runBlocking {
        val workout = strengthWorkout()
        workouts.saveAtomically(workout)

        assertEquals(1, resultCount(workout.id))

        workouts.deleteAtomically(workout.id)

        assertNull(workouts.get(workout.id))
        assertEquals(0, resultCount(workout.id))
    }

    @Test
    fun failedUpdateRestoresPreviousWorkoutAndResults() = runBlocking {
        val original = strengthWorkout()
        val other = strengthWorkout()

        workouts.saveAtomically(original)
        workouts.saveAtomically(other)

        // Навмисно використовуємо ідентифікатор результату
        // іншого тренування, щоб викликати конфлікт у базі.
        val invalidUpdate = original.copy(
            notes = "Ця зміна не повинна зберегтися",
            results = listOf(
                original.results.first().copy(
                    id = other.results.first().id,
                    weightKg = 99.0
                )
            )
        )

        var storageErrorReceived = false

        try {
            workouts.saveAtomically(invalidUpdate)
        } catch (_: AppError.StorageError) {
            storageErrorReceived = true
        }

        assertTrue(storageErrorReceived)

        // Після помилки мають залишитися попередні дані.
        assertEquals(original, workouts.get(original.id))
        assertEquals(other, workouts.get(other.id))
        assertEquals(1, resultCount(original.id))
        assertEquals(1, resultCount(other.id))
    }

    @Test
    fun pagesContainWholeWorkoutsWithoutDuplicates() = runBlocking {
        val created = mutableListOf<Workout>()

        // Створюємо 25 тренувань по 3 підходи.
        repeat(25) { workoutIndex ->
            val workout = Workout(
                id = "page-workout-$workoutIndex",
                date = LocalDate.now(),
                activityType = ActivityType.STRENGTH,
                results = List(3) { setIndex ->
                    Result(
                        id = "page-result-$workoutIndex-$setIndex",
                        exerciseId = squat.id,
                        weightKg = 30.0 + workoutIndex,
                        reps = 10
                    )
                }
            )

            workouts.saveAtomically(workout)
            created.add(workout)
        }

        val firstPage = workouts.findPage(
            filter = WorkoutFilter(),
            limit = 20,
            offset = 0
        )

        val secondPage = workouts.findPage(
            filter = WorkoutFilter(),
            limit = 20,
            offset = 20
        )

        val emptyPage = workouts.findPage(
            filter = WorkoutFilter(),
            limit = 20,
            offset = 40
        )

        assertEquals(20, firstPage.size)
        assertEquals(5, secondPage.size)
        assertTrue(emptyPage.isEmpty())

        val combined = firstPage + secondPage

        // Жодне тренування не повторюється між сторінками.
        assertEquals(25, combined.map { it.id }.distinct().size)

        // Для однакової дати останнє додане йде першим.
        assertEquals(
            created.asReversed().map { it.id },
            combined.map { it.id }
        )

        // Кожне тренування містить усі свої підходи
        // та правильний порядок результатів.
        val expected = created.associateBy { it.id }

        combined.forEach { actual ->
            assertEquals(
                expected.getValue(actual.id),
                actual
            )
            assertEquals(3, actual.results.size)
        }

        // Фільтр застосовується до всієї вибірки до поділу на сторінки.
        val filteredPage = workouts.findPage(
            filter = WorkoutFilter(
                activityType = ActivityType.STRENGTH,
                exerciseId = squat.id
            ),
            limit = 20,
            offset = 20
        )

        assertEquals(
            secondPage.map { it.id },
            filteredPage.map { it.id }
        )

        val noMatches = workouts.findPage(
            filter = WorkoutFilter(
                activityType = ActivityType.RUNNING
            ),
            limit = 20,
            offset = 0
        )

        assertTrue(noMatches.isEmpty())
    }

    private fun resultCount(workoutId: String): Int {
        return database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM results WHERE workout_id = ?",
            arrayOf(workoutId)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }
}