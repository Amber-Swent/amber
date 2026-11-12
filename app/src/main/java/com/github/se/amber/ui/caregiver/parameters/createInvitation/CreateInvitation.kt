// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex

package com.github.se.amber.ui.caregiver.parameters.createInvitation

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.SMALL_ROUND_CORNER
import com.github.se.amber.ui.theme.lightOrange
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// Define A lot of values here to adjust the components more easily
private val screenHorizontalPadding = 30.dp
private val contentTopPadding = 30.dp
private val createCardWidth = 160.dp
private val createCardHeight = 125.dp
private val hintBoxWidth = 150.dp
private val hintBoxHeight = 103.dp
private val sectionGap = 50.dp
private val codeListHeight = 350.dp
private val codeListTopPadding = 15.dp
private val createIconSize = 30.dp
private val invitationBorder = Color(0xFFD32F2F)

object CreateInvitationTestTags {
  const val TITLE = "create_invitation_title"
  const val CREATE_CARD = "create_invitation_card"
  const val HINT_BOX = "create_invitation_hint_box"
  const val USAGE_TEXT = "create_invitation_usage_text"
  const val CODE_LIST = "create_invitation_code_list"
  const val CIRCLE_CHOICES = "create_invitation_circle_choices"
  const val ERROR_MESSAGE = "create_invitation_error_message"

  // one per invitation, its text starting with the code
  fun codeRow(code: String) = "create_invitation_code_row_$code"

  fun circleChoice(circleId: String) = "create_invitation_circle_$circleId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvitationScreen(
    viewModel: CreateInvitationViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = { _ -> },
    checkout: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  CreateInvitationContent(
      uiState = uiState,
      onSelectCircle = viewModel::selectCircle,
      onCreateInvitation = viewModel::createInvitation,
      onTabSelected = onTabSelected,
      checkout = checkout,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInvitationContent(
    uiState: CreateInvitationUiState,
    onSelectCircle: (String) -> Unit,
    onCreateInvitation: () -> Unit,
    onTabSelected: (Tab) -> Unit = { _ -> },
    checkout: () -> Unit = {},
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = checkout,
                )
                DisplayCaregiverTitle(
                    Modifier.testTag(CreateInvitationTestTags.TITLE),
                    stringResource(R.string.createInvitationTitle),
                )
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Setting,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        val locale = LocalConfiguration.current.locales[0]
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          Column(
              modifier =
                  Modifier.fillMaxSize()
                      .padding(horizontal = screenHorizontalPadding)
                      .padding(top = contentTopPadding),
          ) {
            if (uiState.circles.size > 1) {
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .horizontalScroll(rememberScrollState())
                          .testTag(CreateInvitationTestTags.CIRCLE_CHOICES),
                  horizontalArrangement = Arrangement.spacedBy(PADDING_SMALL),
              ) {
                uiState.circles.forEach { circle ->
                  val selected = circle.id == uiState.selectedCircleId
                  TextButton(
                      onClick = { onSelectCircle(circle.id) },
                      enabled = !uiState.isCreating,
                      modifier = Modifier.testTag(CreateInvitationTestTags.circleChoice(circle.id)),
                  ) {
                    Text(
                        text = circle.name,
                        color = if (selected) invitationBorder else Color.Black,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(PADDING_SMALL))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
              Button(
                  onClick = { onCreateInvitation() },
                  enabled = !uiState.isCreating,
                  modifier =
                      Modifier.width(createCardWidth)
                          .height(createCardHeight)
                          .testTag(CreateInvitationTestTags.CREATE_CARD),
                  shape = RoundedCornerShape(SMALL_ROUND_CORNER),
                  colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
              ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(PADDING_SMALL, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                      Icons.Outlined.GroupAdd,
                      contentDescription = stringResource(R.string.createInvitationButton),
                      modifier = Modifier.size(createIconSize),
                      tint = Color.Black,
                  )
                  Text(
                      text = stringResource(R.string.createInvitationButton),
                      color = Color.Black,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              Text(
                  text =
                      uiState.generatedCode ?: stringResource(R.string.createInvitationPlaceholder),
                  modifier =
                      Modifier.padding(top = PADDING_SMALL)
                          .width(hintBoxWidth)
                          .height(hintBoxHeight)
                          .border(
                              DIVIDER_THICKNESS,
                              invitationBorder,
                              RoundedCornerShape(SMALL_ROUND_CORNER),
                          )
                          .padding(horizontal = PADDING_MEDIUM, vertical = PADDING_SMALL)
                          .testTag(CreateInvitationTestTags.HINT_BOX),
                  color = Color.Black,
                  fontSize = 15.sp,
                  lineHeight = 21.sp,
              )
            }
            Spacer(modifier = Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.createInvitationUsage),
                modifier = Modifier.fillMaxWidth().testTag(CreateInvitationTestTags.USAGE_TEXT),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
            uiState.errorMsgRes?.let { errorMsgRes ->
              Text(
                  text = stringResource(errorMsgRes),
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(top = PADDING_SMALL)
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
                        .border(
                            DIVIDER_THICKNESS,
                            invitationBorder,
                            RoundedCornerShape(SMALL_ROUND_CORNER),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(top = codeListTopPadding)
                        .testTag(CreateInvitationTestTags.CODE_LIST),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  text = stringResource(R.string.createInvitationListHeader),
                  color = Color.Black,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.SemiBold,
                  textAlign = TextAlign.Center,
              )
              uiState.invitations.forEach { invitation ->
                Text(
                    text =
                        stringResource(
                            R.string.createInvitationListRow,
                            invitation.code,
                            formatCreatedAt(invitation.createdAt, locale),
                            stringResource(invitation.status.labelRes()),
                        ),
                    modifier = Modifier.testTag(CreateInvitationTestTags.codeRow(invitation.code)),
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

/**
 * Formats when an invitation was created, in the app language.
 *
 * The parameters have no default values on purpose. A default time of 0 would show a date in 1970,
 * and `Locale.getDefault()` would ignore the app language that the screen passes in.
 *
 * @param createdAtMillis the creation time, in milliseconds since the epoch.
 * @param locale the app locale, from `LocalConfiguration.current.locales[0]`.
 */
internal fun formatCreatedAt(createdAtMillis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(createdAtMillis))

/** The status shown in the invitation list, in the app language. */
@StringRes
internal fun InvitationStatus.labelRes(): Int =
    when (this) {
      InvitationStatus.ACTIVE -> R.string.createInvitationStatusActive
      InvitationStatus.EXPIRED -> R.string.createInvitationStatusExpired
      InvitationStatus.USED -> R.string.createInvitationStatusUsed
    }

/** Preview of [CreateInvitationScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = false)
@Composable
private fun CreateInvitationScreenPreview() {
  AmberAppTheme(dynamicColor = false) {
    CreateInvitationContent(
        uiState = CreateInvitationUiState(),
        onSelectCircle = {},
        onCreateInvitation = {},
    )
  }
}
