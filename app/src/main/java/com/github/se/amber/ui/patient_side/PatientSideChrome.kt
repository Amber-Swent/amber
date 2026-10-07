// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.amber.R
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

@Composable
fun PatientSearchBar(modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(40.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(lightOrange)
              .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = "Search",
        tint = darkOrange,
        modifier = Modifier.size(24.dp),
    )
    Text(
        text = "Search",
        color = darkOrange,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1f),
    )
    Icon(
        imageVector = Icons.Outlined.Mic,
        contentDescription = "Voice search",
        tint = darkOrange,
        modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
fun PatientShowMoreRow(
    onShowMore: () -> Unit,
    onShowLess: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    ShowChoiceButton("show less", onShowLess, Modifier.weight(1f))
    ShowChoiceButton("show more", onShowMore, Modifier.weight(1f))
  }
}

@Composable
private fun ShowChoiceButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(lightOrange)
              .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = label, color = darkOrange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun PatientBottomBar(
    onHome: () -> Unit,
    onJournal: () -> Unit = {},
    homeSelected: Boolean = true,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().height(56.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        Icons.Filled.Home,
        contentDescription = "Home",
        tint = if (homeSelected) orange else lightOrange,
        modifier = Modifier.size(28.dp).clickable(onClick = onHome),
    )
    Icon(
        Icons.AutoMirrored.Outlined.MenuBook,
        contentDescription = "Journal",
        tint = if (homeSelected) lightOrange else orange,
        modifier = Modifier.size(28.dp).clickable(onClick = onJournal),
    )
    Icon(
        painterResource(R.drawable.ic_ai_chatbot),
        contentDescription = "AI Chatbot",
        tint = Color.Unspecified,
        modifier = Modifier.size(28.dp),
    )
    Icon(
        Icons.Outlined.SportsEsports,
        contentDescription = "Games",
        tint = lightOrange,
        modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
fun PatientSectionDivider(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(lightGray))
}
