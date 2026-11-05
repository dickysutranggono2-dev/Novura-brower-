package com.dik.videoedit.videoeditor

import com.dik.videoedit.models.AudioTrack
import com.dik.videoedit.models.Clip
import com.dik.videoedit.models.Project
import com.dik.videoedit.models.SpeedRamp
import java.util.UUID

/** Operasi murni pada proyek (tanpa efek samping), mudah diuji. */
object ProjectOps {
    const val MIN_CLIP_MS = 500L
    private const val MIN_SPLIT_EDGE_MS = 300L

    fun updateClip(p: Project, index: Int, change: (Clip) -> Clip): Project =
        p.copy(clips = p.clips.mapIndexed { i, c -> if (i == index) change(c) else c })

    fun trim(p: Project, index: Int, startMs: Long, endMs: Long): Project = updateClip(p, index) {
        val s = startMs.coerceIn(0L, it.sourceDurationMs - MIN_CLIP_MS)
        val e = endMs.coerceIn(s + MIN_CLIP_MS, it.sourceDurationMs)
        it.copy(trimStartMs = s, trimEndMs = e)
    }

    /** Membagi klip pada posisi sumber [atSourceMs]; null bila terlalu dekat ke tepi klip. */
    fun split(p: Project, index: Int, atSourceMs: Long): Project? {
        val clip = p.clips.getOrNull(index) ?: return null
        if (atSourceMs < clip.trimStartMs + MIN_SPLIT_EDGE_MS || atSourceMs > clip.trimEndMs - MIN_SPLIT_EDGE_MS) return null
        val first = clip.copy(id = UUID.randomUUID().toString(), trimEndMs = atSourceMs)
        val second = clip.copy(id = UUID.randomUUID().toString(), trimStartMs = atSourceMs)
        val list = p.clips.toMutableList()
        list[index] = first
        list.add(index + 1, second)
        return p.copy(clips = list)
    }

    const val MIN_RAMP_MS = 3000L

    /**
     * Speed ramp: membagi klip menjadi beberapa bagian berurutan, masing-masing dengan kecepatan sendiri
     * (porsi dihitung dari durasi sumber yang sudah dipotong). Null bila klip terlalu pendek.
     */
    fun speedRamp(p: Project, index: Int, ramp: SpeedRamp): Project? {
        val clip = p.clips.getOrNull(index) ?: return null
        if (clip.trimmedMs < MIN_RAMP_MS) return null
        val parts = ArrayList<Clip>()
        var cursor = clip.trimStartMs
        ramp.parts.forEachIndexed { i, (fraction, speed) ->
            val end = if (i == ramp.parts.lastIndex) clip.trimEndMs
            else (cursor + clip.trimmedMs * fraction).toLong().coerceAtMost(clip.trimEndMs)
            parts += clip.copy(id = UUID.randomUUID().toString(), trimStartMs = cursor, trimEndMs = end, speed = speed)
            cursor = end
        }
        val list = p.clips.toMutableList()
        list.removeAt(index)
        list.addAll(index, parts)
        return p.copy(clips = list)
    }

    fun delete(p: Project, index: Int): Project =
        if (p.clips.size <= 1) p else p.copy(clips = p.clips.filterIndexed { i, _ -> i != index })

    fun move(p: Project, index: Int, delta: Int): Project {
        val target = index + delta
        if (index !in p.clips.indices || target !in p.clips.indices) return p
        val list = p.clips.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        return p.copy(clips = list)
    }

    // ---- audio ---------------------------------------------------------------------------------

    const val MIN_AUDIO_MS = 300L
    const val MAX_AUDIO_TRACKS = 8

    /** Posisi awal klip ke-[index] di timeline (ms). */
    fun clipStartMs(p: Project, index: Int): Long = p.clips.take(index).sumOf { it.effectiveMs }

    /** Posisi awal tiap klip di timeline. */
    fun clipStarts(clips: List<Clip>): List<Long> {
        var acc = 0L
        return clips.map { val s = acc; acc += it.effectiveMs; s }
    }

    /** Fade tidak boleh lebih panjang dari separuh klip audio. */
    private fun fit(a: AudioTrack): AudioTrack {
        val half = a.durationMs / 2
        return a.copy(fadeInMs = a.fadeInMs.coerceIn(0L, half), fadeOutMs = a.fadeOutMs.coerceIn(0L, half))
    }

    fun updateAudio(p: Project, id: String, change: (AudioTrack) -> AudioTrack): Project =
        p.copy(audio = p.audio.map { if (it.id == id) fit(change(it)) else it })

    /** Atur titik potong dan posisi sekaligus; semua nilai dijepit ke rentang yang valid. */
    fun trimAudio(p: Project, id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long): Project =
        updateAudio(p, id) {
            val minLen = minOf(MIN_AUDIO_MS, it.sourceDurationMs)
            val s = trimStartMs.coerceIn(0L, it.sourceDurationMs - minLen)
            val e = trimEndMs.coerceIn(s + minLen, it.sourceDurationMs)
            it.copy(startMs = startMs.coerceAtLeast(0L), trimStartMs = s, trimEndMs = e)
        }

    fun moveAudio(p: Project, id: String, startMs: Long): Project =
        updateAudio(p, id) { it.copy(startMs = startMs.coerceAtLeast(0L)) }

    fun removeAudio(p: Project, id: String): Project = p.copy(audio = p.audio.filterNot { it.id == id })

    /** Membagi klip audio pada posisi timeline [atMs]; null bila terlalu dekat ke tepi klip. */
    fun splitAudio(p: Project, id: String, atMs: Long): Project? {
        val a = p.audio.firstOrNull { it.id == id } ?: return null
        val rel = atMs - a.startMs
        if (rel < MIN_AUDIO_MS || rel > a.durationMs - MIN_AUDIO_MS) return null
        val cut = a.trimStartMs + (rel * a.speed).toLong()
        val first = fit(a.copy(id = UUID.randomUUID().toString(), trimEndMs = cut, fadeOutMs = 0L))
        val second = fit(a.copy(id = UUID.randomUUID().toString(), startMs = a.startMs + rel, trimStartMs = cut, fadeInMs = 0L))
        val list = p.audio.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        list[i] = first
        list.add(i + 1, second)
        return p.copy(audio = list)
    }

    /** Salinan klip audio yang diletakkan tepat setelah aslinya. */
    fun duplicateAudio(p: Project, id: String): Pair<Project, AudioTrack>? {
        val a = p.audio.firstOrNull { it.id == id } ?: return null
        val copy = a.copy(id = UUID.randomUUID().toString(), startMs = a.endMs)
        return p.copy(audio = p.audio + copy) to copy
    }

    /**
     * Menyusun klip audio ke baris (lane): klip yang tumpang tindih dipindah ke baris di bawahnya.
     * @return peta id -> nomor baris (mulai 0) dan jumlah baris.
     */
    fun assignLanes(audio: List<AudioTrack>): Pair<Map<String, Int>, Int> {
        val laneEnds = mutableListOf<Long>()
        val result = HashMap<String, Int>()
        for (t in audio.sortedWith(compareBy({ it.startMs }, { it.id }))) {
            var lane = laneEnds.indexOfFirst { it <= t.startMs }
            if (lane < 0) {
                laneEnds.add(t.endMs)
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = t.endMs
            }
            result[t.id] = lane
        }
        return result to laneEnds.size
    }
}
