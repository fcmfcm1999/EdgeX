package com.fan.edgex.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeXSystemToastTest {
    @Test
    fun normalizeMessageTrimsAndRejectsBlankText() {
        assertNull(EdgeXSystemToast.normalizeMessage(""))
        assertNull(EdgeXSystemToast.normalizeMessage("   \n\t  "))
        assertEquals("Wi-Fi enabled", EdgeXSystemToast.normalizeMessage("  Wi-Fi enabled  "))
    }
}
