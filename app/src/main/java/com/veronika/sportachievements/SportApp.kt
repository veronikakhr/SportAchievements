package com.veronika.sportachievements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SportApp() {
    val context = LocalContext.current.applicationContext

    val database = remember(context) { SportDatabase(context) }
    val workoutRepository = remember(database) {
        LocalWorkoutRepository(database)
    }
    val exerciseRepository = remember(database) {
        LocalExerciseRepository(database)
    }
    val goalRepository = remember(database) {
        LocalGoalRepository(database)
    }

    val profileRepository = remember(database) {
        LocalProfileRepository(database)
    }
    val workoutService = remember(
        workoutRepository,
        exerciseRepository
    ) {
        WorkoutService(
            workouts = workoutRepository,
            exercises = exerciseRepository
        )
    }
    val profileService = remember(profileRepository) {
        ProfileService(profileRepository)
    }

    val exerciseService = remember(exerciseRepository) {
        ExerciseService(exerciseRepository)
    }

    val goalService = remember(goalRepository, exerciseRepository) {
        GoalService(
            repository = goalRepository,
            exercises = exerciseRepository
        )
    }
    val scope = rememberCoroutineScope()

    var workouts by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Workout?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(2)
    }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var detailsWorkout by remember {
        mutableStateOf<Workout?>(null)
    }
    var historyFilter by remember {
        mutableStateOf(WorkoutFilter())
    }
    var visibleWorkouts by remember {
        mutableStateOf<List<Workout>>(emptyList())
    }
    var hasMoreHistory by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(true) }
    var historyError by remember { mutableStateOf<String?>(null) }

    val matchingWorkouts  = remember(workouts, historyFilter) {
        workouts.filter { workout ->
            val from = historyFilter.dateFrom
            val to = historyFilter.dateTo
            val activity = historyFilter.activityType
            val exercise = historyFilter.exerciseId

            (from == null || workout.date >= from) &&
                    (to == null || workout.date <= to) &&
                    (activity == null || workout.activityType == activity) &&
                    (
                            exercise == null ||
                                    workout.results.any { it.exerciseId == exercise }
                            )
        }
    }

    suspend fun loadHistory(reset: Boolean) {
        historyLoading = true
        historyError = null

        val requestedFilter = historyFilter
        val offset = if (reset) 0 else visibleWorkouts.size

        try {
            // Беремо додатковий запис, щоб визначити,
            // чи потрібна кнопка наступної сторінки.
            val page = workoutRepository.findPage(
                filter = requestedFilter,
                limit = 21,
                offset = offset
            )

            if (historyFilter == requestedFilter) {
                val nextItems = page.take(20)

                visibleWorkouts = if (reset) {
                    nextItems
                } else {
                    visibleWorkouts + nextItems
                }

                hasMoreHistory = page.size > 20
            }
        } catch (error: AppError) {
            historyError = error.message
        } finally {
            historyLoading = false
        }
    }
    suspend fun reload() {
        exercises = exerciseService.list()
        workouts = workoutRepository.find(WorkoutFilter())
    }

    LaunchedEffect(historyFilter, workouts) {
        loadHistory(reset = true)
    }

    LaunchedEffect(Unit) {
        busy = true
        try {
            ensureDefaultExercises(exerciseRepository)
            val preferences = context.getSharedPreferences(
                "sport_app_settings",
                android.content.Context.MODE_PRIVATE
            )

            val demoLoaded = preferences.getBoolean(
                "demo_data_loaded",
                false
            )

            if (!demoLoaded) {
                addDemoData(
                    workoutRepository,
                    exerciseRepository,
                    goalRepository
                )

                val saved = kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {
                    preferences.edit()
                        .putBoolean("demo_data_loaded", true)
                        .commit()
                }

                if (!saved) {
                    throw AppError.StorageError(
                        "Не вдалося зберегти налаштування"
                    )
                }
            }

            reload()
        } catch (error: AppError) {
            message = error.message
        } finally {
            busy = false
        }
    }

    if (showProfile) {
        ProfileEditor(
            service = profileService,
            onClose = { showProfile = false }
        )
        return
    }

    val openedWorkout = detailsWorkout

    if (openedWorkout != null) {
        WorkoutDetailsScreen(
            workout = openedWorkout,
            exercises = exercises,
            onBack = {
                detailsWorkout = null
            },
            onEdit = {
                editingWorkout = openedWorkout
                detailsWorkout = null
                showEditor = true
            },
            onDelete = {
                pendingDelete = openedWorkout
                detailsWorkout = null
            }
        )
        return
    }

    if (showEditor) {
        WorkoutEditor(
            initial = editingWorkout,
            exercises = exercises,
            service = workoutService,
            onSaved = {
                showEditor = false
                editingWorkout = null

                scope.launch {
                    busy = true
                    message = "Тренування збережено"

                    try {
                        reload()
                    } catch (error: AppError) {
                        message =
                            "Тренування збережено, але список не оновився. " +
                                    "Перезапустіть застосунок."
                    } finally {
                        busy = false
                    }
                }
            },
            onCancel = {
                showEditor = false
                editingWorkout = null
            }
        )
        return
    }
    val exerciseNames = exercises.associate { it.id to it.name }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val titles = listOf(
                    "Вправи",
                    "Прогрес",
                    "Історія",
                    "Цілі",
                    "Профіль"
                )

                val symbols = listOf(
                    "🏋",
                    "📈",
                    "📋",
                    "🎯",
                    "👤"
                )

                titles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        enabled = !busy && !historyLoading,
                        icon = { Text(symbols[index]) },
                        label = { Text(title, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            ExercisesScreen(
                exercises = exercises,
                service = exerciseService,
                onSaved = {
                    scope.launch {
                        busy = true
                        message = null

                        try {
                            reload()
                        } catch (error: AppError) {
                            message =
                                "Вправу збережено, але список не оновився. " +
                                        "Перезапустіть застосунок."
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        if (selectedTab == 1) {
            ProgressScreen(
                workouts = workouts,
                exercises = exercises,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        if (selectedTab == 3) {
            GoalsScreen(
                workouts = workouts,
                exercises = exercises,
                service = goalService,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        if (selectedTab == 4) {
            ProfileScreen(
                service = profileService,
                onEdit = { showProfile = true },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Спортивні досягнення",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Історія тренувань",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (busy || historyLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            message?.let { text ->
                item {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                Button(
                    enabled = !busy && !historyLoading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        editingWorkout = null
                        showEditor = true
                    }
                ) {
                    Text("Додати тренування")
                }
            }

            item {
                HistoryFilters(
                    exercises = exercises,
                    appliedFilter = historyFilter,
                    onApply = {
                        if (!busy && !historyLoading) {
                            historyFilter = it
                        }
                    }
                )
            }

            item {
                Text(
                    "Показано: ${visibleWorkouts.size} із ${matchingWorkouts.size} знайдених"
                )
            }

            if (
                visibleWorkouts.isEmpty() &&
                !busy &&
                !historyLoading &&
                historyError == null
            ) {
                item {
                    Text(
                        if (workouts.isEmpty()) {
                            "Тренувань поки немає."
                        } else {
                            "За вибраними фільтрами тренувань не знайдено."
                        }
                    )
                }
            }

            items(visibleWorkouts, key = { it.id }) { workout ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = workout.activityType.title,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(formatDate(workout.date))

                        if (workout.notes.isNotBlank()) {
                            Text(workout.notes)
                        }

                        workout.results.forEachIndexed { index, result ->
                            val name = exerciseNames[result.exerciseId]
                                ?: "Вправа"

                            Text(
                                "${index + 1}. $name: ${formatResult(result)}"
                            )
                        }

                        TextButton(
                            enabled = !busy && !historyLoading,
                            onClick = {
                                detailsWorkout = workout
                            }
                        ) {
                            Text("Деталі")
                        }

                        TextButton(
                            enabled = !busy && !historyLoading,
                            onClick = {
                                editingWorkout = workout
                                showEditor = true
                            }
                        ) {
                            Text("Редагувати")
                        }

                        TextButton(
                            enabled = !busy && !historyLoading,
                            onClick = {
                                pendingDelete = workout
                            }
                        ) {
                            Text("Видалити")
                        }
                    }
                }
            }

            historyError?.let { text ->
                item {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.error
                    )

                    TextButton(
                        enabled = !busy && !historyLoading,
                        onClick = {
                            scope.launch {
                                loadHistory(
                                    reset = visibleWorkouts.isEmpty()
                                )
                            }
                        }
                    ) {
                        Text("Повторити завантаження")
                    }
                }
            }

            if (hasMoreHistory && historyError == null) {
                item {
                    OutlinedButton(
                        enabled = !busy && !historyLoading,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                loadHistory(reset = false)
                            }
                        }
                    ) {
                        Text(
                            if (historyLoading) {
                                "Завантаження…"
                            } else {
                                "Показати ще"
                            }
                        )
                    }
                }
            }
        }
    }


    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) pendingDelete = null
            },
            title = { Text("Видалити тренування?") },
            text = {
                Text(
                    "Тренування за ${formatDate(workout.date)} " +
                            "та всі його результати буде видалено."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && !historyLoading,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                workoutService.delete(
                                    id = workout.id,
                                    confirmed = true
                                )
                                pendingDelete = null
                                reload()
                                message = "Тренування видалено"
                            } catch (error: AppError) {
                                message = error.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy && !historyLoading,
                    onClick = { pendingDelete = null }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

fun formatNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value)
        .trimEnd('0')
        .trimEnd('.')

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60

    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        hours,
        minutes,
        remaining
    )
}

fun formatResult(result: Result): String {
    val weight = result.weightKg
    val reps = result.reps
    val distance = result.distanceM
    val duration = result.durationSec

    return when {
        weight != null && reps != null ->
            "${formatNumber(weight)} кг × $reps повт."

        distance != null && duration != null ->
            "${formatNumber(distance / 1000.0)} км • " +
                    formatDuration(duration)

        else -> "Немає результату"
    }
}

private suspend fun addDemoData(
    workouts: IWorkoutRepository,
    exercises: IExerciseRepository,
    goals: IGoalRepository
) {
    val running = Exercise(
        id = "demo-exercise-running",
        name = "Біг",
        activityType = ActivityType.RUNNING,
        metricType = MetricType.DISTANCE_TIME
    )

    val squat = Exercise(
        id = "demo-exercise-squat",
        name = "Присідання",
        activityType = ActivityType.STRENGTH,
        metricType = MetricType.WEIGHT_REPS
    )

    // Наявні вправи та тренування не перезаписуються.
    for (exercise in listOf(running, squat)) {
        if (exercises.get(exercise.id) == null) {
            exercises.save(exercise)
        }
    }

    val today = LocalDate.now()

    val runningWorkouts = listOf(32, 30, 28).mapIndexed { index, minutes ->
        Workout(
            id = "demo-workout-run-$index",
            date = today.minusDays((7 - index * 2).toLong()),
            activityType = ActivityType.RUNNING,
            notes = "Демонстраційна пробіжка",
            results = listOf(
                Result(
                    id = "demo-result-run-$index",
                    exerciseId = running.id,
                    distanceM = 5000,
                    durationSec = minutes * 60
                )
            )
        )
    }

    val strengthWorkouts = listOf(30.0, 35.0).mapIndexed { index, weight ->
        Workout(
            id = "demo-workout-strength-$index",
            date = today.minusDays((6 - index * 4).toLong()),
            activityType = ActivityType.STRENGTH,
            notes = "Демонстраційне силове тренування",
            results = List(3) { setIndex ->
                Result(
                    id = "demo-result-strength-$index-$setIndex",
                    exerciseId = squat.id,
                    weightKg = weight,
                    reps = 10
                )
            }
        )
    }

    for (workout in runningWorkouts + strengthWorkouts) {
        if (workouts.get(workout.id) == null) {
            workouts.saveAtomically(workout)
        }
    }

    val goalId = "demo-goal-running"

    if (goals.list().none { it.id == goalId }) {
        goals.save(
            Goal(
                id = goalId,
                context = ComparisonContext(
                    exerciseId = running.id,
                    metricType = MetricType.DISTANCE_TIME,
                    distanceM = 5000
                ),
                targetValue = 27.0 * 60,
                startedAt = today.minusDays(7),
                deadline = today.plusDays(30)
            )
        )
    }
}