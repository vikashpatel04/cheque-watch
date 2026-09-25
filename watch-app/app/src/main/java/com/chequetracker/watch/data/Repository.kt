package com.chequetracker.watch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import com.chequetracker.watch.tile.TodayTileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cacheStore: DataStore<Preferences> by preferencesDataStore(name = "today_cache")
private val TODAY_JSON = stringPreferencesKey("today_json")

/** Cache + refresh. The tile reads [cached]; only [refresh] touches the network. */
object Repository {

    fun cachedFlow(context: Context): Flow<TodayData?> =
        context.applicationContext.cacheStore.data.map { prefs -> prefs[TODAY_JSON]?.let(::decode) }

    suspend fun cached(context: Context): TodayData? = cachedFlow(context).first()

    /** Fetch → save to DataStore → ask the system to re-render the tile. */
    suspend fun refresh(context: Context): FetchResult {
        val app = context.applicationContext
        val result = Api.fetchToday()
        if (result is FetchResult.Success) {
            val json = AppJson.encodeToString(TodayData.serializer(), result.data)
            app.cacheStore.edit { it[TODAY_JSON] = json }
            TileService.getUpdater(app).requestUpdate(TodayTileService::class.java)
        }
        return result
    }

    private fun decode(json: String): TodayData? =
        runCatching { AppJson.decodeFromString(TodayData.serializer(), json) }.getOrNull()
}
