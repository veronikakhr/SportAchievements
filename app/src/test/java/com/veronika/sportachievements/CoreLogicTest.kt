package com.veronika.sportachievements

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {

    private val squat = Exercise(
        id = "squat",
        name = "Присідання",
        activityType = ActivityType.STRENGTH,
        metricType = MetricType.WEIGHT_REPS
    )

    private fun strengthWorkout(
        weight: Double = 30.0,
        reps: Int = 10,
        date: LocalDate = LocalDate.now()
    ) = Workout(
        date = date,
        activityType = ActivityType.STRENGTH,
        results = listOf(
            Result(
                exerciseId = squat.id,
                weightKg = weight,
                reps = reps
            )
        )
    )

    private fun runningWorkout(
        minutes: Int,
        distance: Int = 5000,
        date: LocalDate = LocalDate.now()
    ) = Workout(
        date = date,
        activityType = ActivityType.RUNNING,
        results = listOf(
            Result(
                exerciseId = "running",
                distanceM = distance,
                durationSec = minutes * 60
            )
        )
    )

    @Test
    fun validWorkoutIsSaved() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )
        val workout = strengthWorkout()

        service.save(workout)

        assertEquals(workout, repository.get(workout.id))
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun futureWorkoutIsRejectedBeforeSaving() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )

        expectValidation {
            service.save(
                strengthWorkout(date = LocalDate.now().plusDays(1))
            )
        }

        assertEquals(0, repository.saveCalls)
        assertTrue(repository.find(WorkoutFilter()).isEmpty())
    }

    @Test
    fun zeroWeightIsAllowedForBodyweightExercise() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )
        val workout = strengthWorkout(weight = 0.0)

        service.save(workout)

        assertNotNull(repository.get(workout.id))
    }

    @Test
    fun zeroRepetitionsAreRejected() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )

        expectValidation {
            service.save(strengthWorkout(reps = 0))
        }

        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun exerciseFromDifferentActivityIsRejected() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )

        val workout = Workout(
            activityType = ActivityType.RUNNING,
            date = LocalDate.now(),
            results = listOf(
                Result(
                    exerciseId = squat.id,
                    distanceM = 5000,
                    durationSec = 1800
                )
            )
        )

        expectValidation {
            service.save(workout)
        }

        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun editingUpdatesExistingWorkoutWithoutDuplicate() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )
        val original = strengthWorkout()

        service.save(original)

        val updated = original.copy(
            results = original.results.map {
                it.copy(weightKg = 42.0)
            }
        )

        service.update(original.id, updated)

        assertEquals(1, repository.find(WorkoutFilter()).size)
        assertEquals(updated, repository.get(original.id))
    }

    @Test
    fun deletionRequiresConfirmation() = runBlocking {
        val repository = MemoryWorkouts()
        val service = WorkoutService(
            repository,
            MemoryExercises(listOf(squat))
        )
        val workout = strengthWorkout()
        service.save(workout)

        expectValidation {
            service.delete(workout.id, confirmed = false)
        }

        assertNotNull(repository.get(workout.id))

        service.delete(workout.id, confirmed = true)

        assertNull(repository.get(workout.id))
    }

    @Test
    fun strengthRecordComparesOnlySameRepetitions() {
        val service = AchievementService()
        val workouts = listOf(
            strengthWorkout(weight = 40.0, reps = 10),
            strengthWorkout(weight = 60.0, reps = 5),
            strengthWorkout(weight = 45.0, reps = 10)
        )

        val record = service.personalRecord(
            workouts,
            ComparisonContext(
                exerciseId = squat.id,
                metricType = MetricType.WEIGHT_REPS,
                reps = 10
            )
        )

        assertNotNull(record)
        assertEquals(45.0, record!!.value, 0.0)
    }

    @Test
    fun runningRecordComparesOnlySameDistance() {
        val service = AchievementService()

        val record = service.personalRecord(
            listOf(
                runningWorkout(minutes = 30),
                runningWorkout(minutes = 10, distance = 1000),
                runningWorkout(minutes = 28)
            ),
            ComparisonContext(
                exerciseId = "running",
                metricType = MetricType.DISTANCE_TIME,
                distanceM = 5000
            )
        )

        assertNotNull(record)
        assertEquals(1680.0, record!!.value, 0.0)
    }

    @Test
    fun equalResultKeepsEarlierRecord() {
        val service = AchievementService()
        val first = runningWorkout(
            minutes = 28,
            date = LocalDate.now().minusDays(2)
        )
        val second = runningWorkout(
            minutes = 28,
            date = LocalDate.now().minusDays(1)
        )

        val record = service.personalRecord(
            listOf(second, first),
            ComparisonContext(
                exerciseId = "running",
                metricType = MetricType.DISTANCE_TIME,
                distanceM = 5000
            )
        )

        assertEquals(first.id, record?.workoutId)
    }

    @Test
    fun achievedGoalStaysAchievedAfterDeadline() {
        val today = LocalDate.of(2026, 10, 28)
        val goal = runningGoal(
            start = today.minusDays(7),
            deadline = today.minusDays(1)
        )

        val progress = AchievementService().goalProgress(
            goal,
            listOf(
                runningWorkout(
                    minutes = 27,
                    date = goal.deadline
                )
            ),
            today
        )

        assertEquals(GoalStatus.ACHIEVED, progress.status)
    }

    @Test
    fun resultsBeforeGoalStartAreIgnored() {
        val today = LocalDate.of(2026, 10, 28)
        val goal = runningGoal(
            start = today,
            deadline = today.plusDays(7)
        )

        val progress = AchievementService().goalProgress(
            goal,
            listOf(
                runningWorkout(
                    minutes = 25,
                    date = today.minusDays(1)
                )
            ),
            today
        )

        assertEquals(GoalStatus.NO_DATA, progress.status)
        assertNull(progress.currentValue)
    }

    @Test
    fun unfinishedGoalExpires() {
        val today = LocalDate.of(2026, 10, 28)
        val goal = runningGoal(
            start = today.minusDays(7),
            deadline = today.minusDays(1)
        )

        val progress = AchievementService().goalProgress(
            goal,
            listOf(
                runningWorkout(
                    minutes = 30,
                    date = today.minusDays(2)
                )
            ),
            today
        )

        assertEquals(GoalStatus.EXPIRED, progress.status)
    }

    @Test
    fun removingBestWorkoutRecalculatesRecord() {
        val service = AchievementService()
        val slower = runningWorkout(minutes = 30)
        val faster = runningWorkout(minutes = 28)

        val context = ComparisonContext(
            exerciseId = "running",
            metricType = MetricType.DISTANCE_TIME,
            distanceM = 5000
        )

        val before = service.personalRecord(
            listOf(slower, faster),
            context
        )
        val after = service.personalRecord(
            listOf(slower),
            context
        )

        assertEquals(1680.0, before!!.value, 0.0)
        assertEquals(1800.0, after!!.value, 0.0)
    }

    private fun runningGoal(
        start: LocalDate,
        deadline: LocalDate
    ) = Goal(
        context = ComparisonContext(
            exerciseId = "running",
            metricType = MetricType.DISTANCE_TIME,
            distanceM = 5000
        ),
        targetValue = 27.0 * 60,
        startedAt = start,
        deadline = deadline
    )

    private suspend fun expectValidation(
        action: suspend () -> Unit
    ) {
        try {
            action()
            fail("Очікувалася помилка перевірки даних")
        } catch (_: AppError.ValidationError) {
            // Очікуваний результат.
        }
    }
}

private class MemoryWorkouts : IWorkoutRepository {
    private val records = linkedMapOf<String, Workout>()
    var saveCalls = 0
        private set

    override suspend fun find(filter: WorkoutFilter): List<Workout> =
        records.values.filter { workout ->
            val from = filter.dateFrom
            val to = filter.dateTo
            val activity = filter.activityType
            val exercise = filter.exerciseId

            (from == null || workout.date >= from) &&
                    (to == null || workout.date <= to) &&
                    (activity == null || workout.activityType == activity) &&
                    (
                            exercise == null ||
                                    workout.results.any { it.exerciseId == exercise }
                            )
        }.sortedByDescending { it.date }

    override suspend fun get(id: String): Workout? = records[id]

    override suspend fun saveAtomically(workout: Workout) {
        saveCalls++
        records[workout.id] = workout
    }

    override suspend fun deleteAtomically(id: String) {
        if (records.remove(id) == null) {
            throw AppError.NotFound()
        }
    }
}

private class MemoryExercises(
    initial: List<Exercise>
) : IExerciseRepository {
    private val records = initial.associateBy { it.id }.toMutableMap()

    override suspend fun list(): List<Exercise> =
        records.values.toList()

    override suspend fun get(id: String): Exercise? =
        records[id]

    override suspend fun save(exercise: Exercise) {
        records[exercise.id] = exercise
    }
}