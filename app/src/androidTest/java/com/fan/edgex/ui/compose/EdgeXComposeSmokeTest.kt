package com.fan.edgex.ui.compose

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ConditionStore
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
        composeRule.onNodeWithText(appContext.getString(R.string.compose_home_hero_title)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext.getString(R.string.header_pie_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_about_support_author)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeTilesNavigateThroughComposeStack() {
        composeRule.onNodeWithTag("home_tile_theme").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.header_theme)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(appContext.getString(R.string.compose_back)).assert(hasClickAction()).performClick()
        composeRule.onNodeWithText("EdgeX").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_gestures_hero)).assertIsDisplayed()
    }

    @Test
    fun gestureSheetWritesDirectAction() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_view_list)).performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.gesture_swipe_left)).performClick()
        composeRule.onNodeWithTag("gesture_action_back").performClick()

        val prefs = appContext.configPrefs()
        assertEquals("back", prefs.getString(AppConfig.gestureAction("right_mid", "swipe_left"), null))
        assertEquals(appContext.getString(R.string.action_back), prefs.getString(AppConfig.gestureActionLabel("right_mid", "swipe_left"), null))
    }

    @Test
    fun themeControlsPersistAccentDarkModeAndCustomColor() {
        composeRule.onNodeWithTag("home_tile_theme").performScrollTo().performClick()

        listOf("default", "classic", "cedar", "ocean", "ember").forEach { accent ->
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
        composeRule.onNodeWithText(appContext.getString(R.string.compose_filter_all)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_app_frozen)).performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_filter_active)).performClick()

        composeRule.onNodeWithTag("freezer_search").performTextInput("zzzz-no-such-package")
        waitUntilTextExists(appContext.getString(R.string.compose_no_matching_apps))
        composeRule.onNodeWithText(appContext.getString(R.string.compose_no_matching_apps)).assertIsDisplayed()
    }

    @Test
    fun conditionPickerDisplaysAndSaves() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_view_list)).performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.gesture_swipe_left)).performClick()
        composeRule.onNodeWithTag("gesture_action_condition").performScrollTo().performClick()

        // Tap the IF row
        composeRule.onNodeWithText(appContext.getString(R.string.cond_label_if)).performClick()

        // Scroll to and tap on a condition at the bottom (e.g. Screen landscape) to verify scrolling works
        composeRule.onNodeWithText(appContext.getString(R.string.cond_screen_landscape))
            .performScrollTo()
            .performClick()

        // Verify the condition label was updated in the ConditionSheet
        composeRule.onNodeWithText(appContext.getString(R.string.cond_screen_landscape))
            .assertIsDisplayed()
    }

    @Test
    fun foregroundAppConditionConfiguresSearchSelectionAndRestoresState() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_view_list)).performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.gesture_swipe_left)).performClick()
        composeRule.onNodeWithTag("gesture_action_condition").performScrollTo().performClick()

        composeRule.onNodeWithText(appContext.getString(R.string.cond_label_if)).performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.cond_foreground_app)).performClick()
        composeRule.onNodeWithTag("foreground_app_condition_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("foreground_app_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("foreground_app_search").performTextInput(appContext.packageName)
        waitUntilTagExists("foreground_app_package_${appContext.packageName}")
        composeRule.onNodeWithTag("foreground_app_package_${appContext.packageName}").performClick()
        composeRule.onNodeWithTag("foreground_app_save").assertIsEnabled().performClick()

        val expectedSummary = appContext.getString(
            R.string.cond_foreground_summary,
            appContext.getString(R.string.cond_foreground_app),
            1,
        )
        composeRule.onNodeWithText(expectedSummary).assertIsDisplayed()

        val action = appContext.configPrefs().getString(
            AppConfig.gestureAction("right_mid", "swipe_left"),
            null,
        ).orEmpty()
        val conditionId = requireNotNull(ConditionStore.extractId(action))
        assertEquals(
            setOf(appContext.packageName),
            ConditionStore.decodePackageNames(
                appContext.configPrefs().getString(ConditionStore.foregroundPackagesKey(conditionId), null).orEmpty(),
            ),
        )

        composeRule.onNodeWithText(appContext.getString(R.string.cond_label_if)).performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.cond_foreground_app)).performClick()
        waitUntilTagExists("foreground_app_checkbox_${appContext.packageName}")
        composeRule.onNodeWithTag("foreground_app_checkbox_${appContext.packageName}").assertIsOn()
    }

    @Test
    fun conditionThenElseShellCommandPickerFlow() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_view_list)).performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.gesture_swipe_left)).performClick()
        composeRule.onNodeWithTag("gesture_action_condition").performScrollTo().performClick()

        // Tap the THEN row
        composeRule.onNodeWithText(appContext.getString(R.string.cond_label_then)).performClick()

        // Click Shell Command
        composeRule.onNodeWithTag("gesture_action_shell_command").performScrollTo().performClick()

        // Verify Shell Command configuration sheet is immediately shown
        composeRule.onNodeWithText(appContext.getString(R.string.label_run_as_root)).assertIsDisplayed()
    }

    @Test
    fun subGestureShellCommandPickerFlow() {
        composeRule.onNodeWithTag("home_tile_gestures").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.compose_view_list)).performClick()
        composeRule.onNodeWithTag("gesture_zone_right_mid").performScrollTo().performClick()
        composeRule.onNodeWithText(appContext.getString(R.string.gesture_swipe_left)).performClick()
        composeRule.onNodeWithTag("gesture_action_sub_gesture").performScrollTo().performClick()

        // Tap Hold direction
        composeRule.onNodeWithText(appContext.getString(R.string.sub_gesture_hold)).performClick()

        // Click Shell Command
        composeRule.onNodeWithTag("gesture_action_shell_command").performScrollTo().performClick()

        // Verify Shell Command configuration sheet is immediately shown
        composeRule.onNodeWithText(appContext.getString(R.string.label_run_as_root)).assertIsDisplayed()
    }

    private fun waitUntilTextExists(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTagExists(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
