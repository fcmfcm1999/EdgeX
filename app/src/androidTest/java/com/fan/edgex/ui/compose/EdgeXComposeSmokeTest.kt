package com.fan.edgex.ui.compose

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.configPrefs
import com.fan.edgex.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EdgeXComposeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val appContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearUiTestPrefs() {
        appContext.configPrefs().edit()
            .remove(AppConfig.gestureAction("right_mid", "swipe_left"))
            .remove(AppConfig.gestureActionLabel("right_mid", "swipe_left"))
            .remove(AppConfig.UI_ACCENT)
            .remove(AppConfig.UI_DARK_MODE)
            .commit()
    }

    @Test
    fun homeShowsPrimaryEntryPoints() {
        composeRule.onNodeWithText("EdgeX").assertIsDisplayed()
        composeRule.onNodeWithText("你的手势\n掌控一切").assertIsDisplayed()
        composeRule.onNodeWithText("Pie 菜单").assertIsDisplayed()
        composeRule.onNodeWithText("Premium").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeTilesNavigateThroughComposeStack() {
        composeRule.onNodeWithTag("home_tile_theme").performScrollTo().performClick()
        composeRule.onNodeWithText("当前主题").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("返回").assert(hasClickAction()).performClick()
        composeRule.onNodeWithText("EdgeX").assertIsDisplayed()

        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText("点击屏幕\n边缘配置手势").assertIsDisplayed()
    }

    @Test
    fun gestureSheetWritesDirectAction() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText("左划").performClick()
        composeRule.onNodeWithTag("gesture_action_back").performClick()

        val prefs = appContext.configPrefs()
        assertEquals("back", prefs.getString(AppConfig.gestureAction("right_mid", "swipe_left"), null))
        assertEquals("返回", prefs.getString(AppConfig.gestureActionLabel("right_mid", "swipe_left"), null))
    }

    @Test
    fun themeControlsPersistAccentDarkModeAndCustomColor() {
        composeRule.onNodeWithTag("home_tile_theme").performScrollTo().performClick()

        listOf("green", "blue", "coral", "violet", "amber").forEach { accent ->
            composeRule.onNodeWithTag("theme_accent_$accent").performClick()
            assertEquals(accent, appContext.configPrefs().getString(AppConfig.UI_ACCENT, null))
        }

        composeRule.onNodeWithTag("theme_dark_mode").performScrollTo().performClick()
        assertNotNull(appContext.configPrefs().getString(AppConfig.UI_DARK_MODE, null))

        composeRule.onNodeWithTag("theme_custom_apply").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun freezerTabsAndSearchRenderEmptyState() {
        composeRule.onNodeWithTag("home_tile_freezer").performScrollTo().performClick()
        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("已冻结").performClick()
        composeRule.onNodeWithText("使用中").performClick()

        composeRule.onNodeWithTag("freezer_search").performTextInput("zzzz-no-such-package")
        waitUntilTextExists("没有匹配的应用")
        composeRule.onNodeWithText("没有匹配的应用").assertIsDisplayed()
    }

    private fun waitUntilTextExists(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
