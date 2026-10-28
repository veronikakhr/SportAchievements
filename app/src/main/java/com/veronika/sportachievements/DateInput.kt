package com.veronika.sportachievements

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DateInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    allowClear: Boolean = false,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    error: String? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var showCalendar by remember { mutableStateOf(false) }

    val parsedDate = try {
        value.takeIf { it.isNotBlank() }?.let {
            LocalDate.parse(it)
        }
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        OutlinedButton(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                focusManager.clearFocus()
                showCalendar = true
            }
        ) {
            Text(
                text = parsedDate?.let { formatDate(it) }
                    ?: "Обрати дату"
            )
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (allowClear && value.isNotBlank()) {
            TextButton(
                enabled = enabled,
                onClick = { onValueChange("") }
            ) {
                Text("Очистити дату")
            }
        }
    }

    if (showCalendar) {
        DisposableEffect(Unit) {
            var initialDate = parsedDate ?: LocalDate.now()

            minDate?.let {
                if (initialDate < it) initialDate = it
            }

            maxDate?.let {
                if (initialDate > it) initialDate = it
            }

            val dialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    onValueChange(
                        LocalDate.of(year, month + 1, day).toString()
                    )
                    showCalendar = false
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            )

            val zone = ZoneId.systemDefault()

            minDate?.let {
                dialog.datePicker.minDate =
                    it.atStartOfDay(zone).toInstant().toEpochMilli()
            }

            maxDate?.let {
                dialog.datePicker.maxDate =
                    it.atStartOfDay(zone).toInstant().toEpochMilli()
            }

            dialog.setOnDismissListener {
                showCalendar = false
            }

            dialog.show()

            onDispose {
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
    }
}