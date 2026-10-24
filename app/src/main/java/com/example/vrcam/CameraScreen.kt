package com.example.vrcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Settings the camera thread needs to read while recording video. */
private class LiveState {
    @Volatile var filter: CamFilter = CamFilter.NORMAL
    @Volatile var vr: Boolean = true
    @Volatile var params: ViewParams = ViewParams()
    @Volatile var recorder: FrameRecorder? = null
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VR Cam needs camera access", color = Color.White, fontSize = 18.sp)
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Grant permission") }
            }
        }
        return
    }

    // ---- state ----
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var filter by remember { mutableStateOf(CamFilter.NORMAL) }
    var vrMode by remember { mutableStateOf(true) }
    var flip180 by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var torch by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    val camera = remember { mutableStateOf<Camera?>(null) }
    val frame = remember { mutableStateOf<Bitmap?>(null) }
    val live = remember { LiveState() }
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }
    val prefs = remember { context.getSharedPreferences("vrcam", Context.MODE_PRIVATE) }

    SideEffect {
        live.filter = filter
        live.vr = vrMode
        live.params = ViewParams(offsetX, scale, flip180)
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // ---- camera ----
    DisposableEffect(lens) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            val mirror = lens == CameraSelector.LENS_FACING_FRONT
            analysis.setAnalyzer(executor) { proxy ->
                val raw = proxy.toBitmap()
                val degrees = proxy.imageInfo.rotationDegrees
                proxy.close()
                val upright = orient(raw, degrees, mirror)
                frame.value = upright
                live.recorder?.draw { c, w, h ->
                    renderFrame(c, w.toFloat(), h.toFloat(), upright, live.filter, live.vr, live.params)
                }
            }
            p.unbindAll()
            camera.value = p.bindToLifecycle(
                activity,
                CameraSelector.Builder().requireLensFacing(lens).build(),
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) {
        onDispose {
            live.recorder?.stop()
            live.recorder = null
            executor.shutdown()
        }
    }
    LaunchedEffect(lens) { torch = false }
    LaunchedEffect(camera.value, zoom) { camera.value?.cameraControl?.setLinearZoom(zoom) }
    LaunchedEffect(camera.value, exposure) {
        val cam = camera.value ?: return@LaunchedEffect
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        if (range.upper != 0 || range.lower != 0) {
            val steps = if (exposure >= 0f) range.upper else -range.lower
            cam.cameraControl.setExposureCompensationIndex((exposure * steps).roundToInt())
        }
    }
    LaunchedEffect(camera.value, torch) {
        val cam = camera.value ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torch)
    }
    LaunchedEffect(brightness) {
        val lp = activity.window.attributes
        lp.screenBrightness = brightness
        activity.window.attributes = lp
    }
    BackHandler(enabled = panelOpen) { panelOpen = false }

    // ---- actions ----
    fun capture() {
        val bmp = frame.value ?: return
        val params = ViewParams(offsetX, scale, flip180)
        val currentFilter = filter
        val vr = vrMode
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val w = if (vr) 2 * bmp.width else bmp.width
                val out = Bitmap.createBitmap(w, bmp.height, Bitmap.Config.ARGB_8888)
                renderFrame(Canvas(out), w.toFloat(), bmp.height.toFloat(), bmp, currentFilter, vr, params)
                saveToGallery(context, out)
            }
            toast(if (ok) "Saved to Pictures/VRCam" else "Could not save photo")
        }
    }

    fun startRecording(withAudio: Boolean) {
        if (recording) return
        val w = if (vrMode) 1920 else 1280
        val h = if (vrMode) 960 else 720
        try {
            live.recorder = FrameRecorder(context, w, h, withAudio)
            recording = true
        } catch (e: Exception) {
            toast("Could not start recording")
        }
    }

    fun stopRecording() {
        val r = live.recorder ?: return
        live.recorder = null
        recording = false
        toast(if (r.stop()) "Video saved to Movies/VRCam" else "Recording failed")
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> startRecording(granted) }

    fun autofocus() {
        val cam = camera.value ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        cam.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).build()
        )
    }

    fun savePreset(n: Int) {
        prefs.edit()
            .putString("p${n}_filter", filter.name)
            .putFloat("p${n}_offset", offsetX)
            .putFloat("p${n}_scale", scale)
            .putFloat("p${n}_zoom", zoom)
            .putFloat("p${n}_exposure", exposure)
            .putFloat("p${n}_brightness", brightness)
            .putBoolean("p${n}_flip", flip180)
            .apply()
        toast("Saved preset P$n")
    }

    fun loadPreset(n: Int) {
        if (!prefs.contains("p${n}_filter")) {
            toast("P$n is empty. Hold it to save the current settings")
            return
        }
        filter = CamFilter.entries.firstOrNull { it.name == prefs.getString("p${n}_filter", "") }
            ?: CamFilter.NORMAL
        offsetX = prefs.getFloat("p${n}_offset", 0f)
        scale = prefs.getFloat("p${n}_scale", 1f)
        zoom = prefs.getFloat("p${n}_zoom", 0f)
        exposure = prefs.getFloat("p${n}_exposure", 0f)
        brightness = prefs.getFloat("p${n}_brightness", 1f)
        flip180 = prefs.getBoolean("p${n}_flip", false)
        toast("Loaded preset P$n")
    }

    // ---- UI ----
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Tap = next filter, double tap = photo, long press = open/close the panel
                // (or unlock when locked).
                detectTapGestures(
                    onTap = { if (!locked && !panelOpen) filter = filter.next() },
                    onDoubleTap = { if (!locked && !panelOpen) capture() },
                    onLongPress = {
                        if (locked) {
                            locked = false
                            toast("Unlocked")
                        } else {
                            panelOpen = !panelOpen
                        }
                    }
                )
            }
    ) {
        val colorFilter = remember(filter) { filter.colorFilter() }
        val paint = remember(colorFilter) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply { this.colorFilter = colorFilter }
        }

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val bmp = frame.value ?: return@Canvas
            drawIntoCanvas { c ->
                renderFrame(
                    c.nativeCanvas, size.width, size.height, bmp, filter, vrMode,
                    ViewParams(offsetX, scale, flip180), paint
                )
            }
        }

        if (!panelOpen) {
            if (vrMode) {
                // One filter label per eye.
                Row(
                    Modifier.fillMaxSize().rotate(if (flip180) 180f else 0f).padding(top = 12.dp)
                ) {
                    repeat(2) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            Text(
                                filter.label, color = Color.White, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (!locked) {
                FilledTonalButton(
                    onClick = { panelOpen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) { Text("Menu") }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { capture() }
                )
            }
        }

        if (panelOpen) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top bar
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelButton("Switch camera", Color(0xFF1F5F7A)) {
                        lens = if (lens == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }
                    PanelButton("Rotate 180°", Color(0xFF7A5A12)) { flip180 = !flip180 }
                    PanelButton(if (torch) "Flash on" else "Flash off", Color(0xFF7A7A14)) {
                        if (camera.value?.cameraInfo?.hasFlashUnit() == true) torch = !torch
                        else toast("This camera has no flash")
                    }
                    PanelButton(if (vrMode) "VR: on" else "VR: off", Color(0xFF3A3A6A)) { vrMode = !vrMode }
                    for (n in 1..3) {
                        PresetButton("P$n", onLoad = { loadPreset(n) }, onSave = { savePreset(n) })
                    }
                    PanelButton("Lock", Color(0xFF444444)) {
                        locked = true
                        panelOpen = false
                        toast("Locked. Long-press to unlock")
                    }
                    Text("Hold P1-P3 to save", color = Color.Gray, fontSize = 11.sp)
                }

                // Sliders
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    SliderRow("Reset X offset", offsetX, -0.25f..0.25f, Color(0xFF3AA0E0), { offsetX = it }) {
                        offsetX = 0f
                    }
                    SliderRow("Reset scale", scale, 0.5f..1.5f, Color(0xFF3AA0E0), { scale = it }) {
                        scale = 1f
                    }
                    SliderRow("Reset zoom", zoom, 0f..1f, Color(0xFFFF5A1F), { zoom = it }) {
                        zoom = 0f
                    }
                    SliderRow("Auto exposure", exposure, -1f..1f, Color(0xFFF2D21B), { exposure = it }) {
                        exposure = 0f
                    }
                    SliderRow("Max brightness", brightness, 0.05f..1f, Color.White, { brightness = it }) {
                        brightness = 1f
                    }
                }

                // Bottom bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PanelButton(
                        if (recording) "Stop recording" else "Start recording",
                        if (recording) Color(0xFFD32F2F) else Color(0xFF8B1E1E),
                        Modifier.weight(1f)
                    ) {
                        when {
                            recording -> stopRecording()
                            has(Manifest.permission.RECORD_AUDIO) -> startRecording(true)
                            else -> audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    PanelButton("Autofocus", Color(0xFF1F5F7A), Modifier.weight(1f)) { autofocus() }
                    Box(Modifier.weight(1f)) {
                        PanelButton("Filter: ${filter.label}", Color(0xFF4A1B6D), Modifier.fillMaxWidth()) {
                            filterMenu = true
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            val colours = CamFilter.entries.filter { it.effect == Effect.NONE }
                            val effects = CamFilter.entries.filter { it.effect != Effect.NONE }
                            DropdownMenuItem(
                                text = { Text("COLOUR FILTERS", fontSize = 11.sp, color = Color.Gray) },
                                onClick = {}, enabled = false
                            )
                            colours.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(if (f == filter) "● ${f.label}" else f.label) },
                                    onClick = { filter = f; filterMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("EFFECTS", fontSize = 11.sp, color = Color.Gray) },
                                onClick = {}, enabled = false
                            )
                            effects.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(if (f == filter) "● ${f.label}" else f.label) },
                                    onClick = { filter = f; filterMenu = false }
                                )
                            }
                        }
                    }
                    PanelButton("Close panel", Color(0xFF8B1E2B), Modifier.weight(1f)) { panelOpen = false }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text.uppercase(), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PresetButton(label: String, onLoad: () -> Unit, onSave: () -> Unit) {
    Box(
        Modifier
            .size(width = 48.dp, height = 40.dp)
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onLoad() }, onLongPress = { onSave() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.width(150.dp).height(36.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            border = BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(label.uppercase(), fontSize = 11.sp, maxLines = 1)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}
