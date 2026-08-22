package com.aiplacementos.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/** Thrown when the underlying MediaRecorder fails to prepare/start (mic busy, hardware
 * failure, permission revoked mid-session) or reports a runtime error during recording. */
class AudioRecordingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin wrapper around MediaRecorder, recording AAC/M4A (matches what the backend's
 * OllamaProvider.transcribe expects -- see interview_service.py). One recording at a time;
 * calling start() while already recording restarts cleanly. Caps recordings at
 * MAX_DURATION_MS so a stuck/forgotten recording can't grow unbounded on-device.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @Volatile
    private var recordingError: String? = null

    /** @throws AudioRecordingException if the recorder can't be prepared/started -- e.g.
     * the mic is already in use by another app, a hardware failure, or the permission was
     * revoked between the caller's own check and this call. Neither `prepare()` nor
     * `start()` was previously wrapped, so any of these crashed the app outright. */
    fun start(outputFile: File) {
        release()
        recordingError = null

        val mediaRecorder = newRecorder()
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(outputFile.absolutePath)
                setOnErrorListener { _, what, extra -> recordingError = "MediaRecorder error $what/$extra" }
                prepare()
                start()
            }
        } catch (e: IOException) {
            mediaRecorder.release()
            throw AudioRecordingException("Could not start recording", e)
        } catch (e: IllegalStateException) {
            mediaRecorder.release()
            throw AudioRecordingException("Could not start recording", e)
        } catch (e: SecurityException) {
            mediaRecorder.release()
            throw AudioRecordingException("Microphone permission was denied", e)
        }

        recorder = mediaRecorder
        this.outputFile = outputFile
    }

    /**
     * Stops the current recording and returns the file it was written to, or null if no
     * recording was in progress.
     * @throws AudioRecordingException if MediaRecorder reported a runtime error mid-recording
     * (e.g. the OS suspended mic access while the app was backgrounded) -- the resulting file
     * may be truncated/corrupt, so this must not be silently treated as a success.
     */
    fun stop(): File? {
        val file = outputFile
        val error = recordingError
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // Throws if start() never captured any audio (e.g. stopped immediately) -- not
            // itself reportable unless recordingError was also set via the error listener.
        }
        recorder?.release()
        recorder = null
        outputFile = null
        recordingError = null

        if (error != null) {
            throw AudioRecordingException(error)
        }
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
        recordingError = null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private companion object {
        const val MAX_DURATION_MS = 120_000 // 2 minutes per answer
    }
}
