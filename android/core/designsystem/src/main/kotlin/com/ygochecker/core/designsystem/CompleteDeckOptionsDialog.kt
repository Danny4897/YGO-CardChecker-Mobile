package com.ygochecker.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ygochecker.core.model.FormatExtraStaples
import com.ygochecker.core.model.GameFormat

/**
 * Main / Extra / Side target fields + format staple toggles, shared by "Auto-complete deck"
 * (Decklist) and "Build deck from this card" (Search). [extraContent] renders above the body
 * text — used by callers that need an extra field (e.g. a deck-name input) before the targets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompleteDeckOptionsDialog(
    format: GameFormat,
    busy: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: (targetMain: Int, targetExtra: Int, targetSide: Int, staples: Set<String>) -> Unit,
    initialTargetMain: Int = 40,
    initialTargetExtra: Int = 15,
    initialTargetSide: Int = 15,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    var targetMain by remember { mutableStateOf(initialTargetMain.toString()) }
    var targetExtra by remember { mutableStateOf(initialTargetExtra.toString()) }
    var targetSide by remember { mutableStateOf(initialTargetSide.toString()) }
    var selectedStaples by remember(format) {
        mutableStateOf(FormatExtraStaples.defaultsFor(format))
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extraContent()
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = targetMain,
                    onValueChange = { targetMain = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.editor_complete_main_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetExtra,
                    onValueChange = { targetExtra = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.editor_complete_extra_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetSide,
                    onValueChange = { targetSide = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.editor_complete_side_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.editor_complete_staples),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.editor_complete_staples_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton({
                        selectedStaples = FormatExtraStaples.defaultsFor(format)
                    }) { Text(stringResource(R.string.editor_complete_staples_all)) }
                    TextButton({
                        selectedStaples = emptySet()
                    }) { Text(stringResource(R.string.editor_complete_staples_none)) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FormatExtraStaples.entriesFor(format).forEach { entry ->
                        val on = entry.name in selectedStaples
                        FilterChip(
                            selected = on,
                            onClick = {
                                selectedStaples = if (on) {
                                    selectedStaples - entry.name
                                } else {
                                    selectedStaples + entry.name
                                }
                            },
                            label = { Text(entry.shortLabel) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        targetMain.toIntOrNull() ?: initialTargetMain,
                        targetExtra.toIntOrNull() ?: initialTargetExtra,
                        targetSide.toIntOrNull() ?: initialTargetSide,
                        selectedStaples,
                    )
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.editor_complete_run)) }
        },
        dismissButton = {
            TextButton(onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
