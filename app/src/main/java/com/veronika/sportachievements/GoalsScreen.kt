package com.veronika.sportachievements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun GoalsScreen(
    workouts: List<Workout>,
    exercises: List<Exercise>,
    service: IGoalService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val achievementService = remember { AchievementService() }

    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    var showEditor by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var deletingGoal by remember { mutableStateOf<Goal?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        error = null

        try {
            goals = service.list()
        } catch (problem: AppError) {
            error = problem.message
        } finally {
            loading = false
        }
    }

    if (showEditor) {
        GoalEditor(
            initial = editingGoal,
            exercises = exercises,
            service = service,
            onClose = {
                showEditor = false
                editingGoal = null
            },
            onSaved = {
                showEditor = false
                editingGoal = null
                refresh++
            },
            modifier = modifier
        )
        return
    }

    val progress = achievementService.allGoalProgress(goals, workouts)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Спортивні цілі",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Button(
                enabled = !loading && !deleting,
                onClick = {
                    editingGoal = null
                    showEditor = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Створити ціль")
            }
        }

        if (loading || deleting) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        error?.let { text ->
            item {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { refresh++ }) {
                    Text("Повторити завантаження")
                }
            }
        }

        if (goals.isEmpty() && !loading && error == null) {
            item {
                Text("Цілей поки немає. Створіть свою першу ціль.")
            }
        }

        items(progress, key = { it.goal.id }) { item ->
            val goal = item.goal
            val exercise = exercises.firstOrNull {
                it.id == goal.context.exerciseId
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exercise?.name ?: "Вправа",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(goalContextText(goal.context))

                    Text(
                        "Ціль: " + formatMetricValue(
                            goal.targetValue,
                            goal.context.metricType
                        )
                    )

                    Text(
                        "Найкращий результат за період: " +
                                (
                                        item.currentValue?.let {
                                            formatMetricValue(
                                                it,
                                                goal.context.metricType
                                            )
                                        } ?: "немає даних"
                                        )
                    )

                    Text(
                        text = "Статус: ${item.status.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.status == GoalStatus.ACHIEVED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        "${formatDate(goal.startedAt)} — " +
                                formatDate(goal.deadline)
                    )

                    TextButton(
                        enabled = !loading && !deleting,
                        onClick = {
                            editingGoal = goal
                            showEditor = true
                        }
                    ) {
                        Text("Редагувати")
                    }

                    TextButton(
                        enabled = !loading && !deleting,
                        onClick = { deletingGoal = goal }
                    ) {
                        Text("Видалити")
                    }
                }
            }
        }
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = {
                if (!deleting) deletingGoal = null
            },
            title = { Text("Видалити ціль?") },
            text = {
                Text("Тренування та їхні результати залишаться.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        scope.launch {
                            deleting = true
                            error = null

                            try {
                                service.delete(goal.id)
                                deletingGoal = null
                                refresh++
                            } catch (problem: AppError) {
                                error = problem.message
                                deletingGoal = null
                            } finally {
                                deleting = false
                            }
                        }
                    }
                ) {
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { deletingGoal = null }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

private fun goalContextText(context: ComparisonContext): String =
    when (context.metricType) {
        MetricType.WEIGHT_REPS ->
            "${context.reps} повторень • збільшення ваги"

        MetricType.DISTANCE_TIME ->
            "${formatNumber((context.distanceM ?: 0) / 1000.0)} км" +
                    " • зменшення часу"
    }

@Composable
private fun GoalEditor(
    initial: Goal?,
    exercises: List<Exercise>,
    service: IGoalService,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val originalExercise = exercises.firstOrNull {
        it.id == initial?.context?.exerciseId
    }

    val goalId = remember {
        initial?.id ?: UUID.randomUUID().toString()
    }

    val startedAt = remember {
        initial?.startedAt ?: LocalDate.now()
    }

    var activity by remember {
        mutableStateOf(
            originalExercise?.activityType ?: ActivityType.RUNNING
        )
    }

    var exerciseId by remember {
        mutableStateOf(initial?.context?.exerciseId.orEmpty())
    }

    var reps by remember {
        mutableStateOf(initial?.context?.reps?.toString() ?: "10")
    }

    var distance by remember {
        mutableStateOf(
            initial?.context?.distanceM?.let {
                java.math.BigDecimal.valueOf(it.toLong())
                    .movePointLeft(3)
                    .stripTrailingZeros()
                    .toPlainString()
            } ?: "5"
        )
    }

    var targetWeight by remember {
        mutableStateOf(
            if (initial?.context?.metricType == MetricType.WEIGHT_REPS) {
                formatNumber(initial.targetValue)
            } else {
                ""
            }
        )
    }

    var targetTime by remember {
        mutableStateOf(
            if (initial?.context?.metricType == MetricType.DISTANCE_TIME) {
                formatDuration(initial.targetValue.toInt())
            } else {
                ""
            }
        )
    }

    var deadline by remember {
        mutableStateOf(
            (initial?.deadline ?: LocalDate.now().plusDays(30)).toString()
        )
    }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val availableExercises = exercises.filter {
        it.activityType == activity
    }

    BackHandler {
        if (!saving) onClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (initial == null) "Нова ціль" else "Редагування цілі",
            style = MaterialTheme.typography.headlineMedium
        )

        ChoiceField(
            label = "Активність",
            selected = activity.title,
            options = ActivityType.values().map { it.title },
            enabled = !saving,
            onSelected = { index ->
                activity = ActivityType.values()[index]
                exerciseId = ""
                error = null
            }
        )

        if (activity == ActivityType.STRENGTH) {
            ChoiceField(
                label = "Вправа",
                selected = availableExercises.firstOrNull {
                    it.id == exerciseId
                }?.name ?: "Оберіть вправу",
                options = availableExercises.map { it.name },
                enabled = !saving && availableExercises.isNotEmpty(),
                onSelected = { index ->
                    exerciseId = availableExercises[index].id
                }
            )

            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                enabled = !saving,
                label = { Text("Кількість повторень") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = targetWeight,
                onValueChange = { targetWeight = it },
                enabled = !saving,
                label = { Text("Цільова вага, кг") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = distance,
                onValueChange = { distance = it },
                enabled = !saving,
                label = { Text("Дистанція, км") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Цільовий час")

            key(activity) {
                DurationInput(
                    value = targetTime,
                    enabled = !saving,
                    onValueChange = { targetTime = it }
                )
            }
        }

        Text("Початок обліку: ${formatDate(startedAt)}")

        DateInput(
            value = deadline,
            onValueChange = {
                deadline = it
                error = null
            },
            label = "Кінцева дата цілі",
            enabled = !saving,
            minDate = startedAt
        )

        Text(
            "Враховуються результати від початкової до кінцевої дати " +
                    "включно. Під час редагування початкова дата зберігається."
        )

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (saving) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    saving = true
                    error = null

                    val saved = try {
                        val parsedDeadline = try {
                            LocalDate.parse(deadline.trim())
                        } catch (_: java.time.format.DateTimeParseException) {
                            throw AppError.ValidationError(
                                "Введіть дійсну кінцеву дату: рік-місяць-день"
                            )
                        }

                        if (parsedDeadline < startedAt) {
                            throw AppError.ValidationError(
                                "Кінцева дата має бути не раніше початкової"
                            )
                        }

                        val context: ComparisonContext
                        val target: Double

                        if (activity == ActivityType.STRENGTH) {
                            val selected = availableExercises.firstOrNull {
                                it.id == exerciseId
                            } ?: throw AppError.ValidationError(
                                "Оберіть силову вправу"
                            )

                            val parsedReps = reps.trim().toIntOrNull()
                            val parsedWeight = targetWeight.trim()
                                .replace(',', '.')
                                .toDoubleOrNull()

                            if (parsedReps == null || parsedReps <= 0) {
                                throw AppError.ValidationError(
                                    "Повторення мають бути додатним цілим числом"
                                )
                            }

                            if (
                                parsedWeight == null ||
                                !parsedWeight.isFinite() ||
                                parsedWeight < 0
                            ) {
                                throw AppError.ValidationError(
                                    "Введіть цільову вагу від 0 кг"
                                )
                            }

                            context = ComparisonContext(
                                exerciseId = selected.id,
                                metricType = MetricType.WEIGHT_REPS,
                                reps = parsedReps
                            )
                            target = parsedWeight
                        } else {
                            val defaultId =
                                if (activity == ActivityType.RUNNING) {
                                    "demo-exercise-running"
                                } else {
                                    "default-exercise-cycling"
                                }

                            val selected = availableExercises.firstOrNull {
                                it.id == defaultId
                            } ?: throw AppError.ValidationError(
                                "Активність не знайдено"
                            )

                            context = ComparisonContext(
                                exerciseId = selected.id,
                                metricType = MetricType.DISTANCE_TIME,
                                distanceM = parseDistanceMeters(distance)
                            )
                            target = parseDurationSeconds(targetTime).toDouble()
                        }

                        service.save(
                            Goal(
                                id = goalId,
                                context = context,
                                targetValue = target,
                                startedAt = startedAt,
                                deadline = parsedDeadline
                            )
                        )

                        true
                    } catch (problem: AppError) {
                        error = problem.message
                        false
                    } finally {
                        saving = false
                    }

                    if (saved) onSaved()
                }
            }
        ) {
            Text("Зберегти ціль")
        }

        TextButton(
            enabled = !saving,
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Скасувати")
        }
    }
}