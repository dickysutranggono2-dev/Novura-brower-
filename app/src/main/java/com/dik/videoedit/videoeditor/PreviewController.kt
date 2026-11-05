package com.dik.videoedit.videoeditor

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.dik.videoedit.models.AudioTrack
import com.dik.videoedit.models.Clip
import com.dik.videoedit.models.Project
import com.dik.videoedit.videoprocessing.EffectsFactory
import kotlin.math.abs

/**
 * Preview memakai ExoPlayer: satu playlist berisi klip video yang sudah di-trim (dengan efek visual yang
 * sama seperti export), ditambah satu pemutar audio untuk SETIAP klip audio. Pemutar audio dijalankan/dijeda
 * oleh [syncAudio] mengikuti posisi playhead, sehingga posisi, trim, volume, dan fade sama dengan hasil export.
 * Video tidak pernah dimuat penuh ke RAM.
 */
class PreviewController(private val context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private class AudioHolder(val player: ExoPlayer, var track: AudioTrack, var signature: String) {
        var lastVolume = -1f
    }

    private val audioPlayers = LinkedHashMap<String, AudioHolder>()

    /** Pemutar khusus suara: trek gambar dimatikan, jadi aman dipakai untuk audio dari file video. */
    private fun newAudioPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }

    private var clips: List<Clip> = emptyList()
    private var clipSignature = ""
    private var effectSignature: String? = null

    /** Sinkronkan pemutar dengan proyek. Aman dipanggil berulang; hanya bekerja bila ada perubahan. */
    fun update(p: Project) {
        clips = p.clips

        val fx = "${p.rotation}|${p.aspect}|${p.filter}|${p.effect}|${p.animation}|${p.outputRatio}|" +
            "${p.clips.firstOrNull()?.width}x${p.clips.firstOrNull()?.height}"
        if (fx != effectSignature) {
            // Wajib dipanggil sebelum prepare() pertama.
            player.setVideoEffects(EffectsFactory.previewEffects(p))
            effectSignature = fx
        }

        val sig = p.clips.joinToString("|") { "${it.id}:${it.uri}:${it.trimStartMs}:${it.trimEndMs}" }
        if (sig != clipSignature) {
            val oldIndex = player.currentMediaItemIndex
            val oldPos = player.currentPosition
            val items = p.clips.map { c ->
                MediaItem.Builder()
                    .setUri(c.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.trimStartMs)
                            .setEndPositionMs(c.trimEndMs)
                            .build()
                    )
                    .build()
            }
            val index = oldIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val maxPos = ((p.clips.getOrNull(index)?.trimmedMs ?: 1L) - 1).coerceAtLeast(0L)
            player.setMediaItems(items, index, oldPos.coerceIn(0L, maxPos))
            player.prepare()
            clipSignature = sig
        }

        updateAudio(p)
    }

    private fun updateAudio(p: Project) {
        val wanted = p.audio.take(MAX_AUDIO_PLAYERS)
        val ids = wanted.map { it.id }.toSet()
        audioPlayers.keys.filter { it !in ids }.forEach { audioPlayers.remove(it)?.player?.release() }

        for (t in wanted) {
            val sig = "${t.uri}|${t.trimStartMs}|${t.trimEndMs}|${t.speed}"
            val holder = audioPlayers.getOrPut(t.id) { AudioHolder(newAudioPlayer(), t, "") }
            if (holder.signature != sig) {
                holder.player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(t.uri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(t.trimStartMs)
                                .setEndPositionMs(t.trimEndMs)
                                .build()
                        )
                        .build()
                )
                holder.player.setPlaybackSpeed(t.speed)
                holder.player.prepare()
                holder.signature = sig
            }
            holder.track = t
        }
    }

    /**
     * Menjalankan/menjeda tiap pemutar audio sesuai posisi playhead [globalMs]. Dipanggil berkala dari UI.
     * Pemutar audio hanya berbunyi saat video diputar dan playhead berada di dalam klip audio tersebut.
     */
    fun syncAudio(globalMs: Long, videoPlaying: Boolean) {
        for (h in audioPlayers.values) {
            val t = h.track
            val rel = globalMs - t.startMs
            val inRange = rel >= 0 && rel < t.durationMs

            val volume = if (inRange) t.gainAt(rel).coerceIn(0f, 1f) else t.volume
            if (abs(volume - h.lastVolume) > 0.005f) {
                h.player.volume = volume
                h.lastVolume = volume
            }

            if (videoPlaying && inRange) {
                val srcPos = (rel * t.speed).toLong()
                if (!h.player.playWhenReady) {
                    h.player.seekTo(srcPos)
                    h.player.play()
                } else if (h.player.playbackState == Player.STATE_READY &&
                    abs(h.player.currentPosition - srcPos) > DRIFT_MS
                ) {
                    h.player.seekTo(srcPos)
                }
            } else if (h.player.playWhenReady) {
                h.player.pause()
            }
        }
    }

    /** Kecepatan & volume bisa berbeda tiap klip; terapkan sesuai klip yang sedang diputar. */
    fun syncClipParams() {
        val c = clips.getOrNull(player.currentMediaItemIndex) ?: return
        if (player.playbackParameters.speed != c.speed) player.setPlaybackSpeed(c.speed)
        if (player.volume != c.volume) player.volume = c.volume
    }

    /** Saat scrub, lompat ke keyframe terdekat agar cepat; setelah selesai kembali ke seek presisi. */
    fun setScrubbing(on: Boolean) {
        player.setSeekParameters(if (on) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
    }

    fun globalMs(): Long {
        val index = player.currentMediaItemIndex
        var t = 0L
        for (i in 0 until index.coerceAtMost(clips.size)) t += clips[i].effectiveMs
        val c = clips.getOrNull(index) ?: return t
        return t + (player.currentPosition.coerceAtLeast(0L) / c.speed).toLong()
    }

    fun seekToGlobal(ms: Long) {
        var remaining = ms.coerceAtLeast(0L)
        for ((i, c) in clips.withIndex()) {
            if (remaining < c.effectiveMs || i == clips.lastIndex) {
                player.seekTo(i, (remaining.coerceAtMost(c.effectiveMs) * c.speed).toLong())
                break
            }
            remaining -= c.effectiveMs
        }
    }

    fun seekToClip(index: Int) {
        if (index in clips.indices) player.seekTo(index, 0L)
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
            player.play()
        }
    }

    fun pause() {
        player.pause()
        audioPlayers.values.forEach { it.player.pause() }
    }

    fun release() {
        player.release()
        audioPlayers.values.forEach { it.player.release() }
        audioPlayers.clear()
    }

    private companion object {
        /** Batas pemutar audio bersamaan di preview (export tetap memakai semua klip audio). */
        const val MAX_AUDIO_PLAYERS = 8
        const val DRIFT_MS = 250L
    }
}
