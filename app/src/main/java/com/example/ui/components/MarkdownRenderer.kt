package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LocalAppThemeMode

data class TocItem(
    val sectionIndex: Int,
    val title: String,
    val level: Int
)

object MarkdownParser {

    fun extractToc(sections: List<com.example.data.NoteSection>): List<TocItem> {
        val items = mutableListOf<TocItem>()
        sections.forEachIndexed { index, sec ->
            if (sec.title.isNotBlank()) {
                items.add(TocItem(sectionIndex = index, title = sec.title.trim(), level = 1))
            }
            sec.content.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#### ") ->
                        items.add(TocItem(index, trimmed.removePrefix("#### ").trim(), 4))
                    trimmed.startsWith("### ") ->
                        items.add(TocItem(index, trimmed.removePrefix("### ").trim(), 3))
                    trimmed.startsWith("## ") ->
                        items.add(TocItem(index, trimmed.removePrefix("## ").trim(), 2))
                    trimmed.startsWith("# ") ->
                        items.add(TocItem(index, trimmed.removePrefix("# ").trim(), 1))
                }
            }
        }
        return items
    }

    /**
     * Converts common LaTeX math macros to crisp Unicode mathematical notation for native rendering.
     */
    fun formatMathExpression(latex: String): String {
        return latex.trim()
            .replace("\\int_", "∫_")
            .replace("\\int", "∫")
            .replace("\\sum", "∑")
            .replace("\\prod", "∏")
            .replace("\\sqrt", "√")
            .replace("\\infty", "∞")
            .replace("\\pi", "π")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\theta", "θ")
            .replace("\\Delta", "Δ")
            .replace("\\lambda", "λ")
            .replace("\\neq", "≠")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\approx", "≈")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\rightarrow", "→")
            .replace("\\Rightarrow", "⇒")
            .replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}"""), "($1) / ($2)")
    }
}

sealed class MarkdownBlock {
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class MathBlock(val formula: String) : MarkdownBlock()
    data class TableBlock(val rows: List<List<String>>) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced Code Block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        // Block Math $$...$$
        if (trimmed.startsWith("$$")) {
            if (trimmed.endsWith("$$") && trimmed.length > 4) {
                blocks.add(MarkdownBlock.MathBlock(trimmed.removeSurrounding("$$").trim()))
                i++
                continue
            }
            val mathLines = mutableListOf<String>()
            val firstRest = trimmed.removePrefix("$$").trim()
            if (firstRest.isNotEmpty()) mathLines.add(firstRest)
            i++
            while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                mathLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                val lastRest = lines[i].trim().removeSuffix("$$").trim()
                if (lastRest.isNotEmpty()) mathLines.add(lastRest)
            }
            blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
            i++
            continue
        }

        // GFM Table
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2) {
            val tableRows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowTrim = lines[i].trim()
                if (rowTrim.startsWith("|") && rowTrim.endsWith("|") && rowTrim.count { it == '|' } >= 2) {
                    val isSeparator = rowTrim.replace("|", "").replace(":", "").replace("-", "").trim().isEmpty()
                    if (!isSeparator) {
                        val cells = rowTrim.trim('|').split("|").map { it.trim() }
                        tableRows.add(cells)
                    }
                    i++
                } else {
                    break
                }
            }
            if (tableRows.isNotEmpty()) {
                blocks.add(MarkdownBlock.TableBlock(tableRows))
            }
            continue
        }

        // Horizontal Divider
        if (trimmed == "---" || trimmed == "***") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Headings
        when {
            trimmed.startsWith("#### ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("#### "), 4))
                i++
                continue
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("### "), 3))
                i++
                continue
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("## "), 2))
                i++
                continue
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(trimmed.removePrefix("# "), 1))
                i++
                continue
            }
        }

        // Bullet List
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith("> ")) {
            blocks.add(MarkdownBlock.BlockQuote(trimmed.removePrefix("> ")))
            i++
            continue
        }

        // Paragraph
        blocks.add(MarkdownBlock.Paragraph(line))
        i++
    }
    return blocks
}

@Composable
fun RichMarkdownContent(
    markdown: String,
    fontFamily: FontFamily,
    fontSizeSp: Int,
    highlightQuery: String = "",
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeMode.current
    val blocks = remember(markdown) { parseBlocks(markdown) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val hSize = when (block.level) {
                            1 -> (fontSizeSp + 6).sp
                            2 -> (fontSizeSp + 4).sp
                            3 -> (fontSizeSp + 2).sp
                            else -> fontSizeSp.sp
                        }
                        Text(
                            text = buildInlineStyledText(block.text, theme.bgAlt, highlightQuery),
                            fontFamily = fontFamily,
                            fontSize = hSize,
                            fontWeight = FontWeight.Bold,
                            color = theme.text,
                            lineHeight = (hSize.value * 1.35f).sp
                        )
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = buildInlineStyledText(block.text, theme.bgAlt, highlightQuery),
                            fontFamily = fontFamily,
                            fontSize = fontSizeSp.sp,
                            color = theme.text,
                            lineHeight = (fontSizeSp * 1.55f).sp
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•  ",
                                fontFamily = fontFamily,
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent
                            )
                            Text(
                                text = buildInlineStyledText(block.text, theme.bgAlt, highlightQuery),
                                fontFamily = fontFamily,
                                fontSize = fontSizeSp.sp,
                                color = theme.text,
                                lineHeight = (fontSizeSp * 1.5f).sp
                            )
                        }
                    }

                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.bgAlt)
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .background(theme.accent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = buildInlineStyledText(block.text, theme.surface, highlightQuery),
                                fontFamily = fontFamily,
                                fontSize = fontSizeSp.sp,
                                fontStyle = FontStyle.Italic,
                                color = theme.text
                            )
                        }
                    }

                    is MarkdownBlock.CodeBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.bgAlt)
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    fontFamily = JetBrainsMonoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.muted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = block.code,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = (fontSizeSp - 2).coerceAtLeast(12).sp,
                                color = theme.text,
                                lineHeight = ((fontSizeSp - 2).coerceAtLeast(12) * 1.45f).sp
                            )
                        }
                    }

                    is MarkdownBlock.MathBlock -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.bgAlt)
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = MarkdownParser.formatMathExpression(block.formula),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = (fontSizeSp + 1).sp,
                                fontWeight = FontWeight.Medium,
                                color = theme.text
                            )
                        }
                    }

                    is MarkdownBlock.TableBlock -> {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                        ) {
                            block.rows.forEachIndexed { rowIdx, rowCells ->
                                val isHeader = rowIdx == 0
                                Row(
                                    modifier = Modifier
                                        .background(if (isHeader) theme.bgAlt else theme.surface)
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowCells.forEach { cell ->
                                        Text(
                                            text = MarkdownParser.formatMathExpression(cell.replace("$", "")),
                                            fontFamily = fontFamily,
                                            fontSize = (fontSizeSp - 1).sp,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            color = theme.text,
                                            modifier = Modifier.width(120.dp)
                                        )
                                    }
                                }
                                if (rowIdx < block.rows.lastIndex) {
                                    HorizontalDivider(color = theme.border, thickness = 1.dp)
                                }
                            }
                        }
                    }

                    MarkdownBlock.Divider -> {
                        HorizontalDivider(color = theme.border, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

private fun buildInlineStyledText(
    raw: String,
    codeBg: androidx.compose.ui.graphics.Color,
    highlightQuery: String
): AnnotatedString {
    return buildAnnotatedString {
        // Handle inline **bold**, `code`, and $math$
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith("**", i)) {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFont, background = codeBg)) {
                        append(" " + raw.substring(i + 1, end) + " ")
                    }
                    i = end + 1
                    continue
                }
            }
            if (raw[i] == '$') {
                val end = raw.indexOf('$', i + 1)
                if (end != -1) {
                    val expr = MarkdownParser.formatMathExpression(raw.substring(i + 1, end))
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFont,
                            fontStyle = FontStyle.Italic,
                            background = codeBg
                        )
                    ) {
                        append(" $expr ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(raw[i])
            i++
        }

        if (highlightQuery.isNotBlank()) {
            val fullStr = toAnnotatedString().text
            var searchIndex = fullStr.indexOf(highlightQuery, ignoreCase = true)
            while (searchIndex >= 0) {
                addStyle(
                    SpanStyle(
                        background = androidx.compose.ui.graphics.Color(0xFFFDE047),
                        color = androidx.compose.ui.graphics.Color(0xFF09090B),
                        fontWeight = FontWeight.Bold
                    ),
                    start = searchIndex,
                    end = searchIndex + highlightQuery.length
                )
                searchIndex = fullStr.indexOf(highlightQuery, searchIndex + highlightQuery.length, ignoreCase = true)
            }
        }
    }
}
