package com.fan.edgex.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.fan.edgex.R

object EdgeXIcons {
    @DrawableRes val Back = R.drawable.ic_arrow_back
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val More = R.drawable.ic_more_vert
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val Check = R.drawable.ic_action_dot
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val Save = R.drawable.ic_save
    @DrawableRes val Duplicate = R.drawable.ic_duplicate
    @DrawableRes val Execute = R.drawable.ic_execute
    @DrawableRes val MoveUp = R.drawable.ic_arrow_drop_up
    @DrawableRes val MoveDown = R.drawable.ic_arrow_drop_down
    @DrawableRes val Plus = R.drawable.ic_add
    @DrawableRes val Gesture = R.drawable.ic_gesture
    @DrawableRes val Freeze = R.drawable.ic_freezer
    @DrawableRes val Keys = R.drawable.ic_keyboard
    @DrawableRes val Pie = R.drawable.ic_pie_menu
    @DrawableRes val Multi = R.drawable.ic_multi_action
    @DrawableRes val Theme = R.drawable.ic_theme
    @DrawableRes val DarkMode = R.drawable.ic_dark_mode
    @DrawableRes val Sparkle = R.drawable.ic_supporter_extra
    @DrawableRes val VolumeUp = R.drawable.ic_volume_up
    @DrawableRes val VolumeDown = R.drawable.ic_volume_down
    @DrawableRes val Power = R.drawable.ic_power
    @DrawableRes val Home = R.drawable.ic_home
    @DrawableRes val Recents = R.drawable.ic_recents
    @DrawableRes val Lock = R.drawable.ic_power
    @DrawableRes val Screenshot = R.drawable.ic_camera
    @DrawableRes val Flashlight = R.drawable.ic_flashlight
    @DrawableRes val Notifications = R.drawable.ic_notifications
    @DrawableRes val BrightnessUp = R.drawable.ic_brightness_up
    @DrawableRes val BrightnessDown = R.drawable.ic_brightness_down
    @DrawableRes val ClearBackground = R.drawable.ic_clear_recent
    @DrawableRes val KillApp = R.drawable.ic_kill_app
    @DrawableRes val PrevApp = R.drawable.ic_prev_app
    @DrawableRes val NextApp = R.drawable.ic_next_app
    @DrawableRes val Apps = R.drawable.ic_apps
    @DrawableRes val CustomPanel = R.drawable.ic_apps
    @DrawableRes val EdgePanel = R.drawable.ic_edge_panel
    @DrawableRes val SideBar = R.drawable.ic_side_bar
    @DrawableRes val SideBarLeft = R.drawable.ic_side_bar_left
    @DrawableRes val SideBarRight = R.drawable.ic_side_bar_right
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val DeveloperMode = R.drawable.ic_developer_mode
    @DrawableRes val ArcDrawer = R.drawable.ic_arc_drawer
    @DrawableRes val About = R.drawable.ic_about
    @DrawableRes val Person = R.drawable.ic_person
    @DrawableRes val EdgeLighting = R.drawable.ic_edge_lighting
    @DrawableRes val FluidEffect = R.drawable.ic_fluid_effect
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Donate = R.drawable.ic_donate
    @DrawableRes val Link = R.drawable.ic_link
    @DrawableRes val Terminal = R.drawable.ic_terminal
    @DrawableRes val Vibration = R.drawable.ic_vibration
    @DrawableRes val Restart = R.drawable.ic_restart_alt
    @DrawableRes val SubGesture = R.drawable.ic_sub_gesture
    @DrawableRes val LaunchApp = R.drawable.ic_launch_app
    @DrawableRes val LaunchActivity = R.drawable.ic_launch_app
    @DrawableRes val Assistant = R.drawable.ic_search
    @DrawableRes val AppShortcut = R.drawable.ic_app_shortcut
    @DrawableRes val Clipboard = R.drawable.ic_paste
    @DrawableRes val UniversalCopy = R.drawable.ic_content_copy
    @DrawableRes val Music = R.drawable.ic_music
    @DrawableRes val FastScroll = R.drawable.ic_fast_scroll
    @DrawableRes val Condition = R.drawable.ic_condition
    @DrawableRes val If = R.drawable.ic_if
    @DrawableRes val Wifi = R.drawable.ic_wifi
    @DrawableRes val MobileData = R.drawable.ic_mobile_data
    @DrawableRes val GooglePlay = R.drawable.ic_google_play
    @DrawableRes val GameMode = R.drawable.ic_game_mode
    @DrawableRes val Refreeze = R.drawable.ic_refreeze
    @DrawableRes val PartialScreenshot = R.drawable.ic_partial_screenshot
    @DrawableRes val Alipay = R.drawable.ic_alipay
    @DrawableRes val WechatPay = R.drawable.ic_wechat_pay
    @DrawableRes val KoFi = R.drawable.ic_ko_fi
    @DrawableRes val Eth = R.drawable.ic_eth
}

@Composable
fun EdgeXIcon(
    @DrawableRes imageVector: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
