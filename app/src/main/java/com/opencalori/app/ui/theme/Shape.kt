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
/** Shared spacing scale keeps screen density intentional rather than incidental. */
object AppSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 20.dp
    val Section = 24.dp
    val Screen = 32.dp
}
