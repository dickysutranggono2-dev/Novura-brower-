package com.dik.videoedit.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dik.videoedit.AppContainer
import com.dik.videoedit.models.Project
import com.dik.videoedit.models.TemplatePreset
import com.dik.videoedit.videoeditor.Importer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun refresh() {
        viewModelScope.launch { _projects.value = c.projects.list() }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            c.projects.delete(id)
            refresh()
        }
    }

    fun rename(project: Project, name: String) {
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            c.projects.save(project.copy(name = clean, updatedAt = System.currentTimeMillis()))
            refresh()
        }
    }

    /** Impor video terpilih, buat proyek baru (opsional dengan preset template), lalu buka editor. */
    fun createProject(uris: List<Uri>, template: TemplatePreset?, onCreated: (String) -> Unit) {
        if (uris.isEmpty() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = Importer.importVideos(c.context, uris)
            if (result.failed > 0) messages.tryEmit(Importer.failedMessage(result.failed))
            if (result.clips.isEmpty()) {
                _busy.value = false
                return@launch
            }
            val clips = result.clips.map { clip ->
                var c2 = clip
                if (template != null && template.speed != 1f) c2 = c2.copy(speed = template.speed)
                // Template slot: tiap video dipotong sepanjang slot (dari awal video).
                template?.slotMs?.let { slot ->
                    if (c2.sourceDurationMs > slot) c2 = c2.copy(trimStartMs = 0L, trimEndMs = slot)
                }
                c2
            }
            val project = Project(
                name = clips.first().name.substringBeforeLast('.').ifBlank { "Proyek Baru" }.take(60),
                clips = clips,
                aspect = template?.aspect ?: com.dik.videoedit.models.AspectOption.ORIGINAL,
                filter = template?.filter ?: com.dik.videoedit.models.FilterType.NONE,
            )
            c.projects.save(project)
            _busy.value = false
            onCreated(project.id)
        }
    }
}
