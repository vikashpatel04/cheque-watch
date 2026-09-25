package com.chequetracker.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/** Labels from the web app's STATUS_LABELS (src/types/index.ts). */
private val STATUS_LABELS = mapOf(
    "PENDING" to "Pending",
    "DEPOSITED" to "Deposited",
    "PASSED" to "Passed",
    "RETURNED" to "Returned",
    "CANCELLED" to "Cancelled",
    "WRITTEN_OFF" to "Written Off",
)

/**
 * Dark-screen versions of the web app's StatusPill hues
 * (amber / blue / gray / red / slate / zinc): tinted background, light text.
 */
private data class ChipColors(val bg: Color, val fg: Color)

private val STATUS_COLORS = mapOf(
    "PENDING" to ChipColors(Color(0xFF4A3508), Color(0xFFFCD34D)),
    "DEPOSITED" to ChipColors(Color(0xFF10284D), Color(0xFF93C5FD)),
    "PASSED" to ChipColors(Color(0xFF2A2D33), Color(0xFFD1D5DB)),
    "RETURNED" to ChipColors(Color(0xFF4C1515), Color(0xFFFCA5A5)),
    "CANCELLED" to ChipColors(Color(0xFF263040), Color(0xFFCBD5E1)),
    "WRITTEN_OFF" to ChipColors(Color(0xFF2E2E33), Color(0xFFD4D4D8)),
)
private val FALLBACK = ChipColors(Color(0xFF2A2D33), Color(0xFFD1D5DB))

fun statusLabel(status: String): String = STATUS_LABELS[status] ?: status

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val colors = STATUS_COLORS[status] ?: FALLBACK
    Text(
        text = statusLabel(status),
        color = colors.fg,
        fontSize = 11.sp,
        maxLines = 1,
        textDecoration = if (status == "WRITTEN_OFF") TextDecoration.LineThrough else null,
        modifier = modifier
            .background(colors.bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
