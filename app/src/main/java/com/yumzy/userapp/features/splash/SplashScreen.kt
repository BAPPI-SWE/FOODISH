package com.yumzy.userapp.features.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.yumzy.userapp.R
import kotlinx.coroutines.delay
@Composable
fun SplashScreen(onAnimationFinish: () -> Unit) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.splash_animation)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )

    LaunchedEffect(progress) {
        if (progress == 1f) {
            delay(10)
            onAnimationFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(
                    scaleX = 1.1f,  // Zoom in horizontally
                    scaleY = 1.1f   // Zoom in vertically
                )
        )

    }

}
