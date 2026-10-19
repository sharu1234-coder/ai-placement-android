package com.aiplacementos.app.voice.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Puts the assistant's voice on a chosen output, and puts the device back afterwards.
 *
 * ## What was actually wrong
 *
 * The playback `AudioTrack` in [AudioTrackPcmPlayback] is built with
 * `USAGE_VOICE_COMMUNICATION`. That is the right usage for this session -- it holds the
 * microphone and the speaker at once, and the usage is what enlists the platform's acoustic
 * echo canceller -- but it carries two consequences nothing in the app had accounted for:
 *
 * 1. the platform prefers the **earpiece** for communication audio, and
 * 2. the level follows `STREAM_VOICE_CALL`, not `STREAM_MUSIC`.
 *
 * Together those are the reported defect exactly: speech that is audible but quiet, coming out
 * of the earpiece instead of the loudspeaker, and unmoved by the volume keys the student
 * reaches for. Nothing was wrong with the synthesiser or the track. The device was simply
 * never asked to behave like a speakerphone -- [AndroidAudioRouteMonitor] observes the route
 * and, by its own documented choice, never sets one.
 *
 * Entering `MODE_IN_COMMUNICATION` is also what gives the volume keys back: while that mode is
 * active the platform maps them to the voice-call stream, so the student keeps ordinary control
 * of the level. That is why this class changes the mode and never touches a volume itself.
 *
 * ## Restoring state is not optional
 *
 * The audio mode is device-global. A session that entered `MODE_IN_COMMUNICATION` and exited
 * without restoring it leaves every other app routed as though a call were in progress -- which
 * the user meets as "my music comes out of the earpiece now", long after leaving this screen
 * and with nothing to connect it to. [end] therefore never throws, guards each step
 * independently, and is safe to call more than once.
 */
class AndroidAudioOutputRouter(context: Context) : AudioOutputRouter {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Null until [begin] captures it, which is what makes [begin] safe to call twice. */
    private var savedMode: Int? = null
    private var savedSpeakerphone: Boolean? = null

    override fun availableOutputs(): List<VoiceAudioRoute> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = runCatching { audioManager.availableCommunicationDevices }
                .getOrNull() ?: return emptyList()
            return devices.mapNotNull { routeOf(it.type) }.distinct()
        }
        // Pre-31 there is no communication-device list; the output device list is all the
        // platform offers.
        val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull() ?: return emptyList()
        return outputs.mapNotNull { routeOf(it.type) }.distinct()
    }

    override fun begin(preference: VoiceAudioRoute?): VoiceAudioError? {
        if (savedMode == null) {
            savedMode = runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                savedSpeakerphone =
                    runCatching { audioManager.isSpeakerphoneOn }.getOrDefault(false)
            }
        }
        val modeSet = runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        modeSet.exceptionOrNull()?.let {
            return VoiceAudioError.DeviceUnavailable(
                "could not enter communication audio mode: " +
                    (it.message ?: "rejected by the platform")
            )
        }
        return select(preference ?: autoTarget())
    }

    /**
     * Where to send audio when the caller states no preference.
     *
     * An attached headset wins. Connecting one is an explicit statement about where audio
     * should go, and dragging it to the loudspeaker because a screen opened is the same
     * rudeness [AndroidAudioRouteMonitor]'s KDoc declines to commit. The loudspeaker is the
     * default only when nothing else is attached -- which is the case the defect is about.
     */
    private fun autoTarget(): VoiceAudioRoute {
        val available = availableOutputs()
        return when {
            VoiceAudioRoute.BLUETOOTH in available -> VoiceAudioRoute.BLUETOOTH
            VoiceAudioRoute.WIRED_HEADSET in available -> VoiceAudioRoute.WIRED_HEADSET
            else -> VoiceAudioRoute.SPEAKER
        }
    }

    override fun select(target: VoiceAudioRoute): VoiceAudioError? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = runCatching { audioManager.availableCommunicationDevices }
                .getOrNull()
                ?.firstOrNull { routeOf(it.type) == target }
                ?: return VoiceAudioError.DeviceUnavailable(
                    "the platform offers no communication device for " + target
                )
            val applied = runCatching { audioManager.setCommunicationDevice(device) }
            // SecurityException is the documented failure when another app owns the
            // communication device. It must not reach the student as a crash.
            applied.exceptionOrNull()?.let {
                return VoiceAudioError.DeviceUnavailable(
                    "could not select " + target + ": " +
                        (it.message ?: it.javaClass.simpleName)
                )
            }
            if (!applied.getOrDefault(false)) {
                return VoiceAudioError.PlaybackFailed(
                    "the platform refused to route to " + target
                )
            }
            return null
        }

        // Pre-31 the speakerphone flag is the only lever, so only "loudspeaker" and "not
        // loudspeaker" are expressible. A request for Bluetooth is reported as unsupported
        // rather than silently answered with success.
        if (target != VoiceAudioRoute.SPEAKER && target != VoiceAudioRoute.EARPIECE) {
            return VoiceAudioError.UnsupportedConfiguration(
                "selecting " + target + " needs Android 12 or newer; this device can only " +
                    "switch between the loudspeaker and the earpiece"
            )
        }
        @Suppress("DEPRECATION")
        val applied = runCatching {
            audioManager.isSpeakerphoneOn = target == VoiceAudioRoute.SPEAKER
        }
        return applied.exceptionOrNull()?.let {
            VoiceAudioError.DeviceUnavailable(
                "could not switch the loudspeaker: " + (it.message ?: "rejected")
            )
        }
    }

    override fun activeOutput(): VoiceAudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = runCatching { audioManager.communicationDevice }.getOrNull()
            return device?.type?.let { routeOf(it) } ?: VoiceAudioRoute.UNKNOWN
        }
        @Suppress("DEPRECATION")
        val speaker = runCatching { audioManager.isSpeakerphoneOn }.getOrNull()
            ?: return VoiceAudioRoute.UNKNOWN
        return if (speaker) VoiceAudioRoute.SPEAKER else VoiceAudioRoute.EARPIECE
    }

    override fun end() {
        // Each step guarded separately: a throw on the first must not skip the mode restore,
        // which is the one with the longest tail.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        } else {
            savedSpeakerphone?.let { previous ->
                @Suppress("DEPRECATION")
                runCatching { audioManager.isSpeakerphoneOn = previous }
            }
        }
        savedMode?.let { previous -> runCatching { audioManager.mode = previous } }
        savedMode = null
        savedSpeakerphone = null
    }

    /**
     * Only the device types this product can route between.
     *
     * Returns null for anything else -- HDMI, a hearing aid, an auxiliary line -- rather than
     * folding them into UNKNOWN, so [availableOutputs] offers a list of real choices instead
     * of one unnamed entry a student cannot reason about.
     */
    private fun routeOf(type: Int): VoiceAudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> VoiceAudioRoute.SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> VoiceAudioRoute.EARPIECE
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> VoiceAudioRoute.WIRED_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> VoiceAudioRoute.BLUETOOTH
        else -> null
    }
}
