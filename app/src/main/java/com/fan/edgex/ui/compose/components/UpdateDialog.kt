package com.fan.edgex.ui.compose.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.fan.edgex.R
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import com.fan.edgex.utils.UpdateChecker

@Composable
fun UpdateDialog(
    release: UpdateChecker.ReleaseInfo,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = LocalEdgeXColors.current
    val rawBody = remember(release.body, configuration.locales) {
        UpdateChecker.extractLocalizedBody(release.body, context)
    }
    val body = rawBody.ifBlank { stringResource(R.string.update_no_changelog) }
    val formattedBody = remember(body) { parseReleaseMarkdown(body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_new_version_title, release.versionName),
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = formattedBody,
                    color = colors.onSurface2,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()))
                    }.onFailure {
                        Toast.makeText(context, R.string.toast_cannot_open_browser, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.update_view_release), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.update_skip_version), color = colors.onSurfaceDim)
            }
        },
        shape = RoundedCornerShape(EdgeXRadius.md),
        containerColor = colors.surface1,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface2,
    )
}

private fun parseReleaseMarkdown(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r\n", "\n").lines()
    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        val heading = UpdateChecker.parseMarkdownHeading(line)
        val bullet = Regex("^\\s*[-*]\\s+(.*)").matchEntire(line)
        when {
            heading != null -> {
                val size = when (heading.first) {
                    1 -> 20.sp
                    2 -> 17.sp
                    else -> 15.sp
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size))
                appendInlineMarkdown(heading.second)
                pop()
            }
            bullet != null -> {
                append("•  ")
                appendInlineMarkdown(bullet.groupValues[1])
            }
            else -> appendInlineMarkdown(line)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    val regex = Regex("""\*\*(.+?)\*\*|`(.+?)`|\[(.+?)]\((.+?)\)""")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[1])
                pop()
            }
            match.groupValues[2].isNotEmpty() -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(match.groupValues[2])
                pop()
            }
            else -> append(match.groupValues[3])
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
