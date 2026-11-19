package com.example.tellogesturecontrol.gesture

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

data class ClassificationResult(
    val gestureId: Int,
    val label: String,
    val confidence: Float
)

/**
 * Loads `keypoint_classifier.tflite` and `keypoint_classifier_label.csv` from assets
 * and classifies 21 2D hand landmarks using the exact preprocessing from
 * `tello-gesture-control/gestures/gesture_recognition.py`.
 */
class KeypointClassifier(context: Context) {

    private val interpreter: Interpreter
    val labels: List<String>

    init {
        val modelBuffer = loadModelFile(context, "keypoint_classifier.tflite")
        val options = Interpreter.Options().apply {
            numThreads = 2
        }
        interpreter = Interpreter(modelBuffer, options)

        labels = context.assets.open("keypoint_classifier_label.csv")
            .bufferedReader()
            .readLines()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() }
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    /**
     * Converts normalized [0, 1] landmarks into pixel-scaled, wrist-relative,
     * max-abs-normalized 42-element float vector matching `_pre_process_landmark`.
     */
    fun preprocessLandmarks(
        normalizedLandmarks: List<Pair<Float, Float>>,
        imageWidth: Int,
        imageHeight: Int
    ): FloatArray {
        val features = FloatArray(42)
        if (normalizedLandmarks.size < 21) return features

        val baseX = (normalizedLandmarks[0].first * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val baseY = (normalizedLandmarks[0].second * imageHeight).toInt().coerceIn(0, imageHeight - 1)

        var maxAbs = 0f
        for (i in 0 until 21) {
            val px = (normalizedLandmarks[i].first * imageWidth).toInt().coerceIn(0, imageWidth - 1)
            val py = (normalizedLandmarks[i].second * imageHeight).toInt().coerceIn(0, imageHeight - 1)
            val relX = (px - baseX).toFloat()
            val relY = (py - baseY).toFloat()
            features[i * 2] = relX
            features[i * 2 + 1] = relY
            val ax = abs(relX)
            val ay = abs(relY)
            if (ax > maxAbs) maxAbs = ax
            if (ay > maxAbs) maxAbs = ay
        }

        if (maxAbs > 0f) {
            for (i in features.indices) {
                features[i] = features[i] / maxAbs
            }
        }
        return features
    }

    fun classify(features: FloatArray): ClassificationResult {
        val input = arrayOf(features)
        val output = Array(1) { FloatArray(labels.size.coerceAtLeast(8)) }
        interpreter.run(input, output)

        val scores = output[0]
        var bestIdx = 0
        var bestScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIdx = i
            }
        }
        val label = labels.getOrElse(bestIdx) { "Unknown($bestIdx)" }
        return ClassificationResult(
            gestureId = bestIdx,
            label = label,
            confidence = bestScore
        )
    }

    fun close() {
        interpreter.close()
    }
}
