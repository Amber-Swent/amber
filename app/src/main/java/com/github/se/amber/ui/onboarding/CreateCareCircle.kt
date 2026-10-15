// This file was written with the aid of AI.
package com.github.se.amber.ui.onboarding

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_REGULAR
import com.github.se.amber.ui.theme.SMALL_ROUND_CORNER
import com.github.se.amber.ui.theme.SURFACE_BIG
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

/** Stable tags for locating the form controls and validation message in UI tests. */
object CreateCareCircleTestTags {
  const val NAME_FIELD = "createCareCircle_nameField"
  const val NAME_ERROR = "createCareCircle_nameError"
  const val SUBMIT_BUTTON = "createCareCircle_submitButton"
}

/**
 * Displays the first care circle form and connects it to [viewModel].
 *
 * A blank name appears below the field. Repository failures are shown as a toast, while successful
 * creation calls [onCreated] once. The caller decides where to navigate afterward.
 *
 * @param modifier Modifier applied to the root care circle layout.
 * @param context Used to display repository failures as a toast.
 * @param onCreated Called after the repository creates the circle.
 * @param viewModel ViewModel holding the form state and handling creation.
 */
@Composable
fun CreateCareCircleScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onCreated: () -> Unit = {},
    viewModel: CreateCareCircleViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()

  // Keep one stable reference for the effect, but always update the value inside it to the newest
  // onCreated
  val currentOnCreated by rememberUpdatedState(onCreated)

  // Display submit errors when creating the circle
  LaunchedEffect(viewModel) {
    viewModel.errorEvents.collect { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  // LaunchedEffect depends only on the ViewModel since depending on the lambda onCreated
  // can make the compose receive a new lambda on recomposition and therefore triggering a new
  // launch
  LaunchedEffect(viewModel) { viewModel.successEvents.collect { currentOnCreated() } }

  CreateCareCircleContent(
      uiState = uiState,
      onNameChange = viewModel::onNameChange,
      onSubmit = viewModel::createCareCircle,
      modifier = modifier,
  )
}

/**
 * Renders the care circle name field and Create button without owning submission state.
 *
 * The caller supplies the current state and handles user actions through callbacks.
 *
 * @param modifier Modifier applied to the root form layout.
 * @param uiState Name, validation error, loading state, and creation status to display.
 * @param onNameChange Called when the name field changes.
 * @param onSubmit Called when the user presses Create.
 */
@Composable
internal fun CreateCareCircleContent(
    modifier: Modifier = Modifier,
    uiState: CreateCareCircleUiState = CreateCareCircleUiState(),
    onNameChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding() // Avoids drawing underneath Android's top system bar
              .navigationBarsPadding() // Avoid drawing underneath navigation area at the bottom
              .verticalScroll(rememberScrollState())
              .padding(horizontal = PADDING_BIG, vertical = PADDING_BIG),
      verticalArrangement = Arrangement.spacedBy(PADDING_BIG),
  ) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PADDING_REGULAR),
    ) {
      Surface(
          modifier = Modifier.size(SURFACE_BIG),
          shape = CircleShape,
          color = lightOrange,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
              "logo",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = Color.Black,
          )
        }
      }
      Text(
          "Amber",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
      )
    }

    Spacer(Modifier.height(PADDING_BIG))
    Text(
        text = "New Care Circle",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Column(verticalArrangement = Arrangement.spacedBy(PADDING_REGULAR)) {
      OutlinedTextField(
          value = uiState.name,
          onValueChange = onNameChange,
          enabled = !uiState.isLoading && !uiState.isCreated,
          modifier = Modifier.fillMaxWidth().testTag(CreateCareCircleTestTags.NAME_FIELD),
          label = { Text("Circle name") },
          placeholder = { Text("Name your care circle") },
          singleLine = true,
          supportingText =
              uiState.nameError?.let { error ->
                { Text(error, modifier = Modifier.testTag(CreateCareCircleTestTags.NAME_ERROR)) }
              },
          isError = uiState.nameError != null,
          shape = RoundedCornerShape(SMALL_ROUND_CORNER),
          colors =
              OutlinedTextFieldDefaults.colors(
                  unfocusedBorderColor = lightGray,
                  focusedBorderColor = darkGray,
                  focusedLabelColor = darkGray,
              ),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM)) {
      Button(
          onClick = onSubmit,
          enabled = !uiState.isLoading && !uiState.isCreated,
          modifier =
              Modifier.fillMaxWidth()
                  .height(PADDING_ENORMOUS)
                  .testTag(CreateCareCircleTestTags.SUBMIT_BUTTON),
          shape = RoundedCornerShape(SMALL_ROUND_CORNER),
          colors =
              ButtonDefaults.buttonColors(containerColor = lightOrange, contentColor = Color.Black),
      ) {
        if (uiState.isLoading) {
          Text("Creation...", style = MaterialTheme.typography.labelLarge)
        } else {
          Text("Create", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    Text(
        text = "You will be able to invite members to the circle after its creation.",
        modifier = Modifier.fillMaxWidth().padding(top = PADDING_MEDIUM),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun CreateCareCirclePreview() {
  AmberAppTheme(darkTheme = false, dynamicColor = false) {
    CreateCareCircleContent(
        uiState = CreateCareCircleUiState(),
        onNameChange = {},
        onSubmit = {},
    )
  }
}
