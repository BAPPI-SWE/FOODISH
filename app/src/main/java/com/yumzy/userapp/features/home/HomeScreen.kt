package com.yumzy.userapp.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.R
import com.yumzy.userapp.YLogoLoadingIndicator
import com.yumzy.userapp.components.WhatsAppSupportButton
import com.yumzy.userapp.components.isScrollingUp
import com.yumzy.userapp.ui.theme.BrandPink
import com.yumzy.userapp.ui.theme.DeepPink
import com.yumzy.userapp.ui.theme.DarkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

// --- Data classes for Screen ---
data class Offer(
    val imageUrl: String = "",
    val availableLocations: List<String> = emptyList()
)

enum class RestaurantType {
    MAIN, // Old large restaurants
    MINI  // New mini shops/restaurants
}

// UPDATED: Restaurant data class to handle both types and priority
data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val imageUrl: String?,
    val open: String = "yes",
    val type: RestaurantType, // To distinguish between Main and Mini
    val priority: Int? = null // For sorting
)

data class Category(val name: String, val icon: ImageVector, val id: String)
data class UserProfile(val baseLocation: String = "", val subLocation: String = "")
data class SubCategorySearchResult(val name: String, val itemCount: Int, val imageUrl: String = "")

// MiniRestaurant data class for search (kept for search consistency)
data class MiniRestaurant(
    val id: String,
    val name: String,
    val imageUrl: String,
    val open: String // "yes" or "no"
)

sealed class SearchResult {
    data class RestaurantResult(val restaurant: Restaurant) : SearchResult()
    data class SubCategoryResult(val subCategory: SubCategorySearchResult) : SearchResult()
    data class MiniRestaurantResult(val miniRestaurant: MiniRestaurant) : SearchResult()
}

// Love bubble data class
data class LoveBubble(
    val id: Int,
    val startX: Float,
    val scale: Float,
    val duration: Int,
    val delay: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRestaurantClick: (restaurantId: String, restaurantName: String, type: RestaurantType) -> Unit,
    onStoreCategoryClick: (categoryId: String, categoryName: String) -> Unit,
    onSubCategorySearchClick: (subCategoryName: String) -> Unit,
    onMiniRestaurantClick: (miniResId: String, miniResName: String) -> Unit,
    onNotificationClick: () -> Unit
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var combinedRestaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var allSubCategories by remember { mutableStateOf<List<SubCategorySearchResult>>(emptyList()) }
    var miniRestaurantsSearch by remember { mutableStateOf<List<MiniRestaurant>>(emptyList()) }
    var offers by remember { mutableStateOf<List<Offer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showLoveBubbles by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    // Detect scroll direction for WhatsApp button visibility
    val isScrollingUp = lazyListState.isScrollingUp()

    val searchResults by remember(searchQuery, combinedRestaurants, allSubCategories, miniRestaurantsSearch) {
        derivedStateOf {
            if (searchQuery.isNotBlank()) {
                val restaurantResults = combinedRestaurants.filter { restaurant ->
                    restaurant.name.contains(searchQuery, ignoreCase = true) ||
                            restaurant.cuisine.contains(searchQuery, ignoreCase = true)
                }.map { SearchResult.RestaurantResult(it) }

                val subCategoryResults = allSubCategories.filter { subCategory ->
                    subCategory.name.contains(searchQuery, ignoreCase = true)
                }.map { SearchResult.SubCategoryResult(it) }

                val miniRestaurantResults = miniRestaurantsSearch.filter { miniRestaurant ->
                    miniRestaurant.name.contains(searchQuery, ignoreCase = true)
                }.map { SearchResult.MiniRestaurantResult(it) }

                (restaurantResults + subCategoryResults + miniRestaurantResults).distinctBy {
                    when(it) {
                        is SearchResult.RestaurantResult -> "RES_${it.restaurant.id}"
                        is SearchResult.SubCategoryResult -> "SUB_${it.subCategory.name}"
                        is SearchResult.MiniRestaurantResult -> "MINI_${it.miniRestaurant.id}"
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    val isScrolled by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(key1 = Unit) {
        val db = Firebase.firestore
        val currentUser = Firebase.auth.currentUser

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    userProfile = if (document != null && document.exists()) {
                        UserProfile(
                            baseLocation = document.getString("baseLocation") ?: "Campus",
                            subLocation = document.getString("subLocation") ?: ""
                        )
                    } else {
                        UserProfile(subLocation = "")
                    }
                }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(key1 = userProfile) {
        if (userProfile != null && userProfile!!.subLocation.isNotBlank()) {
            isLoading = true
            val db = Firebase.firestore
            val userLocation = userProfile!!.subLocation

            // Fetch Offers
            db.collection("offers")
                .whereArrayContains("availableLocations", userLocation)
                .get()
                .addOnSuccessListener { snapshot ->
                    offers = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Offer::class.java)
                    }
                }

            // Fetch SubCategories for search
            db.collection("store_sub_categories")
                .whereArrayContains("availableLocations", userLocation)
                .get()
                .addOnSuccessListener { subCatSnapshot ->
                    val fetchedSubCats = subCatSnapshot.documents.mapNotNull { doc ->
                        SubCategorySearchResult(
                            name = doc.getString("name") ?: "",
                            itemCount = 0,
                            imageUrl = doc.getString("imageUrl") ?: ""
                        )
                    }

                    if (fetchedSubCats.isNotEmpty()) {
                        db.collection("store_items")
                            .whereIn("subCategory", fetchedSubCats.map { it.name })
                            .get()
                            .addOnSuccessListener { itemsSnapshot ->
                                val itemCounts = itemsSnapshot.documents
                                    .mapNotNull { it.getString("subCategory") }
                                    .groupingBy { it }
                                    .eachCount()

                                allSubCategories = fetchedSubCats.map { subCat ->
                                    subCat.copy(itemCount = itemCounts[subCat.name] ?: 0)
                                }
                            }
                    }
                }

            // Fetch mini restaurants for SEARCH purpose
            db.collection("mini_restaurants")
                .whereArrayContains("availableLocations", userLocation)
                .get()
                .addOnSuccessListener { restaurantSnapshot ->
                    miniRestaurantsSearch = restaurantSnapshot.documents.mapNotNull { doc ->
                        MiniRestaurant(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            open = doc.getString("open") ?: "no"
                        )
                    }
                }

            try {
                // 1. Fetch Main Restaurants (Old)
                val mainResSnapshot = db.collection("restaurants")
                    .whereArrayContains("deliveryLocations", userLocation)
                    .get().await()

                val mainRestaurants = mainResSnapshot.documents.mapNotNull { doc ->
                    Restaurant(
                        id = doc.id,
                        name = doc.getString("name") ?: "No Name",
                        cuisine = doc.getString("cuisine") ?: "General",
                        imageUrl = doc.getString("imageUrl"),
                        open = "yes",
                        type = RestaurantType.MAIN,
                        priority = doc.getLong("priority")?.toInt()
                    )
                }

                // 2. Fetch Mini Restaurants (New)
                val miniResSnapshot = db.collection("mini_restaurants")
                    .whereArrayContains("availableLocations", userLocation)
                    .get().await()

                val miniRestaurants = miniResSnapshot.documents.mapNotNull { doc ->
                    Restaurant(
                        id = doc.id,
                        name = doc.getString("name") ?: "No Name",
                        cuisine = doc.getString("cuisine") ?: "General",
                        imageUrl = doc.getString("imageUrl"),
                        open = doc.getString("open") ?: "yes",
                        type = RestaurantType.MINI,
                        priority = doc.getLong("priority")?.toInt()
                    )
                }

                val combined = (mainRestaurants + miniRestaurants).sortedWith(
                    compareBy<Restaurant> { it.priority ?: Int.MAX_VALUE }
                        .thenBy { it.name }
                )

                combinedRestaurants = combined
                isLoading = false

            } catch (e: Exception) {
                isLoading = false
                combinedRestaurants = emptyList()
            }

        } else if (userProfile != null) {
            isLoading = false
            combinedRestaurants = emptyList()
            offers = emptyList()
            miniRestaurantsSearch = emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Box {
                    HomeTopBar(
                        isScrolled = isScrolled,
                        userProfile = userProfile,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { newQuery -> searchQuery = newQuery },
                        onNotificationClick = onNotificationClick,
                        onFavoriteClick = { showLoveBubbles = true }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 75.dp),
                state = lazyListState
            ) {
                if (searchQuery.isBlank()) {
                    if (offers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            OfferSlider(offers = offers)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                    item {
                        CategorySection(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onCategoryClick = onStoreCategoryClick
                        )
                    }
                }

                if (searchQuery.isNotBlank()) {
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                    item {
                        Text(
                            text = "Search Results",
                            fontSize = 20.sp,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 18.dp, bottom = 11.dp)
                        )
                    }
                    if (searchResults.isEmpty()) {
                        item {
                            EmptyStateMessage(
                                icon = Icons.Default.SearchOff,
                                message = "No restaurants, shops or categories match your search."
                            )
                        }
                    } else {
                        items(searchResults) { result ->
                            when (result) {
                                is SearchResult.RestaurantResult -> {
                                    RestaurantCard(
                                        restaurant = result.restaurant,
                                        onClick = {
                                            onRestaurantClick(
                                                result.restaurant.id,
                                                result.restaurant.name,
                                                result.restaurant.type
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                is SearchResult.SubCategoryResult -> {
                                    SubCategorySearchCard(
                                        subCategory = result.subCategory,
                                        onClick = { onSubCategorySearchClick(result.subCategory.name) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                is SearchResult.MiniRestaurantResult -> {
                                    MiniRestaurantSearchCard(
                                        restaurant = result.miniRestaurant,
                                        onClick = {
                                            if (result.miniRestaurant.open == "yes") {
                                                onMiniRestaurantClick(result.miniRestaurant.id, result.miniRestaurant.name)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                    item {
                        Text(
                            text = "Available Restaurants & Shops",
                            fontSize = 19.sp,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 18.dp, bottom = 11.dp)
                        )
                    }
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                YLogoLoadingIndicator(
                                    size = 35.dp,
                                    color = BrandPink
                                )
                            }
                        }
                    }
                    else if (userProfile?.subLocation.isNullOrBlank()) {
                        item {
                            EmptyStateMessage(
                                icon = Icons.Default.WrongLocation,
                                message = "Please set your delivery hall/building in your profile to find nearby restaurants."
                            )
                        }
                    } else if (combinedRestaurants.isEmpty()) {
                        item {
                            EmptyStateMessage(
                                icon = Icons.Default.Restaurant,
                                message = "No restaurants currently deliver to your location."
                            )
                        }
                    } else {
                        items(combinedRestaurants) { restaurant ->
                            RestaurantCard(
                                restaurant = restaurant,
                                onClick = {
                                    onRestaurantClick(
                                        restaurant.id,
                                        restaurant.name,
                                        restaurant.type
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Love Bubble Animation Overlay
        if (showLoveBubbles) {
            LoveBubblesAnimation(
                onAnimationComplete = { showLoveBubbles = false }
            )
        }

        // WhatsApp Floating Button - FIXED PLACEMENT
        // Using padding(bottom = 100.dp) to stay clear of Bottom Navigation
        WhatsAppSupportButton(
            isVisible = isScrollingUp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp)
                .zIndex(100f)
        )
    }
}

@Composable
fun LoveBubblesAnimation(onAnimationComplete: () -> Unit) {
    val bubbles = remember {
        List(25) { index ->
            LoveBubble(
                id = index,
                startX = Random.nextFloat(),
                scale = Random.nextFloat() * 0.7f + 0.6f,
                duration = Random.nextInt(1500, 2500),
                delay = Random.nextInt(0, 500)
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
        bubbles.forEach { bubble ->
            AnimatedLoveBubble(bubble = bubble)
        }
    }
}

@Composable
fun AnimatedLoveBubble(bubble: LoveBubble) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_${bubble.id}")

    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(bubble.delay.toLong())
        startAnimation = true
    }

    val offsetY by animateFloatAsState(
        targetValue = if (startAnimation) -1200f else 0f,
        animationSpec = tween(
            durationMillis = bubble.duration,
            easing = EaseOut
        ),
        label = "offsetY_${bubble.id}"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 1f,
        animationSpec = tween(
            durationMillis = bubble.duration,
            easing = LinearEasing
        ),
        label = "alpha_${bubble.id}"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation_${bubble.id}"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = bubble.scale,
        targetValue = bubble.scale * 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_${bubble.id}"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .offset(
                    x = (bubble.startX * 350).dp,
                    y = 0.dp
                )
                .graphicsLayer {
                    translationY = offsetY
                    this.alpha = alpha
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Love",
                tint = when (bubble.id % 5) {
                    0 -> Color(0xFFFF1744)
                    1 -> Color(0xFFFF4081)
                    2 -> Color(0xFFF50057)
                    3 -> Color(0xFFE91E63)
                    else -> Color(0xFFEC407A)
                },
                modifier = Modifier.size((32 * bubble.scale).dp)
            )
        }
    }
}

@Composable
fun EmptyStateMessage(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty State",
            modifier = Modifier.size(64.dp),
            tint = Color.Gray.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun HomeTopBar(
    isScrolled: Boolean,
    userProfile: UserProfile?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNotificationClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val gradientColors = listOf(BrandPink, DarkPink)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = if (isScrolled) 4.dp else 0.dp,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    ),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
                    .statusBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = !isScrolled,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.baseLocation ?: "...",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (!userProfile?.subLocation.isNullOrBlank()) {
                                Text(
                                    text = userProfile?.subLocation ?: "",
                                    color = Color.White.copy(alpha = 0.90f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = onFavoriteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = "Favorites",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(if (isScrolled) 8.dp else 12.dp))
                ModernSearchBar(query = searchQuery, onQueryChange = onSearchQueryChange)
            }
        }
    }
}

@Composable
fun ModernSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    val searchBarHeight = 31.dp
    val iconSize = 20.dp
    val horizontalPadding = 16.dp
    val cornerRadius = 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = searchBarHeight, max = searchBarHeight * 1.2f)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                clip = true
            )
            .background(
                color = Color.White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(cornerRadius)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusRequester.requestFocus() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search Icon",
                tint = if (isFocused) BrandPink else Color.Gray,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 16.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(BrandPink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Search Restaurants or Foods......",
                                color = Color.Gray.copy(alpha = 1f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfferSlider(offers: List<Offer>) {
    val pagerState = rememberPagerState(pageCount = { offers.size })
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            if (pagerState.pageCount > 0) {
                val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(page = nextPage, animationSpec = tween(600))
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.height(140.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp
    ) { page ->
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            AsyncImage(
                model = offers[page].imageUrl,
                contentDescription = "Offer",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(id = R.drawable.offer),
                error = painterResource(id = R.drawable.offer)
            )
        }
    }
}

@Composable
fun CategorySection(modifier: Modifier = Modifier, onCategoryClick: (categoryId: String, categoryName: String) -> Unit) {
    val categories = listOf(
        Category("Fast Food", Icons.Default.Fastfood, "fast_food"),
        Category("Pharmacy", Icons.Default.LocalPharmacy, "pharmacy"),
        Category("City Food", Icons.Default.FoodBank, "personal_care"),
        Category("Grocery", Icons.Default.ShoppingCart, "grocery")
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        categories.forEach { category ->
            CategoryItem(category = category, onClick = { onCategoryClick(category.id, category.name) })
        }
    }
}

@Composable
fun CategoryItem(category: Category, onClick: () -> Unit) {
    val scale by if (category.id == "personal_care") {
        rememberInfiniteTransition(label = "").animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = ""
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(DeepPink.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.name,
                tint = DeepPink,
                modifier = Modifier
                    .size(28.dp)
                    .scale(scale)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RestaurantCard(restaurant: Restaurant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    val isClosed = restaurant.open.equals("no", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isClosed, onClick = onClick)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    AsyncImage(
                        model = restaurant.imageUrl,
                        contentDescription = restaurant.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.ic_shopping_bag),
                        error = painterResource(id = R.drawable.ic_shopping_bag)
                    )

                    if (isClosed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DeepPink
                            ) {
                                Text(
                                    text = "CLOSED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.1f)
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                ),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { isFavorite = !isFavorite },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Add to favorites",
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp),
                                tint = if (isFavorite) Color.Red else Color.Gray.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = restaurant.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2C2C),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = "Cuisine",
                            modifier = Modifier.size(14.dp),
                            tint = BrandPink.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = restaurant.cuisine,
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Shop",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFA726)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verified",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2C2C)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Delivery time",
                                modifier = Modifier.size(14.dp),
                                tint = Color.Gray.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Deliver in Time",
                                fontSize = 12.sp,
                                color = Color.Gray.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.Transparent
            ) {}
        }
    }
}

@Composable
fun SubCategorySearchCard(
    subCategory: SubCategorySearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(start = 30.dp, end = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(15.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE6E6))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 43.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = subCategory.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${subCategory.itemCount} items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-30).dp)
                .zIndex(2f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFFFE6E6))
            ) {
                AsyncImage(
                    model = subCategory.imageUrl,
                    contentDescription = subCategory.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .padding(0.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_shopping_bag),
                    error = painterResource(id = R.drawable.ic_shopping_bag)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp)
                .zIndex(2f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "View items",
                        tint = DeepPink,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniRestaurantSearchCard(restaurant: MiniRestaurant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isClosed = restaurant.open.equals("no", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(enabled = !isClosed, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = 1000f
                        )
                    )
            )
            if (isClosed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DeepPink,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Closed",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "CLOSED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            if (!isClosed) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp, topEnd = 20.dp),
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "OPEN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}