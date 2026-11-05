package com.dik.videoedit.videoprocessing

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Pengurang bising sederhana untuk hasil export: filter high-pass (membuang dengung frekuensi rendah)
 * ditambah noise gate lembut (menurunkan volume saat sinyal sangat pelan, misalnya desis angin/kipas).
 * Suara bicara/musik yang lebih keras dibiarkan. Bukan pengganti peredam bising berbasis AI.
 */
class NoiseReductionAudioProcessor : BaseAudioProcessor() {

    private var hpPrevIn = FloatArray(0)
    private var hpPrevOut = FloatArray(0)
    private var envelope = 0f
    private var gain = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        hpPrevIn = FloatArray(inputAudioFormat.channelCount)
        hpPrevOut = FloatArray(inputAudioFormat.channelCount)
        envelope = 0f
        gain = 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate.toDouble()
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerFrame = channels * (if (isFloat) 4 else 2)
        val frames = size / bytesPerFrame
        if (hpPrevIn.size != channels) onFlush()

        // High-pass orde 1, frekuensi potong 120 Hz.
        val rc = 1.0 / (2 * PI * 120.0)
        val dt = 1.0 / rate
        val a = (rc / (rc + dt)).toFloat()
        // Envelope: serangan cepat, pelepasan lambat. Gain: buka cepat (5 ms), tutup pelan (120 ms).
        val envRelease = exp(-1.0 / (0.15 * rate)).toFloat()
        val gainOpen = exp(-1.0 / (0.005 * rate)).toFloat()
        val gainClose = exp(-1.0 / (0.12 * rate)).toFloat()
        val samples = FloatArray(channels)

        for (f in 0 until frames) {
            var peak = 0f
            for (ch in 0 until channels) {
                val x = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
                val y = a * (hpPrevOut[ch] + x - hpPrevIn[ch])
                hpPrevIn[ch] = x
                hpPrevOut[ch] = y
                samples[ch] = y
                peak = max(peak, abs(y))
            }
            envelope = if (peak > envelope) peak else envelope * envRelease + peak * (1f - envRelease)
            val target = if (envelope < GATE_THRESHOLD) GATE_FLOOR else 1f
            val coef = if (target > gain) gainOpen else gainClose
            gain = target + (gain - target) * coef
            for (ch in 0 until channels) {
                val v = samples[ch] * gain
                if (isFloat) out.putFloat(v) else out.putShort((v * 32768f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    private companion object {
        /** Sekitar -40 dBFS. */
        const val GATE_THRESHOLD = 0.01f
        const val GATE_FLOOR = 0.2f
    }
}
