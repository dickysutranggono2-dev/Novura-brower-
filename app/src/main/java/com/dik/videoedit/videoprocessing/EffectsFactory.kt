package com.dik.videoedit.videoprocessing

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import com.dik.videoedit.models.AnimationType
import com.dik.videoedit.models.EffectType
import com.dik.videoedit.models.FilterType
import com.dik.videoedit.models.Project
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Satu sumber kebenaran untuk efek visual: dipakai oleh preview (ExoPlayer.setVideoEffects)
 * dan export (Transformer) agar hasilnya sama.
 */
object EffectsFactory {

    // Matriks 4x4 kolom-mayor untuk sepia.
    private val SEPIA = floatArrayOf(
        0.393f, 0.349f, 0.272f, 0f,
        0.769f, 0.686f, 0.534f, 0f,
        0.189f, 0.168f, 0.131f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Efek yang diterapkan SEBELUM penyesuaian rasio/resolusi: rotasi, efek, filter. */
    fun styleEffects(p: Project): List<Effect> = buildList {
        if (p.rotation % 360 != 0) {
            // Media3 memutar berlawanan arah jarum jam; rotasi proyek searah jarum jam.
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(((360 - p.rotation) % 360).toFloat()).build())
        }
        when (p.effect) {
            EffectType.ZOOM -> add(Crop(-0.8f, 0.8f, -0.8f, 0.8f)) // zoom 1,25x ke tengah
            EffectType.MIRROR -> add(ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build())
            EffectType.INVERT -> add(RgbFilter.createInvertedFilter())
            else -> Unit
        }
        animationEffect(p.animation)?.let { add(it) }
        when (p.filter) {
            FilterType.NONE -> Unit
            FilterType.BRIGHT -> {
                add(Brightness(0.08f))
                add(Contrast(0.12f))
            }
            FilterType.GRAYSCALE -> add(RgbFilter.createGrayscaleFilter())
            FilterType.SEPIA -> add(RgbMatrix { _, _ -> SEPIA })
            FilterType.WARM -> add(RgbAdjustment.Builder().setRedScale(1.12f).setBlueScale(0.88f).build())
            FilterType.COOL -> add(RgbAdjustment.Builder().setRedScale(0.90f).setBlueScale(1.15f).build())
            FilterType.VIVID -> {
                add(HslAdjustment.Builder().adjustSaturation(35f).build())
                add(Contrast(0.10f))
            }
        }
    }

    /**
     * Animasi berbasis waktu (versi sederhana dari keyframe). Selalu di-zoom sedikit agar tepi video
     * tidak terlihat kosong saat bergerak. Matriks dalam koordinat NDC (-1..1), pusat di (0,0).
     */
    private fun animationEffect(type: AnimationType): Effect? = when (type) {
        AnimationType.NONE -> null
        AnimationType.ZOOM_PULSE -> MatrixTransformation { us ->
            val t = us / 1_000_000.0
            val k = (1.10 + 0.08 * sin(2 * PI * t / 3.0)).toFloat()
            Matrix().apply { postScale(k, k) }
        }
        AnimationType.SLOW_ZOOM -> MatrixTransformation { us ->
            val t = us / 1_000_000.0
            // Gelombang segitiga: 1,0x -> 1,25x -> 1,0x tiap 10 detik.
            val phase = (t % 10.0) / 5.0
            val k = (1.0 + 0.25 * (1.0 - abs(phase - 1.0))).toFloat()
            Matrix().apply { postScale(k, k) }
        }
        AnimationType.DRIFT -> MatrixTransformation { us ->
            val t = us / 1_000_000.0
            val dx = (0.06 * sin(2 * PI * t / 8.0)).toFloat()
            Matrix().apply { postScale(1.14f, 1.14f); postTranslate(dx, 0f) }
        }
        AnimationType.SHAKE -> MatrixTransformation { us ->
            val t = us / 1_000_000.0
            val dx = (0.012 * sin(2 * PI * 7.0 * t)).toFloat()
            val dy = (0.012 * sin(2 * PI * 9.0 * t + 1.0)).toFloat()
            Matrix().apply { postScale(1.06f, 1.06f); postTranslate(dx, dy) }
        }
    }

    /** Efek yang diterapkan SETELAH ukuran akhir ditetapkan (blur bergantung pada piksel). */
    fun postEffects(p: Project, shortSidePx: Int): List<Effect> =
        if (p.effect == EffectType.BLUR) {
            listOf(GaussianBlur((min(shortSidePx, 1080) * 0.012f).coerceAtLeast(3f)))
        } else {
            emptyList()
        }

    fun previewEffects(p: Project): List<Effect> {
        val first = p.clips.firstOrNull()
        val shortSide = if (first != null) min(first.width, first.height) else 720
        return styleEffects(p) +
            Presentation.createForAspectRatio(p.outputRatio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP) +
            postEffects(p, shortSide)
    }
}

/**
 * Fade lewat warna (hitam/putih) di awal dan/atau akhir satu klip, sebagai transisi antar klip.
 * Waktu dihitung relatif terhadap frame pertama yang diterima efek ini, sehingga tidak
 * bergantung pada offset timeline.
 */
class FadeMatrix(
    private val durationUs: Long,
    private val fadeUs: Long,
    private val color: Float,
    private val fadeIn: Boolean,
    private val fadeOut: Boolean,
) : RgbMatrix {
    private var firstUs = Long.MIN_VALUE

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        if (firstUs == Long.MIN_VALUE) firstUs = presentationTimeUs
        val t = presentationTimeUs - firstUs
        var visible = 1f
        if (fadeIn && fadeUs > 0 && t < fadeUs) visible = min(visible, t.toFloat() / fadeUs)
        if (fadeOut && fadeUs > 0 && t > durationUs - fadeUs) visible = min(visible, (durationUs - t).toFloat() / fadeUs)
        val k = visible.coerceIn(0f, 1f)
        val offset = color * (1f - k)
        return floatArrayOf(
            k, 0f, 0f, 0f,
            0f, k, 0f, 0f,
            0f, 0f, k, 0f,
            offset, offset, offset, 1f,
        )
    }
}
