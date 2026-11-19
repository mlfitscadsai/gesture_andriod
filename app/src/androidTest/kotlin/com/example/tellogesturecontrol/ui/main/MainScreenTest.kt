package com.example.tellogesturecontrol.ui.main

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import com.example.tellogesturecontrol.MainActivity
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreen_simulatorFlow_takeoffAndSwitchToManualPad() {
        composeTestRule.onNodeWithText("Gesture Control").assertExists()
        composeTestRule.onNodeWithText("Manual D-Pad").assertExists()

        // Enable Simulator Mode and Takeoff
        composeTestRule.onNodeWithText("Sim").performClick()
        composeTestRule.onNodeWithText("Simulator Active").assertExists()
        composeTestRule.onNodeWithText("Takeoff").performClick()
        composeTestRule.onNodeWithText("Land").assertExists()

        // Switch to Manual D-Pad
        composeTestRule.onNodeWithText("Manual D-Pad").performClick()
        composeTestRule.onNodeWithText("Up").assertExists()
        composeTestRule.onNodeWithText("Down").assertExists()
    }
}
