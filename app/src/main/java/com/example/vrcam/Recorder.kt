package com.example.vrcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface

/**
 * Records whatever the app draws (so filters and effects are baked into the video)
 * to Movies/VRCam. Starts recording as soon as it is created.
 */
class FrameRecorder(context: Context, width: Int, height: Int, withAudio: Boolean) {
    private val lock = Any()
    private val resolver = context.contentResolver
    private val uri: Uri
    private val pfd: ParcelFileDescriptor
    private val recorder: MediaRecorder
    private lateinit var surface: Surface
    private var running = false

    init {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VRCam_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VRCam")
        }
        uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create video file"
        }
        pfd = requireNotNull(resolver.openFileDescriptor(uri, "w")) { "Could not open video file" }
        recorder = newRecorder(context)
        try {
            if (withAudio) recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(8_000_000)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.prepare()
            surface = recorder.surface
            recorder.start()
            running = true
        } catch (e: Exception) {
            recorder.release()
            pfd.close()
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Draws one frame into the video. Safe to call from any thread. */
    fun draw(block: (Canvas, Int, Int) -> Unit) {
        synchronized(lock) {
            if (!running) return
            try {
                val canvas = surface.lockHardwareCanvas()
                try {
                    block(canvas, canvas.width, canvas.height)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                // Skip this frame.
            }
        }
    }

    /** Finishes the file. Returns false if nothing usable was recorded. */
    fun stop(): Boolean {
        synchronized(lock) {
            if (!running) return false
            running = false
            val ok = try {
                recorder.stop()
                true
            } catch (e: Exception) {
                false
            }
            recorder.release()
            surface.release()
            pfd.close()
            if (!ok) resolver.delete(uri, null, null)
            return ok
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
}
