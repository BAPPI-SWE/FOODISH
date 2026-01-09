// UserDetailsScreen.kt
package com.yumzy.userapp.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yumzy.userapp.features.location.LocationViewModel
import com.yumzy.userapp.ui.theme.DarkPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen(
    onSaveClicked: (name: String, phone: String, baseLocation: String, subLocation: String, building: String, floor: String, room: String) -> Unit,
    locationViewModel: LocationViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }

    val locationState by locationViewModel.uiState.collectAsState()
    var baseLocationExpanded by remember { mutableStateOf(false) }
    var subLocationExpanded by remember { mutableStateOf(false) }

    // Validation states
    var isPhoneError by remember { mutableStateOf(false) }
    var phoneErrorText by remember { mutableStateOf("") }

    // Validate form - phone, base location, and sub location are required
    val isFormValid = phone.isNotBlank() &&
            locationState.selectedBaseLocation.isNotBlank() &&
            locationState.selectedSubLocation.isNotBlank()

    // Validate phone number on change
    LaunchedEffect(phone) {
        isPhoneError = phone.isBlank()
        phoneErrorText = if (phone.isBlank()) "Phone number is required" else ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Complete Your Profile",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkPink
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "Please provide this information for accurate delivery.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = isPhoneError,
                supportingText = {
                    if (isPhoneError) {
                        Text(text = phoneErrorText, color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            ExposedDropdownMenuBox(
                expanded = baseLocationExpanded,
                onExpandedChange = { baseLocationExpanded = !baseLocationExpanded }
            ) {
                OutlinedTextField(
                    value = locationState.selectedBaseLocation,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Main Location *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = baseLocationExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = locationState.selectedBaseLocation.isBlank(),
                    supportingText = {
                        if (locationState.selectedBaseLocation.isBlank()) {
                            Text(text = "Main location is required", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = baseLocationExpanded,
                    onDismissRequest = { baseLocationExpanded = false }
                ) {
                    locationState.baseLocationOptions.forEach { location ->
                        DropdownMenuItem(
                            text = { Text(location) },
                            onClick = {
                                locationViewModel.onBaseLocationSelected(location)
                                baseLocationExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = subLocationExpanded,
                onExpandedChange = { subLocationExpanded = !subLocationExpanded }
            ) {
                OutlinedTextField(
                    value = locationState.selectedSubLocation,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Your Area *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subLocationExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = locationState.selectedSubLocation.isBlank(),
                    supportingText = {
                        if (locationState.selectedSubLocation.isBlank()) {
                            Text(text = "Area is required", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = subLocationExpanded,
                    onDismissRequest = { subLocationExpanded = false }
                ) {
                    if (locationState.subLocationOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No locations available") },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        locationState.subLocationOptions.forEach { location ->
                            DropdownMenuItem(
                                text = { Text(location) },
                                onClick = {
                                    locationViewModel.onSubLocationSelected(location)
                                    subLocationExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = building,
                onValueChange = { building = it },
                label = { Text("Building Name / Detail Home Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
//                OutlinedTextField(
//                    value = floor,
//                    onValueChange = { floor = it },
//                    label = { Text("Floor No.") },
//                    modifier = Modifier.weight(1f),
//                    shape = RoundedCornerShape(12.dp)
//                )
//                OutlinedTextField(
//                    value = room,
//                    onValueChange = { room = it },
//                    label = { Text("Room No.") },
//                    modifier = Modifier.weight(1f),
//                    shape = RoundedCornerShape(12.dp)
//                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onSaveClicked(
                        name, phone,
                        locationState.selectedBaseLocation,
                        locationState.selectedSubLocation,
                        building, floor, room
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkPink,
                    contentColor = Color.White
                ),
                enabled = isFormValid
            ) {
                Text(
                    "Save & Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}