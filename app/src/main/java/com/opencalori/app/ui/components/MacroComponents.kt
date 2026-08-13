package com.opencalori.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencalori.app.ui.theme.AppShapes
import com.opencalori.app.ui.theme.CarbsColor
import com.opencalori.app.ui.theme.CarbsColorDark
import com.opencalori.app.ui.theme.FatColor
import com.opencalori.app.ui.theme.FatColorDark
import com.opencalori.app.ui.theme.MotionTokens
import com.opencalori.app.ui.theme.ProteinColor
import com.opencalori.app.ui.theme.ProteinColorDark
import kotlin.math.roundToInt

@Composable
fun CalorieRing(
    consumed: Float,
    target: Float,
    modifier: Modifier = Modifier,
    title: String = "Калории сегодня"
) {
    val progress = if (target > 0) (consumed / target).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(MotionTokens.Standard),
        label = "calorie_progress"
    )
    val animatedConsumed by animateFloatAsState(
        targetValue = consumed,
        animationSpec = tween(MotionTokens.Standard),
        label = "calorie_value"
    )
    val remaining = (target - animatedConsumed).roundToInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.semantics {
                    contentDescription = "Съедено " + consumed.roundToInt() +
                        " из " + target.roundToInt() + " ккал"
                }
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 14.dp,
                    color = if (consumed > target) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        animatedConsumed.roundToInt().toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "из " + target.roundToInt() + " ккал",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (remaining >= 0) "Осталось: " + remaining + " ккал"
                else "Превышение: " + (-remaining) + " ккал",
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun MacroProgressBar(
    label: String,
    current: Float,
    target: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = if (target > 0) (current / target).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(MotionTokens.Quick),
        label = "macro_progress"
    )
    Column(
        modifier = modifier.semantics {
            contentDescription = label + ": " + current.roundToInt() + " из " + target.roundToInt() + " граммов"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
            Text(
                current.roundToInt().toString() + "/" + target.roundToInt() + " г",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun MacroSummaryRow(
    protein: Pair<Float, Float>,
    fat: Pair<Float, Float>,
    carbs: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        val darkTheme = isSystemInDarkTheme()
        val proteinColor = if (darkTheme) ProteinColorDark else ProteinColor
        val fatColor = if (darkTheme) FatColorDark else FatColor
        val carbsColor = if (darkTheme) CarbsColorDark else CarbsColor
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MacroProgressBar("Белки", protein.first, protein.second, proteinColor)
            MacroProgressBar("Жиры", fat.first, fat.second, fatColor)
            MacroProgressBar("Углеводы", carbs.first, carbs.second, carbsColor)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
