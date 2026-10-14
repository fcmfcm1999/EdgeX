package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareKeyConfigTest {
    @Test fun keepsVendorCodesOutsidePublicSdkRange() {
        assertEquals(listOf(1082, 1015), HardwareKeyConfig.parseCodes("1082, 1015,1082,0,-1,invalid,2147483648"))
    }

    @Test fun preservesExistingDefaultsAndRestoresCustomMappings() {
        val codes = HardwareKeyConfig.configuredCodes(mapOf(
            HardwareKeyConfig.CUSTOM_CODES to "1082,24",
            "key_1015_double_click" to "home",
            "key_enabled_1016" to "false",
            "key_999_click_label" to "Ignore labels",
            "key_enabled_bad" to "true",
        ))
        assertEquals(listOf(24, 25, 26, 1082, 1015, 1016), codes)
    }

    @Test fun recognizesEveryTriggerForVendorKeys() {
        AppConfig.KEY_TRIGGERS.forEach { trigger ->
            assertEquals(1082 to trigger, AppConfig.keyActionParts(AppConfig.keyAction(1082, trigger)))
        }
    }
}
