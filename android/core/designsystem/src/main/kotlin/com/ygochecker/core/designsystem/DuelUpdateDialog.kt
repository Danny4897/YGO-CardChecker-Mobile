package com.ygochecker.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Themed FOSS update prompt — duel-table atmosphere, not a stock AlertDialog.
 */
@Composable
fun DuelUpdateDialog(
    versionName: String,
    currentVersionName: String,
    changelog: String,
    downloading: Boolean,
    downloadProgress: Float = 0f,
    awaitingInstall: Boolean = false,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val gold = MaterialTheme.colorScheme.primary
    val busy = downloading || awaitingInstall
    Dialog(
        onDismissRequest = { if (!busy) onLater() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            gold.copy(alpha = 0.85f),
                            gold.copy(alpha = 0.25f),
                            gold.copy(alpha = 0.7f),
                        ),
                    ),
                    shape = shape,
                ),
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ),
                        )
                        .padding(horizontal = DuelSpacing.space5, vertical = DuelSpacing.space4),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.update_available_eyebrow),
                            style = MaterialTheme.typography.labelLarge,
                            color = gold,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    stringResource(R.string.update_available_title, versionName),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(
                                        R.string.update_available_body,
                                        currentVersionName,
                                        versionName,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier.padding(horizontal = DuelSpacing.space5, vertical = DuelSpacing.space3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (changelog.isNotBlank() && !awaitingInstall) {
                        Text(
                            stringResource(R.string.update_changelog_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = gold,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                changelog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(DuelSpacing.space3),
                            )
                        }
                    }

                    if (downloading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (downloadProgress > 0.02f) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = gold,
                                    trackColor = gold.copy(alpha = 0.2f),
                                )
                                Text(
                                    stringResource(
                                        R.string.update_download_percent,
                                        (downloadProgress * 100).toInt().coerceIn(0, 100),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = gold,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.update_downloading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (awaitingInstall) {
                        Text(
                            stringResource(R.string.update_install_confirm_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.update_install_reopen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!awaitingInstall) {
                        Button(
                            onClick = onDownload,
                            enabled = !downloading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.update_download),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Button(
                            onClick = onLater,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.update_install_got_it),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (!awaitingInstall) {
                        TextButton(
                            onClick = onLater,
                            enabled = !downloading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_later))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
