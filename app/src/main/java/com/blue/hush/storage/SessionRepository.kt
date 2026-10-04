package com.blue.hush.storage


import android.content.Context
import android.os.Handler
import android.os.Looper
import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 数据层门面：封装 HushDatabase + 线程切换。
 *
 * 调用方（MainViewModel）不再需要：
 *  - 持有 ioExecutor
 *  - 手写 mainHandler.post
 *  - 关心数据库读写在哪条线程
 *
 * 所有方法都是异步的，结果通过回调切回主线程。
 */
class SessionRepository(
    appContext: Context,
    private val database: HushDatabase = HushDatabase(appContext),
    private val io: Executor = Executors.newSingleThreadExecutor(),
    private val main: Handler = Handler(Looper.getMainLooper()),
) {

    companion object {
        @Volatile
        private var bundledHistoryRestored = false
    }

    /** 只在进程内第一次调用时执行。跨 ViewModel 重建也只跑一次。 */
    fun restoreBundledHistoryOnce(appContext: Context) {
        if (bundledHistoryRestored) return
        io.execute {
            synchronized(Companion) {
                if (bundledHistoryRestored) return@synchronized
                runCatching { database.restoreBundledHistory(appContext) }
                    .onSuccess { bundledHistoryRestored = true }
                    .onFailure { android.util.Log.e("Hush", "Could not restore bundled history", it) }
            }
        }
    }

    fun loadHistory(onResult: (List<SessionSummary>) -> Unit) {
        io.execute {
            val summaries = runCatching { database.loadSummaries() }.getOrDefault(emptyList())
            main.post { onResult(summaries) }
        }
    }

    fun loadDetail(summary: SessionSummary, onResult: (List<StateSample>) -> Unit) {
        io.execute {
            val samples = runCatching { database.loadSamples(summary.id) }.getOrDefault(emptyList())
            main.post { onResult(samples) }
        }
    }

    /** 删除后重新拉一遍历史，一并回调给调用方。 */
    fun delete(summary: SessionSummary, onResult: (Result<List<SessionSummary>>) -> Unit) {
        io.execute {
            val result = runCatching {
                database.deleteSession(summary.id)
                database.loadSummaries()
            }
            main.post { onResult(result) }
        }
    }

    /** 模拟数据是否可用 + 首次历史，一起回调。 */
    fun loadSimulationData(
        appContext: Context,
        onResult: (available: Boolean) -> Unit,
    ) {
        io.execute {
            val samples = runCatching { MuseReplaySource.load(appContext) }.getOrDefault(emptyList())
            val available = runCatching { MuseReplaySource.isUsable(samples) }.getOrDefault(false)
            main.post { onResult(available) }
        }
    }

    fun close() {
        io.execute { database.close() }
        (io as? java.util.concurrent.ExecutorService)?.shutdown()
    }
}