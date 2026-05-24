package com.fan.edgex.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.fan.edgex.ui.MainActivity
import org.junit.Rule
import org.junit.Test

class EdgeXComposeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsPrimaryEntryPoints() {
        composeRule.onNodeWithText("EdgeX").assertIsDisplayed()
        composeRule.onNodeWithText("你的手势\n掌控一切").assertIsDisplayed()
        composeRule.onNodeWithText("Pie 菜单").assertIsDisplayed()
        composeRule.onNodeWithText("Premium").performScrollTo().assertIsDisplayed()
    }
}
