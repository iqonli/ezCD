// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Font parsing and archives never block the event/autosave I/O queue. */
internal object FontIo {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "ezCD-fonts") }
    private val main = Handler(Looper.getMainLooper())
    fun <T> submit(task: () -> T, complete: (Result<T>) -> Unit) {
        executor.execute { val result = runCatching(task); main.post { complete(result) } }
    }
}

internal class FontTask<T> {
    var running = false
        private set
    var progress = 0
        private set
    private var result: Result<T>? = null
    var onChanged: (() -> Unit)? = null
    fun start(task: ((Int) -> Unit) -> T) {
        check(!running)
        running = true; progress = 0
        val main = Handler(Looper.getMainLooper())
        FontIo.submit({
            var last = -1
            task { value ->
                val percent = value.coerceIn(0, 99)
                if (percent > last) {
                    last = percent
                    main.post { progress = percent; onChanged?.invoke() }
                }
            }
        }) { running = false; if (it.isSuccess) progress = 100; result = it; onChanged?.invoke() }
    }
    fun takeResult(): Result<T>? = result.also { result = null }
}
