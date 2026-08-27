package dev.glorioustr.mtzstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppContentStyle.current != AppContentStyle.LIQUID_GLASS) {
        Card(modifier = modifier, content = content)
        return
    }

    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val glassBase = Brush.linearGradient(
        colors = listOf(
            colors.surfaceContainerHigh.copy(alpha = 0.88f),
            colors.primary.copy(alpha = 0.24f),
            colors.secondary.copy(alpha = 0.18f),
            colors.surfaceContainer.copy(alpha = 0.90f),
        ),
        start = Offset.Zero,
        end = Offset(1_100f, 650f),
    )
    val glassEdge = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.72f),
            colors.primary.copy(alpha = 0.72f),
            Color.White.copy(alpha = 0.16f),
            colors.secondary.copy(alpha = 0.60f),
        ),
        start = Offset.Zero,
        end = Offset(1_000f, 700f),
    )

    Card(
        modifier = modifier.border(1.dp, glassEdge, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxWidth().background(glassBase)) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.34f),
                            colors.primary.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(90f, 65f),
                        radius = 520f,
                    ),
                ),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.secondary.copy(alpha = 0.32f),
                            colors.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(1_000f, 560f),
                        radius = 650f,
                    ),
                ),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(850f, 390f),
                    ),
                ),
            )
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
