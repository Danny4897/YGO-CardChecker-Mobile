package com.ygochecker.feature.decklist

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ygochecker.core.common.AppError
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.errorMessage

enum class DeckIoFormat { TEXT, YDKE, YDK }

data class DeckIoUiState(
    val importOpen: Boolean = false,
    val exportOpen: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val noticeKey: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImportSheet(
    busy: Boolean,
    error: AppError?,
    close: () -> Unit,
    import: (String, String, DeckIoFormat, Boolean, String?) -> Unit,
) {
    var format by remember { mutableStateOf(DeckIoFormat.TEXT) }
    val defaultName = stringResource(DesignR.string.import_default_name)
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var content by remember { mutableStateOf("") }
    var isExternal by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(DesignR.string.import_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(DesignR.string.import_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormatTabs(format) { format = it }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(DesignR.string.import_deck_name)) },
                    singleLine = true,
                    enabled = !busy,
                )
                FilterChip(
                    selected = isExternal,
                    onClick = { isExternal = !isExternal },
                    label = { Text(stringResource(DesignR.string.import_external_toggle)) },
                )
                if (isExternal) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(DesignR.string.import_external_group)) },
                        singleLine = true,
                        enabled = !busy,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            stringResource(DesignR.string.import_external_group_test),
                            stringResource(DesignR.string.import_external_group_pending),
                        ).forEach { suggestion ->
                            AssistChip(onClick = { groupName = suggestion }, label = { Text(suggestion) })
                        }
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp),
                        label = {
                            Text(
                                when (format) {
                                    DeckIoFormat.TEXT -> stringResource(DesignR.string.import_text_label)
                                    DeckIoFormat.YDKE -> stringResource(DesignR.string.import_ydke_label)
                                    DeckIoFormat.YDK -> stringResource(DesignR.string.import_ydk)
                                },
                            )
                        },
                        placeholder = {
                            Text(
                                when (format) {
                                    DeckIoFormat.TEXT -> stringResource(DesignR.string.import_text_placeholder)
                                    DeckIoFormat.YDKE -> stringResource(DesignR.string.import_ydke_placeholder)
                                    DeckIoFormat.YDK -> stringResource(DesignR.string.import_ydk_placeholder)
                                },
                            )
                        },
                    minLines = if (format == DeckIoFormat.TEXT || format == DeckIoFormat.YDK) 6 else 3,
                    enabled = !busy,
                )
                TextButton(
                    onClick = { clipboard.getText()?.text?.let { content = it } },
                    enabled = !busy,
                ) {
                    Icon(Icons.Default.ContentPaste, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(DesignR.string.import_paste))
                }
                error?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            errorMessage(it.errorKey, it.detail),
                            Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = DuelSpacing.space3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        import(name, content, format, isExternal, groupName.trim().ifBlank { null })
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = content.isNotBlank() && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (busy) {
                            stringResource(DesignR.string.import_busy)
                        } else {
                            stringResource(DesignR.string.import_save)
                        },
                    )
                }
                TextButton(onClick = close, Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(stringResource(DesignR.string.action_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckExportSheet(
    export: (DeckIoFormat) -> String,
    copied: () -> Unit,
    close: () -> Unit,
) {
    var format by remember { mutableStateOf(DeckIoFormat.TEXT) }
    val value = export(format)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shareTitle = stringResource(DesignR.string.export_share)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(DesignR.string.export_title), style = MaterialTheme.typography.headlineSmall)
                FormatTabs(format) { format = it }
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
                    readOnly = true,
                    minLines = if (format == DeckIoFormat.TEXT) 6 else 3,
                    label = {
                        Text(
                            if (format == DeckIoFormat.TEXT) {
                                stringResource(DesignR.string.import_text)
                            } else {
                                stringResource(DesignR.string.import_ydke)
                            },
                        )
                    },
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = DuelSpacing.space3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(value))
                            copied()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(DesignR.string.action_copy))
                    }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, value)
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(DesignR.string.export_share))
                    }
                }
                TextButton(onClick = close, Modifier.fillMaxWidth()) {
                    Text(stringResource(DesignR.string.action_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatTabs(selected: DeckIoFormat, select: (DeckIoFormat) -> Unit) {
    PrimaryTabRow(DeckIoFormat.entries.indexOf(selected)) {
        DeckIoFormat.entries.forEach { format ->
            Tab(
                selected = selected == format,
                onClick = { select(format) },
                text = {
                    Text(
                        when (format) {
                            DeckIoFormat.TEXT -> stringResource(DesignR.string.import_text)
                            DeckIoFormat.YDKE -> stringResource(DesignR.string.import_ydke)
                            DeckIoFormat.YDK -> stringResource(DesignR.string.import_ydk)
                        },
                    )
                },
            )
        }
    }
}
