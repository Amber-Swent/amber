// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.parameters

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

/** Test tags of [PatientParameterScreen]. */
object PatientParameterTestTags {
  const val TITLE = "PatientParameter_test_tag_title"
  const val SIGN_OUT_BUTTON = "PatientParameter_test_tag_sign_out_button"
}

/**
 * Patient parameters screen.
 *
 * For now it only exposes navigation to the shared sign-out confirmation screen.
 *
 * @param viewModel ViewModel providing the UI state of the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 * @param onSignOutClick Callback invoked when the user chooses to open the sign-out screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientParameterScreen(
    viewModel: PatientParameterViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
    onSignOutClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              DisplayPatientTitle(
                  title = stringResource(R.string.patientParameterTitle),
                  testTag = PatientParameterTestTags.TITLE,
              )
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.Setting,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(top = PATIENT_PADDING_ENORMOUS)
                    .padding(horizontal = PATIENT_PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          DisplayPatientButton(
              text = stringResource(R.string.patientParameterSignOut),
              onClick = onSignOutClick,
              testTag = PatientParameterTestTags.SIGN_OUT_BUTTON,
          )
        }
      },
  )
}

/** Preview of [PatientParameterScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun PatientParameterScreenPreview() {
  AmberAppTheme(dynamicColor = false) { PatientParameterScreen() }
}
