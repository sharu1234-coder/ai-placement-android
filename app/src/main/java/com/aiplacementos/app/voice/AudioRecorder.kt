package com.aiplacementos.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper around MediaRecorder, recording AAC/M4A (matches what the backend's
 * OllamaProvider.transcribe expects -- see interview_service.py). One recording at a time;
 * calling start() while already recording restarts cleanly.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(outputFile: File) {
        release()

        val mediaRecorder = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        this.outputFile = outputFile
    }

    /** Stops the current recording and returns the file it was written to, if any. */
    fun stop(): File? {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // stop() throws if start() never captured any audio (e.g. stopped immediately).
        }
        recorder?.release()
        recorder = null
        outputFile = null
        return file
    }

    fun release() {
        try {
            recorder?.reset()
        } catch (e: Exception) {
            // Ignore -- we're discarding this recorder either way.
        }
        recorder?.release()
        recorder = null
        outputFile = null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
