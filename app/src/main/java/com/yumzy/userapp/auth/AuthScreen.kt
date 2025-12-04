package com.yumzy.userapp.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yumzy.userapp.R
import com.yumzy.userapp.ui.theme.DeepPink

@Composable
fun AuthScreen(
    onGoogleSignInClick: () -> Unit,
    onNavigateToEmailSignIn: () -> Unit,
    onNavigateToEmailSignUp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepPink),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Top section with logo and heading
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to Foodish🍕",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Let's satisfy those cravings together!",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.0f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_shopping_bag),
                    contentDescription = "Shopping Bag",
                    modifier = Modifier.size(100.dp)
                )
            }
        }
        // Bottom card for login options
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign up or Log in",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select your preferred method to continue",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(32.dp))

                // Continue with Google
                OutlinedButton(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Continue with Google",
                        modifier = Modifier.padding(start = 16.dp),
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Continue with Email (sign-in)
                Button(
                    onClick = onNavigateToEmailSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPink)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        tint = Color.White
                    )
                    Text(
                        text = "Sign In with Email",
                        modifier = Modifier.padding(start = 16.dp),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("or", color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))

                // Sign up with Email
                OutlinedButton(
                    onClick = onNavigateToEmailSignUp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Up with Email")
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AuthScreenPreview() {
    AuthScreen(
        onGoogleSignInClick = {},
        onNavigateToEmailSignIn = {},
        onNavigateToEmailSignUp = {}
    )
}
