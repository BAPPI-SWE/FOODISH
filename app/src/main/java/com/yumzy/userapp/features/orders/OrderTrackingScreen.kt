// OrderTrackingScreen.kt
package com.yumzy.userapp.features.orders

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import com.yumzy.userapp.ui.theme.DarkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ---------------------------------------------------------------------------
// Tracking stages. The first four are simulated purely on the client using
// elapsed time since the order was created. The last three mirror the real
// "orderStatus" field that the restaurant/admin updates in Firestore.
// ---------------------------------------------------------------------------
enum class TrackingStage(val stepIndex: Int, val title: String, val subtitle: String) {
    PLACED(1, "Order Placed", "We've sent your order to the restaurant"),
    RECEIVED(2, "Order Received", "The restaurant has seen your order"),
    ORDER_CONFIRMED(3, "Order Confirmed", "Your order has been confirmed"),
    PREPARING(4, "Preparing Your Food", "The kitchen is cooking up your meal"),
    ACCEPTED(5, "Rider Assigned", "A rider has been assigned to your order"),
    ON_THE_WAY(6, "On The Way", "Your food is heading to your doorstep"),
    DELIVERED(7, "Delivered", "Enjoy your meal! Thanks for ordering")
}

private enum class StepState { DONE, ACTIVE, PENDING }

fun computeStage(order: Order, nowMillis: Long): TrackingStage {
    val status = order.orderStatus.trim()
    return when {
        status.equals("Delivered", ignoreCase = true) -> TrackingStage.DELIVERED
        status.equals("On the way", ignoreCase = true) -> TrackingStage.ON_THE_WAY
        status.equals("Accepted", ignoreCase = true) -> TrackingStage.ACCEPTED
        else -> {
            val elapsedSec = (nowMillis - order.createdAt.toDate().time) / 1000
            when {
                elapsedSec < 20 -> TrackingStage.PLACED
                elapsedSec < 60 -> TrackingStage.RECEIVED
                elapsedSec < 120 -> TrackingStage.ORDER_CONFIRMED
                else -> TrackingStage.PREPARING
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingScreen(
    orderId: String,
    onBackClicked: () -> Unit,
    onGoToHome: () -> Unit
) {
    val context = LocalContext.current
    var order by remember { mutableStateOf<Order?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // User's phone number — used to identify them to support when cancelling,
    // same as the old order-details dialog used to do.
    var userPhone by remember { mutableStateOf(Firebase.auth.currentUser?.phoneNumber ?: "") }
    LaunchedEffect(Unit) {
        if (userPhone.isEmpty()) {
            val uid = Firebase.auth.currentUser?.uid
            if (uid != null) {
                Firebase.firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        userPhone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: ""
                    }
            }
        }
    }

    // Live Firestore listener so the screen updates instantly the moment
    // the restaurant/admin changes the order status.
    DisposableEffect(orderId) {
        val registration = Firebase.firestore.collection("orders").document(orderId)
            .addSnapshotListener { doc, _ ->
                isLoading = false
                if (doc != null && doc.exists()) {
                    val itemsData = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val orderItems = itemsData.map { itemMap ->
                        OrderItem(
                            name = itemMap["itemName"] as? String ?: itemMap["name"] as? String ?: "Unknown Item",
                            quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0,
                            price = (itemMap["itemPrice"] as? Number)?.toDouble() ?: (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                            miniResName = itemMap["miniResName"] as? String ?: ""
                        )
                    }
                    order = Order(
                        id = doc.id,
                        restaurantName = doc.getString("restaurantName") ?: "Unknown Restaurant",
                        totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                        orderStatus = doc.getString("orderStatus") ?: "Pending",
                        createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                        items = orderItems,
                        deliveryCharge = doc.getDouble("deliveryCharge") ?: 0.0,
                        serviceCharge = doc.getDouble("serviceCharge") ?: 0.0,
                        riderName = doc.getString("riderName") ?: "",
                        riderId = doc.getString("riderId") ?: ""
                    )
                }
            }
        onDispose { registration.remove() }
    }

    // A 1-second ticker so the simulated early stages (20s / 60s / 120s)
    // progress live on screen without needing any user interaction.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Rider phone lookup
    var riderPhone by remember { mutableStateOf("") }
    LaunchedEffect(order?.riderId) {
        riderPhone = ""
        val riderId = order?.riderId
        if (!riderId.isNullOrEmpty()) {
            Firebase.firestore.collection("riders").document(riderId).get()
                .addOnSuccessListener { doc -> riderPhone = doc.getString("phone") ?: "" }
        }
    }

    val currentOrder = order

    Scaffold(
        containerColor = Color(0xFFF8F9FA),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Track Order",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading || currentOrder == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DarkPink)
            }
            return@Scaffold
        }

        val isCancelled = currentOrder.orderStatus.equals("Cancelled", ignoreCase = true)
        val stage = computeStage(currentOrder, nowMillis)

        // Reactive 5-minute cancellation countdown — recalculated every second
        // via the nowMillis ticker so it counts down live on screen.
        val elapsedSecSinceOrder = (nowMillis - currentOrder.createdAt.toDate().time) / 1000
        val remainingCancelSec = (300 - elapsedSecSinceOrder).coerceAtLeast(0)
        val isCancellable = remainingCancelSec > 0 && currentOrder.orderStatus == "Pending"
        val cancelCountdownText = String.format(
            "%02d:%02d",
            remainingCancelSec / 60,
            remainingCancelSec % 60
        )

        if (showCancelConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelConfirmDialog = false },
                title = { Text("Cancel Order?", fontWeight = FontWeight.Bold) },
                text = { Text("You'll be redirected to our WhatsApp helpline to complete your cancellation request.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showCancelConfirmDialog = false
                            val whatsappNumber = "8801746324620"
                            val message = "Hello, I would like to cancel my order. My phone number is $userPhone."
                            val uri = Uri.parse("https://wa.me/$whatsappNumber?text=${Uri.encode(message)}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Go to WhatsApp") }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelConfirmDialog = false }) { Text("No, Keep Order") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            HeroStatusHeader(order = currentOrder, stage = stage, isCancelled = isCancelled)

            Spacer(Modifier.height(20.dp))

            if (isCancelled) {
                CancelledCard(context = context, userPhone = userPhone)
            } else {
                TrackingTimelineCard(
                    stage = stage,
                    order = currentOrder,
                    riderPhone = riderPhone,
                    context = context
                )
            }

            Spacer(Modifier.height(20.dp))

            OrderItemsCard(order = currentOrder)

            Spacer(Modifier.height(20.dp))

            if (isCancellable) {
                OutlinedButton(
                    onClick = { showCancelConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel Order", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Red.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                cancelCountdownText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onGoToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkPink)
            ) {
                Text("Continue Shopping", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero header — big animated icon + headline that reflects the live stage
// ---------------------------------------------------------------------------
@Composable
private fun HeroStatusHeader(order: Order, stage: TrackingStage, isCancelled: Boolean) {
    val (icon, gradientColors, headline, sub) = when {
        isCancelled -> HeroContent(
            Icons.Default.Cancel,
            listOf(Color(0xFFFF7A7A), Color(0xFFE53935)),
            "Order Cancelled",
            "This order has been cancelled"
        )
        stage == TrackingStage.DELIVERED -> HeroContent(
            Icons.Default.Celebration,
            listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)),
            "Delivered!",
            "Enjoy your meal from ${order.restaurantName}"
        )
        stage == TrackingStage.ON_THE_WAY -> HeroContent(
            Icons.Default.DeliveryDining,
            listOf(Color(0xFF42A5F5), Color(0xFF1565C0)),
            "On The Way",
            "Your rider is heading to you now"
        )
        stage == TrackingStage.ACCEPTED -> HeroContent(
            Icons.Default.TwoWheeler,
            listOf(Color(0xFFFFA726), Color(0xFFEF6C00)),
            "Rider Assigned",
            "Getting your order ready for pickup"
        )
        stage == TrackingStage.PREPARING -> HeroContent(
            Icons.Default.SoupKitchen,
            listOf(Color(0xFFFFB74D), Color(0xFFEF6C00)),
            "Preparing Your Food",
            "${order.restaurantName} is cooking your order"
        )
        stage == TrackingStage.ORDER_CONFIRMED -> HeroContent(
            Icons.Default.CheckCircle,
            listOf(DarkPink, Color(0xFFAD1457)),
            "Order Confirmed",
            "We've confirmed your order"
        )
        stage == TrackingStage.RECEIVED -> HeroContent(
            Icons.Default.MarkEmailRead,
            listOf(DarkPink, Color(0xFFAD1457)),
            "Order Received",
            "${order.restaurantName} has your order"
        )
        else -> HeroContent(
            Icons.Default.Schedule,
            listOf(DarkPink, Color(0xFFAD1457)),
            "Order Placed",
            "Sending your order to the restaurant"
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(gradientColors))
            .padding(vertical = 32.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        if (!isCancelled && stage != TrackingStage.DELIVERED) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
                    .background(Color.White.copy(alpha = 0.22f), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = headline,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                headline,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sub,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Text(
                    "Order #${order.id.takeLast(6).uppercase()}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private data class HeroContent(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradient: List<Color>,
    val headline: String,
    val subtitle: String
)

// ---------------------------------------------------------------------------
// Cancelled state card
// ---------------------------------------------------------------------------
@Composable
private fun CancelledCard(context: android.content.Context, userPhone: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
        border = BorderStroke(1.dp, Color(0xFFFFCDD2))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.RemoveShoppingCart,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This order was cancelled",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB71C1C)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "If this wasn't expected, please reach out to our support team for help.",
                fontSize = 13.sp,
                color = Color(0xFF8D6E6E),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val whatsappNumber = "8801746324620"
                    val message = "Hello, I have a question about my cancelled order. My phone number is $userPhone."
                    val uri = Uri.parse("https://wa.me/$whatsappNumber?text=${Uri.encode(message)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Contact Support")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vertical stepper timeline
// ---------------------------------------------------------------------------
@Composable
private fun TrackingTimelineCard(
    stage: TrackingStage,
    order: Order,
    riderPhone: String,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val stages = TrackingStage.values().toList()
            stages.forEach { s ->
                val state = when {
                    s.stepIndex < stage.stepIndex -> StepState.DONE
                    s.stepIndex == stage.stepIndex -> StepState.ACTIVE
                    else -> StepState.PENDING
                }
                // Steps not yet reached stay completely hidden and reveal
                // themselves one by one, in order, as the order progresses.
                AnimatedVisibility(
                    visible = state != StepState.PENDING,
                    enter = fadeIn(tween(500)) + expandVertically(tween(500)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TimelineStepRow(
                        stage = s,
                        state = state,
                        isLast = state == StepState.ACTIVE
                    ) {
                        when {
                            s == TrackingStage.PREPARING && state == StepState.ACTIVE -> CookingAnimationRow()
                            s == TrackingStage.ACCEPTED && state != StepState.PENDING -> RiderInfoCard(
                                order = order,
                                riderPhone = riderPhone,
                                context = context
                            )
                            s == TrackingStage.ON_THE_WAY && state == StepState.ACTIVE -> OnTheWayAnimationRow()
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineStepRow(
    stage: TrackingStage,
    state: StepState,
    isLast: Boolean,
    extraContent: @Composable () -> Unit
) {
    val color = when (state) {
        StepState.DONE -> Color(0xFF4CAF50)
        StepState.ACTIVE -> DarkPink
        StepState.PENDING -> Color(0xFFCFCFCF)
    }
    val textColor = if (state == StepState.PENDING) Color(0xFFB0B0B0) else Color(0xFF1A1A1A)

    val infiniteTransition = rememberInfiniteTransition(label = "activeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (state == StepState.ACTIVE) color.copy(alpha = glowAlpha) else color.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(if (state == StepState.PENDING) Color(0xFFE8E8E8) else color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (state == StepState.DONE) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (extraContentPresent(stage, state)) 64.dp else 36.dp)
                        .background(if (state == StepState.DONE) Color(0xFF4CAF50) else Color(0xFFE8E8E8))
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(
                stage.title,
                fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stage.subtitle,
                fontSize = 12.sp,
                color = if (state == StepState.PENDING) Color(0xFFC5C5C5) else Color(0xFF757575)
            )
            AnimatedVisibility(
                visible = state != StepState.PENDING,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    extraContent()
                }
            }
        }
    }
}

private fun extraContentPresent(stage: TrackingStage, state: StepState): Boolean {
    return (stage == TrackingStage.PREPARING && state == StepState.ACTIVE) ||
            (stage == TrackingStage.ACCEPTED && state != StepState.PENDING) ||
            (stage == TrackingStage.ON_THE_WAY && state == StepState.ACTIVE)
}

// ---------------------------------------------------------------------------
// Cooking animation — gently bouncing utensil + animated dots
// ---------------------------------------------------------------------------
@Composable
private fun CookingAnimationRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "cooking")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF3E0)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SoupKitchen,
                contentDescription = null,
                tint = Color(0xFFEF6C00),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { translationY = bounce }
            )
            Spacer(Modifier.width(10.dp))
            val dots = ".".repeat((dotCount.toInt() % 3) + 1)
            Text(
                "Your food is cooking$dots",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFEF6C00)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rider card — shown once a rider has been assigned
// ---------------------------------------------------------------------------
@Composable
private fun RiderInfoCard(order: Order, riderPhone: String, context: android.content.Context) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669))), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (order.riderName.isNotEmpty()) {
                    Text(
                        order.riderName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                } else {
                    Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "YOUR RIDER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A),
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (order.riderName.isNotEmpty()) order.riderName else "Assigning a rider...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A1A)
                )
                if (riderPhone.isNotEmpty()) {
                    Text(riderPhone, fontSize = 12.sp, color = Color(0xFF4B5563))
                }
            }
            if (riderPhone.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$riderPhone"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF16A34A), CircleShape)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call Rider", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// "On the way" animation — a rider icon moving along a dashed path
// ---------------------------------------------------------------------------
@Composable
private fun OnTheWayAnimationRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "onTheWay")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE3F2FD)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                "Your rider is speeding your way!",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1565C0)
            )
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                // dashed base line
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFB3D4F5))
                )
                Icon(
                    Icons.Default.DeliveryDining,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            translationX = progress * 220f
                        }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Order items summary
// ---------------------------------------------------------------------------
@Composable
private fun OrderItemsCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = DarkPink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    order.restaurantName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A1A)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Ordered on ${formatDate(order.createdAt)}",
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFEFEFEF))
            Spacer(Modifier.height(14.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${item.quantity} x ${item.name}",
                        fontSize = 13.sp,
                        color = Color(0xFF333333),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "৳${item.price * item.quantity}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEFEFEF))
            Spacer(Modifier.height(10.dp))

            val itemsSubtotal = order.items.sumOf { it.price * it.quantity }
            PriceDetailRow("Items Subtotal", itemsSubtotal)
            if (order.deliveryCharge > 0) PriceDetailRow("Delivery Charge", order.deliveryCharge)
            if (order.serviceCharge > 0) PriceDetailRow("Service Charge/Tax", order.serviceCharge)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFEFEFEF))
            Spacer(Modifier.height(8.dp))
            PriceDetailRow("Total Payable", order.totalPrice, isTotal = true)
        }
    }
}