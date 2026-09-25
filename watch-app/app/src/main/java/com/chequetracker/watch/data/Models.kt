package com.chequetracker.watch.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Response of the `watch-today` Edge Function. Also the cached form. */
@Serializable
data class TodayData(
    /** YYYY-MM-DD in Asia/Kolkata, computed by the server. */
    val date: String,
    /** Sum of today's PENDING cheques only; DEPOSITED ones are already covered. */
    @SerialName("amount_needed") val amountNeeded: Double,
    /** All of today's listed cheques (PENDING + DEPOSITED). */
    val count: Int,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    /** Sum of overdue PENDING cheques. */
    @SerialName("overdue_amount_needed") val overdueAmountNeeded: Double = 0.0,
    /** ISO-8601 instant when the server computed this. */
    @SerialName("updated_at") val updatedAt: String,
    val cheques: List<Cheque> = emptyList(),
)

@Serializable
data class Cheque(
    val id: String,
    @SerialName("party_name") val partyName: String,
    val amount: Double,
    val status: String,
    @SerialName("cheque_number") val chequeNumber: String? = null,
    @SerialName("bank_name") val bankName: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("original_due_date") val originalDueDate: String? = null,
    @SerialName("represent_count") val representCount: Int = 0,
    @SerialName("return_reason") val returnReason: String? = null,
)

val AppJson = Json { ignoreUnknownKeys = true }
