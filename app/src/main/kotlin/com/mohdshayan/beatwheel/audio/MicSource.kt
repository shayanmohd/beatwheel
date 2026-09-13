package com.mohdshayan.beatwheel.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The built-in microphone, pinned so a Bluetooth headset microphone is never used, with the
 * UNPROCESSED source where the device offers it (no gain control smearing the pitch), else
 * VOICE_RECOGNITION. Audio is read into memory for measurement and never written anywhere.
 */
class MicSource(private val context: Context) : SignalSource {
    override val sampleRate = 48_000
    private var record: AudioRecord? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @SuppressLint("MissingPermission")
    override fun start(): StartResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return StartResult.NO_PERMISSION
        }
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, 9_600 * 4))
                .build()
        } catch (e: SecurityException) {
            return StartResult.NO_PERMISSION
        } catch (e: Exception) {
            return StartResult.BUSY
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return StartResult.BUSY
        }
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let { rec.setPreferredDevice(it) }
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            rec.release()
            return StartResult.BUSY
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return StartResult.BUSY
        }
        record = rec
        return StartResult.OK
    }

    override fun read(buffer: FloatArray, len: Int): Int =
        record?.read(buffer, 0, len, AudioRecord.READ_BLOCKING) ?: -1

    override fun isSilencedByOtherApp(): Boolean {
        val rec = record ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return rec.activeRecordingConfiguration?.isClientSilenced == true
    }

    override fun stop() {
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
    }
}
