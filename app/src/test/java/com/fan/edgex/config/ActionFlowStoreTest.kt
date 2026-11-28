package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionFlowStoreTest {

    @Test
    fun `delay code roundtrip and clamping`() {
        assertEquals("delay:1500", ActionFlowStore.buildDelayCode(1500L))
        assertEquals(1500L, ActionFlowStore.parseDelayMs("delay:1500"))
        assertTrue(ActionFlowStore.isDelay("delay:0"))
        assertEquals(0L, ActionFlowStore.parseDelayMs(ActionFlowStore.buildDelayCode(-10L)))
        assertEquals(
            ActionFlowStore.MAX_DELAY_MS,
            ActionFlowStore.parseDelayMs(ActionFlowStore.buildDelayCode(Long.MAX_VALUE)),
        )
        assertNull(ActionFlowStore.parseDelayMs("delay:-5"))
        assertNull(ActionFlowStore.parseDelayMs("delay:abc"))
        assertFalse(ActionFlowStore.isDelay("back"))
    }

    @Test
    fun `format delay duration uses seconds for whole seconds`() {
        assertEquals("0ms", ActionFlowStore.formatDelayDuration(0L))
        assertEquals("500ms", ActionFlowStore.formatDelayDuration(500L))
        assertEquals("1s", ActionFlowStore.formatDelayDuration(1000L))
        assertEquals("1500ms", ActionFlowStore.formatDelayDuration(1500L))
        assertEquals("30s", ActionFlowStore.formatDelayDuration(30_000L))
    }

    @Test
    fun `repeat code and count helpers`() {
        assertEquals("repeat:abc", ActionFlowStore.buildRepeatCode("abc"))
        assertEquals("abc", ActionFlowStore.extractRepeatId("repeat:abc"))
        assertNull(ActionFlowStore.extractRepeatId("repeat:"))
        assertNull(ActionFlowStore.extractRepeatId("multi_action:abc"))
        assertEquals(5, ActionFlowStore.parseRepeatCount("5"))
        assertEquals(0, ActionFlowStore.parseRepeatCount("-3"))
        assertEquals(0, ActionFlowStore.parseRepeatCount("bad"))
        assertEquals(ActionFlowStore.MAX_REPEAT_COUNT, ActionFlowStore.parseRepeatCount("99999999"))
    }
}
