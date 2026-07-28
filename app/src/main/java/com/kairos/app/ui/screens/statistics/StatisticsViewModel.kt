package com.kairos.app.ui.screens.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kairos.app.data.models.KairosFocusData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class StatisticsViewModel : ViewModel() {

    var statsRange by mutableStateOf(StatsRange.WEEK)

    data class ChartDataPoint(val label: String, val value: Float)

    fun getChartData(focusData: KairosFocusData, isFocusTime: Boolean): List<ChartDataPoint> {
        val now = LocalDate.now()
        val log = if (isFocusTime) focusData.dailyFocusLog else focusData.dailyTasksLog.mapValues { it.value.toLong() }
        
        return when (statsRange) {
            StatsRange.WEEK -> {
                (6 downTo 0).map { i ->
                    val date = now.minusDays(i.toLong())
                    val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    ChartDataPoint(
                        label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        value = log[key]?.toFloat() ?: 0f
                    )
                }
            }
            StatsRange.MONTH -> {
                val currentMonth = now.month
                val currentYear = now.year
                val weeks = mutableListOf<Float>(0f, 0f, 0f, 0f, 0f)
                
                log.forEach { (key, value) ->
                    try {
                        val date = LocalDate.parse(key)
                        if (date.month == currentMonth && date.year == currentYear) {
                            val weekIndex = ((date.dayOfMonth - 1) / 7).coerceAtMost(4)
                            weeks[weekIndex] += value.toFloat()
                        }
                    } catch (e: Exception) {}
                }
                
                weeks.mapIndexed { i, v -> ChartDataPoint("Wk ${i + 1}", v) }
            }
            StatsRange.YEAR -> {
                val currentYear = now.year
                val months = Array(12) { 0f }
                val monthLabels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                
                log.forEach { (key, value) ->
                    try {
                        val date = LocalDate.parse(key)
                        if (date.year == currentYear) {
                            months[date.monthValue - 1] += value.toFloat()
                        }
                    } catch (e: Exception) {}
                }
                
                months.mapIndexed { i, v -> ChartDataPoint(monthLabels[i], v) }
            }
        }
    }
}

enum class StatsRange { WEEK, MONTH, YEAR }
