package com.fitcheckai.feature.tryon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitcheckai.feature.tryon.contract.MediaRef
import java.io.File

internal val TryOnScreenHorizontalPadding = 20.dp

/** Feature-local Track A-aligned theme tokens (matches WardrobeDesign.kt without cross-feature deps). */
@Composable
internal fun TryOnEditorialTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFF3EEE7),
            onPrimary = Color(0xFF232522),
            primaryContainer = Color(0xFF373B34),
            onPrimaryContainer = Color(0xFFF3EEE7),
            secondary = Color(0xFFE4AB90),
            onSecondary = Color(0xFF3E2319),
            secondaryContainer = Color(0xFF49362D),
            onSecondaryContainer = Color(0xFFF4CBB5),
            tertiary = Color(0xFFBAC9AF),
            onTertiary = Color(0xFF273323),
            tertiaryContainer = Color(0xFF323D2D),
            onTertiaryContainer = Color(0xFFDCE9D4),
            background = Color(0xFF191B18),
            onBackground = Color(0xFFF3EEE7),
            surface = Color(0xFF191B18),
            onSurface = Color(0xFFF3EEE7),
            surfaceVariant = Color(0xFF2E312A),
            onSurfaceVariant = Color(0xFFBBBEB3),
            surfaceContainerLowest = Color(0xFF22251F),
            surfaceContainerLow = Color(0xFF242720),
            surfaceContainer = Color(0xFF282B24),
            surfaceContainerHigh = Color(0xFF32352E),
            outline = Color(0xFF8E9385),
            outlineVariant = Color(0xFF3D4237),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF252822),
            onPrimary = Color(0xFFFFFDF9),
            primaryContainer = Color(0xFFE8EBDD),
            onPrimaryContainer = Color(0xFF30372B),
            secondary = Color(0xFFA24F35),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF5E6DC),
            onSecondaryContainer = Color(0xFF783E2B),
            tertiary = Color(0xFF526348),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEAF0E3),
            onTertiaryContainer = Color(0xFF3D5133),
            background = Color(0xFFF8F5EF),
            onBackground = Color(0xFF252822),
            surface = Color(0xFFF8F5EF),
            onSurface = Color(0xFF252822),
            surfaceVariant = Color(0xFFEFEAE1),
            onSurfaceVariant = Color(0xFF676B60),
            surfaceContainerLowest = Color(0xFFFFFEFB),
            surfaceContainerLow = Color(0xFFF3EFE7),
            surfaceContainer = Color(0xFFEFEAE1),
            surfaceContainerHigh = Color(0xFFE8E3D9),
            outline = Color(0xFF797E70),
            outlineVariant = Color(0xFFE1DED4),
        )
    }
    val type = MaterialTheme.typography.copy(
        displaySmall = MaterialTheme.typography.displaySmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 36.sp,
            lineHeight = 40.sp,
        ),
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Medium,
        ),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontSize = 26.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.4.sp,
        ),
        labelSmall = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
        ),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}

@Composable
internal fun TryOnPanel(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun TryOnSectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal enum class TryOnNoticeTone {
    INFO,
    NEUTRAL,
    ERROR,
}

@Composable
internal fun TryOnChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = when {
        !enabled && selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = when {
        !enabled && selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = when {
        selected -> null
        !enabled -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    // Outer 48.dp selectable touch target wraps the 40.dp Track A pill visual cleanly
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .indication(interactionSource, ripple()),
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            border = border,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (leadingIcon != null) 10.dp else 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        leadingIcon()
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun TryOnPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TryOnInfoNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: TryOnNoticeTone = TryOnNoticeTone.INFO,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val containerColor = when (tone) {
        TryOnNoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        TryOnNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        TryOnNoticeTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (tone) {
        TryOnNoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        TryOnNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        TryOnNoticeTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val resolvedIcon = icon ?: if (tone == TryOnNoticeTone.ERROR) {
        Icons.Outlined.ErrorOutline
    } else {
        Icons.Outlined.Info
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun TryOnErrorNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    action: (@Composable () -> Unit)? = null,
) = TryOnInfoNotice(
    text = text,
    modifier = modifier,
    tone = TryOnNoticeTone.ERROR,
    icon = icon,
    action = action,
)

@Composable
internal fun TryOnPhotoFrame(
    mediaRef: MediaRef?,
    contentDescription: String?,
    placeholderIcon: ImageVector,
    modifier: Modifier = Modifier,
    missingText: String? = "Chưa có ảnh",
) {
    val diskFile = mediaRef?.localRelativePath
        ?.takeIf { !it.startsWith("seed://") }
        ?.let(::File)
    val hasDiskFile = diskFile != null && diskFile.exists()
    var failed by remember(
        mediaRef?.id,
        mediaRef?.contentHash,
        mediaRef?.revision,
        mediaRef?.localRelativePath,
    ) {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaRef == null || !hasDiskFile || failed) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (failed) Icons.Outlined.ImageNotSupported else placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                )
                if (failed) {
                    Text(
                        text = "Ảnh chưa khả dụng",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else if (mediaRef == null && missingText != null) {
                    Text(
                        text = missingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            val context = LocalContext.current
            // Include MediaRef identity/hash/revision because gallery picks overwrite fixed slot paths in place.
            val imageRequest = remember(
                context,
                mediaRef.id,
                mediaRef.contentHash,
                mediaRef.revision,
                mediaRef.localRelativePath,
            ) {
                val cacheKey = "${mediaRef.id}:${mediaRef.contentHash}:${mediaRef.revision}"
                ImageRequest.Builder(context)
                    .data(diskFile)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                onError = { failed = true },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TryOnBackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại",
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
