package com.yumzy.userapp.features.stores

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.R
import com.yumzy.userapp.YLogoLoadingIndicator
import com.yumzy.userapp.features.cart.CartViewModel
import com.yumzy.userapp.ui.theme.BrandPink
import com.yumzy.userapp.ui.theme.DarkPink
import com.yumzy.userapp.ui.theme.DeepPink
import kotlinx.coroutines.tasks.await

// Data class for item variants
data class ItemVariant(
    val name: String = "",
    val price: Double = 0.0
)

// Sort order enum
enum class SortOrder {
    NONE,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW
}

// Updated StoreItem data class with multi-variant support
data class StoreItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val itemDescription: String = "",
    val additionalDeliveryCharge: Double = 0.0,
    val additionalServiceCharge: Double = 0.0,
    val isShopOpen: Boolean = true,
    val stock: String = "yes",
    val multiVariant: Int = 0,
    val variants: List<ItemVariant> = emptyList(),
    val miniResId: String = "",
    val miniResName: String = "",
    val priority: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreItemGridScreen(
    title: String,
    subCategoryName: String?,
    miniResId: String?,
    onBackClicked: () -> Unit,
    cartViewModel: CartViewModel = viewModel(),
    onPlaceOrder: (restaurantId: String) -> Unit,
    onViewCartClick: () -> Unit
) {
    var allItems by remember { mutableStateOf<List<StoreItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedItem by remember { mutableStateOf<StoreItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf<SortOrder>(SortOrder.NONE) }

    val cartSelection by cartViewModel.currentSelection.collectAsState()
    val savedCart by cartViewModel.savedCart.collectAsState()

    val context = LocalContext.current

    val currentSelectionCount = cartSelection.values.sumOf { it.quantity }
    val savedCartCount = savedCart.values.sumOf { it.quantity }
    val totalGlobalCartCount = currentSelectionCount + savedCartCount

    // Filter and sort items based on search query and sort order
    val filteredItems by remember(searchQuery, allItems, sortOrder) {
        derivedStateOf {
            val filtered = if (searchQuery.isBlank()) {
                allItems
            } else {
                allItems.filter { item ->
                    item.name.contains(searchQuery, ignoreCase = true) ||
                            item.itemDescription.contains(searchQuery, ignoreCase = true)
                }
            }

            // Apply sorting
            when (sortOrder) {
                SortOrder.PRICE_LOW_TO_HIGH -> filtered.sortedBy { item ->
                    if (item.multiVariant >= 2 && item.variants.isNotEmpty()) {
                        item.variants.minOf { it.price }
                    } else {
                        item.price
                    }
                }
                SortOrder.PRICE_HIGH_TO_LOW -> filtered.sortedByDescending { item ->
                    if (item.multiVariant >= 2 && item.variants.isNotEmpty()) {
                        item.variants.maxOf { it.price }
                    } else {
                        item.price
                    }
                }
                SortOrder.NONE -> filtered
            }
        }
    }

    LaunchedEffect(key1 = subCategoryName, key2 = miniResId) {
        isLoading = true
        val db = Firebase.firestore
        try {
            if (!miniResId.isNullOrBlank()) {
                val miniResDoc = db.collection("mini_restaurants").document(miniResId).get().await()
                val isRestaurantOpen = miniResDoc.getString("open")?.equals("yes", ignoreCase = true) ?: false
                val miniResName = miniResDoc.getString("name") ?: "Unknown Restaurant"

                val snapshot = db.collection("store_items").whereEqualTo("miniRes", miniResId).get().await()

                val fetchedItems = snapshot.documents.mapNotNull { doc ->
                    val multiVariant = doc.getLong("multiVariant")?.toInt() ?: 0
                    val variants = if (multiVariant >= 2) {
                        (1..multiVariant).mapNotNull { index ->
                            val variantName = doc.getString("variant${index}name")
                            val variantPrice = doc.getDouble("variant${index}price")
                            if (variantName != null && variantPrice != null) {
                                ItemVariant(variantName, variantPrice)
                            } else null
                        }
                    } else emptyList()

                    StoreItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        imageUrl = doc.getString("imageUrl") ?: "",
                        itemDescription = doc.getString("itemDescription") ?: "No description available.",
                        additionalDeliveryCharge = doc.getDouble("additionalDeliveryCharge") ?: 0.0,
                        additionalServiceCharge = doc.getDouble("additionalServiceCharge") ?: 0.0,
                        isShopOpen = isRestaurantOpen,
                        stock = doc.getString("stock") ?: "yes",
                        multiVariant = multiVariant,
                        variants = variants,
                        miniResId = miniResId,
                        miniResName = miniResName,
                        priority = doc.getLong("priority")?.toInt()
                    )
                }

                allItems = fetchedItems.sortedWith(
                    compareBy<StoreItem> { it.priority ?: Int.MAX_VALUE }
                        .thenBy { it.name }
                )
            } else if (!subCategoryName.isNullOrBlank()) {
                val itemsSnapshot = db.collection("store_items").whereEqualTo("subCategory", subCategoryName).get().await()
                val itemsWithMiniResIds = itemsSnapshot.documents.map { it to it.getString("miniRes") }

                val miniResIds = itemsWithMiniResIds.mapNotNull { it.second }.distinct()
                val statusMap = if (miniResIds.isNotEmpty()) {
                    db.collection("mini_restaurants").whereIn(com.google.firebase.firestore.FieldPath.documentId(), miniResIds).get().await()
                        .documents.associate { it.id to (it.getString("open")?.equals("yes", true) ?: false) }
                } else {
                    emptyMap()
                }

                val nameMap = if (miniResIds.isNotEmpty()) {
                    db.collection("mini_restaurants").whereIn(com.google.firebase.firestore.FieldPath.documentId(), miniResIds).get().await()
                        .documents.associate { it.id to (it.getString("name") ?: "Unknown Restaurant") }
                } else {
                    emptyMap()
                }

                val fetchedItems = itemsWithMiniResIds.map { (doc, resId) ->
                    val isOpen = resId?.let { statusMap[it] } ?: true
                    val resName = resId?.let { nameMap[it] } ?: "Unknown Restaurant"
                    val multiVariant = doc.getLong("multiVariant")?.toInt() ?: 0
                    val variants = if (multiVariant >= 2) {
                        (1..multiVariant).mapNotNull { index ->
                            val variantName = doc.getString("variant${index}name")
                            val variantPrice = doc.getDouble("variant${index}price")
                            if (variantName != null && variantPrice != null) {
                                ItemVariant(variantName, variantPrice)
                            } else null
                        }
                    } else emptyList()

                    StoreItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        imageUrl = doc.getString("imageUrl") ?: "",
                        itemDescription = doc.getString("itemDescription") ?: "No description available.",
                        additionalDeliveryCharge = doc.getDouble("additionalDeliveryCharge") ?: 0.0,
                        additionalServiceCharge = doc.getDouble("additionalServiceCharge") ?: 0.0,
                        isShopOpen = isOpen,
                        stock = doc.getString("stock") ?: "yes",
                        multiVariant = multiVariant,
                        variants = variants,
                        miniResId = resId ?: "",
                        miniResName = resName,
                        priority = doc.getLong("priority")?.toInt()
                    )
                }

                allItems = fetchedItems.sortedWith(
                    compareBy<StoreItem> { it.priority ?: Int.MAX_VALUE }
                        .thenBy { it.name }
                )
            } else {
                allItems = emptyList()
            }
        } catch (e: Exception) {
            allItems = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            ModernSearchTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onBackClicked = onBackClicked,
                sortOrder = sortOrder,
                onSortOrderChange = { sortOrder = it }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = cartSelection.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomBarWithTwoButtons(
                    onAddToCartClick = {
                        cartViewModel.saveSelectionToCart()
                        Toast.makeText(context, "Items added to cart!", Toast.LENGTH_SHORT).show()
                    },
                    onPlaceOrderClick = {
                        cartViewModel.saveSelectionToCart()
                        onPlaceOrder("yumzy_store")
                    },
                    totalItems = currentSelectionCount
                )
            }
        },
        // --- NEW: Floating Action Button for Cart ---
        floatingActionButton = {
            if (totalGlobalCartCount > 0) {
                FloatingActionButton(
                    onClick = {
                        cartViewModel.saveSelectionToCart()
                        onViewCartClick()
                    },
                    containerColor = DarkPink,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    modifier = Modifier.padding(bottom = if (cartSelection.isNotEmpty()) 16.dp else 0.dp) // Lift up if bottom bar visible? Scaffold handles this automatically usually, but padding ensures safety
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "View Cart",
                            modifier = Modifier.padding(12.dp).size(24.dp)
                        )
                        // Badge logic
                        if (totalGlobalCartCount > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 6.dp, y = (-2).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp)
                                    .background(Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (totalGlobalCartCount > 99) "99+" else totalGlobalCartCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                YLogoLoadingIndicator(size = 35.dp, color = DeepPink)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    BannerAd()
                    if (filteredItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isBlank()) Icons.Default.ShoppingBag else Icons.Default.SearchOff,
                                    contentDescription = "Empty",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Gray.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) "No items found." else "No items match your search.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredItems) { item ->
                                StoreItemCard(
                                    item = item,
                                    cartViewModel = cartViewModel,
                                    cartSelection = cartSelection,
                                    onClick = { selectedItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedItem?.let { item ->
            if (item.multiVariant >= 2) {
                MultiVariantDialog(
                    item = item,
                    cartViewModel = cartViewModel,
                    cartSelection = cartSelection,
                    onDismiss = { selectedItem = null }
                )
            } else {
                val itemQuantity = cartSelection[item.id]?.quantity ?: 0
                StoreItemDetailDialog(
                    item = item,
                    quantity = itemQuantity,
                    cartViewModel = cartViewModel,
                    onDismiss = { selectedItem = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSearchTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClicked: () -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (!isSearchActive) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            },
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
            },
            actions = {
                // Sort button
                IconButton(
                    onClick = {
                        onSortOrderChange(
                            when (sortOrder) {
                                SortOrder.NONE -> SortOrder.PRICE_LOW_TO_HIGH
                                SortOrder.PRICE_LOW_TO_HIGH -> SortOrder.PRICE_HIGH_TO_LOW
                                SortOrder.PRICE_HIGH_TO_LOW -> SortOrder.NONE
                            }
                        )
                    }
                ) {
                    Icon(
                        imageVector = when (sortOrder) {
                            SortOrder.NONE -> Icons.Default.SwapVert
                            SortOrder.PRICE_LOW_TO_HIGH -> Icons.Default.ArrowUpward
                            SortOrder.PRICE_HIGH_TO_LOW -> Icons.Default.ArrowDownward
                        },
                        contentDescription = when (sortOrder) {
                            SortOrder.NONE -> "Sort by price"
                            SortOrder.PRICE_LOW_TO_HIGH -> "Low to High"
                            SortOrder.PRICE_HIGH_TO_LOW -> "High to Low"
                        },
                        tint = if (sortOrder != SortOrder.NONE) BrandPink else Color.Black
                    )
                }

                // Search button
                IconButton(
                    onClick = { isSearchActive = true }
                ) {
                    Icon(
                        Icons.Default.ManageSearch,
                        contentDescription = "Search",
                        tint = Color.Black
                    )
                }
            }
        )
    } else {
        // Search active mode
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBackClicked,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.05f))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(22.dp),
                                clip = true
                            )
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) BrandPink else Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isFocused) BrandPink else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused },
                                textStyle = TextStyle(
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(BrandPink),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() }
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search items...",
                                                color = Color.Gray.copy(alpha = 0.6f),
                                                fontSize = 15.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            isSearchActive = false
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            color = BrandPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ... MultiVariantDialog, StoreItemDetailDialog, StoreItemCard, ModernQuantitySelector, BottomBarWithTwoButtons, BannerAd ...
// (These remain exactly the same as before; assume they are present in the final file)
@Composable
fun MultiVariantDialog(
    item: StoreItem,
    cartViewModel: CartViewModel,
    cartSelection: Map<String, com.yumzy.userapp.features.cart.CartItem>,
    onDismiss: () -> Unit
) {
    val hasSelection = item.variants.any { variant ->
        val variantId = "${item.id}_${variant.name}"
        (cartSelection[variantId]?.quantity ?: 0) > 0
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .background(
                                color = if (hasSelection) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .zIndex(1f)
                    ) {
                        Icon(
                            imageVector = if (hasSelection) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (hasSelection) "Done" else "Close",
                            tint = if (hasSelection) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (item.miniResName.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = "Restaurant",
                                tint = BrandPink,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.miniResName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandPink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = item.itemDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!item.isShopOpen) {
                        Text(
                            text = "This item is currently unavailable as the shop is closed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (item.stock != "yes") {
                        Text(
                            text = "This item is currently out of stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Choose Variant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    item.variants.forEach { variant ->
                        val variantId = "${item.id}_${variant.name}"
                        val variantQuantity = cartSelection[variantId]?.quantity ?: 0
                        val genericMenuItem = com.yumzy.userapp.features.home.MenuItem(
                            id = variantId,
                            name = "${item.name} (${variant.name})",
                            price = variant.price,
                            category = "Store Item"
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = variant.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "৳${String.format("%.0f", variant.price)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPink
                                    )
                                }
                                ModernQuantitySelector(
                                    quantity = variantQuantity,
                                    onAdd = { cartViewModel.addToSelection(genericMenuItem, "yumzy_store", item.miniResName) },
                                    onIncrement = { cartViewModel.incrementSelection(genericMenuItem) },
                                    onDecrement = { cartViewModel.decrementSelection(genericMenuItem) },
                                    enabled = item.isShopOpen && item.stock == "yes"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreItemDetailDialog(
    item: StoreItem,
    quantity: Int,
    cartViewModel: CartViewModel,
    onDismiss: () -> Unit
) {
    val genericMenuItem = com.yumzy.userapp.features.home.MenuItem(
        id = item.id,
        name = item.name,
        price = item.price,
        category = "Store Item"
    )

    val hasSelection = quantity > 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .background(
                                color = if (hasSelection) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .zIndex(1f)
                    ) {
                        Icon(
                            imageVector = if (hasSelection) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (hasSelection) "Done" else "Close",
                            tint = if (hasSelection) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (item.miniResName.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = "Restaurant",
                                tint = BrandPink,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.miniResName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandPink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = item.itemDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!item.isShopOpen) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "This item is currently unavailable as the shop is closed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                    } else if (item.stock != "yes") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "This item is currently out of stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Price",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "৳${String.format("%.0f", item.price)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = BrandPink
                            )
                        }
                        ModernQuantitySelector(
                            quantity = quantity,
                            onAdd = { cartViewModel.addToSelection(genericMenuItem, "yumzy_store", item.miniResName) },
                            onIncrement = { cartViewModel.incrementSelection(genericMenuItem) },
                            onDecrement = { cartViewModel.decrementSelection(genericMenuItem) },
                            enabled = item.isShopOpen && item.stock == "yes"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreItemCard(
    item: StoreItem,
    cartViewModel: CartViewModel,
    cartSelection: Map<String, com.yumzy.userapp.features.cart.CartItem>,
    onClick: () -> Unit
) {
    val isItemAvailable = item.isShopOpen && item.stock == "yes"
    val hasMultiVariant = item.multiVariant >= 2

    val totalQuantity = if (hasMultiVariant) {
        item.variants.sumOf { variant ->
            val variantId = "${item.id}_${variant.name}"
            cartSelection[variantId]?.quantity ?: 0
        }
    } else {
        cartSelection[item.id]?.quantity ?: 0
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(enabled = isItemAvailable, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.img),
                        error = painterResource(id = R.drawable.img)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.1f)
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                    if (totalQuantity > 0) {
                        Surface(
                            modifier = Modifier
                                .padding(12.dp)
                                .align(Alignment.TopEnd),
                            shape = CircleShape,
                            color = BrandPink.copy(alpha = 0.6f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "$totalQuantity",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (!isItemAvailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (!item.isShopOpen) Icons.Default.Lock else Icons.Default.Block,
                                    contentDescription = if (!item.isShopOpen) "Shop Closed" else "Out of Stock",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (!item.isShopOpen) "SHOP CLOSED" else "OUT OF STOCK",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Price",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            if (hasMultiVariant && item.variants.isNotEmpty()) {
                                Text(
                                    text = "৳${String.format("%.0f", item.variants.minOf { it.price })} - ৳${String.format("%.0f", item.variants.maxOf { it.price })}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPink,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "৳${String.format("%.0f", item.price)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPink,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (hasMultiVariant) {
                            if (totalQuantity == 0) {
                                FilledTonalButton(
                                    onClick = onClick,
                                    enabled = isItemAvailable,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = BrandPink.copy(alpha = 0.1f),
                                        contentColor = BrandPink
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Choose variant",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    onClick = onClick,
                                    enabled = isItemAvailable,
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = "$totalQuantity",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit variants",
                                            modifier = Modifier.size(16.dp),
                                            tint = BrandPink
                                        )
                                    }
                                }
                            }
                        } else {
                            val genericMenuItem = com.yumzy.userapp.features.home.MenuItem(
                                id = item.id,
                                name = item.name,
                                price = item.price,
                                category = "Store Item"
                            )
                            ModernQuantitySelector(
                                quantity = totalQuantity,
                                onAdd = { cartViewModel.addToSelection(genericMenuItem, "yumzy_store", item.miniResName) },
                                onIncrement = { cartViewModel.incrementSelection(genericMenuItem) },
                                onDecrement = { cartViewModel.decrementSelection(genericMenuItem) },
                                enabled = isItemAvailable
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernQuantitySelector(
    quantity: Int,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabled: Boolean = true
) {
    if (quantity == 0) {
        FilledTonalButton(
            onClick = onAdd,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = BrandPink.copy(alpha = 0.1f),
                contentColor = BrandPink
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add to cart",
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                FilledIconButton(
                    onClick = onDecrement,
                    enabled = enabled,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = BrandPink
                    )
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease quantity",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "$quantity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                FilledIconButton(
                    onClick = onIncrement,
                    enabled = enabled,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = BrandPink,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase quantity",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomBarWithTwoButtons(
    onAddToCartClick: () -> Unit,
    onPlaceOrderClick: () -> Unit,
    totalItems: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
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
                    contentColor = BrandPink
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