package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Test

class FreezerAppPolicyTest {

    private data class FakeApp(
        val pkg: String,
        val label: String,
        val enabled: Boolean,
    )

    @Test
    fun parseFreezerPackages_trimsAndIgnoresBlanks() {
        assertEquals(emptySet<String>(), FreezerAppPolicy.parseFreezerPackages(null))
        assertEquals(emptySet<String>(), FreezerAppPolicy.parseFreezerPackages(" , , "))
        assertEquals(
            setOf("com.a", "com.b", "com.c"),
            FreezerAppPolicy.parseFreezerPackages(" com.a , ,com.b, com.c "),
        )
    }

    @Test
    fun mergeLaunchableApps_includesFrozenFreezerAppsAndExcludesOtherDisabledApps() {
        val enabled = listOf(
            FakeApp("com.zebra", "Zebra", enabled = true),
            FakeApp("com.alpha", "Alpha", enabled = true),
        )
        val disabled = listOf(
            FakeApp("com.frozen.freezer", "Beta Frozen", enabled = false),
            FakeApp("com.disabled.bloat", "Bloatware", enabled = false),
            FakeApp("com.alpha", "Alpha Duplicate", enabled = false),
            FakeApp("com.enabled.in.freezer", "Gamma", enabled = true),
        )

        val merged = FreezerAppPolicy.mergeLaunchableApps(
            enabledApps = enabled,
            disabledCandidates = disabled,
            freezerPackages = setOf("com.frozen.freezer", "com.alpha", "com.enabled.in.freezer"),
            packageNameOf = { it.pkg },
            enabledOf = { it.enabled },
            labelOf = { it.label },
        )

        assertEquals(
            listOf("com.alpha", "com.frozen.freezer", "com.zebra"),
            merged.map { it.pkg },
        )
    }

    @Test
    fun mergeLaunchableApps_withoutFreezerPackagesReturnsOnlyEnabledSortedAndDistinct() {
        val enabled = listOf(
            FakeApp("com.b", "Beta", enabled = true),
            FakeApp("com.a", "Alpha", enabled = true),
            FakeApp("com.a", "Alpha2", enabled = true),
        )
        val disabled = listOf(
            FakeApp("com.c", "Charlie", enabled = false),
        )

        val merged = FreezerAppPolicy.mergeLaunchableApps(
            enabledApps = enabled,
            disabledCandidates = disabled,
            freezerPackages = emptySet(),
            packageNameOf = { it.pkg },
            enabledOf = { it.enabled },
            labelOf = { it.label },
        )

        assertEquals(listOf("com.a", "com.b"), merged.map { it.pkg })
    }

    @Test
    fun packagesToRefreeze_selectsCurrentlyEnabledFreezerApps() {
        val result = FreezerAppPolicy.packagesToRefreeze(
            freezerPackages = setOf("com.clock", "com.maps", "com.uninstalled"),
            enabledStateByPackage = mapOf(
                "com.clock" to true,
                "com.maps" to false,
                "com.other" to true,
            ),
        )

        assertEquals(listOf("com.clock"), result)
    }
}
