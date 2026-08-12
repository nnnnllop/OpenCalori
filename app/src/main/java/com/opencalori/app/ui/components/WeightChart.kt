package com.opencalori.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.opencalori.app.domain.model.WeightEntry
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeightChart(history: List<WeightEntry>, modifier: Modifier = Modifier) {
    if (history.size < 2) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val sorted = remember(history) { history.sortedBy { it.dateEpochDay } }

    LaunchedEffect(sorted) {
        modelProducer.runTransaction {
            lineSeries {
                series(sorted.map { it.weightKg })
            }
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale("ru")) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ ->
                    val idx = value.toInt().coerceIn(0, sorted.size - 1)
                    LocalDate.ofEpochDay(sorted[idx].dateEpochDay).format(dateFormatter)
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}
