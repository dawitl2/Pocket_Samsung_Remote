package com.enkud.pocketsamsungremote

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal data class AirInkPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long
)

internal class AirWritingRecognizer(
    context: Context,
    private val onModelStatus: (String) -> Unit
) : AutoCloseable {
    private val completedStrokes = mutableListOf<List<AirInkPoint>>()
    private val currentStroke = mutableListOf<AirInkPoint>()
    private val model: DigitalInkRecognitionModel
    private val recognizer: DigitalInkRecognizer

    @Volatile
    private var modelReady = false

    init {
        val identifier = requireNotNull(
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
        ) { "English handwriting model is unavailable." }
        model = DigitalInkRecognitionModel.builder(identifier).build()
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )

        onModelStatus("Preparing handwriting model…")
        val manager = RemoteModelManager.getInstance()
        manager.isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    modelReady = true
                    onModelStatus("Handwriting ready")
                } else {
                    onModelStatus("Downloading handwriting model…")
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            modelReady = true
                            onModelStatus("Handwriting ready")
                        }
                        .addOnFailureListener { error ->
                            onModelStatus(
                                error.message ?: "Handwriting model download failed"
                            )
                        }
                }
            }
            .addOnFailureListener { error ->
                onModelStatus(error.message ?: "Could not check handwriting model")
            }
    }

    @Synchronized
    fun addPoint(point: AirInkPoint) {
        val previous = currentStroke.lastOrNull()
        if (previous == null ||
            hypot(point.x - previous.x, point.y - previous.y) >= 0.0025f
        ) {
            currentStroke += point
        }
    }

    @Synchronized
    fun endStroke() {
        if (currentStroke.size >= 3) {
            completedStrokes += currentStroke.toList()
        }
        currentStroke.clear()
    }

    @Synchronized
    fun clear() {
        completedStrokes.clear()
        currentStroke.clear()
    }

    fun recognize(
        preContext: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strokes = synchronized(this) {
            endStroke()
            completedStrokes.map { it.toList() }.also {
                completedStrokes.clear()
            }
        }
        if (strokes.isEmpty()) return

        if (isHorizontalDash(strokes)) {
            onResult(" ")
            return
        }
        recognizeStrokes(
            strokes = strokes,
            preContext = preContext,
            onCandidates = { candidates ->
                val text = candidates.firstOrNull().orEmpty()
                if (text.isBlank()) onError("No letter recognized") else onResult(text)
            },
            onError = onError
        )
    }

    fun preview(
        preContext: String,
        onCandidates: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val strokes = synchronized(this) {
            buildList {
                addAll(completedStrokes.map { it.toList() })
                if (currentStroke.size >= 3) add(currentStroke.toList())
            }
        }
        if (strokes.isEmpty()) return
        if (isHorizontalDash(strokes)) {
            onCandidates(listOf(" "))
            return
        }
        recognizeStrokes(strokes, preContext, onCandidates, onError)
    }

    private fun recognizeStrokes(
        strokes: List<List<AirInkPoint>>,
        preContext: String,
        onCandidates: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!modelReady) {
            onError("Handwriting model is still downloading")
            return
        }

        val inkBuilder = Ink.builder()
        normalizeStrokes(strokes).forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.forEach { point ->
                strokeBuilder.addPoint(
                    Ink.Point.create(
                        point.x * WRITING_SCALE,
                        point.y * WRITING_SCALE,
                        point.timeMs
                    )
                )
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val context = RecognitionContext.builder()
            .setPreContext(preContext.takeLast(20))
            .setWritingArea(WritingArea(WRITING_SCALE, WRITING_SCALE))
            .build()
        recognizer.recognize(inkBuilder.build(), context)
            .addOnSuccessListener { result ->
                val candidates = result.candidates.map { it.text }.filter { it.isNotBlank() }
                if (candidates.isEmpty()) {
                    onError("No letter recognized")
                } else {
                    onCandidates(candidates)
                }
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Handwriting recognition failed")
            }
    }

    private fun normalizeStrokes(
        strokes: List<List<AirInkPoint>>
    ): List<List<AirInkPoint>> {
        val allPoints = strokes.flatten()
        if (allPoints.isEmpty()) return strokes

        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val extent = max(maxX - minX, maxY - minY).coerceAtLeast(0.001f)

        return strokes.map { stroke ->
            stroke.map { point ->
                point.copy(
                    x = 0.5f + ((point.x - centerX) / extent) * NORMALIZED_INK_SIZE,
                    y = 0.5f + ((point.y - centerY) / extent) * NORMALIZED_INK_SIZE
                )
            }
        }
    }

    private fun isHorizontalDash(strokes: List<List<AirInkPoint>>): Boolean {
        if (strokes.size != 1) return false
        val points = strokes.first()
        if (points.size < 4) return false
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val first = points.first()
        val last = points.last()
        return width >= 0.08f &&
            width > height * 3.2f &&
            abs(last.y - first.y) < width * 0.28f
    }

    override fun close() {
        clear()
        recognizer.close()
    }

    private companion object {
        const val WRITING_SCALE = 1000f
        const val NORMALIZED_INK_SIZE = 0.72f
    }
}
