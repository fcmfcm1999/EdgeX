package com.fan.edgex.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fan.edgex.ui.MainActivity
import org.junit.Rule
import org.junit.Test

class EdgeXComposeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsPrimaryEntryPoints() {
        composeRule.onNodeWithText("EdgeX").assertIsDisplayed()
        composeRule.onNodeWithText("手势").assertIsDisplayed()
        composeRule.onNodeWithText("冰箱").assertIsDisplayed()
        composeRule.onNodeWithText("Premium").assertIsDisplayed()
    }
}
