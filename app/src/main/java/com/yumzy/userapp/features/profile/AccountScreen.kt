package com.yumzy.userapp.features.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.userapp.YLogoLoadingIndicator
import com.yumzy.userapp.ui.theme.DarkPink
import com.yumzy.userapp.ui.theme.DeepPink
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext

// Data class for clickable items
data class ClickableItem(
    val text: String,
    val icon: ImageVector,
    val uri: String? = null
)
data class UserProfileDetails(
    val name: String = "...",
    val email: String = "...",
    val photoUrl: String? = null,
    val phone: String = "...",
    val fullAddress: String = "..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onSignOut: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    refreshTrigger: Boolean
) {
    var userProfile by remember { mutableStateOf<UserProfileDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAppInfoDialog by remember { mutableStateOf(false) }

    // Animation for profile picture border
    val infiniteTransition = rememberInfiniteTransition(label = "border_animation")
    val animatedAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    LaunchedEffect(key1 = refreshTrigger) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            isLoading = true
            Firebase.firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val address = "${document.getString("subLocation")}, ${document.getString("baseLocation")}"
                        userProfile = UserProfileDetails(
                            name = document.getString("name") ?: (currentUser.displayName ?: "N/A"),
                            email = currentUser.email ?: "N/A",
                            photoUrl = currentUser.photoUrl?.toString(),
                            phone = document.getString("phone") ?: "N/A",
                            fullAddress = address
                        )
                    }
                    isLoading = false
                }.addOnFailureListener {
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }

    if (showAppInfoDialog) {
        ModernAppInfoDialog(onDismiss = { showAppInfoDialog = false })
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
                            "My Account",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(
                            onClick = { showAppInfoDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "App Info",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                YLogoLoadingIndicator(size = 40.dp, color = DeepPink)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFFF8F9FA))
            ) {
                // Profile Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture with gradient border
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(120.dp),
                            color = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = userProfile?.photoUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // Edit button overlay
                        Surface(
                            shape = CircleShape,
                            color = DarkPink,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp),
                            shadowElevation = 4.dp
                        ) {
                            IconButton(onClick = onNavigateToEditProfile) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Name and Email
                    Text(
                        text = userProfile?.name ?: "User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Spacer(Modifier.height(20.dp))
                }

                // Profile Information Section - Compact Design
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CompactInfoRow(
                                icon = Icons.Default.Person,
                                label = "Full Name",
                                value = userProfile?.name ?: "...",
                                iconColor = Color(0xFF2196F3)
                            )

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFE0E0E0)
                            )

                            CompactInfoRow(
                                icon = Icons.Default.Email,
                                label = "Email Address",
                                value = userProfile?.email ?: "...",
                                iconColor = Color(0xFFFF9800)
                            )

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFE0E0E0)
                            )

                            CompactInfoRow(
                                icon = Icons.Default.Phone,
                                label = "Phone Number",
                                value = userProfile?.phone ?: "...",
                                iconColor = Color(0xFF4CAF50)
                            )

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFE0E0E0)
                            )

                            CompactInfoRow(
                                icon = Icons.Default.LocationOn,
                                label = "Delivery Address",
                                value = userProfile?.fullAddress ?: "...",
                                iconColor = Color(0xFFF44336)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action Buttons
                    Text(
                        text = "Account Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )

                    Button(
                        onClick = onNavigateToEditProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkPink
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 6.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Edit Profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFF44336)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF44336)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Sign Out",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun CompactInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF757575),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ModernAppInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo/Header Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(DarkPink, DeepPink)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {


                        Text(
                            text = "Yumzy",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 28.sp
                        )

                        Text(
                            text = "Food & Grocery Delivery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Version 2.0.0",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Info Cards Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Platform Info Card
                    InfoCard(
                        icon = Icons.Default.Smartphone,
                        iconColor = Color(0xFF2196F3),
                        title = "Platform",
                        items = listOf(
                            "Android • Web" to Icons.Default.Check
                        )
                    )

                    // Contact Info Card
                    ClickableInfoCard(
                        icon = Icons.Default.Phone,
                        iconColor = Color(0xFF4CAF50),
                        title = "Helpline",
                        items = listOf(
                            ClickableItem("+880 1590093644", Icons.Default.Chat, "https://wa.me/8801590093644")
                        )
                    )

                    // Developer Info Card
                    ClickableInfoCard(
                        icon = Icons.Default.Code,
                        iconColor = Color(0xFF9C27B0),
                        title = "Developer",
                        items = listOf(
                            ClickableItem("BAPPI", Icons.Default.Person, null),
                            ClickableItem("bappi616@gmail.com", Icons.Default.Email, "mailto:bappi616@gmail.com"),
                            ClickableItem("linkedin.com/in/bappi-swe", Icons.Default.Link, "https://linkedin.com/in/bappi-swe")
                        )
                    )

                    // Release Info Card
                    InfoCard(
                        icon = Icons.Default.CalendarMonth,
                        iconColor = Color(0xFFFF9800),
                        title = "Release",
                        items = listOf(
                            "October 2025" to Icons.Default.NewReleases
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DarkPink.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = DarkPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Thank you for choosing Yumzy! We're committed to delivering excellence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF616161),
                            lineHeight = 18.sp,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkPink
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    "Close",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun ClickableInfoCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    items: List<ClickableItem>
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    fontSize = 16.sp
                )
            }

            // Card Items
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (item.uri != null) {
                                    Modifier.clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.uri))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Handle error silently
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = iconColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )

                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.uri != null) iconColor else Color(0xFF424242),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (item.uri != null) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = iconColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 28.dp),
                            thickness = 0.5.dp,
                            color = Color(0xFFEEEEEE)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    items: List<Pair<String, ImageVector>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    fontSize = 16.sp
                )
            }

            // Card Items
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, (text, itemIcon) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = iconColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )

                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF424242),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 28.dp),
                            thickness = 0.5.dp,
                            color = Color(0xFFEEEEEE)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun DialogInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF666666)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF333333)
        )
    }
}