package com.fan.edgex.ui.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GuideUrlResolverTest {
    @Test
    fun chineseLocalesResolveToChineseGuide() {
        assertEquals("edgexy.app/zh/guide", resolveGuideDisplayUrl(Locale.SIMPLIFIED_CHINESE))
        assertEquals("https://edgexy.app/zh/guide", resolveGuideUrl(Locale.SIMPLIFIED_CHINESE))
        assertEquals("https://edgexy.app/zh/guide", resolveGuideUrl(Locale.TRADITIONAL_CHINESE))
        assertEquals("https://edgexy.app/zh/guide", resolveGuideUrl(Locale.CHINESE))
    }

    @Test
    fun nonChineseLocalesResolveToEnglishGuide() {
        assertEquals("edgexy.app/guide", resolveGuideDisplayUrl(Locale.US))
        assertEquals("https://edgexy.app/guide", resolveGuideUrl(Locale.US))
        assertEquals("https://edgexy.app/guide", resolveGuideUrl(Locale.UK))
        assertEquals("https://edgexy.app/guide", resolveGuideUrl(Locale.JAPAN))
    }
}
