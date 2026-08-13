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
fun DailyBalanceCard(
    title: String,
    consumed: Float,
    target: Float,
    protein: Pair<Float, Float>,
    fat: Pair<Float, Float>,
    carbs: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    val progress = if (target > 0) (consumed / target).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(MotionTokens.Standard),
        label = "daily_balance_progress"
    )
    val animatedConsumed by animateFloatAsState(
        targetValue = consumed,
        animationSpec = tween(MotionTokens.Standard),
        label = "daily_balance_consumed"
    )
    val remaining = (target - animatedConsumed).roundToInt()
    val darkTheme = isSystemInDarkTheme()
    val proteinColor = if (darkTheme) ProteinColorDark else ProteinColor
    val fatColor = if (darkTheme) FatColorDark else FatColor
    val carbsColor = if (darkTheme) CarbsColorDark else CarbsColor
    val progressColor = if (consumed > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (remaining >= 0) "\u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c \u043d\u0430 \u0441\u0435\u0433\u043e\u0434\u043d\u044f" else "\u041f\u043b\u0430\u043d \u043f\u0440\u0435\u0432\u044b\u0448\u0435\u043d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
                Text(
                    target.roundToInt().toString() + " \u043a\u043a\u0430\u043b",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "\u0421\u044a\u0435\u0434\u0435\u043d\u043e " + consumed.roundToInt() +
                            " \u0438\u0437 " + target.roundToInt() + " \u043a\u043a\u0430\u043b"
                    }
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(136.dp),
                        strokeWidth = 12.dp,
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            animatedConsumed.roundToInt().toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "\u043a\u043a\u0430\u043b",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (remaining >= 0) "\u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c " + remaining + " \u043a\u043a\u0430\u043b"
                        else "\u041f\u0440\u0435\u0432\u044b\u0448\u0435\u043d\u0438\u0435 " + (-remaining) + " \u043a\u043a\u0430\u043b",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remaining >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                    Text(
                        "\u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u0435\u0434\u0443, \u0447\u0442\u043e\u0431\u044b \u043e\u0431\u043d\u043e\u0432\u0438\u0442\u044c \u0431\u0430\u043b\u0430\u043d\u0441.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MacroMiniStat("\u0411\u0435\u043b\u043a\u0438", protein, proteinColor, Modifier.weight(1f))
                MacroMiniStat("\u0416\u0438\u0440\u044b", fat, fatColor, Modifier.weight(1f))
                MacroMiniStat("\u0423\u0433\u043b\u0435\u0432\u043e\u0434\u044b", carbs, carbsColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroMiniStat(label: String, value: Pair<Float, Float>, color: Color, modifier: Modifier = Modifier) {
    val progress = if (value.second > 0) (value.first / value.second).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(MotionTokens.Quick),
        label = label + "_mini_progress"
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Text(
            value.first.roundToInt().toString() + "/" + value.second.roundToInt() + " \u0433",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(AppShapes.Pill),
            color = color,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
        )
    }
}

@Composable
fun CalorieRing(
    consumed: Float,
    target: Float,
    modifier: Modifier = Modifier,
    title: String = "\u041a\u0430\u043b\u043e\u0440\u0438\u0438 \u0441\u0435\u0433\u043e\u0434\u043d\u044f"
) {
    DailyBalanceCard(
        title = title,
        consumed = consumed,
        target = target,
        protein = 0f to 1f,
        fat = 0f to 1f,
        carbs = 0f to 1f,
        modifier = modifier
    )
}

@Composable
fun MacroProgressBar(label: String, current: Float, target: Float, color: Color, modifier: Modifier = Modifier) {
    val progress = if (target > 0) (current / target).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(MotionTokens.Quick),
        label = "macro_progress"
    )
    Column(
        modifier = modifier.semantics {
            contentDescription = label + ": " + current.roundToInt() + " \u0438\u0437 " + target.roundToInt() + " \u0433\u0440\u0430\u043c\u043c\u043e\u0432"
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(current.roundToInt().toString() + "/" + target.roundToInt() + " \u0433", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(AppShapes.Pill),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun MacroSummaryRow(protein: Pair<Float, Float>, fat: Pair<Float, Float>, carbs: Pair<Float, Float>, modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacroProgressBar("\u0411\u0435\u043b\u043a\u0438", protein.first, protein.second, if (darkTheme) ProteinColorDark else ProteinColor)
        MacroProgressBar("\u0416\u0438\u0440\u044b", fat.first, fat.second, if (darkTheme) FatColorDark else FatColor)
        MacroProgressBar("\u0423\u0433\u043b\u0435\u0432\u043e\u0434\u044b", carbs.first, carbs.second, if (darkTheme) CarbsColorDark else CarbsColor)
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
