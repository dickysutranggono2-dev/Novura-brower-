package com.dik.videoedit.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dik.videoedit.models.AnimationType
import com.dik.videoedit.models.AspectOption
import com.dik.videoedit.models.AudioTrack
import com.dik.videoedit.models.EffectType
import com.dik.videoedit.models.FilterType
import com.dik.videoedit.models.Project
import com.dik.videoedit.models.SpeedRamp
import com.dik.videoedit.models.TextColors
import com.dik.videoedit.models.TextItem
import com.dik.videoedit.models.TextPosition
import com.dik.videoedit.models.TextSize
import com.dik.videoedit.models.TransitionType
import com.dik.videoedit.videoeditor.formatSpeed
import com.dik.videoedit.videoeditor.formatTimeTenth
import com.dik.videoedit.viewmodels.EditorViewModel

@Composable
private fun PanelTitle(text: String, hint: String? = null) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (hint != null) Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

/** Crop & rotasi: rasio 9:16 / 1:1 / 16:9 dan putar 90°. */
@Composable
internal fun CropPanel(vm: EditorViewModel, project: Project) {
    PanelTitle("Crop & Rotasi", "Video diisi penuh ke rasio yang dipilih (bagian tepi dipotong).")
    Label("Rasio")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectOption.entries.forEach { a ->
            FilterChip(selected = project.aspect == a, onClick = { vm.setAspect(a) }, label = { Text(a.label) })
        }
    }
    Spacer(Modifier.height(14.dp))
    Label("Rotasi saat ini: ${project.rotation}°")
    OutlinedButton(onClick = { vm.rotate() }) {
        Icon(Icons.Filled.RotateRight, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp)); Text("Putar 90°")
    }
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f)

@Composable
internal fun SpeedPanel(vm: EditorViewModel, project: Project, selected: Int) {
    val clip = project.clips.getOrNull(selected) ?: return
    PanelTitle("Kecepatan", "Berlaku untuk klip ${selected + 1}. Suara ikut menyesuaikan tanpa mengubah nada.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SPEEDS.forEach { s ->
            FilterChip(selected = clip.speed == s, onClick = { vm.setSpeed(s, false) }, label = { Text(formatSpeed(s)) })
        }
    }
    TextButton(onClick = { vm.setSpeed(clip.speed, true) }) { Text("Terapkan ${formatSpeed(clip.speed)} ke semua klip") }

    Spacer(Modifier.height(10.dp))
    Label("Slow motion")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = clip.speed == 0.5f, onClick = { vm.setSpeed(0.5f, false) }, label = { Text("Slow 0,5x") })
        FilterChip(selected = clip.speed == 0.25f, onClick = { vm.setSpeed(0.25f, false) }, label = { Text("Super Slow 0,25x") })
    }
    Spacer(Modifier.height(10.dp))
    Label("Speed ramp")
    Text(
        "Klip dibagi otomatis menjadi beberapa bagian dengan kecepatan berbeda (minimal 3 detik).",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeedRamp.entries.forEach { r ->
            FilterChip(selected = false, onClick = { vm.speedRamp(r) }, label = { Text(r.label) })
        }
    }
}

/** Volume suara asli klip video yang dipilih. */
@Composable
internal fun ClipVolumePanel(vm: EditorViewModel, project: Project, selected: Int) {
    val clip = project.clips.getOrNull(selected) ?: return
    PanelTitle("Volume Klip ${selected + 1}", "Suara asli video. Atur ke 0% untuk membisukan.")
    VolumeSlider("Volume", clip.volume, key = clip.id) { vm.setClipVolume(it) }
    NoiseReductionSwitch(vm, project)
}

/** Volume klip audio yang dipilih. */
@Composable
internal fun AudioVolumePanel(vm: EditorViewModel, project: Project, track: AudioTrack) {
    PanelTitle("Volume Audio", track.name)
    VolumeSlider("Volume", track.volume, key = track.id) { vm.setAudioVolume(track.id, it) }
    NoiseReductionSwitch(vm, project)
}

/** Fade in dan fade out klip audio (maksimal separuh durasi klip, atau 5 detik). */
@Composable
internal fun AudioFadePanel(vm: EditorViewModel, track: AudioTrack) {
    val maxMs = minOf(5000L, track.durationMs / 2).coerceAtLeast(100L).toFloat()
    var fadeIn by remember(track.id, track.fadeInMs) { mutableFloatStateOf(track.fadeInMs.toFloat().coerceAtMost(maxMs)) }
    var fadeOut by remember(track.id, track.fadeOutMs) { mutableFloatStateOf(track.fadeOutMs.toFloat().coerceAtMost(maxMs)) }
    PanelTitle("Fade Audio", track.name)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Fade in", fontSize = 14.sp)
        Text(formatTimeTenth(fadeIn.toLong()).drop(3) + " dtk", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = fadeIn, onValueChange = { fadeIn = it }, valueRange = 0f..maxMs,
        onValueChangeFinished = { vm.setAudioFade(track.id, fadeIn.toLong(), fadeOut.toLong()) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Fade out", fontSize = 14.sp)
        Text(formatTimeTenth(fadeOut.toLong()).drop(3) + " dtk", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = fadeOut, onValueChange = { fadeOut = it }, valueRange = 0f..maxMs,
        onValueChangeFinished = { vm.setAudioFade(track.id, fadeIn.toLong(), fadeOut.toLong()) },
    )
}

/** Kurangi bising untuk semua audio; diterapkan pada hasil export. */
@Composable
private fun NoiseReductionSwitch(vm: EditorViewModel, project: Project) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Kurangi bising", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Membuang dengung rendah dan desis pelan. Berlaku untuk semua audio dan terdengar pada hasil export.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = project.noiseReduction, onCheckedChange = { vm.setNoiseReduction(it) })
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, key: Any, onCommit: (Float) -> Unit) {
    var v by remember(key, value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text("${(v * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1f, onValueChangeFinished = { onCommit(v) })
}

@Composable
internal fun TextPanel(vm: EditorViewModel, project: Project) {
    var text by remember { mutableStateOf("") }
    var position by remember { mutableStateOf(TextPosition.BOTTOM) }
    var size by remember { mutableStateOf(TextSize.MEDIUM) }
    var color by remember { mutableStateOf(TextColors.all.first()) }

    val textItems = project.texts.filter { !it.plain }
    PanelTitle("Teks", "Teks tampil sepanjang video.")
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(30).replace("\n", " ") },
        label = { Text("Isi teks (maks. 30 huruf)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Label("Posisi")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextPosition.entries.forEach { p -> FilterChip(position == p, { position = p }, { Text(p.label) }) }
    }
    Label("Ukuran")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextSize.entries.forEach { s -> FilterChip(size == s, { size = s }, { Text(s.label) }) }
    }
    Label("Warna")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextColors.all.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(
                        if (c == color) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        CircleShape,
                    )
                    .clickable { color = c },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            vm.addText(TextItem(text = text.trim(), position = position, color = color, size = size))
            text = ""
        },
        enabled = text.isNotBlank() && textItems.size < 5,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (textItems.size < 5) "Tambah Teks" else "Maksimal 5 teks") }

    if (textItems.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Label("Teks di video")
        textItems.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.position.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { vm.removeText(t.id) }) { Icon(Icons.Filled.Delete, "Hapus teks") }
            }
        }
    }
}

private val EMOJIS = listOf(
    "😀", "😂", "😍", "😎", "🥳", "😭", "🔥", "❤️",
    "👍", "👏", "🎉", "⭐", "✨", "💯", "🎵", "📍",
    "☀️", "🌈", "🌸", "🐶", "🐱", "🍕", "⚽", "🚗",
)

/** Stiker emoji dan overlay foto (ditumpuk di atas video, tampil sepanjang video). */
@Composable
internal fun StickerPanel(vm: EditorViewModel, project: Project, onPickImage: () -> Unit) {
    var emoji by remember { mutableStateOf(EMOJIS.first()) }
    var position by remember { mutableStateOf(TextPosition.CENTER) }
    var anchorX by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(TextSize.MEDIUM) }
    val items = project.texts.filter { it.plain }
    val full = project.texts.size >= 12

    PanelTitle("Stiker & Overlay", "Tambah emoji atau foto dari galeri di atas video.")
    Label("Pilih stiker")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EMOJIS.forEach { e ->
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { emoji = e },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 24.sp) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Label("Posisi")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Kiri" to -0.75f, "Tengah" to 0f, "Kanan" to 0.75f).forEach { (name, x) ->
            FilterChip(anchorX == x, { anchorX = x }, { Text(name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextPosition.entries.forEach { p -> FilterChip(position == p, { position = p }, { Text(p.label) }) }
    }
    Label("Ukuran")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextSize.entries.forEach { s -> FilterChip(size == s, { size = s }, { Text(s.label) }) }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                vm.addText(TextItem(text = emoji, plain = true, position = position, anchorX = anchorX, size = size))
            },
            enabled = !full, modifier = Modifier.weight(1f),
        ) { Text("Tambah Stiker") }
        OutlinedButton(onClick = onPickImage, enabled = !full, modifier = Modifier.weight(1f)) { Text("Overlay Foto") }
    }
    if (full) Text("Maksimal 12 teks, stiker, dan overlay.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)

    if (items.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Label("Stiker & overlay di video")
        items.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.imagePath != null) "Foto" else t.text, modifier = Modifier.weight(1f), fontSize = 18.sp)
                Text(t.position.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { vm.removeText(t.id) }) { Icon(Icons.Filled.Delete, "Hapus") }
            }
        }
    }
}

/** Animasi otomatis sepanjang video (versi sederhana dari keyframe). */
@Composable
internal fun AnimationPanel(
    vm: EditorViewModel, project: Project, premiumActive: Boolean, premiumMinutes: Int,
    requirePremium: (() -> Unit) -> Unit,
) {
    PanelTitle("Animasi", "Gerakan zoom atau geser otomatis. Terlihat di preview dan hasil export.")
    PremiumNote(premiumActive, premiumMinutes)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimationType.entries.forEach { a ->
            FilterChip(
                selected = project.animation == a,
                onClick = { if (a.premium) requirePremium { vm.setAnimation(a) } else vm.setAnimation(a) },
                label = { Text(a.label) },
                leadingIcon = if (a.premium && !premiumActive) ({ LockIcon() }) else null,
            )
        }
    }
}

@Composable
private fun PremiumNote(active: Boolean, minutes: Int) {
    Text(
        if (active) "Premium aktif • sekitar $minutes menit lagi" else "Item bergembok membutuhkan menonton 1 iklan (membuka semua fitur premium 30 menit).",
        fontSize = 12.sp,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LockIcon() = Icon(Icons.Filled.Lock, null, Modifier.size(16.dp))

@Composable
internal fun FilterPanel(
    vm: EditorViewModel, project: Project, premiumActive: Boolean, premiumMinutes: Int,
    requirePremium: (() -> Unit) -> Unit,
) {
    PanelTitle("Filter Warna")
    PremiumNote(premiumActive, premiumMinutes)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterType.entries.forEach { f ->
            FilterChip(
                selected = project.filter == f,
                onClick = { if (f.premium) requirePremium { vm.setFilter(f) } else vm.setFilter(f) },
                label = { Text(f.label) },
                leadingIcon = if (f.premium && !premiumActive) ({ LockIcon() }) else null,
            )
        }
    }
}

@Composable
internal fun EffectPanel(
    vm: EditorViewModel, project: Project, premiumActive: Boolean, premiumMinutes: Int,
    requirePremium: (() -> Unit) -> Unit,
) {
    PanelTitle("Efek & Transisi")
    PremiumNote(premiumActive, premiumMinutes)
    Label("Efek video")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EffectType.entries.forEach { e ->
            FilterChip(
                selected = project.effect == e,
                onClick = { if (e.premium) requirePremium { vm.setEffect(e) } else vm.setEffect(e) },
                label = { Text(e.label) },
                leadingIcon = if (e.premium && !premiumActive) ({ LockIcon() }) else null,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Label("Transisi antar klip" + if (project.clips.size < 2) " (butuh 2 klip atau lebih)" else "")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransitionType.entries.forEach { t ->
            FilterChip(
                selected = project.transition == t,
                onClick = { if (t.premium) requirePremium { vm.setTransition(t) } else vm.setTransition(t) },
                label = { Text(t.label) },
                leadingIcon = if (t.premium && !premiumActive) ({ LockIcon() }) else null,
            )
        }
    }
    Text(
        "Transisi terlihat pada hasil export. Preview menampilkan potongan langsung.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
