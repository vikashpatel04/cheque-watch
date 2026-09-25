package com.chequetracker.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.chequetracker.watch.data.Cheque
import com.chequetracker.watch.data.TodayData
import com.chequetracker.watch.data.chequeCountLabel
import com.chequetracker.watch.data.formatInr
import com.chequetracker.watch.data.isForToday
import com.chequetracker.watch.data.overdueLabel
import com.chequetracker.watch.data.updatedLabel

val AmountColor = Color(0xFFFCD34D)
val OverdueColor = Color(0xFFFB923C)
val MutedColor = Color(0xFF9CA3AF)

@Composable
fun TodayScreen(state: UiState, onRefresh: () -> Unit, onOpenCheque: (String) -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val today = state.data?.takeIf { it.isForToday() }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onRefresh,
                buttonSize = EdgeButtonSize.ExtraSmall,
                enabled = !state.refreshing,
            ) {
                Text(if (state.refreshing) "…" else "Refresh")
            }
        },
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !state.cacheLoaded || (today == null && state.refreshing) -> item { Loading() }
                today == null -> item { NotSynced(state) }
                else -> todayContent(today, state, onOpenCheque)
            }
        }
    }
}

private fun ScalingLazyListScope.todayContent(
    data: TodayData,
    state: UiState,
    onOpenCheque: (String) -> Unit,
) {
    item {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (data.count == 0) {
                Text(
                    "No cheques today",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                val total = formatInr(data.amountNeeded)
                Text("Needed today", color = MutedColor, fontSize = 11.sp)
                Text(
                    total,
                    color = AmountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (total.length <= 10) 28.sp else 22.sp,
                    maxLines = 1,
                )
                Text(chequeCountLabel(data.count), style = MaterialTheme.typography.bodyLarge)
            }
            if (data.overdueCount > 0) {
                Text(
                    overdueLabel(data.overdueCount, data.overdueAmountNeeded),
                    color = OverdueColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            StatusLine(state, data)
        }
    }
    items(data.cheques, key = { it.id }) { cheque ->
        ChequeRow(cheque, onClick = { onOpenCheque(cheque.id) })
    }
}

/** "Updated 10:30", plus an offline / error / refreshing hint when relevant. */
@Composable
private fun StatusLine(state: UiState, data: TodayData?) {
    val hint = when {
        state.refreshing -> "Refreshing…"
        state.offline -> "Offline · saved data"
        state.error != null -> state.error
        else -> null
    }
    val color = if (state.error != null && !state.refreshing) OverdueColor else MutedColor
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (data != null) Text(updatedLabel(data.updatedAt), color = MutedColor, fontSize = 11.sp)
        if (hint != null) Text(hint, color = color, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChequeRow(cheque: Cheque, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            cheque.partyName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatInr(cheque.amount),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(cheque.status, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Loading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text("Loading…", color = MutedColor, fontSize = 12.sp)
    }
}

/** No data for today's date yet (first launch, or offline since midnight). */
@Composable
private fun NotSynced(state: UiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (state.data == null) "No data yet" else "Not synced today",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        StatusLine(state, state.data)
    }
}
