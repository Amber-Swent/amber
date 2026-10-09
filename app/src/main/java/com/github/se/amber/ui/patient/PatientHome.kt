// Written by YANG Yuhan, with assistance from
// Grok (xAI) via Cursor.
package com.github.se.amber.ui.patient

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as semanticsTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.backOrange
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

object PatientHomeTestTags {
  const val TITLE = "patient_home_title"
  const val SEARCH_BUTTON = "patient_home_search_button"
  const val PICTURE = "patient_home_picture"
}

/**
 * Patient shell: checkout bar, bottom navigation, and the selected screen.
 *
 * @param onSearchClick callback invoked when search is selected.
 * @param onCheckout callback invoked when the user selects checkout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHome(onSearchClick: () -> Unit = {}, onCheckout: () -> Unit = {}) {
  var selectedTab by remember { mutableStateOf<PatientTab>(PatientTab.Home) }
  Scaffold(
      topBar = {
        TopNavigationMenu(action = TopNavigationAction.CHECKOUT, onBackClick = onCheckout)
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = selectedTab,
            onTabSelected = { tab -> if (tab is PatientTab) selectedTab = tab },
            tabs = PatientTab.patientTabs,
        )
      },
  ) { padding ->
    when (selectedTab) {
      PatientTab.Home -> PatientHomeContent(onSearchClick, Modifier.padding(padding))
      PatientTab.SeePictures -> PatientPictures(Modifier.padding(padding))
    }
  }
}

/**
 * Home screen content: title, search, and one picture placeholder.
 *
 * @param onSearchClick callback invoked when search is selected.
 * @param modifier modifier applied to the screen content.
 */
@Composable
private fun PatientHomeContent(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = PADDING_BIG)) {
    Spacer(modifier = Modifier.height(PADDING_SMALL))
    Text(
        text = "You are on Home",
        modifier = Modifier.fillMaxWidth().testTag(PatientHomeTestTags.TITLE),
        color = orange,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(PADDING_MEDIUM))
    Button(
        onClick = onSearchClick,
        modifier = Modifier.fillMaxWidth().height(80.dp).testTag(PatientHomeTestTags.SEARCH_BUTTON),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PADDING_SMALL),
      ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = "Search",
            modifier = Modifier.size(24.dp),
            tint = Color.Black,
        )
        Text("Search", color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Medium)
      }
    }
    Spacer(modifier = Modifier.height(PADDING_SMALL))
    Box(
        modifier =
            Modifier.fillMaxWidth().height(273.dp).background(backOrange).semantics(
                mergeDescendants = true,
            ) {
              semanticsTag = PatientHomeTestTags.PICTURE
            },
    ) {
      Text(
          text = "One random Picture",
          modifier = Modifier.padding(start = 37.dp, top = 35.dp),
          color = Color.Black,
          fontSize = 14.sp,
      )
    }
  }
}
