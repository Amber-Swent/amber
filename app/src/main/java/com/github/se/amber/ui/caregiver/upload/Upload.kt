/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Upload(
    viewModel: UploadViewModel = viewModel(),
    uploadText: () -> Unit = {},
    uploadPicture: () -> Unit = {},
    uploadAudio: () -> Unit = {},
    goBack: () -> Unit = {},
    // TODO : create NavigationActions
    // navigationActions: NavigationActions? = null,
) {
  val uiState by viewModel.uiState.collectAsState()
  val errorMessage = uiState.errorMsg
  val context = LocalContext.current

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = { TopNavigationMenu(Modifier, TopNavigationAction.CHECKOUT, goBack) },
      // TODO: unExtract the following lines once Navigation is implement
      //      bottomBar = { BottomNavigationMenu(selectedTab = CaregiverTab.Upload,
      //          onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) }, )
      //      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text = "Upload ",
              color = orange,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )

          Spacer(modifier = Modifier.height(16.dp))

          DisplayButton(
              icon = Icons.Outlined.Description,
              text = "Upload Text",
              description = "Text",
              onClick = { uploadText() },
          )

          DisplayButton(
              icon = Icons.Outlined.PhotoCamera,
              text = "Upload Picture",
              description = "Picture",
              onClick = { uploadPicture() },
          )

          DisplayButton(
              icon = Icons.Outlined.Mic,
              text = "Upload Audio",
              description = "Audio",
              onClick = { uploadAudio() },
          )
        }
      },
  )
}

@Composable
fun DisplayButton(icon: ImageVector, text: String, description: String, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text, color = Color.Black)
      Icon(icon, contentDescription = description, tint = Color.Black)
    }
  }
}
