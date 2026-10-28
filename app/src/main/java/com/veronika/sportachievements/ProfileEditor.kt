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

@Composable
fun ProfileEditor(
    service: IProfileService,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(loadAttempt) {
        loading = true
        error = null

        try {
            profile = service.get()
            name = profile?.displayName.orEmpty()
            loaded = true
        } catch (problem: AppError) {
            error = problem.message
            loaded = false
        } finally {
            loading = false
        }
    }

    BackHandler {
        if (!saving) onClose()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Мій профіль",
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Ім’я зберігається лише на цьому пристрої.")

            if (loading || saving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                enabled = loaded && !loading && !saving,
                label = { Text("Ваше ім’я") },
                singleLine = true,
                isError = nameError != null,
                supportingText = {
                    nameError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!loaded && !loading) {
                OutlinedButton(
                    onClick = { loadAttempt++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Повторити завантаження")
                }
            }

            Button(
                enabled = loaded && !loading && !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmedName = name.trim()

                    if (trimmedName.isEmpty()) {
                        nameError = "Введіть ім’я"
                    } else {
                        scope.launch {
                            saving = true
                            error = null

                            val saved = try {
                                val updated = profile?.copy(
                                    displayName = trimmedName
                                ) ?: Profile(
                                    displayName = trimmedName
                                )

                                service.save(updated)
                                profile = updated
                                true
                            } catch (problem: AppError) {
                                error = problem.message
                                false
                            } finally {
                                saving = false
                            }

                            if (saved) onClose()
                        }
                    }
                }
            ) {
                Text("Зберегти профіль")
            }

            TextButton(
                enabled = !saving,
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Назад")
            }
        }
    }
}