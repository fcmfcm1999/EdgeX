package com.fan.edgex.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseMarkdownHeading_allowsIndentedHeadings() {
        assertEquals(2 to "🔧 修复与优化", UpdateChecker.parseMarkdownHeading("  ## 🔧 修复与优化"))
    }

    @Test
    fun parseMarkdownHeading_rejectsIndentedCodeBlockHeading() {
        assertNull(UpdateChecker.parseMarkdownHeading("    ## Not a heading"))
    }
}
