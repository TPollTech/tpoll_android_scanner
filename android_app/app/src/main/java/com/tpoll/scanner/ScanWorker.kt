package com.tpoll.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ScanService.startScan(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
