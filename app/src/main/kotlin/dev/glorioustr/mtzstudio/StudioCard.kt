package dev.glorioustr.mtzstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (LocalAppContentStyle.current != AppContentStyle.LIQUID_GLASS) {
        Card(modifier = modifier, shape = shape, content = content)
        return
    }

    val colors = MaterialTheme.colorScheme
    val glassBase = Brush.linearGradient(
        colors = listOf(
            colors.primary.copy(alpha = 0.20f),
            colors.surfaceContainerHigh.copy(alpha = 0.74f),
            colors.secondary.copy(alpha = 0.16f),
            colors.surfaceContainer.copy(alpha = 0.78f),
        ),
    )
    val glassEdge = Brush.linearGradient(
        colors = listOf(
            colors.primary.copy(alpha = 0.72f),
            colors.outline.copy(alpha = 0.58f),
            colors.secondary.copy(alpha = 0.56f),
        ),
    )

    Card(
        modifier = modifier.border(1.dp, glassEdge, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxWidth().background(glassBase)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
