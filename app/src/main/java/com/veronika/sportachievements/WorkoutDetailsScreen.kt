package com.veronika.sportachievements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WorkoutDetailsScreen(
    workout: Workout,
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val exerciseNames = exercises.associate { it.id to it.name }

    BackHandler(onBack = onBack)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("← До історії")
                }
            }

            item {
                Text(
                    text = "Деталі тренування",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = workout.activityType.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("Дата: ${formatDate(workout.date)}")
                        Text("Кількість результатів: ${workout.results.size}")
                    }
                }
            }

            item {
                Text(
                    text = "Результати",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            itemsIndexed(
                items = workout.results,
                key = { _, result -> result.id }
            ) { index, result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${index + 1}. " +
                                    (exerciseNames[result.exerciseId] ?: "Вправа"),
                            style = MaterialTheme.typography.titleMedium
                        )

                        val weight = result.weightKg
                        val reps = result.reps
                        val distance = result.distanceM
                        val duration = result.durationSec

                        if (weight != null && reps != null) {
                            Text("Вага: ${formatNumber(weight)} кг")
                            Text("Повторення: $reps")

                            if (weight == 0.0) {
                                Text("Вправа з власною вагою")
                            }
                        }

                        if (distance != null && duration != null) {
                            Text(
                                "Дистанція: " +
                                        "${formatNumber(distance / 1000.0)} км"
                            )
                            Text("Тривалість: ${formatDuration(duration)}")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Примітки",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            workout.notes.ifBlank {
                                "Приміток немає"
                            }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Редагувати тренування")
                }
            }

            item {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Видалити тренування")
                }
            }
        }
    }
}