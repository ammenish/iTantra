package com.mirage.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mirage.itantra.ui.theme.AlertBackground
import com.mirage.itantra.ui.theme.DarkBackground
import com.mirage.itantra.ui.theme.GlowGreen
import com.mirage.itantra.ui.theme.GlowRed
import com.mirage.itantra.ui.theme.LocalAlertMode
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen premium animated background.
 * Provides a deep dark gradient + floating glowing particles.
 */
@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isAlertMode = LocalAlertMode.current
    
    val bgColor by animateColorAsState(
        targetValue = if (isAlertMode) AlertBackground else DarkBackground,
        animationSpec = tween(800),
        label = "bg_anim"
    )
    
    val particleColor = if (isAlertMode) GlowRed else GlowGreen

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        // Center radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            particleColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )
        
        // Particles
        GlowingParticles(color = particleColor)
        
        // Content on top
        content()
    }
}

@Composable
private fun GlowingParticles(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Generate random static properties for particles once
    val particles = remember {
        List(25) {
            ParticleData(
                xSeed = Random.nextFloat(),
                ySeed = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.5f,
                speedY = (Random.nextFloat() - 0.5f) * 0.5f,
                radius = Random.nextFloat() * 4f + 1f,
                phase = Random.nextFloat() * Math.PI.toFloat() * 2
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEach { p ->
            // Calculate current position based on time
            val x = (p.xSeed * w + p.speedX * time) % w
            val y = (p.ySeed * h + p.speedY * time) % h
            
            // Handle wrap-around seamlessly
            val wrappedX = if (x < 0) x + w else x
            val wrappedY = if (y < 0) y + h else y
            
            // Pulsing alpha
            val alpha = (sin(time * 0.05f + p.phase) * 0.3f + 0.4f).coerceIn(0.1f, 0.7f)

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(wrappedX, wrappedY)
            )
        }
    }
}

private data class ParticleData(
    val xSeed: Float,
    val ySeed: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val phase: Float
)
