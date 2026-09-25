package com.chequetracker.watch.data

import com.chequetracker.watch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Outcome of one fetch. Offline = couldn't reach the server at all. */
sealed interface FetchResult {
    data class Success(val data: TodayData) : FetchResult
    data object Offline : FetchResult
    data class Error(val message: String) : FetchResult
}

/**
 * The only network call in the app: one GET to the Edge Function.
 * Uses the default network, so on the watch it goes over the phone's
 * Bluetooth proxy when paired and nearby; Wi-Fi is never forced.
 */
object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    val isConfigured: Boolean
        get() = BuildConfig.WATCH_ENDPOINT.isNotBlank() && BuildConfig.WATCH_KEY.isNotBlank()

    suspend fun fetchToday(): FetchResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext FetchResult.Error("Not configured")
        val request = Request.Builder()
            .url(BuildConfig.WATCH_ENDPOINT)
            .header("x-watch-key", BuildConfig.WATCH_KEY)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 -> FetchResult.Error("Wrong watch key")
                    !resp.isSuccessful -> FetchResult.Error("Server error ${resp.code}")
                    else -> {
                        val body = resp.body?.string().orEmpty()
                        runCatching { AppJson.decodeFromString(TodayData.serializer(), body) }
                            .fold(
                                onSuccess = { FetchResult.Success(it) },
                                onFailure = { FetchResult.Error("Bad response") },
                            )
                    }
                }
            }
        } catch (e: IOException) {
            FetchResult.Offline
        }
    }
}
