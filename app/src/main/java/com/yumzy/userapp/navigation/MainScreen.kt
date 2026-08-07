package com.yumzy.userapp.navigation

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.R
import com.yumzy.userapp.features.cart.CartScreen
import com.yumzy.userapp.features.cart.CartViewModel
import com.yumzy.userapp.features.cart.CheckoutScreen
import com.yumzy.userapp.features.home.HomeScreen
import com.yumzy.userapp.features.home.PreOrderCategoryMenuScreen
import com.yumzy.userapp.features.home.RestaurantMenuScreen
import com.yumzy.userapp.features.home.RestaurantType
import com.yumzy.userapp.features.orders.OrdersScreen
import com.yumzy.userapp.features.profile.AccountScreen
import com.yumzy.userapp.features.profile.EditProfileScreen
import com.yumzy.userapp.features.stores.StoreItemGridScreen
import com.yumzy.userapp.features.stores.SubCategoryListScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CustomIcon(
    val vectorIcon: ImageVector? = null,
    val drawableRes: Int? = null,
    val painter: Painter? = null
)

sealed class Screen(
    val route: String,
    val label: String,
    val icon: CustomIcon,
    val selectedIcon: CustomIcon = icon
) {
    companion object {
        val HOME_ICON = CustomIcon(drawableRes = R.drawable.ic_home_outline)
        val HOME_SELECTED_ICON = CustomIcon(drawableRes = R.drawable.ic_home_filled)
        val CART_ICON = CustomIcon(drawableRes = R.drawable.ic_cart_outline)
        val CART_SELECTED_ICON = CustomIcon(drawableRes = R.drawable.ic_cart_filled)
        val ORDERS_ICON = CustomIcon(drawableRes = R.drawable.ic_orders_outline)
        val ORDERS_SELECTED_ICON = CustomIcon(drawableRes = R.drawable.ic_orders_filled)
        val ACCOUNT_ICON = CustomIcon(drawableRes = R.drawable.ic_account_outline)
        val ACCOUNT_SELECTED_ICON = CustomIcon(drawableRes = R.drawable.ic_account_filled)
    }

    data object Home : Screen("home", "Home", HOME_ICON, HOME_SELECTED_ICON)
    data object Cart : Screen("cart", "Cart", CART_ICON, CART_SELECTED_ICON)
    data object Orders : Screen("orders", "Orders", ORDERS_ICON, ORDERS_SELECTED_ICON)
    data object Account : Screen("account", "Account", ACCOUNT_ICON, ACCOUNT_SELECTED_ICON)
    data object RestaurantMenu : Screen("restaurant_menu", "Menu", CustomIcon(vectorIcon = Icons.Default.Home))
    data object PreOrderCategoryMenu : Screen("preorder_menu", "Pre-Order Menu", CustomIcon(vectorIcon = Icons.Default.Home))
    data object SubCategoryList : Screen("sub_category_list", "Sub-Categories", CustomIcon(vectorIcon = Icons.Default.Home))
    data object StoreItemGrid : Screen("store_item_grid", "Store Items", CustomIcon(vectorIcon = Icons.Default.Home))
    data object Checkout : Screen("checkout", "Checkout", CustomIcon(vectorIcon = Icons.Default.Home))
    data object EditUserProfile : Screen("edit_user_profile", "Edit Profile", CustomIcon(vectorIcon = Icons.Default.Edit))
}

@Composable
fun CustomIconRenderer(
    icon: CustomIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    when {
        icon.painter != null -> {
            Image(
                painter = icon.painter,
                contentDescription = contentDescription,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint)
            )
        }
        icon.drawableRes != null -> {
            Image(
                painter = painterResource(id = icon.drawableRes),
                contentDescription = contentDescription,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint)
            )
        }
        icon.vectorIcon != null -> {
            Icon(
                imageVector = icon.vectorIcon,
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint
            )
        }
    }
}


@Composable
fun MainScreen(
    onSignOut: () -> Unit,
    isConnected: Boolean
) {

    val navController = rememberNavController()
    val cartViewModel: CartViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bottomBarItems = listOf(Screen.Home, Screen.Cart, Screen.Orders, Screen.Account)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val screensWithoutBottomBar = listOf(
        "${Screen.SubCategoryList.route}/{mainCategoryId}/{mainCategoryName}",
        "${Screen.StoreItemGrid.route}?subCategoryName={subCategoryName}&miniResId={miniResId}&title={title}",
        "${Screen.Checkout.route}/{restaurantId}",
        "${Screen.RestaurantMenu.route}/{restaurantId}/{restaurantName}",
        "${Screen.PreOrderCategoryMenu.route}/{restaurantId}/{restaurantName}/{categoryName}",
        Screen.EditUserProfile.route
    )

    Scaffold(
        bottomBar = {
            if (currentRoute !in screensWithoutBottomBar) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
                    NavigationBar(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black.copy(alpha = 0.1f),
                                spotColor = Color.Black.copy(alpha = 0.1f)
                            ),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        bottomBarItems.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    Box(
                                        modifier = if (isSelected) {
                                            Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    CircleShape
                                                )
                                        } else {
                                            Modifier.size(40.dp)
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CustomIconRenderer(
                                            icon = if (isSelected) screen.selectedIcon else screen.icon,
                                            contentDescription = screen.label,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                },
                                label = { Text(text = screen.label, fontSize = 12.sp, maxLines = 1) },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = PaddingValues(bottom = 0.dp).calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onRestaurantClick = { id, name, type ->
                        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())

                        if (type == RestaurantType.MAIN) {
                            navController.navigate("${Screen.RestaurantMenu.route}/$id/$encodedName")
                        } else {
                            navController.navigate("${Screen.StoreItemGrid.route}?miniResId=$id&title=$encodedName")
                        }
                    },
                    onStoreCategoryClick = { categoryId, categoryName ->
                        val encodedCatName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.SubCategoryList.route}/$categoryId/$encodedCatName")
                    },
                    onSubCategorySearchClick = { subCategoryName ->
                        val encodedSubCatName = URLEncoder.encode(subCategoryName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.StoreItemGrid.route}?subCategoryName=$encodedSubCatName&title=$encodedSubCatName")
                    },
                    onMiniRestaurantClick = { miniResId, miniResName ->
                        val encodedResName = URLEncoder.encode(miniResName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.StoreItemGrid.route}?miniResId=$miniResId&title=$encodedResName")
                    },
                    onNotificationClick = {
                        navController.navigate(Screen.Orders.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Cart.route) {
                CartScreen(cartViewModel = cartViewModel, onPlaceOrder = { restaurantId ->
                    navController.navigate("${Screen.Checkout.route}/$restaurantId")
                })
            }
            composable(
                route = "${Screen.Orders.route}?showAd={showAd}",
                arguments = listOf(navArgument("showAd") { type = NavType.BoolType; defaultValue = false })
            ) { backStackEntry ->
                val showAd = backStackEntry.arguments?.getBoolean("showAd") ?: false
                OrdersScreen(showAdOnLoad = showAd)
            }
            composable(Screen.Account.route) { backStackEntry ->
                val shouldRefresh = backStackEntry.savedStateHandle.get<Boolean>("refresh_profile") ?: true
                AccountScreen(
                    onSignOut = onSignOut,
                    onNavigateToEditProfile = { navController.navigate(Screen.EditUserProfile.route) },
                    refreshTrigger = shouldRefresh
                )
                backStackEntry.savedStateHandle["refresh_profile"] = false
            }

            composable(
                route = "${Screen.RestaurantMenu.route}/{restaurantId}/{restaurantName}",
                arguments = listOf(
                    navArgument("restaurantId") { type = NavType.StringType },
                    navArgument("restaurantName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                val encodedName = backStackEntry.arguments?.getString("restaurantName") ?: ""
                val restaurantName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())

                RestaurantMenuScreen(
                    restaurantId = restaurantId,
                    restaurantName = restaurantName,
                    cartViewModel = cartViewModel,
                    onCategoryClick = { restId, restName, categoryName ->
                        val encodedRestName = URLEncoder.encode(restName, StandardCharsets.UTF_8.toString())
                        val encodedCatName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.PreOrderCategoryMenu.route}/$restId/$encodedRestName/$encodedCatName")
                    },
                    onBackClicked = { navController.popBackStack() },
                    onPlaceOrder = { restId -> navController.navigate("${Screen.Checkout.route}/$restId") }
                )
            }

            composable(
                route = "${Screen.PreOrderCategoryMenu.route}/{restaurantId}/{restaurantName}/{categoryName}",
                arguments = listOf(
                    navArgument("restaurantId") { type = NavType.StringType },
                    navArgument("restaurantName") { type = NavType.StringType },
                    navArgument("categoryName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                val encodedRestaurantName = backStackEntry.arguments?.getString("restaurantName") ?: ""
                val restaurantName = URLDecoder.decode(encodedRestaurantName, StandardCharsets.UTF_8.toString())
                val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                val categoryName = URLDecoder.decode(encodedCategoryName, StandardCharsets.UTF_8.toString())

                PreOrderCategoryMenuScreen(
                    restaurantId = restaurantId,
                    restaurantName = restaurantName,
                    categoryName = categoryName,
                    cartViewModel = cartViewModel,
                    onBackClicked = { navController.popBackStack() },
                    onPlaceOrder = { restId -> navController.navigate("${Screen.Checkout.route}/$restId") }
                )
            }

            composable(
                route = "${Screen.SubCategoryList.route}/{mainCategoryId}/{mainCategoryName}",
                arguments = listOf(
                    navArgument("mainCategoryId") { type = NavType.StringType },
                    navArgument("mainCategoryName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mainCategoryId = backStackEntry.arguments?.getString("mainCategoryId") ?: ""
                val mainCategoryName = URLDecoder.decode(backStackEntry.arguments?.getString("mainCategoryName") ?: "", StandardCharsets.UTF_8.toString())

                SubCategoryListScreen(
                    mainCategoryId = mainCategoryId,
                    mainCategoryName = mainCategoryName,
                    onSubCategoryClick = { subCategoryName ->
                        val encodedSubCatName = URLEncoder.encode(subCategoryName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.StoreItemGrid.route}?subCategoryName=$encodedSubCatName&title=$encodedSubCatName")
                    },
                    onMiniRestaurantClick = { miniResId, miniResName ->
                        val encodedResName = URLEncoder.encode(miniResName, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Screen.StoreItemGrid.route}?miniResId=$miniResId&title=$encodedResName")
                    },
                    onBackClicked = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.StoreItemGrid.route}?subCategoryName={subCategoryName}&miniResId={miniResId}&title={title}",
                arguments = listOf(
                    navArgument("subCategoryName") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("miniResId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val subCategoryName = backStackEntry.arguments?.getString("subCategoryName")?.let {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                }
                val miniResId = backStackEntry.arguments?.getString("miniResId")
                val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())

                StoreItemGridScreen(
                    title = title,
                    subCategoryName = subCategoryName,
                    miniResId = miniResId,
                    onBackClicked = { navController.popBackStack() },
                    cartViewModel = cartViewModel,
                    onPlaceOrder = { restaurantId ->
                        navController.navigate("${Screen.Checkout.route}/$restaurantId")
                    },
                    // --- NEW: Handle View Cart navigation ---
                    onViewCartClick = {
                        navController.navigate(Screen.Cart.route)
                    }
                )
            }

            // In MainScreen.kt, update the CheckoutScreen composable call:

            composable(
                route = "${Screen.Checkout.route}/{restaurantId}",
                arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
            ) { backStackEntry ->
                val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                val savedCartState by cartViewModel.savedCart.collectAsState()
                val itemsForRestaurant = savedCartState.values.filter { it.restaurantId == restaurantId }

                if (itemsForRestaurant.isNotEmpty()) {
                    CheckoutScreen(
                        cartItems = itemsForRestaurant,
                        restaurantId = restaurantId,
                        onBackClicked = { navController.popBackStack() },
                        onConfirmOrder = { delivery, service, total, paymentMethod, userNote ->
                            scope.launch {
                                val user = Firebase.auth.currentUser ?: return@launch
                                Firebase.firestore.collection("users").document(user.uid).get()
                                    .addOnSuccessListener { userDoc ->
                                        val orderItems = itemsForRestaurant.map { mapOf(
                                            "itemName" to it.menuItem.name,
                                            "quantity" to it.quantity,
                                            "price" to it.menuItem.price,
                                            "miniResName" to it.restaurantName
                                        )}
                                        val firstItemCategory = itemsForRestaurant.firstOrNull()?.menuItem?.category ?: ""
                                        val isPreOrder = firstItemCategory.startsWith("Pre-order")
                                        val orderType = if (isPreOrder) "PreOrder" else "Instant"

                                        val newOrder = hashMapOf(
                                            "userId" to user.uid,
                                            "userName" to (userDoc.getString("name") ?: "N/A"),
                                            "userPhone" to (userDoc.getString("phone") ?: "N/A"),
                                            "userBaseLocation" to (userDoc.getString("baseLocation") ?: "N/A"),
                                            "userSubLocation" to (userDoc.getString("subLocation") ?: "N/A"),
                                            "building" to (userDoc.getString("building") ?: ""),
                                            "floor" to (userDoc.getString("floor") ?: ""),
                                            "room" to (userDoc.getString("room") ?: ""),
                                            "restaurantId" to restaurantId,
                                            "restaurantName" to itemsForRestaurant.first().restaurantName,
                                            "totalPrice" to total,
                                            "deliveryCharge" to delivery,
                                            "serviceCharge" to service,
                                            "items" to orderItems,
                                            "orderStatus" to "Pending",
                                            "createdAt" to Timestamp.now(),
                                            "orderType" to orderType,
                                            "preOrderCategory" to if (orderType == "PreOrder") firstItemCategory else "",
                                            "payment" to paymentMethod,  // NEW: Add payment method field
                                            "userNote" to userNote  // NEW: Optional note from the customer
                                        )

                                        Firebase.firestore.collection("orders").add(newOrder)
                                            .addOnSuccessListener {
                                                cartViewModel.clearCartForRestaurant(restaurantId)
                                                Toast.makeText(context, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack(Screen.Home.route, inclusive = false)
                                                navController.navigate("${Screen.Orders.route}?showAd=true") {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Failed to place order: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    }
                            }
                        }
                    )
                }
            }

            composable(Screen.EditUserProfile.route) {
                EditProfileScreen(
                    onBackClicked = { navController.popBackStack() },
                    onSaveChanges = { name, phone, baseLocation, subLocation, building, floor, room ->
                        val user = Firebase.auth.currentUser
                        if (user != null) {
                            val profileUpdates = userProfileChangeRequest { displayName = name }
                            user.updateProfile(profileUpdates)

                            val firestoreUpdates = mapOf(
                                "name" to name, "phone" to phone,
                                "baseLocation" to baseLocation, "subLocation" to subLocation,
                                "building" to building, "floor" to floor, "room" to room
                            )
                            Firebase.firestore.collection("users").document(user.uid)
                                .update(firestoreUpdates)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
                                    navController.previousBackStackEntry?.savedStateHandle?.set("refresh_profile", true)
                                    navController.popBackStack()
                                }
                        }
                    }
                )
            }
        }
    }
}