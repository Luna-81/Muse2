package com.blue.hush.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.blue.hush.R
import com.blue.hush.session.MusicTrack

/** Streams bundled nature recordings without synthesizing audio on the session thread. */
class AmbientAudioEngine(context: Context) {
    private val resources = context.applicationContext.resources
    private val lock = Any()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var wantsPlayback = false
    private var volume = 0.7f

    fun play(track: MusicTrack, paused: Boolean = false) {
        synchronized(lock) {
            stopLocked()
            val output = MediaPlayer()
            player = output
            wantsPlayback = !paused
            try {
                output.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                output.setOnPreparedListener {
                    synchronized(lock) {
                        // A dismissed preview or replaced track must never start late.
                        if (player === output) {
                            prepared = true
                            output.isLooping = true
                            output.setVolume(volume, volume)
                            if (wantsPlayback) output.start()
                        }
                    }
                }
                output.setOnErrorListener { _, what, extra ->
                    synchronized(lock) {
                        if (player === output) {
                            Log.e(TAG, "Soundscape playback failed: $what / $extra")
                            stopLocked()
                        }
                    }
                    true
                }
                val resource = when (track) {
                    MusicTrack.RAIN -> R.raw.rain
                    MusicTrack.OCEAN -> R.raw.ocean
                    MusicTrack.FIREPLACE -> R.raw.fireplace
                }
                resources.openRawResourceFd(resource).use { asset ->
                    output.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
                output.prepareAsync()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to load soundscape ${track.name}", error)
                stopLocked()
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            wantsPlayback = false
            if (prepared) player?.pause()
        }
    }

    fun resume() {
        synchronized(lock) {
            wantsPlayback = true
            if (prepared) player?.start()
        }
    }

    fun setVolume(value: Float) {
        synchronized(lock) {
            volume = value.coerceIn(0f, 1f)
            if (prepared) player?.setVolume(volume, volume)
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        val previous = player
        player = null
        prepared = false
        wantsPlayback = false
        previous?.release()
    }

    private companion object {
        const val TAG = "HushAmbientAudio"
    }
}
