package com.yumzy.userapp.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// WhatsApp Green Color
val WhatsAppGreen = Color(0xFF25D366)

@Composable
fun WhatsAppSupportButton(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val phoneNumber = "+8801746324620"

    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // 1. Full FAB (Visible when scrolling up)
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn() + fadeIn() + slideInHorizontally { it },
            exit = scaleOut() + fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.padding(end = 14.dp, bottom = 38.dp) // Standard FAB margin
        ) {
            FloatingActionButton(
                onClick = { openWhatsApp(context, phoneNumber) },
                containerColor = WhatsAppGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "WhatsApp Support",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Docked Slide Arrow (Visible when scrolling down)
        AnimatedVisibility(
            visible = !isVisible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.padding(bottom = 42.dp) // Align vertically with where FAB was
        ) {
            Surface(
                onClick = { openWhatsApp(context, phoneNumber) },
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                color = WhatsAppGreen.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                modifier = Modifier.size(width = 20.dp, height = 40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Arrow pointing into screen
                        contentDescription = "Open Support",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Extension to detect scroll direction
@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

private fun openWhatsApp(context: Context, number: String) {
    try {
        val url = "https://api.whatsapp.com/send?phone=$number"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    }
}