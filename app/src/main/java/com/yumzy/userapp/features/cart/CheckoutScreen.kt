// CheckoutScreen.kt
package com.yumzy.userapp.features.cart

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.ads.SharedInterstitialAdManager
import com.yumzy.userapp.ui.theme.DarkPink
import kotlinx.coroutines.delay
import kotlin.random.Random

// Add this import at the top of your file if not already present
import androidx.compose.material.icons.filled.Check
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement

data class UserProfileDetails(
    val name: String = "...",
    val email: String = "...",
    val fullAddress: String = "...",
    val baseLocation: String = "",
    val subLocation: String = ""
)

// Celebration confetti data class
data class ConfettiParticle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val scale: Float,
    val rotation: Float,
    val duration: Int,
    val delay: Int,
    val color: Color,
    val shape: ParticleShape
)

enum class ParticleShape {
    STAR, CIRCLE, SQUARE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    cartItems: List<CartItem>,
    restaurantId: String? = null,
    onConfirmOrder: (deliveryCharge: Double, serviceCharge: Double, finalTotal: Double) -> Unit,
    onBackClicked: () -> Unit
) {
    val itemsSubtotal = cartItems.sumOf { it.menuItem.price * it.quantity }
    var deliveryCharge by remember { mutableStateOf(20.0) }
    var serviceCharge by remember { mutableStateOf(5.0) }
    var isLoadingCharges by remember { mutableStateOf(true) }
    val isPreOrder = cartItems.isNotEmpty() && cartItems.first().menuItem.category.startsWith("Pre-order")
    val finalTotal = itemsSubtotal + deliveryCharge + serviceCharge
    var userProfile by remember { mutableStateOf<UserProfileDetails?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    var showCelebration by remember { mutableStateOf(false) }
    var isPlacingOrder by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Pre-load the ad when screen is first composed - it will load in background
    LaunchedEffect(Unit) {
        SharedInterstitialAdManager.loadAd(context)
        Log.d("CheckoutScreen", "Started pre-loading ad in background")
    }

    // Fetch user profile from Firestore
    LaunchedEffect(Unit) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            Firebase.firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val address = "${document.getString("baseLocation") ?: ""} , " +
                                "${document.getString("subLocation") ?: ""}"
                        userProfile = UserProfileDetails(
                            name = document.getString("name") ?: "N/A",
                            email = currentUser.email ?: "N/A",
                            fullAddress = address,
                            baseLocation = document.getString("baseLocation") ?: "",
                            subLocation = document.getString("subLocation") ?: ""
                        )
                    }
                    isLoadingProfile = false
                }
                .addOnFailureListener {
                    isLoadingProfile = false
                }
        } else {
            isLoadingProfile = false
        }
    }

    // Calculate dynamic charges based on user location and store type + additional charges from items
    LaunchedEffect(userProfile) {
        if (userProfile != null && userProfile!!.baseLocation.isNotEmpty() && userProfile!!.subLocation.isNotEmpty()) {
            isLoadingCharges = true
            val db = Firebase.firestore

            // First, get base charges from location
            db.collection("locations")
                .whereEqualTo("name", userProfile!!.baseLocation)
                .get()
                .addOnSuccessListener { documents ->
                    var baseDeliveryCharge = 20.0
                    var baseServiceCharge = 5.0

                    if (!documents.isEmpty) {
                        val locationDoc = documents.documents[0]
                        val subLocations = locationDoc.get("subLocations") as? List<String> ?: emptyList()
                        val subLocationIndex = subLocations.indexOf(userProfile!!.subLocation)
                        if (subLocationIndex != -1) {
                            if (isPreOrder) {
                                val serviceChargeArray = locationDoc.get("serviceCharge") as? List<Number> ?: emptyList()
                                val deliveryChargeArray = locationDoc.get("deliveryCharge") as? List<Number> ?: emptyList()
                                if (subLocationIndex < serviceChargeArray.size) baseServiceCharge = serviceChargeArray[subLocationIndex].toDouble()
                                if (subLocationIndex < deliveryChargeArray.size) baseDeliveryCharge = deliveryChargeArray[subLocationIndex].toDouble()
                            } else {
                                val serviceChargeArray = locationDoc.get("serviceChargeYumzy") as? List<Number> ?: emptyList()
                                val deliveryChargeArray = locationDoc.get("deliveryChargeYumzy") as? List<Number> ?: emptyList()
                                if (subLocationIndex < serviceChargeArray.size) baseServiceCharge = serviceChargeArray[subLocationIndex].toDouble()
                                if (subLocationIndex < deliveryChargeArray.size) baseDeliveryCharge = deliveryChargeArray[subLocationIndex].toDouble()
                            }
                        }
                    }

                    // Now check for additional charges from store items
                    if (restaurantId == "yumzy_store") {
                        val baseItemIds = cartItems.map {
                            val itemId = it.menuItem.id
                            if (itemId.contains("_")) itemId.substringBefore("_") else itemId
                        }.distinct()

                        var additionalDelivery = 0.0
                        var additionalService = 0.0
                        var itemsProcessed = 0

                        if (baseItemIds.isEmpty()) {
                            deliveryCharge = baseDeliveryCharge
                            serviceCharge = baseServiceCharge
                            isLoadingCharges = false
                            return@addOnSuccessListener
                        }

                        baseItemIds.forEach { baseItemId ->
                            db.collection("store_items").document(baseItemId).get()
                                .addOnSuccessListener { itemDoc ->
                                    if (itemDoc.exists()) {
                                        additionalDelivery += itemDoc.getDouble("additionalDeliveryCharge") ?: 0.0
                                        additionalService += itemDoc.getDouble("additionalServiceCharge") ?: 0.0
                                    }
                                    itemsProcessed++
                                    if (itemsProcessed == baseItemIds.size) {
                                        deliveryCharge = baseDeliveryCharge + additionalDelivery
                                        serviceCharge = baseServiceCharge + additionalService
                                        isLoadingCharges = false
                                    }
                                }
                                .addOnFailureListener {
                                    itemsProcessed++
                                    if (itemsProcessed == baseItemIds.size) {
                                        deliveryCharge = baseDeliveryCharge + additionalDelivery
                                        serviceCharge = baseServiceCharge + additionalService
                                        isLoadingCharges = false
                                    }
                                }
                        }
                    } else {
                        deliveryCharge = baseDeliveryCharge
                        serviceCharge = baseServiceCharge
                        isLoadingCharges = false
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("CheckoutScreen", "Error loading charges: ${exception.message}")
                    isLoadingCharges = false
                }
        } else {
            isLoadingCharges = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Confirm Order",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClicked,
                            enabled = !isPlacingOrder
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            // --- NEW: Added bottom bar for the confirm button ---
            bottomBar = {
                CheckoutBottomBar(
                    totalAmount = finalTotal,
                    isLoading = isLoadingCharges,
                    isPlacingOrder = isPlacingOrder,
                    onConfirmClick = {
                        showCelebration = true
                        isPlacingOrder = true
                        onConfirmOrder(deliveryCharge, serviceCharge, finalTotal)
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8F9FA))
                    .verticalScroll(rememberScrollState())
                    // Add padding at the bottom to ensure content doesn't hide behind the bar
                    .padding(bottom = 80.dp)
            ) {
                // Delivery Address Section
                SectionHeader(title = "Delivery Address")
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        if (isLoadingProfile) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DarkPink,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Loading address...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Address",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    userProfile?.fullAddress ?: "No Address Found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Order Summary Section
                SectionHeader(title = "Order Summary")
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        cartItems.forEach { cartItem ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${cartItem.quantity} x",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF666666)
                                    ),
                                    modifier = Modifier.width(40.dp)
                                )
                                Text(
                                    cartItem.menuItem.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )
                                )
                                Text(
                                    "৳${cartItem.menuItem.price * cartItem.quantity}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = DarkPink
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Price Details Section
                SectionHeader(title = "Price Details")
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isLoadingCharges) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DarkPink,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Calculating charges...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                            }
                        } else {
                            PriceRow(label = "Items Subtotal", amount = itemsSubtotal)
                            PriceRow(label = "Delivery Charge", amount = deliveryCharge)
                            PriceRow(label = "Service Charge/Tax", amount = serviceCharge)
                            Divider(
                                Modifier.padding(vertical = 8.dp),
                                color = Color(0xFFE0E0E0)
                            )
                            PriceRow(label = "Total to Pay", amount = finalTotal, isTotal = true)
                        }
                    }
                }
                // --- The old button was here and has been removed ---
            }
        }

        // Celebration Animation Overlay
        if (showCelebration) {
            OrderSentAnimation(
                onAnimationComplete = { showCelebration = false }
            )
            CelebrationAnimation(
                onAnimationComplete = { }
            )
        }
    }
}

// --- NEW Composable for the Bottom Bar ---
@Composable
fun CheckoutBottomBar(
    totalAmount: Double,
    isLoading: Boolean,
    isPlacingOrder: Boolean,
    onConfirmClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Button(
            onClick = onConfirmClick,
            enabled = !isLoading && !isPlacingOrder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkPink,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            if (isPlacingOrder) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Placing Order...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Confirm & Place Order",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
//                    Text(
//                        "৳${"%.2f".format(totalAmount)}",
//                        fontSize = 16.sp,
//                        fontWeight = FontWeight.Bold
//                    )
                }
            }
        }
    }
}


@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp, top = 8.dp),
        color = Color(0xFF333333)
    )
}

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                clip = true
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(content = content)
    }
}

@Composable
fun CelebrationAnimation(onAnimationComplete: () -> Unit) {
    val particles = remember {
        List(35) { index ->
            ConfettiParticle(
                id = index,
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.3f,
                scale = Random.nextFloat() * 0.6f + 0.5f,
                rotation = Random.nextFloat() * 360f,
                duration = Random.nextInt(1800, 2800),
                delay = Random.nextInt(0, 400),
                color = when (Random.nextInt(6)) {
                    0 -> Color(0xFFFFD700) // Gold
                    1 -> Color(0xFFFF6B6B) // Red
                    2 -> Color(0xFF4ECDC4) // Teal
                    3 -> Color(0xFFFFE66D) // Yellow
                    4 -> Color(0xFF95E1D3) // Mint
                    else -> Color(0xFFF38181) // Pink
                },
                shape = when (Random.nextInt(3)) {
                    0 -> ParticleShape.STAR
                    1 -> ParticleShape.CIRCLE
                    else -> ParticleShape.SQUARE
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
    ) {
        particles.forEach { particle ->
            AnimatedConfettiParticle(particle = particle)
        }
    }
}

@Composable
fun AnimatedConfettiParticle(particle: ConfettiParticle) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle_${particle.id}")

    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(particle.delay.toLong())
        startAnimation = true
    }

    val offsetY by animateFloatAsState(
        targetValue = if (startAnimation) 1400f else 0f,
        animationSpec = tween(
            durationMillis = particle.duration,
            easing = FastOutSlowInEasing
        ),
        label = "offsetY_${particle.id}"
    )

    val offsetX by animateFloatAsState(
        targetValue = if (startAnimation) (Random.nextFloat() - 0.5f) * 200f else 0f,
        animationSpec = tween(
            durationMillis = particle.duration,
            easing = LinearEasing
        ),
        label = "offsetX_${particle.id}"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 1f,
        animationSpec = tween(
            durationMillis = particle.duration,
            easing = LinearEasing
        ),
        label = "alpha_${particle.id}"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = particle.rotation,
        targetValue = particle.rotation + 720f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_${particle.id}"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .offset(
                    x = (particle.startX * 350).dp,
                    y = (particle.startY * 100).dp
                )
                .graphicsLayer {
                    translationY = offsetY
                    translationX = offsetX
                    this.alpha = alpha
                    rotationZ = rotation
                    scaleX = particle.scale
                    scaleY = particle.scale
                }
        ) {
            when (particle.shape) {
                ParticleShape.STAR -> {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star",
                        tint = particle.color,
                        modifier = Modifier.size((28 * particle.scale).dp)
                    )
                }
                ParticleShape.CIRCLE -> {
                    Box(
                        modifier = Modifier
                            .size((20 * particle.scale).dp)
                            .background(particle.color, CircleShape)
                    )
                }
                ParticleShape.SQUARE -> {
                    Box(
                        modifier = Modifier
                            .size((18 * particle.scale).dp)
                            .background(particle.color, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PriceRow(label: String, amount: Double, isTotal: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
            color = if (isTotal) DarkPink else Color(0xFF666666),
            fontSize = if (isTotal) 18.sp else 16.sp
        )
        Text(
            "৳$amount",
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isTotal) DarkPink else Color(0xFF333333),
            fontSize = if (isTotal) 18.sp else 16.sp
        )
    }
}

@Composable
fun OrderSentAnimation(onAnimationComplete: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    // Trigger animation start
    LaunchedEffect(Unit) {
        delay(200)
        startAnimation = true
        delay(3500)
        onAnimationComplete()
    }

    // Checkmark scale animation
    val checkScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkScale"
    )

    // Circle scale animation
    val circleScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circleScale"
    )

    // Ripple effect
    val rippleScale by animateFloatAsState(
        targetValue = if (startAnimation) 2.5f else 0.8f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "rippleScale"
    )

    val rippleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 0.4f,
        animationSpec = tween(1200, easing = LinearEasing),
        label = "rippleAlpha"
    )

    // Text animations
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )

    val textOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 30f,
        animationSpec = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "textOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .zIndex(999f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated container with ripple effect
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Ripple effect
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = rippleScale
                            scaleY = rippleScale
                            alpha = rippleAlpha
                        }
                        .background(
                            color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )

                // Main circle background
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = circleScale
                            scaleY = circleScale
                        }
                        .background(
                            color = Color(0xFF4CAF50),
                            shape = CircleShape
                        )
                        .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Checkmark icon
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Order Sent",
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // "Order Sent!" text
            Text(
                text = "Order Sent!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha
                        translationY = textOffsetY
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle text
            Text(
                text = "Your order has been confirmed",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha
                        translationY = textOffsetY
                    }
            )
        }
    }
}