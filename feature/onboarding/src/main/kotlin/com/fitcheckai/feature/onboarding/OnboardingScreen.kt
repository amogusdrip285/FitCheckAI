package com.fitcheckai.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing

/** TODO(Track D): 3 bước (chào → phong cách có ảnh → thành phố/quyền vị trí) + thông báo quyền riêng tư. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(bottom = Spacing.lg)) {
        ScreenHeader(eyebrow = "FitCheck AI", title = "Phong cách của bạn là gì?")
        Text(
            "Chọn vài phong cách bạn thích — AI sẽ phối đồ theo gu của bạn.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        FlowRow(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            STYLE_OPTIONS.forEach { style ->
                FilterChip(
                    selected = style in selected,
                    onClick = { viewModel.toggle(style) },
                    label = { Text(style) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.finish(onFinished) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        ) {
            Text("Bắt đầu")
        }
    }
}
