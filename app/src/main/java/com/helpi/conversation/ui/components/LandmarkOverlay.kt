package com.helpi.conversation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.helpi.conversation.keypoints.KeypointContract
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.vision.LandmarkFrame
import kotlin.math.min

/**
 * Dibuja los landmarks que realmente consume Eva: 21 puntos por mano y pose
 * 11..24. La preview frontal está espejada para la persona usuaria; el cuadro
 * que analiza MediaPipe no, por eso la proyección invierte únicamente x.
 *
 * El overlay es sólo una vista del resultado crudo. No transforma ni vuelve a
 * alimentar el contrato de 168 coordenadas.
 */
@Composable
internal fun LandmarkOverlay(
    frame: LandmarkFrame?,
    visualState: VisualChannelState,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            // Es información visual continua; las instrucciones equivalentes
            // ya se anuncian en el chip de estado sin saturar TalkBack a 15 fps.
            .clearAndSetSemantics { }
            .testTag("landmarkOverlay"),
    ) {
        val current = frame ?: return@Canvas
        val transform = overlayTransform(
            canvasWidth = size.width,
            canvasHeight = size.height,
            imageWidth = current.imageWidth,
            imageHeight = current.imageHeight,
            mirrorHorizontally = true,
        ) ?: return@Canvas

        val recognizing = visualState == VisualChannelState.CAPTURANDO_SENA
        val handColor = if (recognizing) HelpiColors.Success else HelpiColors.LedSoft

        drawLandmarkGroup(
            landmarks = current.pose,
            firstLandmarkIndex = KeypointContract.POSE_SOURCE_START,
            landmarkCount = KeypointContract.POSE_LANDMARKS,
            connections = POSE_CONNECTIONS,
            transform = transform,
            color = HelpiColors.BrandCore,
        )
        drawLandmarkGroup(
            landmarks = current.leftHand,
            firstLandmarkIndex = 0,
            landmarkCount = HAND_LANDMARKS,
            connections = HAND_CONNECTIONS,
            transform = transform,
            color = handColor,
        )
        drawLandmarkGroup(
            landmarks = current.rightHand,
            firstLandmarkIndex = 0,
            landmarkCount = HAND_LANDMARKS,
            connections = HAND_CONNECTIONS,
            transform = transform,
            color = handColor,
        )
    }
}

internal data class OverlayTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val mirrorHorizontally: Boolean,
) {
    fun project(x: Float, y: Float): Offset {
        val sourceX = (if (mirrorHorizontally) 1f - x else x) * imageWidth
        return Offset(offsetX + sourceX * scale, offsetY + y * imageHeight * scale)
    }
}

internal fun overlayTransform(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    mirrorHorizontally: Boolean,
): OverlayTransform? {
    if (canvasWidth <= 0f || canvasHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return null
    }
    val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    return OverlayTransform(
        scale = scale,
        offsetX = (canvasWidth - imageWidth * scale) / 2f,
        offsetY = (canvasHeight - imageHeight * scale) / 2f,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        mirrorHorizontally = mirrorHorizontally,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLandmarkGroup(
    landmarks: FloatArray?,
    firstLandmarkIndex: Int,
    landmarkCount: Int,
    connections: Array<IntArray>,
    transform: OverlayTransform,
    color: Color,
) {
    val endLandmarkIndex = firstLandmarkIndex + landmarkCount
    if (landmarks == null || landmarks.size < endLandmarkIndex * 3) return

    fun normalized(index: Int): Pair<Float, Float>? {
        val x = landmarks[index * 3]
        val y = landmarks[index * 3 + 1]
        return if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) x to y else null
    }

    for (edge in connections) {
        val from = normalized(edge[0]) ?: continue
        val to = normalized(edge[1]) ?: continue
        val start = transform.project(from.first, from.second)
        val end = transform.project(to.first, to.second)
        drawLine(
            Color.Black.copy(alpha = 0.72f),
            start,
            end,
            4.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(color.copy(alpha = 0.92f), start, end, 2.dp.toPx(), StrokeCap.Round)
    }

    for (index in firstLandmarkIndex until endLandmarkIndex) {
        val point = normalized(index) ?: continue
        val center = transform.project(point.first, point.second)
        drawCircle(Color.Black.copy(alpha = 0.78f), radius = 4.dp.toPx(), center = center)
        drawCircle(color, radius = 2.5.dp.toPx(), center = center)
    }
}

private const val HAND_LANDMARKS = 21

private val HAND_CONNECTIONS = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
    intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
    intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
    intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
    intArrayOf(13, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
    intArrayOf(0, 17),
)

private val POSE_CONNECTIONS = arrayOf(
    intArrayOf(11, 12),
    intArrayOf(11, 13), intArrayOf(13, 15), intArrayOf(15, 17), intArrayOf(17, 19),
    intArrayOf(19, 15), intArrayOf(15, 21),
    intArrayOf(12, 14), intArrayOf(14, 16), intArrayOf(16, 18), intArrayOf(18, 20),
    intArrayOf(20, 16), intArrayOf(16, 22),
    intArrayOf(11, 23), intArrayOf(12, 24), intArrayOf(23, 24),
)
