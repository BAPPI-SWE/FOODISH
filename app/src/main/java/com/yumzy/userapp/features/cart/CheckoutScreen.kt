// CheckoutScreen.kt
package com.yumzy.userapp.features.cart

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notes
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.R
import com.yumzy.userapp.ads.SharedInterstitialAdManager
import com.yumzy.userapp.ui.theme.DarkPink
import kotlinx.coroutines.delay
import kotlin.random.Random

// Coupon validation states
sealed class CouponState {
    object Idle : CouponState()
    object Loading : CouponState()
    data class Success(val discountPercent: Double, val isFreeDelivery: Boolean) : CouponState()
    data class Error(val message: String) : CouponState()
}

// A single voucher/coupon as stored in the "discount" Firestore collection.
// Document id      -> code (e.g. "FOOD25", "FREE")
// field "taka"     -> minimum order amount (৳) required to use this code
// field "discount" -> % discount on subtotal (ignored for the special "FREE" code)
data class VoucherInfo(
    val code: String,
    val discountPercent: Double,
    val minOrderAmount: Double,
    val isFreeDelivery: Boolean
)

data class UserProfileDetails(
    val name: String = "...",
    val email: String = "...",
    val fullAddress: String = "...",
    val baseLocation: String = "",
    val subLocation: String = ""
)

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

data class PaymentMethod(
    val type: PaymentType,
    val details: String = ""
)

enum class PaymentType {
    COD, BKASH, NAGAD, ROCKET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    cartItems: List<CartItem>,
    restaurantId: String? = null,
    onConfirmOrder: (deliveryCharge: Double, serviceCharge: Double, finalTotal: Double, paymentMethod: String, userNote: String) -> Unit,
    onBackClicked: () -> Unit
) {
    val itemsSubtotal = cartItems.sumOf { it.menuItem.price * it.quantity }
    var deliveryCharge by remember { mutableStateOf(20.0) }
    var serviceCharge by remember { mutableStateOf(5.0) }
    var rainyCharge by remember { mutableStateOf(0.0) }
    var showRainyDialog by remember { mutableStateOf(false) }
    var isLoadingCharges by remember { mutableStateOf(true) }
    val isPreOrder = cartItems.isNotEmpty() && cartItems.first().menuItem.category.startsWith("Pre-order")
    var userProfile by remember { mutableStateOf<UserProfileDetails?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    var showCelebration by remember { mutableStateOf(false) }
    var isPlacingOrder by remember { mutableStateOf(false) }

    // Order userNote states
    var userNote by remember { mutableStateOf("") }
    var showUserNoteDialog by remember { mutableStateOf(false) }

    // Coupon / discount states
    var couponInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf("") }
    var discountPercent by remember { mutableStateOf(0.0) }
    var isFreeDeliveryCoupon by remember { mutableStateOf(false) }
    var appliedMinOrderAmount by remember { mutableStateOf(0.0) }
    var couponState by remember { mutableStateOf<CouponState>(CouponState.Idle) }

    // "View available coupons" dialog states
    var showVoucherDialog by remember { mutableStateOf(false) }
    var isLoadingVouchers by remember { mutableStateOf(false) }
    var voucherList by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    var voucherFetchError by remember { mutableStateOf<String?>(null) }

    // Derived totals — recomputed whenever charges or discount change
    val discountAmount = itemsSubtotal * (discountPercent / 100.0)
    val effectiveDeliveryCharge = if (isFreeDeliveryCoupon) 0.0 else deliveryCharge
    val finalTotal = itemsSubtotal - discountAmount + effectiveDeliveryCharge + serviceCharge + rainyCharge

    // Payment method states
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod(PaymentType.COD)) }
    var showPaymentMethodDialog by remember { mutableStateOf(false) }
    var showDigitalPaymentDialog by remember { mutableStateOf(false) }
    var selectedDigitalPayment by remember { mutableStateOf<PaymentType?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Fallback lookup: "hidden" codes in the "discount_secret" collection.
    // These are NEVER listed anywhere in the app (no public voucher list shows them) —
    // they only work if someone types the exact code in manually.
    // Document schema: { discount: <percent off> }  (optionally "taka" for a min order amount)
    // Firestore rules only allow a direct `get` on this collection (not `list`),
    // so the codes can't be enumerated/dumped by a client — only looked up by exact ID.
    fun trySecretCoupon(code: String) {
        Firebase.firestore.collection("discount_secret").document(code).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minOrderAmount = document.getDouble("taka") ?: 0.0
                    val pct = document.getDouble("discount") ?: 0.0

                    if (itemsSubtotal < minOrderAmount) {
                        couponState = CouponState.Error(
                            "এই কোডটি ব্যবহার করতে ন্যূনতম ৳${"%.0f".format(minOrderAmount)} টাকার অর্ডার করতে হবে"
                        )
                        return@addOnSuccessListener
                    }

                    if (pct > 0) {
                        isFreeDeliveryCoupon = false
                        discountPercent = pct
                        appliedCoupon = code
                        appliedMinOrderAmount = minOrderAmount
                        couponState = CouponState.Success(pct, false)
                    } else {
                        couponState = CouponState.Error("এই কুপনে কোনো ডিসকাউন্ট নেই")
                    }
                } else {
                    couponState = CouponState.Error("ভুল কুপন কোড")
                }
            }
            .addOnFailureListener {
                couponState = CouponState.Error("কুপন যাচাই করা যায়নি, আবার চেষ্টা করুন")
            }
    }

    // Apply coupon: look up the code in Firebase "discount" collection first (public/visible
    // coupons shown in the voucher list). If it isn't found there, silently fall back to the
    // "discount_secret" collection — those codes only work if typed in manually and are never
    // shown in the app's voucher list.
    // Document schema: { taka: <minimum order amount>, discount: <percent off> }
    // Special fixed code "FREE": if subtotal >= taka, delivery charge becomes free.
    // Any other code: if subtotal >= taka, apply `discount`% off the subtotal.
    fun applyCoupon() {
        val code = couponInput.trim().uppercase()
        if (code.isEmpty()) return
        focusManager.clearFocus()
        couponState = CouponState.Loading
        Firebase.firestore.collection("discount").document(code).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minOrderAmount = document.getDouble("taka") ?: 0.0

                    if (itemsSubtotal < minOrderAmount) {
                        couponState = CouponState.Error(
                            "এই কোডটি ব্যবহার করতে ন্যূনতম ৳${"%.0f".format(minOrderAmount)} টাকার অর্ডার করতে হবে"
                        )
                        return@addOnSuccessListener
                    }

                    if (code == "FREE") {
                        isFreeDeliveryCoupon = true
                        discountPercent = 0.0
                        appliedCoupon = code
                        appliedMinOrderAmount = minOrderAmount
                        couponState = CouponState.Success(0.0, true)
                    } else {
                        val pct = document.getDouble("discount") ?: 0.0
                        if (pct > 0) {
                            isFreeDeliveryCoupon = false
                            discountPercent = pct
                            appliedCoupon = code
                            appliedMinOrderAmount = minOrderAmount
                            couponState = CouponState.Success(pct, false)
                        } else {
                            couponState = CouponState.Error("এই কুপনে কোনো ডিসকাউন্ট নেই")
                        }
                    }
                } else {
                    // Not a public coupon — try the hidden/secret collection before giving up.
                    trySecretCoupon(code)
                }
            }
            .addOnFailureListener {
                couponState = CouponState.Error("কুপন যাচাই করা যায়নি, আবার চেষ্টা করুন")
            }
    }

    fun removeCoupon() {
        couponInput = ""
        appliedCoupon = ""
        discountPercent = 0.0
        isFreeDeliveryCoupon = false
        appliedMinOrderAmount = 0.0
        couponState = CouponState.Idle
    }

    // Fetch every document from the "discount" collection to show the user
    // a nice, human-readable list of currently available coupons/vouchers.
    fun fetchAvailableVouchers() {
        isLoadingVouchers = true
        voucherFetchError = null
        Firebase.firestore.collection("discount").get()
            .addOnSuccessListener { snapshot ->
                voucherList = snapshot.documents.mapNotNull { doc ->
                    val minOrderAmount = doc.getDouble("taka") ?: return@mapNotNull null
                    val pct = doc.getDouble("discount") ?: 0.0
                    VoucherInfo(
                        code = doc.id,
                        discountPercent = pct,
                        minOrderAmount = minOrderAmount,
                        isFreeDelivery = doc.id.equals("FREE", ignoreCase = true)
                    )
                }.sortedBy { it.minOrderAmount }
                isLoadingVouchers = false
            }
            .addOnFailureListener {
                voucherFetchError = "ভাউচার লোড করা যায়নি, আবার চেষ্টা করুন"
                isLoadingVouchers = false
            }
    }

    LaunchedEffect(Unit) {
        SharedInterstitialAdManager.loadAd(context)
        Log.d("CheckoutScreen", "Started pre-loading ad in background")
    }

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

    LaunchedEffect(userProfile) {
        if (userProfile != null && userProfile!!.baseLocation.isNotEmpty() && userProfile!!.subLocation.isNotEmpty()) {
            isLoadingCharges = true
            val db = Firebase.firestore

            db.collection("locations")
                .whereEqualTo("name", userProfile!!.baseLocation)
                .get()
                .addOnSuccessListener { documents ->
                    var baseDeliveryCharge = 20.0
                    var baseServiceCharge = 5.0
                    var baseRainyCharge = 0.0

                    if (!documents.isEmpty) {
                        val locationDoc = documents.documents[0]
                        // Rainy day extra charge for this base location.
                        // Applies only when the base location name matches (which it does here,
                        // since the query filters by name == baseLocation).
                        baseRainyCharge = (locationDoc.get("rainyCharge") as? Number)?.toDouble() ?: 0.0
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
                            rainyCharge = baseRainyCharge
                            showRainyDialog = baseRainyCharge > 0
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
                                        rainyCharge = baseRainyCharge
                                        showRainyDialog = baseRainyCharge > 0
                                        isLoadingCharges = false
                                    }
                                }
                                .addOnFailureListener {
                                    itemsProcessed++
                                    if (itemsProcessed == baseItemIds.size) {
                                        deliveryCharge = baseDeliveryCharge + additionalDelivery
                                        serviceCharge = baseServiceCharge + additionalService
                                        rainyCharge = baseRainyCharge
                                        showRainyDialog = baseRainyCharge > 0
                                        isLoadingCharges = false
                                    }
                                }
                        }
                    } else {
                        deliveryCharge = baseDeliveryCharge
                        serviceCharge = baseServiceCharge
                        rainyCharge = baseRainyCharge
                        showRainyDialog = baseRainyCharge > 0
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

    fun handlePaymentSelection(paymentType: PaymentType) {
        when (paymentType) {
            PaymentType.COD -> {
                selectedPaymentMethod = PaymentMethod(PaymentType.COD)
                showPaymentMethodDialog = false
            }
            else -> {
                selectedDigitalPayment = paymentType
                showPaymentMethodDialog = false
                showDigitalPaymentDialog = true
            }
        }
    }

    fun getPaymentDisplayText(): String {
        return when (selectedPaymentMethod.type) {
            PaymentType.COD -> "Cash on Delivery"
            PaymentType.BKASH -> "Bkash - ${selectedPaymentMethod.details}"
            PaymentType.NAGAD -> "Nagad - ${selectedPaymentMethod.details}"
            PaymentType.ROCKET -> "Rocket - ${selectedPaymentMethod.details}"
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
            bottomBar = {
                CheckoutBottomBar(
                    totalAmount = finalTotal,
                    isLoading = isLoadingCharges,
                    isPlacingOrder = isPlacingOrder,
                    onConfirmClick = {
                        val paymentString = when (selectedPaymentMethod.type) {
                            PaymentType.COD -> "COD"
                            PaymentType.BKASH -> "Bkash,01970102586,${selectedPaymentMethod.details}"
                            PaymentType.NAGAD -> "Nagad,01988143409,${selectedPaymentMethod.details}"
                            PaymentType.ROCKET -> "Rocket,017463246207,${selectedPaymentMethod.details}"
                        }

                        showCelebration = true
                        isPlacingOrder = true
                        onConfirmOrder(effectiveDeliveryCharge, serviceCharge, finalTotal, paymentString, userNote.trim())
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, bottom = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Order Summary",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF333333)
                    )
                    EditUserNoteButton(
                        hasUserNote = userNote.isNotBlank(),
                        onClick = { showUserNoteDialog = true }
                    )
                }
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

                        // UserNote preview, shown only when a userNote has been added
                        if (userNote.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFF3E0))
                                    .clickable { showUserNoteDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "UserNote to restaurant",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFE65100)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        userNote,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF666666)
                                        )
                                    )
                                }
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
                            if (discountPercent > 0) {
                                PriceRow(
                                    label = "Discount (${discountPercent.toInt()}% off \"$appliedCoupon\")",
                                    amount = -discountAmount,
                                    isDiscount = true
                                )
                            }
                            if (isFreeDeliveryCoupon) {
                                PriceRow(
                                    label = "Delivery Charge (\"$appliedCoupon\" applied)",
                                    amount = -deliveryCharge,
                                    isDiscount = true
                                )
                            } else {
                                PriceRow(label = "Delivery Charge", amount = deliveryCharge)
                            }
                            PriceRow(label = "Service Charge/Tax", amount = serviceCharge)
                            if (rainyCharge > 0) {
                                PriceRow(label = "\uD83C\uDF27\uFE0F Rainy Day Charge", amount = rainyCharge)
                            }
                            Divider(
                                Modifier.padding(vertical = 8.dp),
                                color = Color(0xFFE0E0E0)
                            )
                            PriceRow(label = "Total to Pay", amount = finalTotal, isTotal = true)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ── Coupon / Voucher Section ──────────────────────────────────
                SectionHeader(title = "Coupon / Voucher")
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (appliedCoupon.isEmpty()) {
                            // Input row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = couponInput,
                                    onValueChange = {
                                        couponInput = it.uppercase()
                                        if (couponState is CouponState.Error) couponState = CouponState.Idle
                                    },
                                    label = { Text("Enter coupon code") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocalOffer,
                                            contentDescription = null,
                                            tint = DarkPink
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DarkPink,
                                        focusedLabelColor = DarkPink
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { applyCoupon() })
                                )
                                Button(
                                    onClick = { applyCoupon() },
                                    enabled = couponInput.isNotBlank() && couponState !is CouponState.Loading,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkPink,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (couponState is CouponState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Apply", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Error message
                            if (couponState is CouponState.Error) {
                                Text(
                                    (couponState as CouponState.Error).message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFDC2626)
                                    )
                                )
                            }

                            // View available vouchers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showVoucherDialog = true
                                        if (voucherList.isEmpty()) fetchAvailableVouchers()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = DarkPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "সব ভাউচার দেখুন",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkPink
                                    )
                                )
                            }
                        } else {
                            // Applied coupon chip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        appliedCoupon,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    )
                                    Text(
                                        text = if (isFreeDeliveryCoupon) {
                                            "ফ্রি ডেলিভারি প্রযোজ্য হয়েছে — আপনি বাঁচালেন ৳${"%.0f".format(deliveryCharge)}"
                                        } else {
                                            "${discountPercent.toInt()}% discount applied — you save ৳${"%.0f".format(discountAmount)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF4CAF50)
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = { removeCoupon() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                                ) {
                                    Text("Remove", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                // ── End Coupon Section ────────────────────────────────────────

                if (showVoucherDialog) {
                    VoucherListDialog(
                        vouchers = voucherList,
                        isLoading = isLoadingVouchers,
                        errorMessage = voucherFetchError,
                        onVoucherSelected = { code ->
                            couponInput = code
                            showVoucherDialog = false
                            applyCoupon()
                        },
                        onDismiss = { showVoucherDialog = false }
                    )
                }

                SectionHeader(title = "Payment Method")
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cash on Delivery option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { handlePaymentSelection(PaymentType.COD) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPaymentMethod.type == PaymentType.COD,
                                onClick = { handlePaymentSelection(PaymentType.COD) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Cash on Delivery",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )
                                )
                                Text(
                                    "Pay when you receive your order",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF666666)
                                    )
                                )
                            }
                        }

                        // Other payment methods option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPaymentMethodDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPaymentMethod.type != PaymentType.COD,
                                onClick = { showPaymentMethodDialog = true }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Other Payment Method",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )
                                )
                                Text(
                                    "Bkash, Nagad, Rocket",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF666666)
                                    )
                                )
                            }
                        }

                        // Show selected digital payment details
                        if (selectedPaymentMethod.type != PaymentType.COD) {
                            Text(
                                getPaymentDisplayText(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = DarkPink,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

            }
        }

        // Payment Method Selection Dialog
        if (showPaymentMethodDialog) {
            PaymentMethodSelectionDialog(
                onDismiss = { showPaymentMethodDialog = false },
                onPaymentSelected = { paymentType -> handlePaymentSelection(paymentType) }
            )
        }

        // Digital Payment Dialog
        if (showDigitalPaymentDialog && selectedDigitalPayment != null) {
            DigitalPaymentDialog(
                paymentType = selectedDigitalPayment!!,
                onDismiss = {
                    showDigitalPaymentDialog = false
                    selectedDigitalPayment = null
                },
                onConfirm = { mobile, transaction ->
                    selectedPaymentMethod = PaymentMethod(
                        type = selectedDigitalPayment!!,
                        details = transaction
                    )
                    showDigitalPaymentDialog = false
                    selectedDigitalPayment = null
                }
            )
        }

        // Edit UserNote Dialog
        if (showUserNoteDialog) {
            EditUserNoteDialog(
                initialUserNote = userNote,
                onDismiss = { showUserNoteDialog = false },
                onSave = { note ->
                    userNote = note.trim()
                    showUserNoteDialog = false
                }
            )
        }

        // Rainy Day Charge Warning Dialog
        if (showRainyDialog && rainyCharge > 0) {
            RainyDayDialog(
                rainyCharge = rainyCharge,
                onDismiss = { showRainyDialog = false }
            )
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

@Composable
fun RainyDayDialog(
    rainyCharge: Double,
    onDismiss: () -> Unit
) {
    // Gentle pop-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rainyScale"
    )

    // Soft floating motion for the emoji
    val infinite = rememberInfiniteTransition(label = "rainyFloat")
    val floatY by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cute circular emoji badge with soft rainy-blue gradient
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFE3F2FD),
                                    Color(0xFFBBDEFB)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83C\uDF27\uFE0F",
                        fontSize = 46.sp,
                        modifier = Modifier.graphicsLayer { translationY = floatY }
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "It's Raining! \u2614",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Due to rainy weather in your area, a small extra charge helps our delivery heroes reach you safely through the rain. \uD83D\uDE4F",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(18.dp))

                // Charge highlight chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Rainy Day Charge",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF1565C0)
                    )
                    Text(
                        text = "৳${"%.0f".format(rainyCharge)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF0D47A1)
                    )
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPink,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "Got it \uD83D\uDC4D",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSelectionDialog(
    onDismiss: () -> Unit,
    onPaymentSelected: (PaymentType) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Select Payment Method",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Bkash option - use R.drawable.bkash
                PaymentOptionCard(
                    title = "Bkash",
                    iconRes = R.drawable.bkash, // Your bkash.jpg/png
                    onClick = { onPaymentSelected(PaymentType.BKASH) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nagad option - use R.drawable.nagad
                PaymentOptionCard(
                    title = "Nagad",
                    iconRes = R.drawable.nagad, // Your nagad.jpg/png
                    onClick = { onPaymentSelected(PaymentType.NAGAD) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rocket option - use R.drawable.rocket
                PaymentOptionCard(
                    title = "Rocket",
                    iconRes = R.drawable.rocket, // Your rocket.jpg/png
                    onClick = { onPaymentSelected(PaymentType.ROCKET) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF5F5F5),
                        contentColor = Color(0xFF666666)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
@Composable
fun DigitalPaymentDialog(
    paymentType: PaymentType,
    onDismiss: () -> Unit,
    onConfirm: (mobile: String, transaction: String) -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var mobileNumber by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var animationComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        animationComplete = true
        delay(500)
        showForm = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AnimatedVisibility(
                visible = !showForm && animationComplete,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                PaymentCardSelection(paymentType = paymentType)
            }

            AnimatedVisibility(
                visible = showForm,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Send Money to ${getPaymentNumberForDisplay(paymentType)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        "Send money to ${getPaymentNumber(paymentType)} and enter your details below:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF666666)
                        ),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { mobileNumber = it },
                        label = { Text("Mobile Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkPink,
                            focusedLabelColor = DarkPink
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = transactionId,
                        onValueChange = { transactionId = it },
                        label = { Text("Transaction ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkPink,
                            focusedLabelColor = DarkPink
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF5F5F5),
                                contentColor = Color(0xFF666666)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (mobileNumber.isNotBlank() && transactionId.isNotBlank()) {
                                    onConfirm(mobileNumber, transactionId)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = mobileNumber.isNotBlank() && transactionId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkPink,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentCardSelection(paymentType: PaymentType) {
    val iconRes = when (paymentType) {
        PaymentType.BKASH -> R.drawable.bkash
        PaymentType.NAGAD -> R.drawable.nagad
        PaymentType.ROCKET -> R.drawable.rocket
        else -> null
    }

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated card icon
        var rotation by remember { mutableStateOf(0f) }
        var scale by remember { mutableStateOf(0.8f) }

        LaunchedEffect(Unit) {
            rotation = 360f
            scale = 1f
        }

        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = when (paymentType) {
                        PaymentType.BKASH -> Color(0xFFE2136E)
                        PaymentType.NAGAD -> Color(0xFFF15A29)
                        PaymentType.ROCKET -> Color(0xFF00AEEF)
                        else -> DarkPink
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = paymentType.name,
                    modifier = Modifier.size(60.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    when (paymentType) {
                        PaymentType.BKASH -> "Bkash"
                        PaymentType.NAGAD -> "Nagad"
                        PaymentType.ROCKET -> "Rocket"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Processing ${when (paymentType) {
                PaymentType.BKASH -> "Bkash"
                PaymentType.NAGAD -> "Nagad"
                PaymentType.ROCKET -> "Rocket"
                else -> ""
            }} Payment",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Please wait...",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF666666)
            )
        )
    }
}
fun getPaymentNumber(paymentType: PaymentType): String {
    return when (paymentType) {
        PaymentType.BKASH -> "01970102586"
        PaymentType.NAGAD -> "01988143409"
        PaymentType.ROCKET -> "017463246207"
        else -> ""
    }
}

fun getPaymentNumberForDisplay(paymentType: PaymentType): String {
    return when (paymentType) {
        PaymentType.BKASH -> "Bkash: 01970102586"
        PaymentType.NAGAD -> "Nagad: 01988143409"
        PaymentType.ROCKET -> "Rocket: 017463246207"
        else -> ""
    }
}

@Composable
fun PaymentOptionCard(
    title: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Use the image resource directly
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Select",
                tint = Color(0xFF666666)
            )
        }
    }
}

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
fun EditUserNoteButton(
    hasUserNote: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = if (hasUserNote) {
                    Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF66BB6A)))
                } else {
                    Brush.horizontalGradient(listOf(DarkPink, DarkPink.copy(alpha = 0.85f)))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (hasUserNote) Icons.Default.Check else Icons.Default.Edit,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            if (hasUserNote) "Note Added" else "Add Note",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        )
    }
}

@Composable
fun EditUserNoteDialog(
    initialUserNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var userNoteText by remember { mutableStateOf(initialUserNote) }
    val maxLength = 200

    // Gentle pop-in animation to match the app's other dialogs
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "userNoteDialogScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFF3E0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Notes,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Add Note",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF999999)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "Any special request for the restaurant or delivery rider? (e.g. less spicy, no onions)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = userNoteText,
                    onValueChange = { if (it.length <= maxLength) userNoteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    placeholder = { Text("Type your note for the restaurant here...") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkPink,
                        focusedLabelColor = DarkPink
                    )
                )

                Text(
                    "${userNoteText.length}/$maxLength",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF999999),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialUserNote.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onSave("") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFDC2626)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Text("Remove")
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF5F5F5),
                                contentColor = Color(0xFF666666)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                    }

                    Button(
                        onClick = { onSave(userNoteText) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkPink,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save Note", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
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
                    0 -> Color(0xFFFFD700)
                    1 -> Color(0xFFFF6B6B)
                    2 -> Color(0xFF4ECDC4)
                    3 -> Color(0xFFFFE66D)
                    4 -> Color(0xFF95E1D3)
                    else -> Color(0xFFF38181)
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
fun VoucherListDialog(
    vouchers: List<VoucherInfo>,
    isLoading: Boolean,
    errorMessage: String?,
    onVoucherSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(DarkPink, DarkPink.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "উপলব্ধ ভাউচার",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            "সেরা অফারটি বেছে নিন",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text(
                            "\u2715",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 460.dp)
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = DarkPink, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "ভাউচার লোড হচ্ছে...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFDC2626),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        vouchers.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = Color(0xFFBDBDBD),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "এই মুহূর্তে কোনো ভাউচার উপলব্ধ নেই",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                vouchers.forEach { voucher ->
                                    VoucherCard(
                                        voucher = voucher,
                                        onUseClicked = { onVoucherSelected(voucher.code) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoucherCard(voucher: VoucherInfo, onUseClicked: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (voucher.isFreeDelivery) Color(0xFFE3F2FD) else Color(0xFFFFF3E0)
            )
            .border(
                width = 1.dp,
                color = if (voucher.isFreeDelivery) Color(0xFF90CAF9) else Color(0xFFFFCC80),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Code badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = if (voucher.isFreeDelivery) Color(0xFF2196F3) else DarkPink,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                voucher.code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (voucher.isFreeDelivery) Color(0xFF1565C0) else DarkPink
                )
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (voucher.isFreeDelivery) "\uD83D\uDE9A ফ্রি ডেলিভারি" else "\uD83C\uDF89 ${voucher.discountPercent.toInt()}% ছাড়",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "৳${"%.0f".format(voucher.minOrderAmount)} টাকার বেশি অর্ডারে প্রযোজ্য",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF757575)
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onUseClicked,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (voucher.isFreeDelivery) Color(0xFF2196F3) else DarkPink,
                contentColor = Color.White
            )
        ) {
            Text("ব্যবহার করুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PriceRow(label: String, amount: Double, isTotal: Boolean = false, isDiscount: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isTotal -> DarkPink
                isDiscount -> Color(0xFF2E7D32)
                else -> Color(0xFF666666)
            },
            fontSize = if (isTotal) 18.sp else 16.sp
        )
        Text(
            if (isDiscount) "-৳${"%.0f".format(-amount)}" else "৳$amount",
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                isTotal -> DarkPink
                isDiscount -> Color(0xFF2E7D32)
                else -> Color(0xFF333333)
            },
            fontSize = if (isTotal) 18.sp else 16.sp
        )
    }
}

@Composable
fun OrderSentAnimation(onAnimationComplete: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        startAnimation = true
        delay(3500)
        onAnimationComplete()
    }

    val checkScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkScale"
    )

    val circleScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circleScale"
    )

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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
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