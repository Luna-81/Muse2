package com.blue.hush.audio

import android.content.Context
import com.blue.hush.session.MusicTrack

/**
 * 试听控制器：独立于 Service 的音频实例。
 *
 * 职责：
 *  - 管理一个 AmbientAudioEngine 实例
 *  - 记住当前试听的 track（切换时停止旧的）
 *  - 暴露 toggle / stop 两个操作
 *
 * 线程：所有调用都应在主线程。
 */
class PreviewController(context: Context) {

    private val engine = AmbientAudioEngine(context)
    private var current: MusicTrack? = null

    /**
     * 切换试听：
     *  - 如果传的 track 就是当前正在试听的，则停止并返回 null
     *  - 否则开始播放新 track，返回新 track
     */
    fun toggle(track: MusicTrack): MusicTrack? {
        return if (current == track) {
            stop()
            null
        } else {
            engine.stop()
            engine.play(track)
            current = track
            track
        }
    }

    fun stop() {
        engine.stop()
        current = null
    }

    fun current(): MusicTrack? = current
}