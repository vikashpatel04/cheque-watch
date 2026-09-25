package com.chequetracker.watch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.chequetracker.watch.data.isForToday

class MainActivity : ComponentActivity() {

    private val viewModel: TodayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        // "Refresh when opened", throttled so waking the screen over the
        // open app doesn't trigger a fetch every time.
        viewModel.refreshOnOpen()
    }
}

@Composable
private fun WatchApp(viewModel: TodayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(navController = navController, startDestination = "today") {
                composable("today") {
                    TodayScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onOpenCheque = { id -> navController.navigate("cheque/$id") },
                    )
                }
                composable("cheque/{id}") { entry ->
                    val id = entry.arguments?.getString("id")
                    val cheque = state.data
                        ?.takeIf { it.isForToday() }
                        ?.cheques
                        ?.firstOrNull { it.id == id }
                    DetailScreen(cheque)
                }
            }
        }
    }
}
