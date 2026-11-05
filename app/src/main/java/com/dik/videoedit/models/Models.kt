package com.dik.videoedit.models

import java.util.UUID

/** Satu klip video dalam timeline. Semua durasi dalam milidetik. */
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    /** Dimensi tampilan (sudah memperhitungkan rotasi metadata). */
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
) {
    val trimmedMs: Long get() = trimEndMs - trimStartMs

    /** Durasi di timeline setelah kecepatan diterapkan. */
    val effectiveMs: Long get() = (trimmedMs / speed).toLong()
}

enum class AspectOption(val label: String, val ratio: Float?) {
    ORIGINAL("Asli", null),
    PORTRAIT("9:16", 9f / 16f),
    SQUARE("1:1", 1f),
    LANDSCAPE("16:9", 16f / 9f),
}

/** anchorY memakai koordinat NDC OpenGL (+1 = atas, -1 = bawah). */
enum class TextPosition(val label: String, val anchorY: Float) {
    TOP("Atas", 0.80f),
    CENTER("Tengah", 0f),
    BOTTOM("Bawah", -0.80f),
}

/** fraction = tinggi huruf relatif terhadap sisi terpendek frame. */
enum class TextSize(val label: String, val fraction: Float) {
    SMALL("Kecil", 0.055f),
    MEDIUM("Sedang", 0.08f),
    LARGE("Besar", 0.115f),
}

object TextColors {
    val all: List<Int> = listOf(
        0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFF63B3FF.toInt(),
        0xFFFF6B6B.toInt(), 0xFF000000.toInt(),
    )
}

data class TextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val position: TextPosition = TextPosition.BOTTOM,
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: TextSize = TextSize.MEDIUM,
    /** Posisi horizontal dalam koordinat NDC: -0.75 kiri, 0 tengah, 0.75 kanan. */
    val anchorX: Float = 0f,
    /** True untuk stiker emoji: tanpa latar dan ukurannya lebih besar dari teks biasa. */
    val plain: Boolean = false,
    /** Path file gambar (overlay foto) di penyimpanan aplikasi; null untuk teks/stiker emoji. */
    val imagePath: String? = null,
) {
    /** Latar semi-transparan agar teks tetap terbaca di video terang/gelap. */
    val backgroundColor: Int
        get() = when {
            plain -> 0x00000000
            color == 0xFF000000.toInt() -> 0x66FFFFFF
            else -> 0x66000000
        }

    /** Tinggi huruf relatif terhadap sisi terpendek frame. */
    val heightFraction: Float get() = size.fraction * (if (plain) 2.2f else 1f)

    /** Lebar overlay foto relatif terhadap lebar frame. */
    val imageWidthFraction: Float
        get() = when (size) {
            TextSize.SMALL -> 0.2f
            TextSize.MEDIUM -> 0.35f
            TextSize.LARGE -> 0.55f
        }
}

/** Animasi otomatis sepanjang video (versi sederhana dari keyframe). */
enum class AnimationType(val label: String, val premium: Boolean) {
    NONE("Tanpa Animasi", false),
    ZOOM_PULSE("Zoom Denyut", true),
    SLOW_ZOOM("Zoom Perlahan", true),
    DRIFT("Geser Pelan", true),
    SHAKE("Getar", true),
}

/** Speed ramp: klip dibagi beberapa bagian dengan kecepatan berbeda. Pasangan (porsi durasi, kecepatan). */
enum class SpeedRamp(val label: String, val parts: List<Pair<Float, Float>>) {
    SLOW_THEN_FAST("Lambat lalu Cepat", listOf(0.4f to 0.5f, 0.6f to 2f)),
    FAST_SLOW_FAST("Cepat - Lambat - Cepat", listOf(0.3f to 2f, 0.4f to 0.4f, 0.3f to 2f)),
    HERO("Hero", listOf(0.3f to 1f, 0.35f to 0.5f, 0.35f to 1.5f)),
}

enum class FilterType(val label: String, val premium: Boolean) {
    NONE("Asli", false),
    BRIGHT("Cerah", false),
    GRAYSCALE("Hitam Putih", true),
    SEPIA("Sepia", true),
    WARM("Hangat", true),
    COOL("Dingin", true),
    VIVID("Vivid", true),
}

enum class EffectType(val label: String, val premium: Boolean) {
    NONE("Tanpa Efek", false),
    ZOOM("Zoom", true),
    MIRROR("Cermin", true),
    BLUR("Blur", true),
    INVERT("Negatif", true),
}

enum class TransitionType(val label: String, val premium: Boolean) {
    CUT("Potong", false),
    FADE_BLACK("Fade Hitam", true),
    FADE_WHITE("Fade Putih", true),
}

/** Fokus seleksi di timeline: tidak ada, sebuah klip video, atau sebuah klip audio. */
enum class Focus { NONE, CLIP, AUDIO }

/**
 * Satu klip audio di track audio (musik dari penyimpanan, atau audio hasil ekstrak dari video).
 * Semua waktu dalam milidetik. File sumber tidak pernah diubah; hanya titik potong yang disimpan.
 */
data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    /** Durasi file sumber (file audio, atau file video bila hasil ekstrak). */
    val sourceDurationMs: Long,
    /** Posisi awal klip audio di timeline. */
    val startMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    /** Kecepatan putar; selain 1 hanya terjadi pada audio yang diekstrak dari klip yang dipercepat/diperlambat. */
    val speed: Float = 1f,
    val extracted: Boolean = false,
) {
    val trimmedMs: Long get() = trimEndMs - trimStartMs

    /** Durasi di timeline setelah kecepatan diterapkan. */
    val durationMs: Long get() = (trimmedMs / speed).toLong()

    val endMs: Long get() = startMs + durationMs

    /** Penguatan (volume x fade) pada posisi [relMs] milidetik sejak awal klip audio di timeline. */
    fun gainAt(relMs: Long): Float {
        val dur = durationMs
        var g = 1f
        if (fadeInMs > 0 && relMs < fadeInMs) g = minOf(g, relMs.toFloat() / fadeInMs)
        if (fadeOutMs > 0 && relMs > dur - fadeOutMs) g = minOf(g, (dur - relMs).toFloat() / fadeOutMs)
        return volume * g.coerceIn(0f, 1f)
    }
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clips: List<Clip>,
    val aspect: AspectOption = AspectOption.ORIGINAL,
    /** Rotasi searah jarum jam: 0, 90, 180, 270. */
    val rotation: Int = 0,
    val audio: List<AudioTrack> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val filter: FilterType = FilterType.NONE,
    val effect: EffectType = EffectType.NONE,
    val transition: TransitionType = TransitionType.CUT,
    val animation: AnimationType = AnimationType.NONE,
    /** Filter pengurang bising untuk semua audio (diterapkan saat export). */
    val noiseReduction: Boolean = false,
) {
    val totalDurationMs: Long get() = clips.sumOf { it.effectiveMs }

    /** Ujung timeline, termasuk audio yang melewati akhir video. */
    val timelineEndMs: Long get() = maxOf(totalDurationMs, audio.maxOfOrNull { it.endMs } ?: 0L)

    val usesPremium: Boolean get() = filter.premium || effect.premium || transition.premium || animation.premium

    /** Rasio (lebar/tinggi) frame hasil akhir. */
    val outputRatio: Float
        get() {
            aspect.ratio?.let { return it }
            val c = clips.firstOrNull() ?: return 16f / 9f
            val r = c.width.toFloat() / c.height.coerceAtLeast(1)
            return if (rotation % 180 != 0) 1f / r else r
        }
}

/** Preset awal untuk layar Template. */
data class TemplatePreset(
    val id: String,
    val title: String,
    val description: String,
    val aspect: AspectOption = AspectOption.ORIGINAL,
    val speed: Float = 1f,
    val filter: FilterType = FilterType.NONE,
    /** Bila diisi, tiap video yang dipilih dipotong menjadi slot sepanjang ini (ms), dari awal video. */
    val slotMs: Long? = null,
)

object Templates {
    val all: List<TemplatePreset> = listOf(
        TemplatePreset("reels", "Reels & Shorts", "Format vertikal 9:16 untuk Instagram, TikTok, dan YouTube Shorts", aspect = AspectOption.PORTRAIT),
        TemplatePreset("square", "Feed Persegi", "Rasio 1:1 yang pas untuk feed dan postingan", aspect = AspectOption.SQUARE),
        TemplatePreset("wide", "Layar Lebar", "Rasio 16:9 untuk YouTube dan layar TV", aspect = AspectOption.LANDSCAPE),
        TemplatePreset("slowmo", "Gerak Lambat", "Kecepatan 0,5x untuk momen yang dramatis", speed = 0.5f),
        TemplatePreset("timelapse", "Timelapse", "Kecepatan 3x untuk perjalanan atau proses panjang", speed = 3f),
        TemplatePreset("bright", "Cerah & Segar", "Filter Cerah agar video lebih hidup", filter = FilterType.BRIGHT),
        TemplatePreset("montage", "Montase Cepat", "Pilih beberapa video, tiap video otomatis jadi potongan 1,5 detik. Tinggal ganti video.", aspect = AspectOption.PORTRAIT, filter = FilterType.BRIGHT, slotMs = 1500L),
        TemplatePreset("vlog", "Vlog Singkat", "Tiap video otomatis jadi potongan 3 detik dalam format vertikal.", aspect = AspectOption.PORTRAIT, slotMs = 3000L),
    )
}
