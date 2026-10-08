package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify app name resource matches JARVIS Voice AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS Voice AI", appName)
    }

    @Test
    fun `hud screen displays orb, input console, and navigates across tabs`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("jarvis_orb_button").assertIsDisplayed()

        // Scroll HUD list to command_input_field and verify it is displayed
        composeTestRule.onNodeWithTag("hud_dashboard_list")
            .performScrollToNode(hasTestTag("command_input_field"))
        composeTestRule.onNodeWithTag("command_input_field").assertIsDisplayed()

        // Navigate to Tools tab
        composeTestRule.onNodeWithTag("nav_tab_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_center_list").assertIsDisplayed()

        // Navigate to Permissions tab
        composeTestRule.onNodeWithTag("nav_tab_permissions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("permissions_screen_list").assertIsDisplayed()

        // Navigate to Privacy & AI Settings tab
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_privacy_list").assertIsDisplayed()
    }

    @Test
    fun `sensitive command triggers confirmation card before execution`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hud_dashboard_list")
            .performScrollToNode(hasTestTag("command_input_field"))

        composeTestRule.onNodeWithTag("command_input_field")
            .performTextInput("Hey Jarvis, call Mom")
        composeTestRule.onNodeWithTag("send_command_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hud_dashboard_list")
            .performScrollToNode(hasTestTag("confirmation_card"))
        composeTestRule.onNodeWithTag("confirmation_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_action_button").performClick()
        composeTestRule.waitForIdle()
    }
}
