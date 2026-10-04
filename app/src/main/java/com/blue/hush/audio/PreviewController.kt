package com.blue.hush.audio

import android.content.Context
import com.blue.hush.session.MusicTrack

class PreviewController(context: Context) {

    private val engine = AmbientAudioEngine(context)
    private var current: MusicTrack? = null

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