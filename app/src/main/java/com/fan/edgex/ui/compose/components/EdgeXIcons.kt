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
    @DrawableRes val ChevronRight = R.drawable.ic_expand_more
    @DrawableRes val Check = R.drawable.ic_action_dot
    @DrawableRes val Plus = R.drawable.ic_add
    @DrawableRes val Gesture = R.drawable.ic_gesture
    @DrawableRes val Freeze = R.drawable.ic_freezer
    @DrawableRes val Keys = R.drawable.ic_keyboard
    @DrawableRes val Pie = R.drawable.ic_pie_menu
    @DrawableRes val Multi = R.drawable.ic_multi_action
    @DrawableRes val Theme = R.drawable.ic_theme
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
    @DrawableRes val CustomPanel = R.drawable.ic_edge_panel
    @DrawableRes val SideBarLeft = R.drawable.ic_side_bar_left
    @DrawableRes val SideBarRight = R.drawable.ic_side_bar_right
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Donate = R.drawable.ic_donate
    @DrawableRes val Link = R.drawable.ic_link
    @DrawableRes val Terminal = R.drawable.ic_terminal
    @DrawableRes val Vibration = R.drawable.ic_vibration
    @DrawableRes val Restart = R.drawable.ic_restart_alt
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
