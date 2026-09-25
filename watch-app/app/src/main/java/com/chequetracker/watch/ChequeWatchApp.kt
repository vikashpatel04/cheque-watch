package com.chequetracker.watch

import android.app.Application
import com.chequetracker.watch.work.RefreshScheduler

class ChequeWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.ensureScheduled(this)
    }
}
