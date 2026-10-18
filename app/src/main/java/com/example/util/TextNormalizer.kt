package com.example.util

import java.text.Normalizer

object TextNormalizer {

    // Zero-width spaces to strip (preserving \u200C ZWNJ and \u200D ZWJ for Bengali র্য & অর্ধ-অক্ষর)
    private val BROKEN_ZERO_WIDTH_REGEX = Regex("[\u200B\uFEFF\u2060]")
    private val FENCED_CODE_REGEX = Regex("```[\\s\\S]*?```|`[^`\n]+`")
    private val BLOCK_MATH_DELIM_REGEX = Regex("""\\\[([\s\S]*?)\\\]""")
    private val INLINE_MATH_DELIM_REGEX = Regex("""\\\(([\s\S]*?)\\\)""")

    /**
     * Normalizes text to NFC, removes broken zero-width characters (\u200B, \uFEFF, \u2060),
     * strictly preserves Bengali ZWNJ (\u200C) and ZWJ (\u200D), converts AI math delimiters
     * (\[...\] -> $$...$$ and \(...\) -> $...$) outside code blocks, and repairs Markdown tables.
     */
    fun normalizeAndClean(input: String, cleanAiMathAndTables: Boolean = false): String {
        if (input.isEmpty()) return input

        // 1. Normalize to Unicode NFC
        var text = Normalizer.normalize(input, Normalizer.Form.NFC)

        // 2. Strip broken zero-width spaces while strictly keeping \u200C and \u200D
        text = text.replace(BROKEN_ZERO_WIDTH_REGEX, "")

        // Normalize Windows CRLF to LF
        text = text.replace("\r\n", "\n")

        if (!cleanAiMathAndTables) {
            return text
        }

        // 3. Protect fenced code blocks before replacing math delimiters and repairing tables
        val codeBlocks = mutableListOf<String>()
        val protectedText = FENCED_CODE_REGEX.replace(text) { matchResult ->
            val index = codeBlocks.size
            codeBlocks.add(matchResult.value)
            "__CN_CODE_BLOCK_${index}__"
        }

        // 4. Convert \[...\] -> $$...$$ and \(...\) -> $...$
        var processed = BLOCK_MATH_DELIM_REGEX.replace(protectedText) { match ->
            val inner = match.groupValues[1].trim()
            "\n$$\n$inner\n$$\n"
        }
        processed = INLINE_MATH_DELIM_REGEX.replace(processed) { match ->
            val inner = match.groupValues[1].trim()
            "$$inner$"
        }

        // 5. Repair broken Markdown table blank lines (when AI inserts blank lines between table rows)
        processed = repairMarkdownTables(processed)

        // Collapse excessive 3+ blank lines into 2
        processed = processed.replace(Regex("\n{3,}"), "\n\n")

        // 6. Restore fenced code blocks
        codeBlocks.forEachIndexed { index, block ->
            processed = processed.replace("__CN_CODE_BLOCK_${index}__", block)
        }

        return processed.trim()
    }

    private fun repairMarkdownTables(text: String): String {
        val lines = text.split("\n")
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val trimmed = current.trim()
            val isTableRow = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
            if (isTableRow) {
                result.add(current)
                // Look ahead: if next line is blank and the line after is also a table row, skip the blank line
                var j = i + 1
                while (j < lines.size && lines[j].trim().isEmpty()) {
                    j++
                }
                if (j > i + 1 && j < lines.size) {
                    val nextTrimmed = lines[j].trim()
                    val nextIsTableRow = nextTrimmed.startsWith("|") && nextTrimmed.endsWith("|") && nextTrimmed.count { it == '|' } >= 2
                    if (nextIsTableRow) {
                        i = j
                        continue
                    }
                }
            } else {
                result.add(current)
            }
            i++
        }
        return result.joinToString("\n")
    }

    /**
     * Converts Bengali digits (০-৯) and ASCII digits (0-9) to standard ASCII digits (0-9)
     * so 4-digit PIN locks work seamlessly in both Bengali and English keyboards.
     */
    fun normalizePinDigits(pin: String): String {
        val sb = StringBuilder()
        for (ch in pin) {
            when (ch) {
                in '০'..'৯' -> sb.append(('0'.code + (ch.code - '০'.code)).toChar())
                in '0'..'9' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Converts integer numbers to Bengali numeral string for clean Bengali UI badges.
     */
    fun toBengaliDigits(number: Int): String {
        val english = number.toString()
        val sb = StringBuilder()
        for (ch in english) {
            if (ch in '0'..'9') {
                sb.append(('০'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }
}
