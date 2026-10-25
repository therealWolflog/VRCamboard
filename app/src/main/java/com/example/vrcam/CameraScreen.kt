package com.example.vrcam

import android.Manifest
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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

/** The camera thread needs to know the current filter (edge detection is done per frame). */
private class LiveState {
    @Volatile var filter: CamFilter = CamFilter.NORMAL
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
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

    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var filter by remember { mutableStateOf(CamFilter.NORMAL) }
    var vrMode by remember { mutableStateOf(true) }
    var flip180 by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val frame = remember { mutableStateOf<Bitmap?>(null) }
    val live = remember { LiveState() }
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }

    SideEffect { live.filter = filter }

    // Back leaves VR mode (to get the menu and buttons).
    BackHandler(enabled = vrMode) { vrMode = false }

    // Bind the camera; every frame is converted to an upright bitmap and published to `frame`.
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
                frame.value = if (live.filter.effect == Effect.EDGES) sobelEdges(upright) else upright
            }
            p.unbindAll()
            p.bindToLifecycle(
                activity,
                CameraSelector.Builder().requireLensFacing(lens).build(),
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    fun capture() {
        val bmp = frame.value ?: return
        val currentFilter = filter
        val vr = vrMode
        val params = ViewParams(flip180 = flip180)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val w = if (vr) 2 * bmp.width else bmp.width
                val out = Bitmap.createBitmap(w, bmp.height, Bitmap.Config.ARGB_8888)
                renderFrame(Canvas(out), w.toFloat(), bmp.height.toFloat(), bmp, currentFilter, vr, params)
                saveToGallery(context, out)
            }
            Toast.makeText(
                context,
                if (ok) "Saved to Pictures/VRCam" else "Could not save photo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(vrMode) {
                // In VR mode the screen is the controller: tap = next filter,
                // double tap = photo, long press = rotate 180. Back = normal view with the menu.
                if (vrMode) {
                    detectTapGestures(
                        onTap = { filter = filter.next() },
                        onDoubleTap = { capture() },
                        onLongPress = { flip180 = !flip180 }
                    )
                }
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
                    ViewParams(flip180 = flip180), paint
                )
            }
        }

        if (vrMode) {
            // One filter label per eye.
            Row(Modifier.fillMaxSize().rotate(if (flip180) 180f else 0f).padding(top = 12.dp)) {
                repeat(2) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Text(
                            filter.label, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Filter dropdown
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                FilledTonalButton(onClick = { menuOpen = true }) {
                    Text("Filter: ${filter.label}  ▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val colours = CamFilter.entries.filter { it.effect == Effect.NONE }
                    val effects = CamFilter.entries.filter { it.effect != Effect.NONE }
                    DropdownMenuItem(
                        text = { Text("COLOUR FILTERS", fontSize = 11.sp, color = Color.Gray) },
                        onClick = {}, enabled = false
                    )
                    colours.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(if (f == filter) "● ${f.label}" else f.label) },
                            onClick = { filter = f; menuOpen = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("EFFECTS", fontSize = 11.sp, color = Color.Gray) },
                        onClick = {}, enabled = false
                    )
                    effects.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(if (f == filter) "● ${f.label}" else f.label) },
                            onClick = { filter = f; menuOpen = false }
                        )
                    }
                }
            }

            // Bottom controls
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        lens = if (lens == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) { Text("Flip") }
                    FilledTonalButton(onClick = { flip180 = !flip180 }) { Text("180°") }
                }

                Box(
                    Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { capture() }
                )

                FilledTonalButton(onClick = { vrMode = true }) { Text("VR") }
            }
        }
    }
}
