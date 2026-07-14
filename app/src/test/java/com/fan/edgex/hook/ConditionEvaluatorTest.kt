package com.fan.edgex.hook

import com.fan.edgex.config.ForegroundAppConditionConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorTest {

    private val packages = setOf("com.example.allowed")

    @Test
    fun matchesOnlyListedForegroundApp() {
        val config = config()

        assertTrue(ConditionEvaluator.matchesForegroundApp(config, "com.example.allowed"))
        assertFalse(ConditionEvaluator.matchesForegroundApp(config, "com.example.other"))
    }

    @Test
    fun invalidConfigurationFailsClosed() {
        assertFalse(ConditionEvaluator.matchesForegroundApp(null, "com.example.allowed"))
        assertFalse(
            ConditionEvaluator.matchesForegroundApp(
                ForegroundAppConditionConfig(emptySet()),
                "com.example.allowed",
            ),
        )
        assertFalse(ConditionEvaluator.matchesForegroundApp(config(), null))
        assertFalse(ConditionEvaluator.matchesForegroundApp(config(), ""))
    }

    private fun config() = ForegroundAppConditionConfig(packages)
}
