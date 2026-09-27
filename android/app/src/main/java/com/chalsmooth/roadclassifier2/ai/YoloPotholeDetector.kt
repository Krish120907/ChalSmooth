package com.chalsmooth.roadclassifier2.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

data class DetectionResult(
    val boundingBox: RectF,
    val classId: Int,
    val label: String,
    val score: Float
)

class YoloPotholeDetector(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("potholeimagedetector.onnx").use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes, OrtSession.SessionOptions())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detect(bitmap: Bitmap, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<DetectionResult> {
        val env = ortEnv ?: return emptyList()
        val session = ortSession ?: return emptyList()

        val targetSize = 640
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)

        val inputBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
        val intValues = IntArray(targetSize * targetSize)
        resizedBitmap.getPixels(intValues, 0, targetSize, 0, 0, targetSize, targetSize)

        val area = targetSize * targetSize
        for (i in 0 until area) {
            val pixel = intValues[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.put(i, r)
            inputBuffer.put(area + i, g)
            inputBuffer.put(2 * area + i, b)
        }
        inputBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong()))

        val candidates = ArrayList<DetectionResult>()
        try {
            val results = session.run(Collections.singletonMap("images", inputTensor))
            val outputTensor = results[0] as OnnxTensor
            val rawOutput = outputTensor.value as Array<Array<FloatArray>> // shape [1][6][8400]

            val origWidth = bitmap.width.toFloat()
            val origHeight = bitmap.height.toFloat()
            val scaleX = origWidth / targetSize.toFloat()
            val scaleY = origHeight / targetSize.toFloat()

            val labels = arrayOf("Pothole", "Sewage-Manhole")

            for (i in 0 until 8400) {
                val score0 = rawOutput[0][4][i]
                val score1 = rawOutput[0][5][i]

                val maxScore: Float
                val classId: Int
                if (score0 >= score1) {
                    maxScore = score0
                    classId = 0
                } else {
                    maxScore = score1
                    classId = 1
                }

                if (maxScore >= confThreshold) {
                    val cx = rawOutput[0][0][i]
                    val cy = rawOutput[0][1][i]
                    val w = rawOutput[0][2][i]
                    val h = rawOutput[0][3][i]

                    val left = (cx - w / 2f) * scaleX
                    val top = (cy - h / 2f) * scaleY
                    val right = (cx + w / 2f) * scaleX
                    val bottom = (cy + h / 2f) * scaleY

                    val rect = RectF(
                        left.coerceIn(0f, origWidth),
                        top.coerceIn(0f, origHeight),
                        right.coerceIn(0f, origWidth),
                        bottom.coerceIn(0f, origHeight)
                    )

                    candidates.add(DetectionResult(rect, classId, labels[classId], maxScore))
                }
            }

            inputTensor.close()
            results.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return applyNMS(candidates, iouThreshold)
    }

    private fun applyNMS(candidates: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        val sorted = candidates.sortedByDescending { it.score }
        val selected = ArrayList<DetectionResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val boxA = sorted[i]
            selected.add(boxA)

            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                val boxB = sorted[j]
                if (boxA.classId == boxB.classId) {
                    val iou = calculateIoU(boxA.boundingBox, boxB.boundingBox)
                    if (iou >= iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }
        return selected
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        val unionArea = areaA + areaB - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    fun drawDetectionsOnBitmap(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val strokeWidthPx = max(4f, min(bitmap.width, bitmap.height) * 0.008f)
        val textSizePx = max(24f, min(bitmap.width, bitmap.height) * 0.035f)

        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = Color.parseColor("#CCFF0000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = textSizePx
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        for (det in detections) {
            canvas.drawRect(det.boundingBox, boxPaint)

            val labelText = "${det.label} ${(det.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textSizePx + 8f

            val textTop = max(0f, det.boundingBox.top - textHeight)
            val textRect = RectF(
                det.boundingBox.left,
                textTop,
                det.boundingBox.left + textWidth + 16f,
                textTop + textHeight
            )
            canvas.drawRect(textRect, textBgPaint)
            canvas.drawText(labelText, textRect.left + 8f, textRect.bottom - 6f, textPaint)
        }

        return mutableBitmap
    }

    fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
