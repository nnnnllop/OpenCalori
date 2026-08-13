package com.opencalori.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared radii keep cards, fields and sheets visually related. */
object AppShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(999.dp)
}

/** Short, purposeful durations for state changes without theatrical motion. */
object MotionTokens {
    const val Quick = 160
    const val Standard = 220
    const val Screen = 240
}
