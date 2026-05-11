package com.fan.edgex.overlay

import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Shared visual tokens for all system overlay windows (DrawerWindow,
 * PanelOverlayWindow sidebar, PanelOverlayWindow custom panel).
 * Centralising these keeps the three surfaces visually consistent and
 * makes future theme changes a single-file edit.
 */
internal object OverlayTheme {

    // ── Surface ──────────────────────────────────────────────────────────
    val SURFACE_BG_DARK  = Color.argb(242, 48, 54, 68)   // original sidebar/panel value
    val SURFACE_BG_LIGHT = Color.argb(238, 250, 248, 255)

    // ── Item card (DrawerWindow app grid) ─────────────────────────────────
    val CARD_BG_DARK  = Color.argb(160, 64, 68, 88)      // lighter than surface for contrast
    val CARD_BG_LIGHT = Color.argb(170, 230, 226, 244)

    // ── Text ──────────────────────────────────────────────────────────────
    val TEXT_PRIMARY_DARK    = Color.WHITE
    val TEXT_PRIMARY_LIGHT   get() = "#1C1B1F".toColorInt()
    val TEXT_SECONDARY_DARK  get() = "#9A97AA".toColorInt()
    val TEXT_SECONDARY_LIGHT get() = "#6B6880".toColorInt()

    // ── Divider ───────────────────────────────────────────────────────────
    val DIVIDER_DARK  = Color.argb(35, 255, 255, 255)
    val DIVIDER_LIGHT = Color.argb(40, 0, 0, 0)

    // ── Frozen-app badge (DrawerWindow) ───────────────────────────────────
    val FROZEN_BADGE_BG = Color.argb(210, 12, 18, 52)

    // ── Corner radii (dp — multiply by displayMetrics.density) ───────────
    const val CORNER_SHEET_DP = 20f   // full-height DrawerWindow panel
    const val CORNER_POPUP_DP = 18f   // custom panel popup
    const val CORNER_BAR_DP   = 10f   // sidebar bar

    // ── Elevation (dp) ────────────────────────────────────────────────────
    const val ELEVATION_DP = 18f

    // ── Dim amounts ───────────────────────────────────────────────────────
    const val DIM_DRAWER  = 0.25f
    const val DIM_PANEL   = 0.18f
    const val DIM_SIDEBAR = 0f
}
