package com.chequetracker.watch.tile

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.chequetracker.watch.data.IST
import com.chequetracker.watch.data.Repository
import com.chequetracker.watch.data.TodayData
import com.chequetracker.watch.data.chequeCountLabel
import com.chequetracker.watch.data.formatInr
import com.chequetracker.watch.data.isForToday
import com.chequetracker.watch.data.overdueLabel
import com.chequetracker.watch.data.updatedLabel
import com.chequetracker.watch.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Glanceable tile. Renders from the DataStore cache only: no network here.
 * Fresh data arrives via [Repository.refresh], which requests a tile update.
 */
class TodayTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
        val data = Repository.cached(this@TodayTileService)
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Re-render (from cache) once just after midnight IST so yesterday's
            // numbers never show as today's, even if the midnight fetch fails.
            .setFreshnessIntervalMillis(millisUntilNextIstMidnight())
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(data)))
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = SuspendToFutureAdapter.launchFuture {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    private fun layout(data: TodayData?): LayoutElement {
        val column = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        when {
            data == null -> {
                column.addContent(text("Cheques", 16f, COLOR_MUTED))
                column.addContent(gap(4f))
                column.addContent(text("Open app to sync", 16f, COLOR_TEXT))
            }
            !data.isForToday() -> {
                column.addContent(text("Not synced today", 16f, COLOR_TEXT))
                column.addContent(gap(6f))
                column.addContent(text(updatedLabel(data.updatedAt), 12f, COLOR_MUTED))
            }
            else -> {
                if (data.count == 0) {
                    column.addContent(text("No cheques today", 18f, COLOR_TEXT, bold = true))
                } else {
                    val amount = formatInr(data.amountNeeded)
                    column.addContent(text("Needed today", 12f, COLOR_MUTED))
                    column.addContent(text(amount, amountSize(amount), COLOR_AMOUNT, bold = true))
                    column.addContent(gap(2f))
                    column.addContent(text(chequeCountLabel(data.count), 16f, COLOR_TEXT))
                }
                if (data.overdueCount > 0) {
                    column.addContent(gap(4f))
                    column.addContent(
                        text(overdueLabel(data.overdueCount, data.overdueAmountNeeded), 13f, COLOR_OVERDUE),
                    )
                }
                column.addContent(gap(6f))
                column.addContent(text(updatedLabel(data.updatedAt), 12f, COLOR_MUTED))
            }
        }

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_app")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false): LayoutElement =
        Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                        else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
                    )
                    .build(),
            )
            .build()

    private fun gap(heightDp: Float): LayoutElement = Spacer.Builder().setHeight(dp(heightDp)).build()

    /** Shrink very large totals (crores) so they fit the round screen. */
    private fun amountSize(amount: String): Float = when {
        amount.length <= 9 -> 30f
        amount.length <= 12 -> 26f
        else -> 22f
    }

    private fun millisUntilNextIstMidnight(): Long {
        val now = ZonedDateTime.now(IST)
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 0, 30)).atZone(IST)
        return Duration.between(now, next).toMillis()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val COLOR_AMOUNT = 0xFFFCD34D.toInt()   // amber, like the web app's "cash needed"
        const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        const val COLOR_MUTED = 0xFF9CA3AF.toInt()
        const val COLOR_OVERDUE = 0xFFFB923C.toInt()  // orange, like the web's overdue tag
    }
}
