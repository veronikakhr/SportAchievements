package com.veronika.sportachievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProgressScreen(
    workouts: List<Workout>,
    exercises: List<Exercise>,
    modifier: Modifier = Modifier
) {
    val service = remember { AchievementService() }

    var activity by remember {
        mutableStateOf(ActivityType.STRENGTH)
    }

    var exerciseId by remember {
        mutableStateOf<String?>(null)
    }

    var repetitions by remember { mutableStateOf("10") }
    var distance by remember { mutableStateOf("5") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }

    var appliedContext by remember {
        mutableStateOf<ComparisonContext?>(null)
    }
    var appliedFrom by remember { mutableStateOf<LocalDate?>(null) }
    var appliedTo by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val availableExercises = exercises.filter {
        it.activityType == activity
    }

    val selectedExercise = if (activity == ActivityType.STRENGTH) {
        availableExercises.firstOrNull { it.id == exerciseId }
    } else {
        val defaultId = if (activity == ActivityType.RUNNING) {
            "demo-exercise-running"
        } else {
            "default-exercise-cycling"
        }

        availableExercises.firstOrNull { it.id == defaultId }
    }

    val record = remember(workouts, appliedContext) {
        appliedContext?.let {
            service.personalRecord(workouts, it)
        }
    }

    val points = remember(
        workouts,
        appliedContext,
        appliedFrom,
        appliedTo
    ) {
        appliedContext?.let {
            service.chartPoints(
                workouts = workouts,
                context = it,
                dateFrom = appliedFrom,
                dateTo = appliedTo
            )
        }.orEmpty()
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
            text = "Прогрес",
            style = MaterialTheme.typography.headlineMedium
        )

        ChoiceField(
            label = "Активність",
            selected = activity.title,
            options = ActivityType.values().map { it.title },
            onSelected = { index ->
                activity = ActivityType.values()[index]
                exerciseId = null
                appliedContext = null
                error = null
            }
        )

        if (activity == ActivityType.STRENGTH) {
            ChoiceField(
                label = "Вправа",
                selected = selectedExercise?.name ?: "Оберіть вправу",
                options = availableExercises.map { it.name },
                enabled = availableExercises.isNotEmpty(),
                onSelected = { index ->
                    exerciseId = availableExercises[index].id
                    appliedContext = null
                }
            )

            OutlinedTextField(
                value = repetitions,
                onValueChange = { repetitions = it },
                label = { Text("Однакова кількість повторень") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = distance,
                onValueChange = { distance = it },
                label = { Text("Дистанція для порівняння, км") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "Період графіка",
            style = MaterialTheme.typography.titleMedium
        )

        DateInput(
            value = dateFrom,
            onValueChange = {
                dateFrom = it
                error = null
            },
            label = "Початкова дата графіка",
            allowClear = true
        )

        DateInput(
            value = dateTo,
            onValueChange = {
                dateTo = it
                error = null
            },
            label = "Кінцева дата графіка",
            allowClear = true
        )

        Text("Якщо дати не обрані, показується весь період.")

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                error = null

                try {
                    val exercise = selectedExercise
                        ?: throw AppError.ValidationError("Оберіть вправу")

                    val from = parseOptionalProgressDate(dateFrom)
                    val to = parseOptionalProgressDate(dateTo)

                    if (from != null && to != null && from > to) {
                        throw AppError.ValidationError(
                            "Кінцева дата має бути не раніше початкової"
                        )
                    }

                    val context = if (
                        activity == ActivityType.STRENGTH
                    ) {
                        val reps = repetitions.trim().toIntOrNull()

                        if (reps == null || reps <= 0) {
                            throw AppError.ValidationError(
                                "Введіть додатну цілу кількість повторень"
                            )
                        }

                        ComparisonContext(
                            exerciseId = exercise.id,
                            metricType = MetricType.WEIGHT_REPS,
                            reps = reps
                        )
                    } else {
                        ComparisonContext(
                            exerciseId = exercise.id,
                            metricType = MetricType.DISTANCE_TIME,
                            distanceM = parseDistanceMeters(distance)
                        )
                    }

                    appliedFrom = from
                    appliedTo = to
                    appliedContext = context
                } catch (problem: AppError) {
                    error = problem.message
                }
            }
        ) {
            Text("Показати прогрес")
        }

        val context = appliedContext

        if (context == null) {
            Text("Оберіть умови порівняння та натисніть «Показати прогрес».")
        } else {
            val name = exercises.firstOrNull {
                it.id == context.exerciseId
            }?.name ?: "Вправа"

            val comparison = when (context.metricType) {
                MetricType.WEIGHT_REPS ->
                    "${context.reps} повторень"

                MetricType.DISTANCE_TIME ->
                    "${formatNumber((context.distanceM ?: 0) / 1000.0)} км"
            }

            Text(
                text = "$name • $comparison",
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Особистий рекорд",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (record == null) {
                        Text("Немає даних за цих умов порівняння.")
                    } else {
                        Text(
                            text = formatMetricValue(
                                record.value,
                                context.metricType
                            ),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("Дата: ${formatDate(record.date)}")
                    }

                    Text("Рекорд визначається за всією історією.")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Графік прогресу",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Період: " +
                                (appliedFrom?.let { formatDate(it) } ?: "початок") +
                                " — " +
                                (appliedTo?.let { formatDate(it) } ?: "сьогодні")
                    )

                    if (points.isEmpty()) {
                        Text("За вибраний період немає зіставних результатів.")
                    } else {
                        ProgressChart(
                            points = points,
                            metricType = context.metricType
                        )

                        Text(
                            if (context.metricType == MetricType.WEIGHT_REPS) {
                                "Більша вага — кращий результат."
                            } else {
                                "Менший час — кращий результат."
                            }
                        )

                        Text("На графіку — найкращий результат за кожен день.")

                        if (points.size == 1) {
                            Text(
                                "Поки є одна точка. Для лінії потрібні " +
                                        "результати щонайменше за два різні дні."
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseOptionalProgressDate(text: String): LocalDate? {
    if (text.isBlank()) return null

    return try {
        LocalDate.parse(text.trim())
    } catch (_: java.time.format.DateTimeParseException) {
        throw AppError.ValidationError(
            "Введіть дійсні дати у форматі рік-місяць-день"
        )
    }
}

fun formatMetricValue(value: Double, metricType: MetricType): String =
    when (metricType) {
        MetricType.WEIGHT_REPS -> "${formatNumber(value)} кг"
        MetricType.DISTANCE_TIME -> formatDuration(value.toInt())
    }

@Composable
private fun ProgressChart(
    points: List<ChartPoint>,
    metricType: MetricType
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline

    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val span = maximum - minimum

    val margin = if (span == 0.0) {
        maxOf(kotlin.math.abs(maximum) * 0.1, 1.0)
    } else {
        span * 0.1
    }

    val lower = maxOf(0.0, minimum - margin)
    val upper = maximum + margin
    val valueRange = upper - lower

    val firstDay = points.first().date.toEpochDay()
    val lastDay = points.last().date.toEpochDay()
    val dayRange = lastDay - firstDay

    Text(
        "Верхня межа: ${formatMetricValue(upper, metricType)}",
        style = MaterialTheme.typography.bodySmall
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val inset = 10.dp.toPx()
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset

        drawLine(
            color = axisColor,
            start = Offset(left, top),
            end = Offset(left, bottom),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = axisColor,
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            strokeWidth = 1.dp.toPx()
        )

        val coordinates = points.map { point ->
            val xFraction = if (dayRange == 0L) {
                0.5f
            } else {
                (
                        (point.date.toEpochDay() - firstDay).toDouble() /
                                dayRange.toDouble()
                        ).toFloat()
            }

            val yFraction = (
                    (point.value - lower) / valueRange
                    ).toFloat()

            Offset(
                x = left + xFraction * (right - left),
                y = bottom - yFraction * (bottom - top)
            )
        }

        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx()
            )
        }

        coordinates.forEach { position ->
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = position
            )
        }
    }

    Text(
        "Нижня межа: ${formatMetricValue(lower, metricType)}",
        style = MaterialTheme.typography.bodySmall
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            formatDate(points.first().date),
            style = MaterialTheme.typography.bodySmall
        )

        if (points.size > 1) {
            Text(
                formatDate(points.last().date),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Text(
        text = "Результати за період",
        style = MaterialTheme.typography.titleSmall
    )

    points.takeLast(10).forEach { point ->
        Text(
            "${formatDate(point.date)} — " +
                    formatMetricValue(point.value, metricType)
        )
    }

    if (points.size > 10) {
        Text("У списку показано останні 10 дат; графік містить увесь період.")
    }
}