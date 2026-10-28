package com.veronika.sportachievements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private data class ResultDraft(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String = "",
    val weight: String = "",
    val reps: String = "",
    val distance: String = "",
    val duration: String = ""
)

@Composable
fun WorkoutEditor(
    initial: Workout?,
    exercises: List<Exercise>,
    service: IWorkoutService,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val workoutId = remember {
        initial?.id ?: UUID.randomUUID().toString()
    }

    var date by remember {
        mutableStateOf((initial?.date ?: LocalDate.now()).toString())
    }

    var activity by remember {
        mutableStateOf(initial?.activityType ?: ActivityType.STRENGTH)
    }

    var notes by remember {
        mutableStateOf(initial?.notes.orEmpty())
    }

    val drafts = remember {
        mutableStateListOf<ResultDraft>().apply {
            val existing = initial?.results.orEmpty()

            if (existing.isEmpty()) {
                add(ResultDraft())
            } else {
                addAll(existing.map { result ->
                    ResultDraft(
                        id = result.id,
                        exerciseId = result.exerciseId,
                        weight = result.weightKg?.let {
                            formatNumber(it)
                        }.orEmpty(),
                        reps = result.reps?.toString().orEmpty(),
                        distance = result.distanceM?.let {
                            BigDecimal.valueOf(it.toLong())
                                .movePointLeft(3)
                                .stripTrailingZeros()
                                .toPlainString()
                        }.orEmpty(),
                        duration = result.durationSec?.let {
                            formatDuration(it)
                        }.orEmpty()
                    )
                })
            }
        }
    }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val availableExercises = exercises.filter {
        it.activityType == activity
    }

    BackHandler {
        if (!busy) onCancel()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) {
                    "Нове тренування"
                } else {
                    "Редагування тренування"
                },
                style = MaterialTheme.typography.headlineMedium
            )

            DateInput(
                value = date,
                onValueChange = {
                    date = it
                    error = null
                },
                label = "Дата тренування",
                enabled = !busy,
                maxDate = LocalDate.now()
            )

            ChoiceField(
                label = "Вид активності",
                selected = activity.title,
                options = ActivityType.values().map { it.title },
                enabled = !busy,
                onSelected = { index ->
                    val selected = ActivityType.values()[index]

                    if (selected != activity) {
                        activity = selected
                        drafts.clear()
                        drafts.add(ResultDraft())
                        error = null
                    }
                }
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                enabled = !busy,
                label = { Text("Примітки — необов’язково") },
                modifier = Modifier.fillMaxWidth()
            )

            if (availableExercises.isEmpty()) {
                Text(
                    "Для цієї активності ще немає вправ. " +
                            "Спочатку потрібно створити вправу."
                )
            }

            drafts.forEachIndexed { index, draft ->
                key(draft.id) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Результат ${index + 1}",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (activity == ActivityType.STRENGTH) {
                                ChoiceField(
                                    label = "Вправа",
                                    selected = availableExercises.firstOrNull {
                                        it.id == draft.exerciseId
                                    }?.name ?: "Оберіть вправу",
                                    options = availableExercises.map { it.name },
                                    enabled = !busy && availableExercises.isNotEmpty(),
                                    onSelected = { selectedIndex ->
                                        drafts[index] = draft.copy(
                                            exerciseId = availableExercises[selectedIndex].id
                                        )
                                    }
                                )
                            }

                            if (activity == ActivityType.STRENGTH) {
                                OutlinedTextField(
                                    value = draft.weight,
                                    onValueChange = {
                                        drafts[index] = draft.copy(weight = it)
                                    },
                                    enabled = !busy,
                                    label = { Text("Вага, кг") },
                                    supportingText = {
                                        Text("0 — вправа з власною вагою")
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = draft.reps,
                                    onValueChange = {
                                        drafts[index] = draft.copy(reps = it)
                                    },
                                    enabled = !busy,
                                    label = { Text("Повторення") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedTextField(
                                    value = draft.distance,
                                    onValueChange = {
                                        drafts[index] = draft.copy(distance = it)
                                    },
                                    enabled = !busy,
                                    label = { Text("Дистанція, км") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DurationInput(
                                    value = draft.duration,
                                    enabled = !busy,
                                    onValueChange = {
                                        drafts[index] = draft.copy(duration = it)
                                    }
                                )
                            }

                            TextButton(
                                enabled = !busy && drafts.size > 1,
                                onClick = { drafts.removeAt(index) }
                            ) {
                                Text("Прибрати результат")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                enabled = !busy && availableExercises.isNotEmpty(),
                onClick = { drafts.add(ResultDraft()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (activity == ActivityType.STRENGTH) {
                        "Додати підхід або вправу"
                    } else {
                        "Додати результат"
                    }
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                enabled = !busy && availableExercises.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null

                        val saved = try {
                            val parsedDate = try {
                                LocalDate.parse(date.trim())
                            } catch (_: java.time.format.DateTimeParseException) {
                                throw AppError.ValidationError(
                                    "Введіть дійсну дату у форматі 2026-10-27"
                                )
                            }

                            val results = drafts.map { draft ->
                                val selectedExerciseId =
                                    if (activity == ActivityType.STRENGTH) {
                                        draft.exerciseId
                                    } else {
                                        val defaultId = when (activity) {
                                            ActivityType.RUNNING -> "demo-exercise-running"
                                            ActivityType.CYCLING -> "default-exercise-cycling"
                                            else -> ""
                                        }

                                        availableExercises.firstOrNull {
                                            it.id == defaultId
                                        }?.id.orEmpty()
                                    }

                                if (selectedExerciseId.isBlank()) {
                                    throw AppError.ValidationError("Оберіть вправу")
                                }

                                if (activity == ActivityType.STRENGTH) {
                                    val weight = draft.weight.trim()
                                        .replace(',', '.')
                                        .toDoubleOrNull()

                                    val reps = draft.reps.trim().toIntOrNull()

                                    if (
                                        weight == null ||
                                        !weight.isFinite() ||
                                        weight < 0 ||
                                        reps == null ||
                                        reps <= 0
                                    ) {
                                        throw AppError.ValidationError(
                                            "Перевірте вагу та повторення"
                                        )
                                    }

                                    Result(
                                        id = draft.id,
                                        exerciseId = selectedExerciseId,
                                        weightKg = weight,
                                        reps = reps
                                    )
                                } else {
                                    Result(
                                        id = draft.id,
                                        exerciseId = selectedExerciseId,
                                        distanceM = parseDistanceMeters(
                                            draft.distance
                                        ),
                                        durationSec = parseDurationSeconds(
                                            draft.duration
                                        )
                                    )
                                }
                            }

                            val workout = Workout(
                                id = workoutId,
                                date = parsedDate,
                                activityType = activity,
                                notes = notes,
                                results = results
                            )

                            if (initial == null) {
                                service.save(workout)
                            } else {
                                service.update(initial.id, workout)
                            }

                            true
                        } catch (problem: AppError) {
                            error = problem.message
                            false
                        } finally {
                            busy = false
                        }

                        if (saved) onSaved()
                    }
                }
            ) {
                Text("Зберегти тренування")
            }

            TextButton(
                enabled = !busy,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Скасувати")
            }
        }
    }
}

@Composable
fun ChoiceField(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)

        Box {
            OutlinedButton(
                enabled = enabled,
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selected)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, title ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        }
                    )
                }
            }
        }
    }
}

fun parseDistanceMeters(text: String): Int {
    val meters = try {
        BigDecimal(text.trim().replace(',', '.'))
            .movePointRight(3)
            .intValueExact()
    } catch (_: NumberFormatException) {
        throw AppError.ValidationError("Введіть коректну дистанцію")
    } catch (_: ArithmeticException) {
        throw AppError.ValidationError(
            "Дистанція завелика або містить частини менші за метр"
        )
    }

    if (meters <= 0) {
        throw AppError.ValidationError("Дистанція має бути більшою за нуль")
    }

    return meters
}

fun parseDurationSeconds(text: String): Int {
    val parts = text.trim().split(':')

    if (
        parts.size != 3 ||
        parts.any { part ->
            part.isEmpty() || part.any { it !in '0'..'9' }
        }
    ) {
        throw AppError.ValidationError(
            "Введіть час у форматі гг:хх:сс, наприклад 00:28:00"
        )
    }

    val hours = parts[0].toLongOrNull()
    val minutes = parts[1].toLongOrNull()
    val seconds = parts[2].toLongOrNull()

    if (
        hours == null || hours > Int.MAX_VALUE.toLong() / 3600 ||
        minutes == null || minutes !in 0L..59L ||
        seconds == null || seconds !in 0L..59L
    ) {
        throw AppError.ValidationError("Перевірте години, хвилини та секунди")
    }

    val total = hours * 3600 + minutes * 60 + seconds

    if (total <= 0 || total > Int.MAX_VALUE.toLong()) {
        throw AppError.ValidationError(
            "Час має бути більшим за нуль і не перевищувати допустимий"
        )
    }

    return total.toInt()
}
@Composable
fun DurationInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val initialParts = remember {
        value.split(':')
    }

    var hours by remember {
        mutableStateOf(initialParts.getOrNull(0).orEmpty())
    }
    var minutes by remember {
        mutableStateOf(initialParts.getOrNull(1).orEmpty())
    }
    var seconds by remember {
        mutableStateOf(initialParts.getOrNull(2).orEmpty())
    }

    fun publish() {
        onValueChange(
            listOf(hours, minutes, seconds).joinToString(":") {
                it.ifEmpty { "00" }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Тривалість")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hours,
                onValueChange = { text ->
                    if (text.length <= 2 && text.all { it in '0'..'9' }) {
                        hours = text
                        publish()
                    }
                },
                enabled = enabled,
                label = { Text("Год") },
                placeholder = { Text("00") },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType =
                            androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                modifier = Modifier.weight(1f)
            )

            Text(":")

            OutlinedTextField(
                value = minutes,
                onValueChange = { text ->
                    if (text.length <= 2 && text.all { it in '0'..'9' }) {
                        minutes = text
                        publish()
                    }
                },
                enabled = enabled,
                label = { Text("Хв") },
                placeholder = { Text("00") },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType =
                            androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                modifier = Modifier.weight(1f)
            )

            Text(":")

            OutlinedTextField(
                value = seconds,
                onValueChange = { text ->
                    if (text.length <= 2 && text.all { it in '0'..'9' }) {
                        seconds = text
                        publish()
                    }
                },
                enabled = enabled,
                label = { Text("Сек") },
                placeholder = { Text("00") },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType =
                            androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}