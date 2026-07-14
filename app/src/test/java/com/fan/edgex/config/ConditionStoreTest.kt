package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionStoreTest {

    @Test
    fun packageNamesEncodeAsSortedDeduplicatedJson() {
        val encoded = ConditionStore.encodePackageNames(
            listOf("com.example.z", " com.example.a ", "com.example.z", ""),
        )

        assertEquals("[\"com.example.a\", \"com.example.z\"]", encoded)
        assertEquals(
            listOf("com.example.a", "com.example.z"),
            ConditionStore.decodePackageNames(encoded).toList(),
        )
    }

    @Test
    fun packageNamesDecodeLegacyCommaSeparatedValue() {
        assertEquals(
            listOf("com.example.a", "com.example.z"),
            ConditionStore.decodePackageNames("com.example.z, com.example.a,com.example.z").toList(),
        )
    }

    @Test
    fun packageNamesRoundTripJsonEscapes() {
        val packageNames = setOf("com.example.quoted\"name", "com.example.back\\slash")

        assertEquals(
            packageNames.sorted(),
            ConditionStore.decodePackageNames(ConditionStore.encodePackageNames(packageNames)).toList(),
        )
    }

    @Test
    fun malformedOrEmptyPackageValuesDecodeToEmptySet() {
        assertTrue(ConditionStore.decodePackageNames("").isEmpty())
        assertTrue(ConditionStore.decodePackageNames("[\"unterminated]").isEmpty())
    }
}
