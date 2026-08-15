package com.ygochecker.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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

data class CollectionPickOption(
    val id: Long,
    val name: String,
)

/**
 * Pick an existing collection or create one with a custom name, then save the card.
 */
@Composable
fun SaveToCollectionDialog(
    collections: List<CollectionPickOption>,
    onDismiss: () -> Unit,
    onPick: (collectionId: Long) -> Unit,
    onCreateAndSave: (name: String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_pick_collection)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                collections.forEach { col ->
                    TextButton(onClick = { onPick(col.id) }) { Text(col.name) }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    label = { Text(stringResource(R.string.profile_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val name = draft.trim()
                        if (name.isNotEmpty()) onCreateAndSave(name)
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.search_save_new_named))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
