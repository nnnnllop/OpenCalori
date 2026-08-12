package com.opencalori.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.opencalori.app.ui.util.NumberFormat

/**
 * Decimal input that behaves like a text field should.
 *
 * Keeps its own draft string so the user can clear it or type "1." mid-way without the
 * value snapping back to 0, renders 200f as "200" instead of "200.0", accepts a comma,
 * and never lets a minus sign into a weight.
 */
@Composable
fun NumberField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    maxLength: Int = 7
) {
    var draft by remember(resetKey) { mutableStateOf(NumberFormat.compact(value)) }

    // Adopt external changes (the raw/cooked toggle recalculating a weight, for example)
    // without overwriting what the user is currently typing.
    LaunchedEffect(value) {
        if (NumberFormat.parse(draft) != value) draft = NumberFormat.compact(value)
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { input ->
            val sanitized = NumberFormat.sanitizeDecimalInput(input, maxLength)
            draft = sanitized
            NumberFormat.parse(sanitized)?.let(onValueChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}
