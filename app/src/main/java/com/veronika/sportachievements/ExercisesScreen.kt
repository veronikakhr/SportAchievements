package com.veronika.sportachievements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ExercisesScreen(
    exercises: List<Exercise>,
    service: IExerciseService,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var storageError by remember { mutableStateOf<String?>(null) }

    // Ідентифікатор залишається тим самим при повторній спробі.
    var newExerciseId by remember {
        mutableStateOf(java.util.UUID.randomUUID().toString())
    }

    val strengthExercises = exercises.filter {
        it.activityType == ActivityType.STRENGTH
    }.sortedBy { it.name.lowercase() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Вправи",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Text(
                "Додавай власні силові вправи. " +
                        "Для бігу й велосипеда активність обирається автоматично."
            )
        }

        item {
            Button(
                onClick = {
                    name = ""
                    nameError = null
                    storageError = null
                    newExerciseId = java.util.UUID.randomUUID().toString()
                    showForm = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Додати власну вправу")
            }
        }

        item {
            Text(
                text = "Силові вправи: ${strengthExercises.size}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(strengthExercises, key = { it.id }) { exercise ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Вага, кг • Повторення")
                }
            }
        }

        item {
            Text(
                text = "Дистанційні активності",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Біг — дистанція та час")
                    Text("Велосипед — дистанція та час")
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) showForm = false
            },
            title = { Text("Нова силова вправа") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                            storageError = null
                        },
                        enabled = !saving,
                        label = { Text("Назва вправи") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = {
                            nameError?.let { Text(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Показники: вага в кілограмах і повторення.")

                    storageError?.let {
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        val trimmedName = name.trim()

                        val duplicate = strengthExercises.any {
                            it.name.trim().equals(
                                trimmedName,
                                ignoreCase = true
                            )
                        }

                        when {
                            trimmedName.isBlank() -> {
                                nameError = "Введіть назву вправи"
                            }

                            duplicate -> {
                                nameError = "Така силова вправа вже є"
                            }

                            else -> {
                                scope.launch {
                                    saving = true
                                    storageError = null

                                    val saved = try {
                                        service.save(
                                            Exercise(
                                                id = newExerciseId,
                                                name = trimmedName,
                                                activityType =
                                                    ActivityType.STRENGTH,
                                                metricType =
                                                    MetricType.WEIGHT_REPS
                                            )
                                        )
                                        true
                                    } catch (problem: AppError) {
                                        storageError = problem.message
                                        false
                                    } finally {
                                        saving = false
                                    }

                                    if (saved) {
                                        showForm = false
                                        onSaved()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = { showForm = false }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}