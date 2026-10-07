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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.amber.ui.theme.SampleAppTheme
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private val waveformHeights =
    listOf(8, 14, 10, 18, 12, 22, 9, 16, 11, 20, 8, 15, 13, 19, 10, 17, 9, 14, 12, 18)

@Composable
fun PatientMemoryDetailScreen(
    post: PatientMemoryPost,
    relatedPosts: List<PatientMemoryPost>,
    onBack: () -> Unit,
    onShowMore: () -> Unit,
    onShowLess: () -> Unit,
    onOpenRelated: (String) -> Unit,
    onHome: () -> Unit,
    onJournal: () -> Unit = {},
    homeSelected: Boolean = true,
    modifier: Modifier = Modifier,
) {
  var playing by remember(post.id) { mutableStateOf(false) }
  val scrollState = rememberScrollState()
  LaunchedEffect(post.id, relatedPosts) {
    if (relatedPosts.isNotEmpty()) {
      snapshotFlow { scrollState.maxValue }.filter { it > 0 }.first()
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }
  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = Color.White,
      bottomBar = {
        PatientBottomBar(onHome = onHome, onJournal = onJournal, homeSelected = homeSelected)
      },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).background(Color.White)) {
      MemoryDetailHeader(onBack = onBack)
      Column(
          Modifier.fillMaxWidth()
              .weight(1f)
              .verticalScroll(scrollState)
              .padding(horizontal = 16.dp),
      ) {
        Spacer(Modifier.height(12.dp))
        PatientSearchBar()
        Spacer(Modifier.height(16.dp))
        AuthorRow(name = post.authorName)
        Spacer(Modifier.height(12.dp))
        AudioRow(playing = playing, onPlay = { playing = true }, onStop = { playing = false })
        Spacer(Modifier.height(12.dp))
        MemoryPicture(label = "The selected post", height = 280.dp)
        Spacer(Modifier.height(12.dp))
        StoryCopy(post = post)
        Spacer(Modifier.height(16.dp))
        PatientShowMoreRow(onShowMore = onShowMore, onShowLess = onShowLess)
        RelatedPosts(posts = relatedPosts, onOpen = onOpenRelated)
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun MemoryDetailHeader(onBack: () -> Unit) {
  Column {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = "Back",
          tint = darkOrange,
          modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(8.dp),
      )
      Icon(
          imageVector = Icons.Filled.Home,
          contentDescription = null,
          tint = darkOrange,
          modifier = Modifier.size(22.dp),
      )
      Text(
          text = "Home - Memories",
          color = darkOrange,
          fontSize = 22.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(start = 8.dp),
      )
    }
    PatientSectionDivider()
  }
}

@Composable
private fun AuthorRow(name: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = orange,
        modifier = Modifier.size(40.dp),
    )
    Text(text = name, color = orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun AudioRow(playing: Boolean, onPlay: () -> Unit, onStop: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Box(
        modifier =
            Modifier.size(30.dp).background(lightOrange, CircleShape).clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = "Play",
          tint = Color.White,
          modifier = Modifier.size(18.dp),
      )
    }
    Waveform(active = playing, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
    Box(
        modifier =
            Modifier.size(30.dp).border(1.dp, lightGray, CircleShape).clickable(onClick = onStop),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Filled.Stop,
          contentDescription = "Stop",
          tint = darkGray,
          modifier = Modifier.size(14.dp),
      )
    }
  }
}

@Composable
private fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
  val color = if (active) lightOrange else Color(0xFFBDBDBD)
  Row(
      modifier = modifier.height(25.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    waveformHeights.forEach { barHeight ->
      Box(
          Modifier.width(3.dp).height(barHeight.dp).background(color, RoundedCornerShape(2.dp)),
      )
    }
  }
}

@Composable
private fun StoryCopy(post: PatientMemoryPost) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Text(
        text = post.topic,
        color = darkOrange,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = post.onDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
        color = darkOrange,
        fontSize = 16.sp,
    )
  }
  Spacer(Modifier.height(8.dp))
  Text(text = post.description, color = darkOrange, fontSize = 16.sp)
}

@Composable
private fun RelatedPosts(posts: List<PatientMemoryPost>, onOpen: (String) -> Unit) {
  if (posts.isEmpty()) return
  Text(
      text = "Related",
      color = darkOrange,
      fontSize = 20.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(top = 20.dp),
  )
  posts.forEach { related ->
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onOpen(related.id) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      MemoryPicture(label = related.topic, height = 140.dp)
      Text(text = related.topic, color = darkOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      Text(text = related.summary, color = darkOrange, fontSize = 16.sp)
    }
  }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812, name = "Memory detail")
@Composable
private fun PatientMemoryDetailPreview() {
  SampleAppTheme {
    val posts = samplePatientMemories(LocalDate.of(2026, 10, 7))
    PatientMemoryDetailScreen(
        post = posts.first(),
        relatedPosts = posts.drop(1),
        onBack = {},
        onShowMore = {},
        onShowLess = {},
        onOpenRelated = {},
        onHome = {},
    )
  }
}
