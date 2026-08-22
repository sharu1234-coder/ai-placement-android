package com.aiplacementos.app.voice

import android.media.MediaPlayer
import java.io.File
import java.io.IOException

/** Thin wrapper around MediaPlayer for playing a locally-saved audio file (a synthesized
 * question, downloaded once per question and cached to disk before playback). */
class AudioPlayer {
    private var player: MediaPlayer? = null

    /** Plays `file`; `onComplete` fires on natural completion, on playback error, or if the
     * player couldn't even be prepared (corrupt/missing file) -- callers treat "couldn't
     * play the question audio" as non-fatal and move on rather than blocking the turn. */
    fun play(file: File, onComplete: () -> Unit) {
        release()

        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.apply {
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
        } catch (e: IOException) {
            mediaPlayer.release()
            onComplete()
            return
        } catch (e: IllegalStateException) {
            mediaPlayer.release()
            onComplete()
            return
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
