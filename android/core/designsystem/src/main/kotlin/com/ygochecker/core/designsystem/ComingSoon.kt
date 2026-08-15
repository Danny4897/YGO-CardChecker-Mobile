package com.ygochecker.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun ComingSoonScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ThemedScreenHeader(title = title, subtitle = stringResource(R.string.coming_soon_badge))
        EmptyState(
            icon = Icons.Default.HourglassTop,
            title = stringResource(R.string.coming_soon_title),
            body = body,
            modifier = Modifier
                .weight(1f)
                .padding(DuelSpacing.space4),
        )
    }
}
