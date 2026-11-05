package com.dik.videoedit.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dik.videoedit.AppContainer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dik.videoedit.models.AnimationType
import com.dik.videoedit.models.AspectOption
import com.dik.videoedit.models.AudioTrack
import com.dik.videoedit.models.Clip
import com.dik.videoedit.models.EffectType
import com.dik.videoedit.models.FilterType
import com.dik.videoedit.models.Focus
import com.dik.videoedit.models.Project
import com.dik.videoedit.models.SpeedRamp
import com.dik.videoedit.models.TextItem
import com.dik.videoedit.models.TransitionType
import com.dik.videoedit.videoeditor.Importer
import com.dik.videoedit.videoeditor.ProjectOps
import com.dik.videoedit.videoprocessing.VideoProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EditorViewModel(private val c: AppContainer, private val projectId: String) : ViewModel() {

    data class State(
        val project: Project? = null,
        /** Klip video yang aktif (mengikuti playhead bila tidak ada klip yang dipilih manual). */
        val selected: Int = 0,
        val focus: Focus = Focus.NONE,
        val selectedAudio: String? = null,
        /** Posisi (ms di timeline) yang harus dilompati preview setelah proyek diperbarui; null = tidak ada. */
        val pendingSeekMs: Long? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val loading: Boolean = true,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val p = c.projects.get(projectId)
        if (p == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        val readable = withContext(Dispatchers.IO) { p.clips.filter { Importer.canRead(c.context, it.uri) } }
        val readableAudio = withContext(Dispatchers.IO) { p.audio.filter { Importer.canRead(c.context, it.uri) } }
        var fixed = p
        if (readable.size != p.clips.size || readableAudio.size != p.audio.size) {
            fixed = p.copy(clips = readable, audio = readableAudio)
            val lost = (p.clips.size - readable.size) + (p.audio.size - readableAudio.size)
            messages.tryEmit("$lost file sumber (video/audio) tidak dapat dibaca dan dilepas dari proyek.")
        }
        _state.update { it.copy(project = fixed, loading = false) }
    }

    // ---- infrastruktur ---------------------------------------------------------------------

    private fun save(p: Project) {
        c.appScope.launch { c.projects.save(p) }
    }

    private fun commit(selected: Int? = null, seek: ((Project) -> Long)? = null, change: (Project) -> Project) {
        val cur = _state.value.project ?: return
        val next = change(cur)
        if (next == cur) return
        undoStack.addLast(cur)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        apply(next.copy(updatedAt = System.currentTimeMillis()), selected, seek?.invoke(next))
    }

    private fun apply(p: Project, selected: Int? = null, seekMs: Long? = null) {
        _state.update {
            val audioOk = it.selectedAudio != null && p.audio.any { a -> a.id == it.selectedAudio }
            it.copy(
                project = p,
                selected = (selected ?: it.selected).coerceIn(0, (p.clips.size - 1).coerceAtLeast(0)),
                focus = if (it.focus == Focus.AUDIO && !audioOk) Focus.NONE else it.focus,
                selectedAudio = if (audioOk) it.selectedAudio else null,
                pendingSeekMs = seekMs ?: it.pendingSeekMs,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        save(p)
    }

    fun undo() {
        val cur = _state.value.project ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(cur)
        apply(prev)
    }

    fun redo() {
        val cur = _state.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(cur)
        apply(next)
    }

    /** Mengikuti klip yang sedang diputar; tidak mengubah fokus (handle trim tidak berpindah sendiri). */
    fun select(index: Int) {
        _state.update {
            val last = (it.project?.clips?.lastIndex ?: 0).coerceAtLeast(0)
            it.copy(selected = index.coerceIn(0, last))
        }
    }

    fun focusClip(index: Int) {
        _state.update {
            val last = (it.project?.clips?.lastIndex ?: 0).coerceAtLeast(0)
            it.copy(selected = index.coerceIn(0, last), focus = Focus.CLIP, selectedAudio = null)
        }
    }

    fun focusAudio(id: String) {
        _state.update { it.copy(focus = Focus.AUDIO, selectedAudio = id) }
    }

    fun clearFocus() {
        _state.update { it.copy(focus = Focus.NONE, selectedAudio = null) }
    }

    fun consumeSeek() {
        _state.update { it.copy(pendingSeekMs = null) }
    }

    private fun commitClip(change: (Clip) -> Clip) {
        val i = _state.value.selected
        commit { p -> ProjectOps.updateClip(p, i, change) }
    }

    // ---- editing ---------------------------------------------------------------------------

    fun trim(startMs: Long, endMs: Long) {
        val i = _state.value.selected
        commit { ProjectOps.trim(it, i, startMs, endMs) }
    }

    /**
     * Trim hasil tarik handle di timeline. [rightEdge] menentukan ke mana playhead dipindah setelahnya:
     * ke awal klip (handle kiri) atau ke ujung klip (handle kanan), sehingga preview langsung menampilkan hasilnya.
     */
    fun trimClip(index: Int, startMs: Long, endMs: Long, rightEdge: Boolean) {
        commit(
            selected = index,
            seek = { p ->
                val s = ProjectOps.clipStartMs(p, index)
                if (rightEdge) (s + (p.clips[index].effectiveMs - 40L)).coerceAtLeast(s) else s
            },
        ) { ProjectOps.trim(it, index, startMs, endMs) }
    }

    /** [atSourceMs] = posisi pada video sumber (trimStart + posisi di dalam klip). */
    fun split(index: Int, atSourceMs: Long) {
        val cur = _state.value.project ?: return
        if (ProjectOps.split(cur, index, atSourceMs) == null) {
            messages.tryEmit("Geser posisi putar ke tengah klip dulu, lalu tekan Pisah.")
            return
        }
        commit(selected = index) { ProjectOps.split(it, index, atSourceMs) ?: it }
    }

    fun deleteSelected() {
        val cur = _state.value.project ?: return
        if (cur.clips.size <= 1) {
            messages.tryEmit("Proyek harus memiliki minimal satu klip.")
            return
        }
        val i = _state.value.selected
        commit(selected = (i - 1).coerceAtLeast(0)) { ProjectOps.delete(it, i) }
    }

    fun moveSelected(delta: Int) {
        val i = _state.value.selected
        commit(selected = i + delta) { ProjectOps.move(it, i, delta) }
    }

    fun setSpeed(speed: Float, applyToAll: Boolean) {
        if (applyToAll) {
            commit { p -> p.copy(clips = p.clips.map { it.copy(speed = speed) }) }
        } else {
            commitClip { it.copy(speed = speed) }
        }
    }

    fun setClipVolume(v: Float) = commitClip { it.copy(volume = v.coerceIn(0f, 1f)) }

    fun addClips(uris: List<Uri>) {
        val before = _state.value.project?.clips?.size ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = Importer.importVideos(c.context, uris)
            _state.update { it.copy(busy = false) }
            if (result.failed > 0) messages.tryEmit(Importer.failedMessage(result.failed))
            if (result.clips.isNotEmpty()) {
                commit(selected = before) { it.copy(clips = it.clips + result.clips) }
            }
        }
    }

    fun setAspect(a: AspectOption) = commit { it.copy(aspect = a) }

    fun rotate() = commit { it.copy(rotation = (it.rotation + 90) % 360) }

    // ---- audio -----------------------------------------------------------------------------

    /** Menambah musik/audio dari penyimpanan; ditaruh di posisi playhead [atMs]. */
    fun addAudio(uri: Uri, atMs: Long) {
        val cur = _state.value.project ?: return
        if (cur.audio.size >= ProjectOps.MAX_AUDIO_TRACKS) {
            messages.tryEmit("Maksimal ${ProjectOps.MAX_AUDIO_TRACKS} klip audio.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val imported = Importer.importAudio(c.context, uri)
            val isVideo = withContext(Dispatchers.IO) {
                c.context.contentResolver.getType(uri)?.startsWith("video/") == true
            }
            val hasSound = imported != null && withContext(Dispatchers.IO) { VideoProbe.hasAudio(c.context, uri) }
            _state.update { it.copy(busy = false) }
            if (imported == null) {
                messages.tryEmit("File tidak dapat dibuka.")
            } else if (!hasSound) {
                messages.tryEmit("File ini tidak memiliki suara.")
            } else {
                val track = imported.copy(
                    extracted = isVideo,
                    name = if (isVideo) "Audio - ${imported.name}" else imported.name,
                )
                commit { p -> p.copy(audio = p.audio + track.copy(startMs = atMs.coerceIn(0L, p.totalDurationMs))) }
                focusAudio(track.id)
                if (isVideo) messages.tryEmit("Suara dari video ditambahkan sebagai track audio.")
            }
        }
    }

    /**
     * Mengekstrak audio dari klip video ke track audio tersendiri (posisi & potongan sama dengan klip),
     * lalu membisukan suara asli klip agar tidak terdengar ganda.
     */
    fun extractAudio(clipIndex: Int) {
        val cur = _state.value.project ?: return
        val clip = cur.clips.getOrNull(clipIndex) ?: return
        if (cur.audio.size >= ProjectOps.MAX_AUDIO_TRACKS) {
            messages.tryEmit("Maksimal ${ProjectOps.MAX_AUDIO_TRACKS} klip audio.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val hasAudio = withContext(Dispatchers.IO) { VideoProbe.hasAudio(c.context, Uri.parse(clip.uri)) }
            _state.update { it.copy(busy = false) }
            if (!hasAudio) {
                messages.tryEmit("Klip ini tidak memiliki suara untuk diekstrak.")
                return@launch
            }
            val track = AudioTrack(
                uri = clip.uri,
                name = "Audio - ${clip.name}",
                sourceDurationMs = clip.sourceDurationMs,
                startMs = ProjectOps.clipStartMs(cur, clipIndex),
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                volume = if (clip.volume < 0.05f) 1f else clip.volume,
                speed = clip.speed,
                extracted = true,
            )
            commit { p ->
                ProjectOps.updateClip(p, clipIndex) { it.copy(volume = 0f) }.copy(audio = p.audio + track)
            }
            focusAudio(track.id)
            messages.tryEmit("Audio diekstrak ke track audio. Suara asli klip dibisukan.")
        }
    }

    fun setAudioVolume(id: String, v: Float) =
        commit { p -> ProjectOps.updateAudio(p, id) { it.copy(volume = v.coerceIn(0f, 1f)) } }

    fun setAudioFade(id: String, fadeInMs: Long, fadeOutMs: Long) =
        commit { p -> ProjectOps.updateAudio(p, id) { it.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs) } }

    /** Geser klip audio di timeline. */
    fun moveAudio(id: String, startMs: Long) = commit { ProjectOps.moveAudio(it, id, startMs) }

    /** Trim klip audio lewat handle (sekaligus posisi awalnya bila handle kiri digeser). */
    fun trimAudio(id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long) =
        commit { ProjectOps.trimAudio(it, id, startMs, trimStartMs, trimEndMs) }

    fun splitAudio(id: String, atMs: Long) {
        val cur = _state.value.project ?: return
        if (ProjectOps.splitAudio(cur, id, atMs) == null) {
            messages.tryEmit("Geser playhead ke bagian tengah klip audio dulu, lalu tekan Pisah.")
            return
        }
        commit { ProjectOps.splitAudio(it, id, atMs) ?: it }
    }

    fun removeAudio(id: String) {
        commit { ProjectOps.removeAudio(it, id) }
    }

    fun duplicateAudio(id: String) {
        val cur = _state.value.project ?: return
        if (cur.audio.size >= ProjectOps.MAX_AUDIO_TRACKS) {
            messages.tryEmit("Maksimal ${ProjectOps.MAX_AUDIO_TRACKS} klip audio.")
            return
        }
        val copy = ProjectOps.duplicateAudio(cur, id)?.second ?: return
        commit { p -> p.copy(audio = p.audio + copy) }
        focusAudio(copy.id)
    }

    fun addText(item: TextItem) = commit { it.copy(texts = it.texts + item) }

    /** Speed ramp pada klip terpilih: klip dibagi beberapa bagian dengan kecepatan berbeda. */
    fun speedRamp(ramp: SpeedRamp) {
        val cur = _state.value.project ?: return
        val i = _state.value.selected
        if (ProjectOps.speedRamp(cur, i, ramp) == null) {
            messages.tryEmit("Klip terlalu pendek untuk speed ramp (minimal ${ProjectOps.MIN_RAMP_MS / 1000} detik).")
            return
        }
        commit(selected = i, seek = { p -> ProjectOps.clipStartMs(p, i) }) { ProjectOps.speedRamp(it, i, ramp) ?: it }
    }

    fun setAnimation(a: AnimationType) = commit { it.copy(animation = a) }

    fun setNoiseReduction(on: Boolean) = commit { it.copy(noiseReduction = on) }

    /** Overlay foto: gambar diperkecil lalu disalin ke penyimpanan aplikasi agar tetap ada setelah aplikasi ditutup. */
    fun addImageOverlay(uri: Uri) {
        val cur = _state.value.project ?: return
        if (cur.texts.size >= MAX_ITEMS) {
            messages.tryEmit("Maksimal $MAX_ITEMS teks/stiker/overlay.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val path = withContext(Dispatchers.IO) { saveOverlayImage(uri) }
            _state.update { it.copy(busy = false) }
            if (path == null) {
                messages.tryEmit("Gambar tidak dapat dibuka.")
            } else {
                commit { p -> p.copy(texts = p.texts + TextItem(text = "", plain = true, imagePath = path, position = com.dik.videoedit.models.TextPosition.CENTER)) }
            }
        }
    }

    private fun saveOverlayImage(uri: Uri): String? = try {
        val resolver = c.context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) {
            null
        } else {
            val dir = File(c.context.filesDir, "overlays").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString() + ".png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_ITEMS = 12
    }

    fun removeText(id: String) = commit { p -> p.copy(texts = p.texts.filterNot { it.id == id }) }

    fun setFilter(f: FilterType) = commit { it.copy(filter = f) }

    fun setEffect(e: EffectType) = commit { it.copy(effect = e) }

    fun setTransition(t: TransitionType) = commit { it.copy(transition = t) }
}
