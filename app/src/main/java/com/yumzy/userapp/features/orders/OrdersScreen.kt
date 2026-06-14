// OrdersScreen.kt
package com.yumzy.userapp.features.orders

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.ads.SharedInterstitialAdManager
import com.yumzy.userapp.ui.theme.DarkPink
import com.yumzy.userapp.ui.theme.LightGray
import java.text.SimpleDateFormat
import java.util.Locale

data class OrderItem(
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val miniResName: String = ""
)

data class Order(
    val id: String = "",
    val restaurantName: String = "",
    val totalPrice: Double = 0.0,
    val orderStatus: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val items: List<OrderItem> = emptyList(),
    val deliveryCharge: Double = 0.0,
    val serviceCharge: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    showAdOnLoad: Boolean = false
) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var hasShownAd by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Full phone number from Firebase Auth (e.g. "+8801712345678")
    var userPhone by remember { mutableStateOf(Firebase.auth.currentUser?.phoneNumber ?: "") }

    // Fetch phone from Firestore if Auth doesn't have it (some login flows)
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

    // Last 4 digits of phone number used as a display User ID on cards
    val userPhoneLastFour = remember(userPhone) {
        if (userPhone.length >= 4) userPhone.takeLast(4) else userPhone
    }

    LaunchedEffect(showAdOnLoad) {
        if (showAdOnLoad && !hasShownAd) {
            kotlinx.coroutines.delay(300)
            val activity = context.findActivity()
            if (activity != null) {
//                SharedInterstitialAdManager.showAd(activity) {
//                    android.util.Log.d("OrdersScreen", "Ad flow completed")
//                }
//                hasShownAd = true
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId != null) {
            Firebase.firestore.collection("orders")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    snapshot?.let {
                        orders = it.documents.mapNotNull { doc ->
                            val itemsData = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                            val orderItems = itemsData.map { itemMap ->
                                OrderItem(
                                    name = itemMap["itemName"] as? String ?: itemMap["name"] as? String ?: "Unknown Item",
                                    quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0,
                                    price = (itemMap["itemPrice"] as? Number)?.toDouble() ?: (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                                    miniResName = itemMap["miniResName"] as? String ?: ""
                                )
                            }

                            Order(
                                id = doc.id,
                                restaurantName = doc.getString("restaurantName") ?: "Unknown Restaurant",
                                totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                                orderStatus = doc.getString("orderStatus") ?: "Unknown",
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                                items = orderItems,
                                deliveryCharge = doc.getDouble("deliveryCharge") ?: 0.0,
                                serviceCharge = doc.getDouble("serviceCharge") ?: 0.0
                            )
                        }.sortedByDescending { it.createdAt }
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.height(80.dp),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                shadowElevation = 8.dp,
                color = DarkPink
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "My Orders",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkPink)
            }
        } else if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "No orders",
                        modifier = Modifier.size(64.dp),
                        tint = LightGray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You haven't placed any orders yet.",
                        color = LightGray,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8F9FA)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(orders) { order ->
                    ModernOrderCard(
                        order = order,
                        userPhoneLastFour = userPhoneLastFour,
                        onClick = { selectedOrder = order }
                    )
                }
            }
        }
    }

    selectedOrder?.let { order ->
        EnhancedOrderDetailsDialog(
            order = order,
            userPhoneLastFour = userPhoneLastFour,
            onDismiss = { selectedOrder = null },
            onCancelOrder = {
                // Redirect to WhatsApp helpline for order cancellation
                val whatsappNumber = "8801746324620"
                val message = "Hello, I would like to cancel my order. My phone number is $userPhone."
                val uri = Uri.parse("https://wa.me/$whatsappNumber?text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
                selectedOrder = null
            }
        )
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun ModernOrderCard(
    order: Order,
    userPhoneLastFour: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Restaurant name and status badge in one line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Restaurant icon
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = getStatusColor(order.orderStatus),
                        modifier = Modifier.size(20.dp)
                    )

                    // Restaurant name
                    Text(
                        text = order.restaurantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Compact status badge
                StatusBadge(status = order.orderStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // User ID row (last 4 digits of phone, shown for reference)
            if (userPhoneLastFour.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "User ID: *$userPhoneLastFour",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact info row: items, date, time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Items count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (order.items.size == 1) "1 item" else "${order.items.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                }

                // Separator dot
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF666666))
                )

                // Date
                Text(
                    text = formatDateShort(order.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )

                // Separator dot
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF666666))
                )

                // Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatTime(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Price and view button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Total:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF262424)
                    )
                    Text(
                        text = "৳${order.totalPrice}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }

                // Compact view button
                TextButton(
                    onClick = onClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = getStatusColor(order.orderStatus)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "View Details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val backgroundColor = when (status.lowercase()) {
        "pending" -> Color(0xFFFFF4E6)
        "confirmed" -> Color(0xFFE3F2FD)
        "preparing" -> Color(0xFFE8F5E9)
        "delivered" -> Color(0xFFE8F5E9)
        "cancelled" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF5F5F5)
    }

    val textColor = when (status.lowercase()) {
        "pending" -> Color(0xFFFF9800)
        "confirmed" -> Color(0xFF2196F3)
        "preparing" -> Color(0xFF4CAF50)
        "delivered" -> Color(0xFF4CAF50)
        "cancelled" -> Color(0xFFF44336)
        "accepted" -> Color(0xFFE91E1E)
        else -> Color(0xFF757575)
    }

    val icon = when (status.lowercase()) {
        "pending" -> Icons.Default.Schedule
        "confirmed" -> Icons.Default.CheckCircle
        "preparing" -> Icons.Default.Restaurant
        "delivered" -> Icons.Default.Done
        "cancelled" -> Icons.Default.Close
        "accepted" -> Icons.Default.DoneOutline
        else -> Icons.Default.Info
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun getStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "pending" -> Color(0xFFFF9800)
        "confirmed" -> Color(0xFF2196F3)
        "preparing" -> Color(0xFF4CAF50)
        "delivered" -> Color(0xFF4CAF50)
        "cancelled" -> Color(0xFFF44336)
        "accepted" -> Color(0xFFE91E1E)
        else -> Color(0xFF757575)
    }
}

fun formatDateShort(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

fun formatTime(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

@Composable
fun EnhancedOrderDetailsDialog(
    order: Order,
    userPhoneLastFour: String,
    onDismiss: () -> Unit,
    onCancelOrder: () -> Unit
) {
    // State to control the confirmation dialog
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // Calculate cancellability: Order is < 5 minutes old AND status is "Pending"
    val isCancellable by remember(order) {
        derivedStateOf {
            val currentTime = Timestamp.now().seconds
            val orderTime = order.createdAt.seconds
            val diffInSeconds = currentTime - orderTime
            // 300 seconds = 5 minutes
            diffInSeconds < 300 && order.orderStatus == "Pending"
        }
    }

    // Alert Dialog for confirmation
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancel Order?", fontWeight = FontWeight.Bold) },
            text = { Text("You'll be redirected to our WhatsApp helpline to complete your cancellation request.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancelOrder()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Go to WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("No, Keep Order")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Order Summary",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = Color(0xFF333333)
                )

                Text(
                    order.restaurantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    "Ordered on ${formatDate(order.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (userPhoneLastFour.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "User ID: *$userPhoneLastFour",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF999999)
                        )
                    }
                }
                Text(
                    "Status: ${order.orderStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkPink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Items:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = Color(0xFF333333)
                )

                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity} x ${item.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF333333)
                            )
                            if(item.miniResName.isNotEmpty()) {
                                Text(
                                    text = "from ${item.miniResName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        Text(
                            "৳${item.price * item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(16.dp))

                val itemsSubtotal = order.items.sumOf { it.price * it.quantity }

                PriceDetailRow("Items Subtotal", itemsSubtotal)
                if (order.deliveryCharge > 0) {
                    PriceDetailRow("Delivery Charge", order.deliveryCharge)
                }
                if (order.serviceCharge > 0) {
                    PriceDetailRow("Service Charge/Tax", order.serviceCharge)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(8.dp))

                PriceDetailRow(
                    "Total Payable",
                    order.totalPrice,
                    isTotal = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // SHOW CANCEL BUTTON IF ELIGIBLE
                if (isCancellable) {
                    OutlinedButton(
                        onClick = { showCancelConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Red
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Order", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPink,
                        contentColor = Color.White
                    )
                ) {
                    Text("Close", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PriceDetailRow(
    label: String,
    amount: Double,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) DarkPink else Color(0xFF666666)
        )
        Text(
            "৳$amount",
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) DarkPink else Color(0xFF333333)
        )
    }
}

fun formatDate(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("dd MMM, yyyy 'at' hh:mm a", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}