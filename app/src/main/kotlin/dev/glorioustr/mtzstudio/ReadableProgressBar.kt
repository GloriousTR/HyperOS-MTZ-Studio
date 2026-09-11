package dev.glorioustr.mtzstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A progress bar whose centered label keeps contrast on both the filled and empty regions. */
@Composable
internal fun ReadableProgressBar(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val shape = RoundedCornerShape(50)
    val trackTextColor = contrastingTextColor(trackColor)
    val fillTextColor = contrastingTextColor(fillColor)
    val fraction = progress?.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(trackColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        val completeWidth = maxWidth
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = fillColor,
                trackColor = trackColor,
            )
        } else if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(completeWidth * fraction)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(fillColor),
            )
        }

        Text(
            text = label,
            modifier = Modifier.requiredWidth(completeWidth),
            color = trackTextColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )

        if (fraction != null && fraction > 0f) {
            Text(
                text = label,
                modifier = Modifier
                    .requiredWidth(completeWidth)
                    .drawWithContent {
                        val contentDrawScope = this
                        clipRect(right = size.width * fraction) {
                            contentDrawScope.drawContent()
                        }
                    },
                color = fillTextColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun contrastingTextColor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
