// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.ChangeCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.amber.ui.theme.SampleAppTheme
import com.github.se.amber.ui.theme.backOrange
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
private val arrowBackground = Color(0xFFF9F9F9)

@Composable
fun PatientHomeScreen(
    post: PatientMemoryPost,
    visibleMonth: YearMonth,
    markedDates: Set<LocalDate>,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onShowMore: () -> Unit,
    onShowLess: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onHome: () -> Unit,
    onJournal: () -> Unit = {},
    homeSelected: Boolean = true,
    modifier: Modifier = Modifier,
) {
  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = Color.White,
      bottomBar = {
        PatientBottomBar(onHome = onHome, onJournal = onJournal, homeSelected = homeSelected)
      },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).background(Color.White)) {
      PatientHomeHeader()
      Column(
          Modifier.fillMaxWidth()
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 16.dp),
      ) {
        Spacer(Modifier.height(12.dp))
        PatientSearchBar()
        Spacer(Modifier.height(16.dp))
        MemoryPicture(
            label = "One random Picture",
            height = 203.dp,
            modifier = Modifier.clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(12.dp))
        FeaturedStoryText(post = post, onOpen = onOpen, onRefresh = onRefresh)
        Spacer(Modifier.height(12.dp))
        PatientShowMoreRow(onShowMore = onShowMore, onShowLess = onShowLess)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Days we had",
            color = darkOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        MonthCalendar(
            month = visibleMonth,
            markedDates = markedDates,
            onMonthChange = onMonthChange,
            onDayClick = onDayClick,
        )
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun PatientHomeHeader() {
  Column {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
          imageVector = Icons.Filled.Home,
          contentDescription = null,
          tint = darkOrange,
          modifier = Modifier.size(24.dp),
      )
      Text(
          text = "Home",
          color = darkOrange,
          fontSize = 24.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
    PatientSectionDivider()
  }
}

@Composable
internal fun MemoryPicture(label: String, height: Dp, modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.fillMaxWidth().height(height).background(backOrange),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = label, color = darkOrange, fontSize = 12.sp, textAlign = TextAlign.Center)
  }
}

@Composable
private fun FeaturedStoryText(
    post: PatientMemoryPost,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(
        Modifier.weight(1f).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(text = post.topic, color = darkOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      Text(text = post.summary, color = darkOrange, fontSize = 16.sp)
    }
    Icon(
        imageVector = Icons.Outlined.ChangeCircle,
        contentDescription = "Another story",
        tint = darkOrange,
        modifier = Modifier.size(32.dp).clickable(onClick = onRefresh),
    )
  }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth().border(1.dp, lightGray, RoundedCornerShape(12.dp)).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)),
          color = darkOrange,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
      )
      CalendarArrow(image = Icons.AutoMirrored.Filled.KeyboardArrowLeft, label = "Previous month") {
        onMonthChange(month.minusMonths(1))
      }
      Spacer(Modifier.size(8.dp))
      CalendarArrow(image = Icons.AutoMirrored.Filled.KeyboardArrowRight, label = "Next month") {
        onMonthChange(month.plusMonths(1))
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      weekdayLabels.forEach { label ->
        Text(
            text = label,
            color = darkGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(32.dp),
        )
      }
    }
    daysInMonthGrid(month).chunked(7).forEach { week ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { date ->
          CalendarDayCell(
              date = date,
              inMonth = YearMonth.from(date) == month,
              marked = date in markedDates,
              onClick = { onDayClick(date) },
          )
        }
      }
    }
  }
}

@Composable
private fun CalendarArrow(
    image: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
  Box(
      modifier =
          Modifier.size(24.dp)
              .background(arrowBackground, RoundedCornerShape(12.dp))
              .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = image,
        contentDescription = label,
        tint = darkOrange,
        modifier = Modifier.size(16.dp),
    )
  }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
) {
  Box(
      modifier =
          Modifier.size(32.dp)
              .then(
                  if (marked) Modifier.border(1.5.dp, darkOrange, CircleShape) else Modifier,
              )
              .clickable(enabled = marked, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = date.dayOfMonth.toString(),
        color = darkOrange.copy(alpha = if (inMonth) 1f else 0.35f),
        fontSize = 13.sp,
    )
  }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812, name = "Patient home")
@Composable
private fun PatientHomePreview() {
  val today = LocalDate.of(2026, 10, 7)
  val posts = samplePatientMemories(today)
  SampleAppTheme {
    PatientHomeScreen(
        post = posts.first(),
        visibleMonth = YearMonth.from(today),
        markedDates = markedDates(today, posts),
        onOpen = {},
        onRefresh = {},
        onShowMore = {},
        onShowLess = {},
        onMonthChange = {},
        onDayClick = {},
        onHome = {},
    )
  }
}

private fun daysInMonthGrid(month: YearMonth): List<LocalDate> {
  val first = month.atDay(1)
  val leading = first.dayOfWeek.value % 7
  val start = first.minusDays(leading.toLong())
  val rowCount = (leading + month.lengthOfMonth() + 6) / 7
  return List(rowCount * 7) { start.plusDays(it.toLong()) }
}
