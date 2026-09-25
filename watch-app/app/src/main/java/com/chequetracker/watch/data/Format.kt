package com.chequetracker.watch.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/** Today's date in IST as YYYY-MM-DD, the same format the server uses. */
fun todayIst(): String = LocalDate.now(IST).toString()

/** Cached data is only shown as "today" when it is for today's IST date. */
fun TodayData.isForToday(): Boolean = date == todayIst()

/**
 * Indian currency format with lakh/crore grouping: 125000 -> "₹1,25,000",
 * 1234.5 -> "₹1,234.50". Paise are shown only when non-zero.
 */
fun formatInr(amount: Double): String {
    val bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    val negative = bd.signum() < 0
    val plain = bd.abs().toPlainString() // e.g. "125000.00"
    val intPart = plain.substringBefore('.')
    val decPart = plain.substringAfter('.', "00")
    val grouped = groupIndian(intPart)
    val body = if (decPart == "00") grouped else "$grouped.$decPart"
    return (if (negative) "-₹" else "₹") + body
}

/** "1234567" -> "12,34,567" (last three digits, then pairs). */
internal fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$pairs,$last3"
}

fun chequeCountLabel(count: Int): String = if (count == 1) "1 cheque" else "$count cheques"

fun overdueLabel(count: Int, amount: Double): String = "+$count overdue · ${formatInr(amount)}"

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayTimeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "Updated 10:30" (IST); includes the date when it isn't today. */
fun updatedLabel(updatedAt: String): String {
    val t = runCatching { ZonedDateTime.ofInstant(Instant.parse(updatedAt), IST) }.getOrNull()
        ?: return "Updated —"
    val sameDay = t.toLocalDate() == LocalDate.now(IST)
    return "Updated " + (if (sameDay) timeFmt else dayTimeFmt).format(t)
}

/** YYYY-MM-DD -> dd/MM/yyyy, as the web app shows dates. */
fun formatDate(iso: String?): String? =
    iso?.let { runCatching { dateFmt.format(LocalDate.parse(it)) }.getOrDefault(it) }
