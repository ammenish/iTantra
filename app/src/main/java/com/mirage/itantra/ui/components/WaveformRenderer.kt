package com.mirage.itantra.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mirage.itantra.ui.theme.GlowGreen
import com.mirage.itantra.ui.theme.GlowRed
import com.mirage.itantra.ui.theme.LocalAlertMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the stunning, organic glowing waveform using math!
 */
@Composable
fun WaveformRenderer(
    modifier: Modifier = Modifier,
    amplitude: Float = 0.5f, // Range 0.0 to 1.0 (reacts to voice)
    isCircular: Boolean = true
) {
    val isAlertMode = LocalAlertMode.current
    val glowColor = if (isAlertMode) GlowRed else GlowGreen

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth out the amplitude input
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.1f, 1.0f),
        animationSpec = tween(150),
        label = "amplitude"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f

        if (isCircular) {
            val baseRadius = minOf(width, height) / 2.5f
            
            // Draw 4 overlapping wave rings for that organic 3D-like glow
            drawCircularWave(cx, cy, baseRadius, phaseOffset, animatedAmplitude, 3f, glowColor, 1.2f)
            drawCircularWave(cx, cy, baseRadius, -phaseOffset * 1.5f, animatedAmplitude, 5f, glowColor, 0.8f)
            drawCircularWave(cx, cy, baseRadius, phaseOffset * 0.8f + 1f, animatedAmplitude * 0.8f, 2f, glowColor, 1.5f)
            drawCircularWave(cx, cy, baseRadius, -phaseOffset * 2f + 2f, animatedAmplitude * 0.5f, 4f, glowColor, 0.6f)
        } else {
            // Linear waveform
            drawLineWave(width, height, phaseOffset, animatedAmplitude, 3f, glowColor, 1.2f)
            drawLineWave(width, height, -phaseOffset * 1.5f, animatedAmplitude, 5f, glowColor, 0.8f)
            drawLineWave(width, height, phaseOffset * 0.8f + 1f, animatedAmplitude * 0.8f, 2f, glowColor, 1.5f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircularWave(
    cx: Float, cy: Float, baseRadius: Float,
    phase: Float, amplitude: Float,
    frequency: Float, color: Color, strokeWidthMultiplier: Float
) {
    val path = Path()
    val points = 120
    val maxDev = baseRadius * 0.3f * amplitude // Max pixel deviation

    for (i in 0..points) {
        val angle = (i.toFloat() / points) * 2 * PI.toFloat()
        
        // Complex sine wave interference
        val wave1 = sin(angle * frequency + phase)
        val wave2 = cos(angle * (frequency - 1) - phase * 0.5f)
        
        // Modulate amplitude so it's smooth
        val r = baseRadius + (wave1 * wave2 * maxDev)
        
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    // Outer faint glow
    drawPath(
        path = path,
        color = color.copy(alpha = 0.15f),
        style = Stroke(width = 16f * strokeWidthMultiplier, cap = StrokeCap.Round),
        blendMode = BlendMode.Screen
    )
    // Inner bright core
    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f),
        style = Stroke(width = 4f * strokeWidthMultiplier, cap = StrokeCap.Round),
        blendMode = BlendMode.Screen
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineWave(
    width: Float, height: Float,
    phase: Float, amplitude: Float,
    frequency: Float, color: Color, strokeWidthMultiplier: Float
) {
    val path = Path()
    val points = 100
    val maxDev = height * 0.4f * amplitude
    val cy = height / 2f

    for (i in 0..points) {
        val x = (i.toFloat() / points) * width
        
        // Fade out at edges
        val edgeFade = sin((i.toFloat() / points) * PI.toFloat())
        
        val wave = sin((x / width) * 2 * PI.toFloat() * frequency + phase)
        val y = cy + (wave * maxDev * edgeFade)
        
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = color.copy(alpha = 0.15f),
        style = Stroke(width = 12f * strokeWidthMultiplier, cap = StrokeCap.Round),
        blendMode = BlendMode.Screen
    )
    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f),
        style = Stroke(width = 3f * strokeWidthMultiplier, cap = StrokeCap.Round),
        blendMode = BlendMode.Screen
    )
}
