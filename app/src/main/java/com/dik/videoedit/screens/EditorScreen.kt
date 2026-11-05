package com.dik.videoedit.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dik.videoedit.LocalAppContainer
import com.dik.videoedit.components.PremiumDialog
import com.dik.videoedit.components.Timeline
import com.dik.videoedit.components.TrimPreview
import com.dik.videoedit.components.rememberPremiumStatus
import com.dik.videoedit.models.AspectOption
import com.dik.videoedit.models.Focus
import com.dik.videoedit.models.Project
import com.dik.videoedit.videoeditor.PreviewController
import com.dik.videoedit.videoeditor.formatTime
import com.dik.videoedit.videoprocessing.TextFit
import com.dik.videoedit.viewmodels.EditorViewModel
import kotlinx.coroutines.delay

enum class Tool { CROP, SPEED, CLIP_VOLUME, AUDIO_VOLUME, AUDIO_FADE, TEXT, STICKER, ANIMATION, FILTER, EFFECT }

private class ToolItem(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit, onExport: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: EditorViewModel = viewModel(
        key = "editor-$projectId",
        factory = viewModelFactory { initializer { EditorViewModel(container, projectId) } },
    )
    val ui by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val project = ui.project
    when {
        ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        project == null || project.clips.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Proyek tidak dapat dibuka. Video sumbernya mungkin sudah dihapus atau dipindahkan.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Kembali") }
        }
        else -> EditorContent(vm, ui, project, snackbar, onBack, onExport)
    }
}

@Composable
private fun EditorContent(
    vm: EditorViewModel,
    ui: EditorViewModel.State,
    project: Project,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val controller = remember { PreviewController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }
    val posState = remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var trimPreview by remember { mutableStateOf<TrimPreview?>(null) }

    // Sinkronkan preview dengan proyek, lalu lompat ke posisi yang diminta (mis. setelah trim/pisah).
    LaunchedEffect(project, ui.pendingSeekMs) {
        controller.update(project)
        val seek = ui.pendingSeekMs
        if (seek != null) {
            controller.seekToGlobal(seek)
            posState.longValue = seek
            vm.consumeSeek()
        }
    }

    // Posisi putar (diperbarui berkala; ringan).
    var playingIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        while (true) {
            controller.syncClipParams()
            isPlaying = controller.player.isPlaying
            if (!scrubbing) posState.longValue = controller.globalMs()
            controller.syncAudio(posState.longValue, isPlaying)
            val idx = controller.player.currentMediaItemIndex
            if (isPlaying && idx != playingIndex) vm.select(idx)
            playingIndex = idx
            delay(40)
        }
    }

    // Premium
    val premiumUntil by container.premium.until.collectAsState(initial = 0L)
    val (premiumActive, premiumMinutes) = rememberPremiumStatus(premiumUntil)
    var showPremium by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requirePremium: (() -> Unit) -> Unit = { action ->
        if (premiumActive) action() else { pendingAction = action; showPremium = true }
    }

    var tool by remember { mutableStateOf<Tool?>(null) }

    val addVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addClips(uris)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAudio(uri, posState.longValue)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addImageOverlay(uri)
    }
    val launchAddVideo = { addVideo.launch(arrayOf("video/*")) }
    val launchPickAudio = { pickAudio.launch(arrayOf("audio/*")) }
    // Ambil suara dari video lain di penyimpanan (seperti "Ekstrak audio" di CapCut).
    val launchPickVideoAudio = { pickAudio.launch(arrayOf("video/*")) }
    // Tombol "Tambah audio" di timeline menerima file audio maupun video.
    val launchPickAnyAudio = { pickAudio.launch(arrayOf("audio/*", "video/*")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { controller.pause(); onExport() },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(40.dp),
                ) { Text("Export", fontWeight = FontWeight.SemiBold) }
            }

            // Preview
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                val ratio = project.outputRatio
                val parentRatio = maxWidth / maxHeight
                val sizeMod = if (ratio > parentRatio) Modifier.fillMaxWidth().aspectRatioCompat(ratio)
                else Modifier.fillMaxHeight().aspectRatioCompat(ratio, matchHeightFirst = true)
                Box(
                    Modifier.align(Alignment.Center).then(sizeMod).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                        .clickable { controller.togglePlay() },
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                player = controller.player
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                    TextOverlays(project)
                    trimPreview?.let { TrimFrameOverlay(it, project.aspect != AspectOption.ORIGINAL) }
                    if (!isPlaying && trimPreview == null) {
                        Box(
                            Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color(0x99000000)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(38.dp)) }
                    }
                }
            }

            // Waktu + kontrol
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeLabel({ posState.longValue }, project.totalDurationMs, Modifier.weight(1f))
                IconButton(onClick = { controller.togglePlay() }) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Jeda" else "Putar", modifier = Modifier.size(30.dp))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = vm::undo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Urungkan") }
                    IconButton(onClick = vm::redo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Ulangi") }
                }
            }

            // Timeline
            Timeline(
                project = project,
                focus = ui.focus,
                selectedClip = ui.selected,
                selectedAudio = ui.selectedAudio,
                positionMs = { posState.longValue },
                onScrubStart = {
                    scrubbing = true
                    controller.pause()
                    controller.setScrubbing(true)
                },
                onSeek = { ms ->
                    posState.longValue = ms
                    controller.seekToGlobal(ms)
                },
                onScrubEnd = {
                    scrubbing = false
                    controller.setScrubbing(false)
                    controller.seekToGlobal(posState.longValue)
                },
                onSelectClip = { vm.focusClip(it) },
                onSelectAudio = { vm.focusAudio(it) },
                onClearFocus = { vm.clearFocus() },
                onTrimClip = { index, startMs, endMs, rightEdge -> vm.trimClip(index, startMs, endMs, rightEdge) },
                onTrimPreview = { trimPreview = it },
                onMoveAudio = { id, startMs -> vm.moveAudio(id, startMs) },
                onTrimAudio = { id, startMs, ts, te -> vm.trimAudio(id, startMs, ts, te) },
                onAddClip = { launchAddVideo() },
                onAddAudio = { launchPickAnyAudio() },
            )
            Text(
                when (ui.focus) {
                    Focus.NONE -> "Ketuk klip atau audio untuk memilih. Geser untuk menggulung, cubit untuk zoom."
                    Focus.CLIP -> "Tarik handle putih di ujung klip untuk memotong."
                    Focus.AUDIO -> "Geser audio untuk memindah. Tarik handle putih untuk memotong."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            // Toolbar yang berubah sesuai pilihan (klip video / klip audio / tidak ada)
            val selectedAudioTrack = project.audio.firstOrNull { it.id == ui.selectedAudio }
            val splitNow: () -> Unit = {
                if (ui.focus == Focus.AUDIO && selectedAudioTrack != null) {
                    vm.splitAudio(selectedAudioTrack.id, posState.longValue)
                } else {
                    val idx = controller.player.currentMediaItemIndex
                    val clip = project.clips.getOrNull(idx)
                    if (clip != null) vm.split(idx, clip.trimStartMs + controller.player.currentPosition)
                }
            }
            val items: List<ToolItem> = when (ui.focus) {
                Focus.CLIP -> listOf(
                    ToolItem("Pisah", Icons.Filled.VerticalSplit, onClick = splitNow),
                    ToolItem("Kecepatan", Icons.Filled.Speed) { tool = Tool.SPEED },
                    ToolItem("Volume", Icons.AutoMirrored.Filled.VolumeUp) { tool = Tool.CLIP_VOLUME },
                    ToolItem("Ekstrak Audio", Icons.Filled.GraphicEq) { vm.extractAudio(ui.selected) },
                    ToolItem("Mundur", Icons.AutoMirrored.Filled.ArrowBack, enabled = ui.selected > 0) { vm.moveSelected(-1) },
                    ToolItem("Maju", Icons.AutoMirrored.Filled.ArrowForward, enabled = ui.selected < project.clips.lastIndex) { vm.moveSelected(1) },
                    ToolItem("Hapus", Icons.Filled.Delete, enabled = project.clips.size > 1) { vm.deleteSelected() },
                )
                Focus.AUDIO -> listOf(
                    ToolItem("Pisah", Icons.Filled.VerticalSplit, onClick = splitNow),
                    ToolItem("Volume", Icons.AutoMirrored.Filled.VolumeUp) { tool = Tool.AUDIO_VOLUME },
                    ToolItem("Fade", Icons.Filled.Tune) { tool = Tool.AUDIO_FADE },
                    ToolItem("Duplikat", Icons.Filled.ContentCopy) { selectedAudioTrack?.let { vm.duplicateAudio(it.id) } },
                    ToolItem("Hapus", Icons.Filled.Delete) { selectedAudioTrack?.let { vm.removeAudio(it.id) } },
                )
                Focus.NONE -> listOf(
                    ToolItem("Potong", Icons.Filled.ContentCut) { vm.focusClip(playingIndex) },
                    ToolItem("Pisah", Icons.Filled.VerticalSplit, onClick = splitNow),
                    ToolItem("Musik", Icons.Filled.MusicNote) { launchPickAudio() },
                    ToolItem("Dari Video", Icons.Filled.GraphicEq) { launchPickVideoAudio() },
                    ToolItem("Crop", Icons.Filled.Crop) { tool = Tool.CROP },
                    ToolItem("Kecepatan", Icons.Filled.Speed) { tool = Tool.SPEED },
                    ToolItem("Teks", Icons.Filled.TextFields) { tool = Tool.TEXT },
                    ToolItem("Stiker", Icons.Filled.EmojiEmotions) { tool = Tool.STICKER },
                    ToolItem("Animasi", Icons.Filled.Animation) { tool = Tool.ANIMATION },
                    ToolItem("Filter", Icons.Filled.FilterVintage) { tool = Tool.FILTER },
                    ToolItem("Efek", Icons.Filled.AutoAwesome) { tool = Tool.EFFECT },
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    ToolTile(item.label, item.icon, Modifier.width(76.dp), item.enabled, item.onClick)
                }
            }
        }
    }

    // Panel alat (bottom sheet)
    tool?.let { t ->
        ModalBottomSheet(
            onDismissRequest = { tool = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                when (t) {
                    Tool.CROP -> CropPanel(vm, project)
                    Tool.SPEED -> SpeedPanel(vm, project, ui.selected)
                    Tool.CLIP_VOLUME -> ClipVolumePanel(vm, project, ui.selected)
                    Tool.AUDIO_VOLUME -> project.audio.firstOrNull { it.id == ui.selectedAudio }?.let { AudioVolumePanel(vm, project, it) }
                    Tool.AUDIO_FADE -> project.audio.firstOrNull { it.id == ui.selectedAudio }?.let { AudioFadePanel(vm, it) }
                    Tool.TEXT -> TextPanel(vm, project)
                    Tool.STICKER -> StickerPanel(vm, project, onPickImage = { pickImage.launch(arrayOf("image/*")) })
                    Tool.ANIMATION -> AnimationPanel(vm, project, premiumActive, premiumMinutes, requirePremium)
                    Tool.FILTER -> FilterPanel(vm, project, premiumActive, premiumMinutes, requirePremium)
                    Tool.EFFECT -> EffectPanel(vm, project, premiumActive, premiumMinutes, requirePremium)
                }
            }
        }
    }

    if (ui.busy) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    if (showPremium) {
        PremiumDialog(
            onDismiss = { showPremium = false; pendingAction = null },
            onGranted = {
                showPremium = false
                pendingAction?.invoke()
                pendingAction = null
            },
        )
    }
}

private fun Modifier.aspectRatioCompat(ratio: Float, matchHeightFirst: Boolean = false): Modifier =
    this.then(Modifier.aspectRatio(ratio, matchHeightFirst))

@Composable
private fun ToolTile(label: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(64.dp), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.35f),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Label waktu; membaca posisi di dalam composable ini sendiri agar hanya bagian ini yang digambar ulang. */
@Composable
private fun TimeLabel(position: () -> Long, totalMs: Long, modifier: Modifier) {
    Row(modifier) {
        Text(formatTime(position()), fontWeight = FontWeight.Bold)
        Text(" / ${formatTime(totalMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Frame video pada posisi handle trim, ditampilkan di atas preview selama handle ditarik. */
@Composable
private fun TrimFrameOverlay(preview: TrimPreview, crop: Boolean) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(Uri.parse(preview.uri))
            .videoFrameMillis(preview.sourceMs)
            .size(720)
            .memoryCacheKey("${preview.uri}@${preview.sourceMs / 40L}@trim")
            .build(),
        contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}

/**
 * Teks, stiker emoji, dan overlay foto di preview digambar dengan Compose; ukuran & posisinya mengikuti
 * rumus yang sama dengan export.
 */
@Composable
private fun TextOverlays(project: Project) {
    if (project.texts.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val shortSide = minOf(wPx, hPx)
        project.texts.forEach { t ->
            val align = BiasAlignment(t.anchorX, -t.position.anchorY)
            val path = t.imagePath
            if (path != null) {
                OverlayImage(path, wPx * t.imageWidthFraction, Modifier.align(align))
            } else {
                val px = TextFit.fitPx(t.text, shortSide * t.heightFraction, wPx * 0.92f)
                Text(
                    t.text,
                    color = Color(t.color),
                    fontSize = with(density) { px.toSp() },
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.align(align)
                        .background(Color(t.backgroundColor))
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OverlayImage(path: String, widthPx: Float, modifier: Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    val b = bmp ?: return
    val density = LocalDensity.current
    val w = with(density) { widthPx.toDp() }
    val h = with(density) { (widthPx * b.height / b.width).toDp() }
    Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = modifier.size(w, h))
}
