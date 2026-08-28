package com.kairos.app.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.components.PremiumButton
import com.kairos.app.ui.components.PremiumCard
import com.kairos.app.ui.navigation.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

data class CountryInfo(val name: String, val isoCode: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(mainViewModel: MainViewModel) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }
    var language by remember { mutableStateOf("English") }
    var timezone by remember { mutableStateOf("UTC") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background Gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 1000f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false // Forced navigation
            ) { page ->
                // Force recomposition on page change to trigger entrance animations
                val key = remember(page) { Any() }
                CompositionLocalProvider(LocalOnboardingPageKey provides key) {
                    when (page) {
                        0 -> PersonalizationPage(name, { name = it })
                        1 -> DateOfBirthPage(birthday, { birthday = it })
                        2 -> LocalizationPage(
                            selectedCountry = selectedCountry, 
                            onCountryChange = { 
                                selectedCountry = it
                                // Auto-reset timezone when country changes to a valid one
                                timezone = ""
                            },
                            language = language, 
                            onLanguageChange = { language = it },
                            timezone = timezone, 
                            onTimezoneChange = { timezone = it }
                        )
                        3 -> TutorialPage()
                    }
                }
            }

            // Bottom Navigation Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button or Page Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = { 
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } 
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    // Page Indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == i) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                    )
                                    .animateContentSize()
                            )
                        }
                    }
                }

                if (pagerState.currentPage < 3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Skip only shown if field is empty or just as a general alternative
                        TextButton(
                            onClick = { 
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } 
                            }
                        ) {
                            Text(
                                text = "Skip", 
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        FloatingActionButton(
                            onClick = { 
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } 
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                } else {
                    PremiumButton(
                        onClick = { 
                            mainViewModel.completeOnboarding(
                                name, 
                                birthday, 
                                selectedCountry?.name ?: "", 
                                language, 
                                timezone
                            )
                        },
                        modifier = Modifier.width(160.dp),
                        enabled = selectedCountry != null && timezone.isNotEmpty()
                    ) {
                        Text("Get Started", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

val LocalOnboardingPageKey = staticCompositionLocalOf { Any() }

@Composable
fun PersonalizationPage(name: String, onNameChange: (String) -> Unit) {
    OnboardingPageContainer(
        icon = Icons.Default.Face,
        title = "What should we call you?",
        description = "This helps us personalize your greetings and notifications."
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Display Name (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateOfBirthPage(birthday: String, onBirthdayChange: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    OnboardingPageContainer(
        icon = Icons.Default.Cake,
        title = "When is your birthday?",
        description = "We use your birth date to calculate age-based productivity benchmarks and milestone celebrations!"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = birthday.ifBlank { "Select Date" },
                color = if (birthday.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("UTC"))
                                .toLocalDate()
                            onBirthdayChange(date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun LocalizationPage(
    selectedCountry: CountryInfo?,
    onCountryChange: (CountryInfo) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    timezone: String,
    onTimezoneChange: (String) -> Unit
) {
    val allCountries = remember {
        Locale.getISOCountries().map {
            CountryInfo(Locale("", it).displayCountry, it)
        }.sortedBy { it.name }
    }
    
    val languages = remember {
        listOf("English", "Spanish", "French", "German", "Chinese", "Japanese", "Hindi", "Korean", "Portuguese", "Russian", "Arabic").sorted()
    }
    
    val countryTimezones = remember(selectedCountry) {
        if (selectedCountry == null) emptyList()
        else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.icu.util.TimeZone.getAvailableIDs(selectedCountry.isoCode).toList().sorted()
            } else {
                java.util.TimeZone.getAvailableIDs().filter { it.startsWith(selectedCountry.name.replace(" ", "_")) }.sorted()
            }
        }
    }

    OnboardingPageContainer(
        icon = Icons.Default.Public,
        title = "Localization",
        description = "Tell us where you are to localize your experience."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Searchable Country Dropdown
            OnboardingSearchableDropdown(
                label = "Country",
                options = allCountries,
                selected = selectedCountry?.name ?: "",
                onSelect = { onCountryChange(it) },
                itemContent = { it.name }
            )
            
            OnboardingDropdown(
                label = "Language", 
                options = languages, 
                selected = language, 
                onSelect = onLanguageChange
            )
            
            // Filtered Timezone Dropdown
            OnboardingDropdown(
                label = "Timezone", 
                options = countryTimezones, 
                selected = timezone, 
                onSelect = onTimezoneChange,
                enabled = selectedCountry != null,
                placeholder = if (selectedCountry == null) "Select Country First" else "Select Timezone"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OnboardingSearchableDropdown(
    label: String,
    options: List<T>,
    selected: String,
    onSelect: (T) -> Unit,
    itemContent: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isEmpty()) options
        else options.filter { itemContent(it).contains(searchQuery, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = if (expanded) searchQuery else selected,
            onValueChange = { 
                searchQuery = it
                if (!expanded) expanded = true 
            },
            label = { Text(label) },
            placeholder = { if (expanded) Text("Search...") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).heightIn(max = 280.dp)
        ) {
            if (filteredOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No results found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    onClick = { },
                    enabled = false
                )
            } else {
                filteredOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(itemContent(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                            searchQuery = ""
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String = ""
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        if (enabled) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface).heightIn(max = 280.dp)
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onSelect(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialPage() {
    var mockTaskCompleted by remember { mutableStateOf(false) }
    
    // Pulse animation for the mock task
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    OnboardingPageContainer(
        icon = Icons.Default.AutoAwesome,
        title = "Master Your Time",
        description = "Here's a quick tour of your new workspace."
    ) {
        PremiumCard(
            modifier = Modifier.scale(if (!mockTaskCompleted) pulseScale else 1f)
        ) {
            Column {
                Text(
                    text = "Tutorial Board",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = mockTaskCompleted,
                        onCheckedChange = { mockTaskCompleted = it }
                    )
                    Text(
                        text = "Complete your first task!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { mockTaskCompleted = !mockTaskCompleted }
                    )
                }
                
                AnimatedVisibility(
                    visible = mockTaskCompleted,
                    enter = fadeIn() + expandVertically()
                ) {
                    Text(
                        text = "✨ Great job! Notice how your stats update in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TutorialItem(Icons.Default.Schedule, "Focus Timer", "Log sessions to earn deep work rewards.")
                TutorialItem(Icons.Default.Explore, "Discovery", "Adopt routines from the global community.")
                TutorialItem(Icons.Default.EmojiEvents, "Achievements", "Unlock badges as you level up your life.")
            }
        }
    }
}

@Composable
fun TutorialItem(icon: ImageVector, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun OnboardingPageContainer(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    val key = LocalOnboardingPageKey.current
    var visible by remember(key) { mutableStateOf(false) }
    
    LaunchedEffect(key) {
        delay(100.milliseconds)
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 200)) + slideInVertically(tween(600, 200)) { it / 2 }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 400)) + slideInVertically(tween(600, 400)) { it / 2 }
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 600)) + slideInVertically(tween(600, 600)) { it / 2 }
        ) {
            content()
        }
    }
}
