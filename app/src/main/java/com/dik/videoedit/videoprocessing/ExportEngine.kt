package com.dik.videoedit.videoprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.dik.videoedit.models.AudioTrack
import com.dik.videoedit.models.Clip
import com.dik.videoedit.models.Project
import com.dik.videoedit.models.TextItem
import com.dik.videoedit.models.TransitionType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Export memakai Media3 Transformer. Video di-stream frame demi frame (tidak dimuat ke RAM).
 * Harus dipanggil dari main thread.
 */
class ExportEngine(private val context: Context) {

    interface Callback {
        fun onDone(sizeBytes: Long)
        fun onError(message: String)
    }

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()

    fun start(project: Project, options: ExportOptions, outputPath: String, callback: Callback) {
        val (w, h) = ExportPlan.outputSize(project.outputRatio, options.shortSide)
        val fps = ExportPlan.effectiveFps(project, options)
        val composition = buildComposition(project, options, w, h, fps)

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(ExportPlan.videoBitrate(w, h, fps)).build()
            )
            .build()

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    callback.onDone(exportResult.fileSizeBytes)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    Log.e(TAG, "Export gagal", exportException)
                    transformer = null
                    callback.onError(friendlyMessage(exportException))
                }
            })
            .build()
        transformer = t
        t.start(composition, outputPath)
    }

    /** Persentase 0..100, atau -1 bila belum tersedia. */
    fun progress(): Int {
        val t = transformer ?: return -1
        return if (t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) progressHolder.progress else -1
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    // ---------------------------------------------------------------------------------------

    private fun buildComposition(p: Project, o: ExportOptions, w: Int, h: Int, fps: Int): Composition {
        val last = p.clips.lastIndex
        val items = p.clips.mapIndexed { i, clip -> editedItem(p, clip, i, last) }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(items).build())

        // Setiap klip audio = satu sequence audio sendiri. Posisi awal di timeline dibuat dengan gap
        // (jeda senyap) di depan klip, lalu Transformer mencampur semua sequence jadi satu audio.
        val totalMs = p.totalDurationMs
        p.audio.sortedBy { it.startMs }.forEach { a ->
            audioSequence(a, totalMs, p.noiseReduction)?.let { sequences += it }
        }

        val videoEffects = mutableListOf<Effect>()
        videoEffects += EffectsFactory.styleEffects(p)
        videoEffects += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        videoEffects += EffectsFactory.postEffects(p, min(w, h))

        val overlays = mutableListOf<TextureOverlay>()
        p.texts.forEach { item -> itemOverlay(item, w, h)?.let { overlays += it } }
        if (!o.removeWatermark) overlays += watermarkOverlay(w, h)
        if (overlays.isNotEmpty()) videoEffects += OverlayEffect(overlays)

        if (fps < ExportPlan.sourceFps(p)) videoEffects += FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat())

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), videoEffects))
            // Video tanpa trek audio tetap diberi audio senyap agar sambungan klip & musik konsisten.
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    private fun editedItem(p: Project, clip: Clip, index: Int, last: Int): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder().apply {
            if (clip.trimStartMs > 0) setStartPositionMs(clip.trimStartMs)
            if (clip.trimEndMs < clip.sourceDurationMs - 50) setEndPositionMs(clip.trimEndMs)
        }.build()
        val media = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(clipping).build()

        val audio = mutableListOf<AudioProcessor>()
        val video = mutableListOf<Effect>()
        if (clip.speed != 1f) {
            video += SpeedChangeEffect(clip.speed)
            audio += SonicAudioProcessor().apply {
                setSpeed(clip.speed)
                setPitch(1f)
            }
        }
        if (p.noiseReduction) audio += NoiseReductionAudioProcessor()
        volumeProcessor(clip.volume)?.let { audio += it }

        if (p.transition != TransitionType.CUT && p.clips.size > 1) {
            val durationUs = clip.effectiveMs * 1000
            video += FadeMatrix(
                durationUs = durationUs,
                fadeUs = min(500_000L, durationUs / 3),
                color = if (p.transition == TransitionType.FADE_WHITE) 1f else 0f,
                fadeIn = index > 0,
                fadeOut = index < last,
            )
        }
        return EditedMediaItem.Builder(media).setEffects(Effects(audio, video)).build()
    }

    /**
     * Membuat sequence untuk satu klip audio, atau null bila klip berada di luar durasi video.
     * Bagian audio yang melewati akhir video dipotong agar durasi hasil export tidak bertambah.
     */
    private fun audioSequence(a: AudioTrack, totalMs: Long, noiseReduction: Boolean): EditedMediaItemSequence? {
        if (a.startMs >= totalMs - 50) return null
        val room = totalMs - a.startMs
        var durMs = a.durationMs
        var trimEnd = a.trimEndMs
        var fadeOutMs = a.fadeOutMs
        if (durMs > room) {
            durMs = room
            trimEnd = a.trimStartMs + (room * a.speed).toLong()
            fadeOutMs = 0L // audio terpotong oleh akhir video; fade-out milik ujung aslinya
        }
        if (trimEnd - a.trimStartMs < 50 || durMs < 50) return null

        val clipping = MediaItem.ClippingConfiguration.Builder().apply {
            if (a.trimStartMs > 0) setStartPositionMs(a.trimStartMs)
            if (trimEnd < a.sourceDurationMs - 50) setEndPositionMs(trimEnd)
        }.build()

        val processors = mutableListOf<AudioProcessor>()
        if (a.speed != 1f) {
            processors += SonicAudioProcessor().apply {
                setSpeed(a.speed)
                setPitch(1f)
            }
        }
        if (noiseReduction) processors += NoiseReductionAudioProcessor()
        processors += GainFadeAudioProcessor(
            volume = a.volume,
            fadeInUs = a.fadeInMs * 1000,
            fadeOutUs = fadeOutMs * 1000,
            totalUs = durMs * 1000,
        )

        val item = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(a.uri).setClippingConfiguration(clipping).build()
        )
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .build()

        val builder = EditedMediaItemSequence.Builder()
        if (a.startMs > 0) builder.addGap(a.startMs * 1000)
        builder.addItem(item)
        return builder.build()
    }

    /** Pengatur volume (0..1). Null bila volume 100% (tidak perlu diproses). */
    private fun volumeProcessor(volume: Float): AudioProcessor? {
        if (abs(volume - 1f) < 0.01f) return null
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
        }
    }

    private fun textOverlay(t: TextItem, w: Int, h: Int): TextureOverlay {
        val shortSide = min(w, h).toFloat()
        val px = TextFit.fitPx(t.text, shortSide * t.heightFraction, w * 0.92f)
        val s = SpannableString(t.text)
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        s.setSpan(ForegroundColorSpan(t.color), 0, s.length, flag)
        s.setSpan(BackgroundColorSpan(t.backgroundColor), 0, s.length, flag)
        s.setSpan(AbsoluteSizeSpan(px.roundToInt(), false), 0, s.length, flag)
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(t.anchorX, t.position.anchorY)
            .build()
        return TextOverlay.createStaticTextOverlay(s, settings)
    }

    /** Teks, stiker emoji, atau overlay foto. Null bila file gambar tidak bisa dibaca. */
    private fun itemOverlay(t: TextItem, w: Int, h: Int): TextureOverlay? {
        val path = t.imagePath ?: return textOverlay(t, w, h)
        val bmp = loadOverlayBitmap(path, (w * t.imageWidthFraction).toInt().coerceAtLeast(16)) ?: return null
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(t.anchorX, t.position.anchorY)
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(bmp, settings)
    }

    private fun loadOverlayBitmap(path: String, targetWidth: Int): Bitmap? = try {
        val src = BitmapFactory.decodeFile(path)
        if (src == null) {
            null
        } else if (src.width == targetWidth) {
            src
        } else {
            val th = (src.height.toFloat() * targetWidth / src.width).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, targetWidth, th, true)
        }
    } catch (e: Exception) {
        null
    }

    private fun watermarkOverlay(w: Int, h: Int): TextureOverlay {
        val px = min(w, h) * 0.045f
        val s = SpannableString("VideoEdit")
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        s.setSpan(ForegroundColorSpan(0xCCFFFFFF.toInt()), 0, s.length, flag)
        s.setSpan(AbsoluteSizeSpan(px.roundToInt(), false), 0, s.length, flag)
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(1f, -1f)
            .setBackgroundFrameAnchor(0.95f, -0.95f)
            .build()
        return TextOverlay.createStaticTextOverlay(s, settings)
    }

    private fun friendlyMessage(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "Video sumber tidak dapat dibaca. Pastikan file masih ada di galeri."

        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_DECODING_FAILED,
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Format video ini tidak didukung oleh perangkat Anda."

        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
            "Perangkat gagal meng-encode video. Coba resolusi atau FPS yang lebih rendah."

        ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED ->
            "Gagal memproses audio (klip audio, volume, atau fade). Coba hapus klip audio terakhir yang ditambahkan lalu export lagi."

        else -> "Export gagal (${e.errorCodeName}). Coba lagi dengan resolusi lebih rendah."
    }

    private companion object {
        const val TAG = "ExportEngine"
    }
}
