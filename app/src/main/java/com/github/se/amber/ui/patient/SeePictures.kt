// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.orange

object SeePicturesScreenTestTags {
    const val TITLE = "pictures_title"
}

@Composable
fun SeePicturesScreen(
    pictureViewModel: SeePicturesViewModel = viewModel(),
    onBackClick: () -> Unit,
    onTabSelected: (Tab) -> Unit,
) {
    val uiState by pictureViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column(){
                TopNavigationMenu(Modifier, TopNavigationAction.CHECKOUT, onBackClick)
                Spacer(modifier = Modifier.height(PADDING_SMALL))
                Text(
                    modifier = Modifier.fillMaxWidth().testTag(SeePicturesScreenTestTags.TITLE),
                    text = "See Pictures",
                    textAlign = TextAlign.Center,
                    color = orange,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(PADDING_BIG))
            }
        },
        bottomBar = {
            BottomNavigationMenu(
                PatientTab.SeePictures,
                onTabSelected,
                PatientTab.patientTabs,
                Modifier
            )
        },
        content = { paddingValues ->


            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
            ) {

            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun SeePicturesScreenPreview() {
    AmberAppTheme {
        SeePicturesScreen(
            onBackClick = {},
            onTabSelected = {},
        )
    }
}
