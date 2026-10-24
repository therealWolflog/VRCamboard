package com.example.vrcam

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    BackHandler(enabled = vrMode) { vrMode = false }
    val frame = remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }

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
                frame.value = orient(raw, degrees, mirror)
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
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val w = if (vrMode) 2 * bmp.width else bmp.width
                val out = Bitmap.createBitmap(w, bmp.height, Bitmap.Config.ARGB_8888)
                renderFrame(Canvas(out), w.toFloat(), bmp.height.toFloat(), bmp, filter, vrMode, flip180)
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
            .pointerInput(vrMode, filter, flip180) {
                // In VR mode the screen is the controller: tap = next filter,
                // double tap = photo, long press = flip 180, Back = leave VR mode.
                if (vrMode) {
                    detectTapGestures(
                        onTap = { filter = filter.next() },
                        onDoubleTap = { capture() },
                        onLongPress = { flip180 = !flip180 }
                    )
                }
            }
    ) {
        val paintFilter = remember(filter) { filter.colorFilter() }
        val paint = remember(paintFilter) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = paintFilter }
        }

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val bmp = frame.value ?: return@Canvas
            drawIntoCanvas { c ->
                renderFrame(c.nativeCanvas, size.width, size.height, bmp, filter, vrMode, flip180, paint)
            }
        }

        if (vrMode) {
            // One filter label per eye.
            Row(Modifier.fillMaxSize().rotate(if (flip180) 180f else 0f).padding(top = 12.dp)) {
                repeat(2) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Text(filter.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CamFilter.entries) { f ->
                        FilterChip(
                            selected = f == filter,
                            onClick = { filter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
}

/** Rotates the sensor bitmap upright and mirrors it for the front camera. */
private fun orient(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
    if (degrees == 0 && !mirror) return src
    val m = Matrix()
    if (degrees != 0) m.postRotate(degrees.toFloat())
    if (mirror) m.postScale(-1f, 1f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Draws [bmp] into [canvas], once (normal) or twice side by side (VR), centre-cropped. */
private fun renderFrame(
    canvas: Canvas,
    width: Float,
    height: Float,
    bmp: Bitmap,
    filter: CamFilter,
    vr: Boolean,
    flip180: Boolean,
    paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter.colorFilter() }
) {
    canvas.drawColor(android.graphics.Color.BLACK)
    canvas.save()
    if (flip180) canvas.rotate(180f, width / 2f, height / 2f)
    if (vr) {
        val half = width / 2f
        drawCropped(canvas, bmp, RectF(0f, 0f, half, height), paint)
        drawCropped(canvas, bmp, RectF(half, 0f, width, height), paint)
    } else {
        drawCropped(canvas, bmp, RectF(0f, 0f, width, height), paint)
    }
    canvas.restore()
}

private fun drawCropped(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint) {
    val dstAspect = dst.width() / dst.height()
    val srcAspect = bmp.width.toFloat() / bmp.height
    val src = if (srcAspect > dstAspect) {
        val w = (bmp.height * dstAspect).toInt()
        val x = (bmp.width - w) / 2
        Rect(x, 0, x + w, bmp.height)
    } else {
        val h = (bmp.width / dstAspect).toInt()
        val y = (bmp.height - h) / 2
        Rect(0, y, bmp.width, y + h)
    }
    canvas.drawBitmap(bmp, src, dst, paint)
}

private fun saveToGallery(context: Context, bmp: Bitmap): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "VRCam_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VRCam")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) false else {
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: false
    }
} catch (e: Exception) {
    false
}
