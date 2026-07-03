package com.yumzy.userapp.features.stores

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.R
import com.yumzy.userapp.YLogoLoadingIndicator
import com.yumzy.userapp.components.WhatsAppSupportButton
import com.yumzy.userapp.components.isScrollingUp
import com.yumzy.userapp.ui.theme.DeepPink
import com.yumzy.userapp.ui.theme.softC
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class for SubCategories
data class SubCategory(
    val id: String,
    val name: String,
    val imageUrl: String,
    val priority: Int? = null
)

// Data class for Mini Restaurants
data class MiniRestaurant(
    val id: String,
    val name: String,
    val imageUrl: String,
    val open: String, // "yes" or "no"
    val priority: Int? = null
)

// Data class for Announcements
data class Announcement(
    val id: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubCategoryListScreen(
    mainCategoryId: String,
    mainCategoryName: String,
    onSubCategoryClick: (subCategoryName: String) -> Unit,
    onMiniRestaurantClick: (miniResId: String, miniResName: String) -> Unit,
    onBackClicked: () -> Unit
) {
    var subCategories by remember { mutableStateOf<List<SubCategory>>(emptyList()) }
    var miniRestaurants by remember { mutableStateOf<List<MiniRestaurant>>(emptyList()) }
    var itemCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var userSubLocation by remember { mutableStateOf<String?>(null) }
    var announcements by remember { mutableStateOf<List<Announcement>>(emptyList()) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Categories", "Shops")
    val coroutineScope = rememberCoroutineScope()

    // Show Grocery terms popup once when entering the Grocery section
    var showGroceryTerms by remember { mutableStateOf(mainCategoryId.equals("grocery", ignoreCase = true)) }

    // State hoisting for scroll detection
    val listState = rememberLazyListState()
    val isScrollingUp = listState.isScrollingUp()

    LaunchedEffect(key1 = mainCategoryId) {
        isLoading = true
        val db = Firebase.firestore
        val currentUser = Firebase.auth.currentUser

        val location = if (currentUser != null) {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                userDoc.getString("subLocation")
            } catch (e: Exception) { null }
        } else { null }
        userSubLocation = location

        if (location.isNullOrBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        // Fetch announcements
        db.collection("announce")
            .whereEqualTo("parentCategory", mainCategoryId)
            .whereArrayContains("availableLocations", location)
            .get()
            .addOnSuccessListener { announceSnapshot ->
                announcements = announceSnapshot.documents.mapNotNull { doc ->
                    Announcement(id = doc.id, text = doc.getString("text") ?: "")
                }
            }


        // Fetch sub-categories
        val subCatJob = coroutineScope.launch {
            db.collection("store_sub_categories")
                .whereEqualTo("parentCategory", mainCategoryId)
                .whereArrayContains("availableLocations", location)
                .get()
                .addOnSuccessListener { subCatSnapshot ->
                    val fetchedSubCats = subCatSnapshot.documents.mapNotNull { doc ->
                        SubCategory(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            priority = doc.getLong("priority")?.toInt()
                        )
                    }

                    subCategories = fetchedSubCats.sortedWith(
                        compareBy<SubCategory> {
                            it.priority ?: Int.MAX_VALUE
                        }.thenBy {
                            it.name
                        }
                    )

                    if (fetchedSubCats.isNotEmpty()) {
                        val subCategoryNames = fetchedSubCats.map { it.name }
                        db.collection("store_items")
                            .whereIn("subCategory", subCategoryNames)
                            .get()
                            .addOnSuccessListener { itemsSnapshot ->
                                itemCounts = itemsSnapshot.documents
                                    .mapNotNull { it.getString("subCategory") }
                                    .groupingBy { it }
                                    .eachCount()
                            }
                    }
                }
        }

        // Fetch mini restaurants
        val miniResJob = coroutineScope.launch {
            db.collection("mini_restaurants")
                .whereEqualTo("parentCategory", mainCategoryId)
                .whereArrayContains("availableLocations", location)
                .get()
                .addOnSuccessListener { restaurantSnapshot ->
                    val fetchedRestaurants = restaurantSnapshot.documents.mapNotNull { doc ->
                        MiniRestaurant(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            open = doc.getString("open") ?: "no",
                            priority = doc.getLong("priority")?.toInt()
                        )
                    }

                    miniRestaurants = fetchedRestaurants.sortedWith(
                        compareBy<MiniRestaurant> {
                            it.priority ?: Int.MAX_VALUE
                        }.thenBy {
                            it.name
                        }
                    )
                }
        }

        // Wait for both fetches to complete
        subCatJob.join()
        miniResJob.join()
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = mainCategoryName,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 28.sp,
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
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(22.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
            // Button moved to outer Box for better positioning
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        YLogoLoadingIndicator(size = 35.dp, color = DeepPink)
                    }
                } else if (userSubLocation.isNullOrBlank()) {
                    NoLocationView()
                } else {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 3.dp,
                                color = DeepPink
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTabIndex == index) DeepPink else Color.Gray
                                    )
                                }
                            )
                        }
                    }

                    when (selectedTabIndex) {
                        0 -> CategoriesTabContent(
                            announcements,
                            subCategories,
                            itemCounts,
                            onSubCategoryClick,
                            listState // Pass state
                        )
                        1 -> RestaurantsTabContent(
                            announcements,
                            miniRestaurants,
                            onMiniRestaurantClick,
                            listState // Pass state
                        )
                    }
                }
            }
        }

        // WhatsApp Floating Button (Root Level placement)
        WhatsAppSupportButton(
            isVisible = isScrollingUp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp)
                .zIndex(100f)
        )

        // Grocery order terms & conditions popup
        if (showGroceryTerms) {
            GroceryTermsDialog(onDismiss = { showGroceryTerms = false })
        }
    }
}

@Composable
fun GroceryTermsDialog(onDismiss: () -> Unit) {
    // Fresh, smooth red-white combination palette
    val freshRed = Color(0xFFE8354E)
    val softRedBg = Color(0xFFFFF3F4)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header band (red -> soft red gradient)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(freshRed, Color(0xFFF4566B))
                            )
                        )
                        .padding(vertical = 22.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.White.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Grocery অর্ডার এর শর্তাবলী",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TermRow(
                        number = "১",
                        text = "শুধু প্রি-অর্ডার করতে পারবেন। প্রি-অর্ডারের ৩ ঘণ্টার মধ্যে ডেলিভারি।",
                        accent = freshRed,
                        bg = softRedBg
                    )
                    TermRow(
                        number = "২",
                        text = "Grocery item এর সাথে Fast food restaurant er খাবার বা city food এর খাবার অর্ডার করলে অর্ডার Cancel হয়ে যাবে।",
                        accent = freshRed,
                        bg = softRedBg
                    )
                }

                // Action button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = freshRed)
                    ) {
                        Text(
                            text = "বুঝেছি",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermRow(number: String, text: String, accent: Color, bg: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFF2B2B2B),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CategoriesTabContent(
    announcements: List<Announcement>,
    subCategories: List<SubCategory>,
    itemCounts: Map<String, Int>,
    onSubCategoryClick: (String) -> Unit,
    listState: LazyListState // Receive state
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (announcements.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    announcements.forEach { announcement ->
                        AnnouncementCard(announcement = announcement)
                    }
                }
            }
        }

        if (subCategories.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No categories found for your location.", textAlign = TextAlign.Center, color = Color.Black)
                }
            }
        } else {
            items(subCategories) { subCategory ->
                SubCategoryCard(
                    subCategory = subCategory,
                    itemCount = itemCounts[subCategory.name] ?: 0,
                    onClick = { onSubCategoryClick(subCategory.name) }
                )
            }
        }
    }
}

@Composable
fun RestaurantsTabContent(
    announcements: List<Announcement>,
    restaurants: List<MiniRestaurant>,
    onMiniRestaurantClick: (String, String) -> Unit,
    listState: LazyListState // Receive state
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (announcements.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    announcements.forEach { announcement ->
                        AnnouncementCard(announcement = announcement)
                    }
                }
            }
        }

        if (restaurants.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No restaurants found for your location.", textAlign = TextAlign.Center, color = Color.Black)
                }
            }
        } else {
            items(restaurants) { restaurant ->
                MiniRestaurantCard(
                    restaurant = restaurant,
                    onClick = {
                        if (restaurant.open == "yes") {
                            onMiniRestaurantClick(restaurant.id, restaurant.name)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NoLocationView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WrongLocation,
            contentDescription = "No Location",
            modifier = Modifier.size(48.dp),
            tint = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Please set your location in your profile to see available categories.",
            textAlign = TextAlign.Center,
            color = Color.Black
        )
    }
}

@Composable
fun MiniRestaurantCard(restaurant: MiniRestaurant, onClick: () -> Unit) {
    val isClosed = restaurant.open.equals("no", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(enabled = !isClosed, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                placeholder = painterResource(id = R.drawable.img),
                error = painterResource(id = R.drawable.img)
            )

            // Gradient Overlay
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

            // Closed Overlay
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

            // Restaurant Name at Bottom
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

            // Open Badge (top right)
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

@Composable
fun AnnouncementCard(announcement: Announcement) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(softC).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(DeepPink, CircleShape)
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Campaign,
                    contentDescription = "Announcement",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = announcement.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SubCategoryCard(subCategory: SubCategory, itemCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(start = 30.dp, end = 16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = RoundedCornerShape(15.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE6E6))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 43.dp, end = 0.dp),
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$itemCount items",
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
                modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, softC)
            ) {
                AsyncImage(
                    model = subCategory.imageUrl,
                    contentDescription = subCategory.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape).padding(0.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.img),
                    error = painterResource(id = R.drawable.img)
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
                modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "View items",
                        tint = Color(0xFFDC0C25),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}