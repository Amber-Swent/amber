/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */

package com.github.se.amber.ui.parameters

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.SMALL_ROUND_CORNER
import com.github.se.amber.ui.theme.lightOrange
import java.text.DateFormat
import java.util.Date

// Define A lot of values here to adjust the components more easily
private val screenHorizontalPadding = 30.dp
private val contentTopPadding = 30.dp
private val createCardWidth = 160.dp
private val createCardHeight = 125.dp
private val hintBoxWidth = 150.dp
private val hintBoxHeight = 103.dp
private val sectionGap = 50.dp
private val codeListHeight = 350.dp
private val invitationBorder = Color(0xFFD32F2F)

object CreateInvitationTestTags {
  const val TITLE = "create_invitation_title"
  const val CREATE_CARD = "create_invitation_card"
  const val HINT_BOX = "create_invitation_hint_box"
  const val USAGE_TEXT = "create_invitation_usage_text"
  const val CODE_LIST = "create_invitation_code_list"
  const val CIRCLE_CHOICES = "create_invitation_circle_choices"
  const val ERROR_MESSAGE = "create_invitation_error_message"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvitationScreen(
    viewModel: CreateInvitationViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = { tab -> },
    onCreateInvitationClick: () -> Unit = {},
    checkout: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CHECKOUT,
              onBackClick = checkout,
          )
          DisplayCaregiverTitle("Parameters", Modifier.testTag(CreateInvitationTestTags.TITLE))
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            // The Setting tab arrives with the parameters home screen.
            selectedTab = CaregiverTab.Home,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
        )
      },
      content = { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          Column(
              modifier =
                  Modifier.fillMaxSize()
                      .padding(horizontal = screenHorizontalPadding)
                      .padding(top = contentTopPadding),
          ) {
            // Not sure should I keep this part or not. Cause it can be in further weeks.
            if (uiState.circles.size > 1) {
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .horizontalScroll(rememberScrollState())
                          .testTag(CreateInvitationTestTags.CIRCLE_CHOICES),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                uiState.circles.forEach { circle ->
                  val selected = circle.id == uiState.selectedCircleId
                  TextButton(
                      onClick = { viewModel.selectCircle(circle.id) },
                      enabled = !uiState.isCreating,
                      modifier = Modifier.testTag("create_invitation_circle_${circle.id}"),
                  ) {
                    Text(
                        text = circle.name,
                        color = if (selected) invitationBorder else Color.Black,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
              Button(
                  onClick = {
                    onCreateInvitationClick()
                    viewModel.createInvitation()
                  },
                  enabled = !uiState.isCreating,
                  modifier =
                      Modifier.width(createCardWidth)
                          .height(createCardHeight)
                          .testTag(CreateInvitationTestTags.CREATE_CARD),
                  shape = RoundedCornerShape(SMALL_ROUND_CORNER),
                  colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
                  contentPadding = PaddingValues(0.dp),
              ) {
                Column(modifier = Modifier.fillMaxSize().padding(top = 33.dp)) {
                  Icon(
                      Icons.Outlined.GroupAdd,
                      contentDescription = "Create invitation",
                      modifier = Modifier.size(30.dp).align(Alignment.CenterHorizontally),
                      tint = Color.Black,
                  )
                  Text(
                      text = "Create invitation",
                      modifier = Modifier.padding(start = 13.dp, top = 15.dp),
                      color = Color.Black,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              Text(
                  text = uiState.generatedCode ?: "Click the left button to create a code",
                  modifier =
                      Modifier.padding(top = 8.dp)
                          .width(hintBoxWidth)
                          .height(hintBoxHeight)
                          .border(1.dp, invitationBorder, RoundedCornerShape(SMALL_ROUND_CORNER))
                          .padding(horizontal = 16.dp, vertical = 8.dp)
                          .testTag(CreateInvitationTestTags.HINT_BOX),
                  color = Color.Black,
                  fontSize = 15.sp,
                  lineHeight = 21.sp,
              )
            }
            Spacer(modifier = Modifier.height(sectionGap))
            Text(
                text = "This code can only be used once.\nIt's active 30 days after its creation.",
                modifier = Modifier.fillMaxWidth().testTag(CreateInvitationTestTags.USAGE_TEXT),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
            uiState.errorMessage?.let { message ->
              Text(
                  text = message,
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(top = 8.dp)
                          .testTag(CreateInvitationTestTags.ERROR_MESSAGE),
                  color = invitationBorder,
                  fontSize = 14.sp,
                  textAlign = TextAlign.Center,
              )
            }

            Spacer(modifier = Modifier.height(sectionGap))
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(codeListHeight)
                        .border(1.dp, invitationBorder, RoundedCornerShape(SMALL_ROUND_CORNER))
                        .verticalScroll(rememberScrollState())
                        .padding(top = 15.dp)
                        .testTag(CreateInvitationTestTags.CODE_LIST),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  text = "Code | Creation  Time | Status",
                  color = Color.Black,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.SemiBold,
                  textAlign = TextAlign.Center,
              )
              uiState.invitations.forEach { invitation ->
                Text(
                    text =
                        "${invitation.code} | ${formatCreatedAt(invitation.createdAt)} | ${invitation.status}",
                    color = Color.Black,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
              }
            }
          }
        }
      },
  )
}

private fun formatCreatedAt(createdAtMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMillis))

/** Preview of [CreateInvitationScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = false)
@Composable
private fun CreateInvitationScreenPreview() {
  AmberAppTheme(dynamicColor = false) { CreateInvitationScreen() }
}
