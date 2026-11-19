package com.example.tellogesturecontrol.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.max
import kotlin.math.min

data class HandDetectionResult(
    val landmarks: List<Pair<Float, Float>> = emptyList(),
    val boundingBox: RectF? = null,
    val rawGestureId: Int = -1,
    val rawGestureLabel: String = "No Hand",
    val confidence: Float = 0f,
    val bufferedGestureId: Int? = null,
    val handedness: String = "",
    val fps: Int = 0
)

/**
 * Runs MediaPipe HandLandmarker + TFLite KeypointClassifier + GestureBuffer on CameraX frames.
 */
class HandGestureRecognizer(context: Context) {

    private val keypointClassifier = KeypointClassifier(context)
    private val gestureBuffer = GestureBuffer(bufferLen = 5)
    private val handLandmarker: HandLandmarker

    private var frameCounter = 0
    private var lastFpsTimestampMs = SystemClock.elapsedRealtime()
    private var currentFps = 0

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.7f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    val gestureLabels: List<String>
        get() = keypointClassifier.labels

    fun processImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean): HandDetectionResult {
        updateFps()
        val rawBitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (isFrontCamera) {
                postScale(-1f, 1f)
            }
        }

        val orientedBitmap = if (rotationDegrees != 0 || isFrontCamera) {
            Bitmap.createBitmap(
                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
            )
        } else {
            rawBitmap
        }

        val mpImage = BitmapImageBuilder(orientedBitmap).build()
        val result = handLandmarker.detect(mpImage)

        val handLandmarks = result.landmarks().firstOrNull()
        if (handLandmarks == null || handLandmarks.size < 21) {
            gestureBuffer.addGesture(-1)
            val buffered = gestureBuffer.getGesture()
            return HandDetectionResult(
                rawGestureId = -1,
                rawGestureLabel = "No Hand",
                bufferedGestureId = buffered,
                fps = currentFps
            )
        }

        val points = handLandmarks.map { Pair(it.x(), it.y()) }

        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for ((x, y) in points) {
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
        val box = RectF(
            minX.coerceIn(0f, 1f),
            minY.coerceIn(0f, 1f),
            maxX.coerceIn(0f, 1f),
            maxY.coerceIn(0f, 1f)
        )

        val features = keypointClassifier.preprocessLandmarks(
            normalizedLandmarks = points,
            imageWidth = orientedBitmap.width,
            imageHeight = orientedBitmap.height
        )
        val classification = keypointClassifier.classify(features)

        gestureBuffer.addGesture(classification.gestureId)
        val buffered = gestureBuffer.getGesture()

        val handedness = result.handedness()
            .firstOrNull()
            ?.firstOrNull()
            ?.categoryName()
            ?: ""

        return HandDetectionResult(
            landmarks = points,
            boundingBox = box,
            rawGestureId = classification.gestureId,
            rawGestureLabel = classification.label,
            confidence = classification.confidence,
            bufferedGestureId = buffered,
            handedness = handedness,
            fps = currentFps
        )
    }

    private fun updateFps() {
        frameCounter++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastFpsTimestampMs
        if (elapsed >= 1000L) {
            currentFps = ((frameCounter * 1000L) / elapsed).toInt()
            frameCounter = 0
            lastFpsTimestampMs = now
        }
    }

    fun clearBuffer() {
        gestureBuffer.clear()
    }

    fun close() {
        handLandmarker.close()
        keypointClassifier.close()
    }
}
