package com.fan.edgex.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumMap

class SystemBarControllerTest {

    private class FakeBackend(
        var inspectable: Boolean = false,
        val succeedingBars: MutableSet<SystemBarType> = mutableSetOf(
            SystemBarType.STATUS_BAR,
            SystemBarType.NAVIGATION_BAR,
        ),
    ) : SystemBarBackend {
        val visibleState = EnumMap<SystemBarType, Boolean>(SystemBarType::class.java).apply {
            put(SystemBarType.STATUS_BAR, true)
            put(SystemBarType.NAVIGATION_BAR, true)
        }
        val calls = mutableListOf<Pair<SystemBarType, Boolean>>()

        override fun isBarHidden(bar: SystemBarType): Boolean? {
            if (!inspectable || bar !in succeedingBars) return null
            return visibleState[bar] == false
        }

        override fun setBarVisible(bar: SystemBarType, visible: Boolean): Boolean {
            calls.add(bar to visible)
            if (bar !in succeedingBars) return false
            visibleState[bar] = visible
            return true
        }
    }

    @Test
    fun toggleImmersiveMode_hidesAndShowsBothBarsTogether() {
        val backend = FakeBackend(inspectable = false)
        val controller = SystemBarStateController(backend)

        assertEquals(true, controller.toggleImmersiveMode())
        assertTrue(controller.isImmersiveModeActive())
        assertEquals(
            listOf(
                SystemBarType.STATUS_BAR to false,
                SystemBarType.NAVIGATION_BAR to false,
            ),
            backend.calls,
        )

        assertEquals(false, controller.toggleImmersiveMode())
        assertFalse(controller.isImmersiveModeActive())
        assertEquals(
            listOf(
                SystemBarType.STATUS_BAR to false,
                SystemBarType.NAVIGATION_BAR to false,
                SystemBarType.STATUS_BAR to true,
                SystemBarType.NAVIGATION_BAR to true,
            ),
            backend.calls,
        )
    }

    @Test
    fun toggleImmersiveMode_succeedsWhenOnlyOneBarExists() {
        val backend = FakeBackend(
            inspectable = false,
            succeedingBars = mutableSetOf(SystemBarType.STATUS_BAR),
        )
        val controller = SystemBarStateController(backend)

        assertEquals(true, controller.toggleImmersiveMode())
        assertTrue(controller.isImmersiveModeActive())
        assertEquals(false, backend.visibleState[SystemBarType.STATUS_BAR])
    }

    @Test
    fun toggleImmersiveMode_whenBothBarsFail_returnsNullAndDoesNotFlipState() {
        val backend = FakeBackend(
            inspectable = false,
            succeedingBars = mutableSetOf(),
        )
        val controller = SystemBarStateController(backend)

        assertNull(controller.toggleImmersiveMode())
        assertFalse(controller.isImmersiveModeActive())
    }

    @Test
    fun toggleImmersiveMode_whenInspectableAndAnyBarVisible_hidesBoth() {
        val backend = FakeBackend(inspectable = true)
        val controller = SystemBarStateController(backend)

        assertEquals(true, controller.toggleImmersiveMode())
        // Simulate only one bar becoming visible again (e.g. after SystemUI restart)
        backend.visibleState[SystemBarType.STATUS_BAR] = true

        assertFalse(controller.isImmersiveModeActive())
        assertEquals(true, controller.toggleImmersiveMode())
        assertEquals(false, backend.visibleState[SystemBarType.STATUS_BAR])
        assertEquals(false, backend.visibleState[SystemBarType.NAVIGATION_BAR])
    }
}
