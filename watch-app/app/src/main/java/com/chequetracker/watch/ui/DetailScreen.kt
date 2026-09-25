package com.chequetracker.watch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.chequetracker.watch.data.Cheque
import com.chequetracker.watch.data.formatDate
import com.chequetracker.watch.data.formatInr

/** View-only details of one of today's cheques. Swipe right / back to leave. */
@Composable
fun DetailScreen(cheque: Cheque?) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (cheque == null) {
                item { Text("Not due today", textAlign = TextAlign.Center) }
                return@ScalingLazyColumn
            }
            // Same rules as the web app's ChequeDetail.
            val rePresented = cheque.originalDueDate != null && cheque.originalDueDate != cheque.dueDate

            item {
                ListHeader {
                    Text(cheque.partyName, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            item {
                Text(
                    formatInr(cheque.amount),
                    color = AmountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    maxLines = 1,
                )
            }
            item { StatusChip(cheque.status) }
            cheque.chequeNumber?.let { item { Field("Cheque no.", it) } }
            cheque.bankName?.let { item { Field("Bank", it) } }
            formatDate(cheque.dueDate)?.let {
                item { Field(if (rePresented) "Due date (re-presented)" else "Due date", it) }
            }
            if (rePresented) {
                formatDate(cheque.originalDueDate)?.let { item { Field("Cheque date", it) } }
            }
            if (cheque.representCount > 0) {
                val label = if (cheque.representCount > 1) "Re-presented ×${cheque.representCount}" else "Re-presented"
                item { Text(label, color = MutedColor, fontSize = 12.sp) }
            }
            cheque.returnReason?.takeIf { it.isNotBlank() }?.let {
                val label = if (cheque.status == "RETURNED") "Return reason" else "Last return reason"
                item { Field(label, it) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = MutedColor, fontSize = 11.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
