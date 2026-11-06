package com.kairos.app.ui.screens.plan

import androidx.compose.runtime.Composable
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.screens.calendar.CalendarScreen

@Composable
fun PlanScreen(mainViewModel: MainViewModel) {
    CalendarScreen(mainViewModel = mainViewModel)
}
