package com.yumzy.userapp.features.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.features.cart.CartViewModel
import com.yumzy.userapp.ui.theme.cardColors
import com.yumzy.userapp.ui.theme.DarkPink
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Data classes defined locally
data class PreOrderCategory(
    val id: String = "",
    val name: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val deliveryTime: String = "",
    val open: Boolean = false
)
data class MenuItem(val id: String = "", val name: String = "", val price: Double = 0.0, val category: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMenuScreen(
    restaurantId: String,
    restaurantName: String,
    cartViewModel: CartViewModel = viewModel(),
    onCategoryClick: (restaurantId: String, restaurantName: String, categoryName: String) -> Unit,
    onBackClicked: () -> Unit,
    onPlaceOrder: (restaurantId: String) -> Unit
) {
    var preOrderCategories by remember { mutableStateOf<List<PreOrderCategory>>(emptyList()) }
    var currentMenuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isInstantDeliveryAvailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cartSelection by cartViewModel.currentSelection.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Calculate total items in cart
    val totalItems = cartSelection.values.sumOf { it.quantity }

    LaunchedEffect(key1 = restaurantId) {
        val db = Firebase.firestore
        val restaurantRef = db.collection("restaurants").document(restaurantId)

        // Updated to fetch the "open" field
        restaurantRef.collection("preOrderCategories")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    preOrderCategories = it.documents.mapNotNull { doc ->
                        PreOrderCategory(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            startTime = doc.getString("startTime") ?: "",
                            endTime = doc.getString("endTime") ?: "",
                            deliveryTime = doc.getString("deliveryTime") ?: "",
                            open = doc.getBoolean("open") ?: false
                        )
                    }
                }
            }

        restaurantRef.collection("menuItems").whereEqualTo("category", "Current Menu")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    currentMenuItems = it.documents.mapNotNull { doc -> doc.toObject(MenuItem::class.java)?.copy(id = doc.id) }
                }
                isLoading = false
            }

        db.collection("riders")
            .whereEqualTo("isAvailable", true)
            .whereArrayContains("serviceableLocations", "Daffodil Smart City")
            .addSnapshotListener { snapshot, _ ->
                isInstantDeliveryAvailable = snapshot?.isEmpty == false
            }
    }

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(restaurantName , fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)) },

                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.05f))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black,
                                modifier = Modifier.align(Alignment.Center).size(22.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = cartSelection.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ModernBottomBarWithButtons(
                    onAddToCartClick = {
                        cartViewModel.saveSelectionToCart()
                        Toast.makeText(context, "Items added to cart!", Toast.LENGTH_SHORT).show()
                    },
                    onPlaceOrderClick = {
                        cartViewModel.saveSelectionToCart()
                        onPlaceOrder(restaurantId)
                    },
                    totalItems = totalItems
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                BannerAd()

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Pre-Order Category", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BlinkingText(
                                    text = "Available Now in Hotel 🔥",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTabIndex) {
                        0 -> PreOrderContent(
                            preOrderCategories = preOrderCategories,
                            onCategoryClick = { category ->
                                if (category.open) {
                                    onCategoryClick(restaurantId, restaurantName, "Pre-order ${category.name}")
                                } else {
                                    Toast.makeText(
                                        context,
                                        "😔 You can't order now. This category is currently closed.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        1 -> CurrentMenuContent(
                            currentMenuItems = currentMenuItems,
                            isInstantDeliveryAvailable = isInstantDeliveryAvailable,
                            cartViewModel = cartViewModel,
                            restaurantId = restaurantId,
                            restaurantName = restaurantName
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlinkingText(
    text: String,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Text(
        text = text,
        fontWeight = fontWeight,
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
private fun PreOrderContent(
    preOrderCategories: List<PreOrderCategory>,
    onCategoryClick: (PreOrderCategory) -> Unit
) {
    // Define premium gradient colors for each category
    val categoryColors = listOf(
        Color(0xFFFF6B9D), // Pink
        Color(0xFF4ECDC4), // Teal
        Color(0xFFFFA07A), // Light Salmon
        Color(0xFF9B59B6), // Purple
        Color(0xFF3498DB), // Blue
        Color(0xFFE74C3C), // Red
        Color(0xFFF39C12), // Orange
        Color(0xFF16A085)  // Green
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (preOrderCategories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pre-order categories available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            itemsIndexed(preOrderCategories) { index, category ->
                val color = categoryColors[index % categoryColors.size]
                PreOrderHeader(
                    category = category,
                    cardColor = color,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
private fun CurrentMenuContent(
    currentMenuItems: List<MenuItem>,
    isInstantDeliveryAvailable: Boolean,
    cartViewModel: CartViewModel,
    restaurantId: String,
    restaurantName: String
) {
    val cartSelection by cartViewModel.currentSelection.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                AvailabilityChip(isAvailable = isInstantDeliveryAvailable)
            }
        }

        if (currentMenuItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No menu items available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            itemsIndexed(currentMenuItems) { index, menuItem ->
                val color = cardColors[index % cardColors.size]
                MenuItemRow(
                    menuItem = menuItem,
                    cardColor = color,
                    quantity = cartSelection[menuItem.id]?.quantity ?: 0,
                    isEnabled = isInstantDeliveryAvailable,
                    onAddClick = { cartViewModel.addToSelection(menuItem, restaurantId, restaurantName) },
                    onIncrement = { cartViewModel.incrementSelection(menuItem) },
                    onDecrement = { cartViewModel.decrementSelection(menuItem) }
                )
            }
        }
    }
}

@Composable
private fun PreOrderHeader(category: PreOrderCategory, cardColor: Color, onClick: () -> Unit) {
    val isOpen = category.open

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) Color.White else Color.White.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOpen) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient background accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                cardColor.copy(alpha = if (isOpen) 0.12f else 0.06f),
                                cardColor.copy(alpha = if (isOpen) 0.03f else 0.01f)
                            )
                        )
                    )
            )

            // Food watermark background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = cardColor.copy(alpha = if (isOpen) 0.08f else 0.04f),
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 40.dp, y = 10.dp)
                )
            }

            // Closed overlay
            if (!isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color.LightGray.copy(alpha = 0.1f))
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Main content area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side - Category info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category name with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isOpen) cardColor else Color.Gray,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isOpen) Icons.Default.Restaurant else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOpen) Color(0xFF1A1A1A) else Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!isOpen) {
                                    Text(
                                        text = "Currently Closed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Red,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Order timing
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isOpen) Color(0xFF666666) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Order: ${category.startTime} - ${category.endTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOpen) Color(0xFF666666) else Color.Gray,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Right side - Arrow button
                    Surface(
                        shape = CircleShape,
                        color = if (isOpen) cardColor else Color.Gray,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOpen) Icons.Default.ArrowForward else Icons.Default.Lock,
                                contentDescription = if (isOpen) "View menu" else "Locked",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Footer section with delivery info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOpen) cardColor.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeliveryDining,
                            contentDescription = null,
                            tint = if (isOpen) cardColor else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Delivery: ${category.deliveryTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOpen) cardColor else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItemRow(
    menuItem: MenuItem,
    cardColor: Color,
    quantity: Int,
    onAddClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    isEnabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    menuItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tk ${menuItem.price}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF46954A),
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                QuantitySelector(
                    quantity = quantity,
                    onAdd = onAddClick,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    isEnabled = isEnabled
                )
            }
        }
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    isEnabled: Boolean
) {
    if (quantity == 0) {
        Button(
            onClick = onAdd,
            enabled = isEnabled,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add to cart")
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = isEnabled,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrement quantity")
            }
            Text("$quantity", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onIncrement,
                enabled = isEnabled,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increment quantity")
            }
        }
    }
}

@Composable
fun ModernBottomBarWithButtons(
    onAddToCartClick: () -> Unit,
    onPlaceOrderClick: () -> Unit,
    totalItems: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onAddToCartClick,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.AddShoppingCart,
                    contentDescription = "Add to Cart",
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = onPlaceOrderClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkPink,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Place Order",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Place Order ($totalItems)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailabilityChip(isAvailable: Boolean) {
    val backgroundColor = if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFFBE9E7)
    val textColor = if (isAvailable) Color(0xFF2E7D32) else Color(0xFFC41150)
    val text = if (isAvailable) "Available" else "Instant Delivery Unavailable"
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-1527833190869655/8094999825"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}