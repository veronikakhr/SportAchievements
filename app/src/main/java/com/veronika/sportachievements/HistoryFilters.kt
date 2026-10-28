package com.veronika.sportachievements

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun HistoryFilters(
    exercises: List<Exercise>,
    appliedFilter: WorkoutFilter,
    onApply: (WorkoutFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var dateFrom by remember {
        mutableStateOf(appliedFilter.dateFrom?.toString().orEmpty())
    }
    var dateTo by remember {
        mutableStateOf(appliedFilter.dateTo?.toString().orEmpty())
    }
    var activity by remember {
        mutableStateOf(appliedFilter.activityType)
    }
    var exerciseId by remember {
        mutableStateOf(appliedFilter.exerciseId)
    }

    var fromError by remember { mutableStateOf<String?>(null) }
    var toError by remember { mutableStateOf<String?>(null) }

    val availableExercises = exercises.filter {
        activity == null || it.activityType == activity
    }

    val filterCount = listOf(
        appliedFilter.dateFrom,
        appliedFilter.dateTo,
        appliedFilter.activityType,
        appliedFilter.exerciseId
    ).count { it != null }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (filterCount == 0) {
                        "Фільтри"
                    } else {
                        "Фільтри • застосовано: $filterCount"
                    }
                )
            }

            if (expanded) {
                DateInput(
                    value = dateFrom,
                    onValueChange = {
                        dateFrom = it
                        fromError = null
                    },
                    label = "Початкова дата",
                    allowClear = true,
                    error = fromError
                )

                DateInput(
                    value = dateTo,
                    onValueChange = {
                        dateTo = it
                        toError = null
                    },
                    label = "Кінцева дата",
                    allowClear = true,
                    error = toError
                )

                ChoiceField(
                    label = "Активність",
                    selected = activity?.title ?: "Усі активності",
                    options = listOf("Усі активності") +
                            ActivityType.values().map { it.title },
                    onSelected = { index ->
                        activity = if (index == 0) {
                            null
                        } else {
                            ActivityType.values()[index - 1]
                        }

                        exerciseId = null
                    }
                )

                ChoiceField(
                    label = "Вправа",
                    selected = availableExercises.firstOrNull {
                        it.id == exerciseId
                    }?.name ?: "Усі вправи",
                    options = listOf("Усі вправи") +
                            availableExercises.map { it.name },
                    onSelected = { index ->
                        exerciseId = if (index == 0) {
                            null
                        } else {
                            availableExercises[index - 1].id
                        }
                    }
                )

                Text("Порожні дати означають відсутність обмеження.")

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        fromError = null
                        toError = null

                        val from = try {
                            dateFrom.trim().takeIf { it.isNotEmpty() }
                                ?.let { LocalDate.parse(it) }
                        } catch (_: java.time.format.DateTimeParseException) {
                            fromError = "Введіть дійсну дату: рік-місяць-день"
                            null
                        }

                        val to = try {
                            dateTo.trim().takeIf { it.isNotEmpty() }
                                ?.let { LocalDate.parse(it) }
                        } catch (_: java.time.format.DateTimeParseException) {
                            toError = "Введіть дійсну дату: рік-місяць-день"
                            null
                        }

                        if (
                            fromError == null &&
                            toError == null &&
                            from != null &&
                            to != null &&
                            from > to
                        ) {
                            toError = "Кінцева дата має бути не раніше початкової"
                        }

                        if (fromError == null && toError == null) {
                            onApply(
                                WorkoutFilter(
                                    dateFrom = from,
                                    dateTo = to,
                                    activityType = activity,
                                    exerciseId = exerciseId
                                )
                            )
                            expanded = false
                        }
                    }
                ) {
                    Text("Застосувати")
                }
            }

            if (filterCount > 0 || expanded) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        dateFrom = ""
                        dateTo = ""
                        activity = null
                        exerciseId = null
                        fromError = null
                        toError = null

                        onApply(WorkoutFilter())
                        expanded = false
                    }
                ) {
                    Text("Скинути фільтри")
                }
            }
        }
    }
}