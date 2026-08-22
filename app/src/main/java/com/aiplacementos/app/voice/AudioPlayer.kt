package com.aiplacementos.app.voice

import android.media.MediaPlayer
import java.io.File

/** Thin wrapper around MediaPlayer for playing a locally-saved audio file (a synthesized
 * question, downloaded once per question and cached to disk before playback). */
class AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(file: File, onComplete: () -> Unit) {
        release()

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                release()
                onComplete()
            }
            setOnErrorListener { _, _, _ ->
                release()
                onComplete()
                true
            }
            prepare()
            start()
        }
        player = mediaPlayer
    }

    fun release() {
        player?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: IllegalStateException) {
                // Already stopped/released -- nothing to do.
            }
            release()
        }
        player = null
    }
}
