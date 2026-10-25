// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object CountdownIo {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ezCD-IO")
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(task: () -> Unit) {
        executor.execute(task)
    }

    fun <T> submit(task: () -> T, onComplete: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(task)
            mainHandler.post { onComplete(result) }
        }
    }
}
