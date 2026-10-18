package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.NoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object NoteExporter {

    fun toMarkdown(note: NoteModel): String {
        val sb = StringBuilder()
        sb.append("# ").append(note.title).append("\n")
        sb.append("> ফোল্ডার: ").append(note.folder).append(" | আপডেট: ").append(note.updatedAt.take(10)).append("\n\n")
        note.sections.forEachIndexed { idx, section ->
            if (section.title.isNotBlank()) {
                sb.append("## ").append(section.title).append("\n\n")
            } else if (note.sections.size > 1) {
                sb.append("## সেকশন ").append(TextNormalizer.toBengaliDigits(idx + 1)).append("\n\n")
            }
            sb.append(section.content.trim()).append("\n\n")
        }
        return sb.toString().trim() + "\n"
    }

    fun toPlainText(note: NoteModel): String {
        val sb = StringBuilder()
        sb.append(note.title).append("\n")
        sb.append("ফোল্ডার: ").append(note.folder).append("\n")
        sb.append("========================================\n\n")
        note.sections.forEachIndexed { idx, section ->
            if (section.title.isNotBlank()) {
                sb.append("[ ").append(section.title).append(" ]\n")
            } else if (note.sections.size > 1) {
                sb.append("[ সেকশন ").append(TextNormalizer.toBengaliDigits(idx + 1)).append(" ]\n")
            }
            val stripped = section.content
                .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
                .replace("**", "")
                .replace("$$", "")
            sb.append(stripped.trim()).append("\n\n")
        }
        return sb.toString().trim() + "\n"
    }

    /**
     * Generates an MS Word compatible (.doc) HTML document with UTF-8 Bengali support,
     * styled tables, math blocks, and clean typography.
     */
    fun toWordDocHtml(note: NoteModel): String {
        val sb = StringBuilder()
        sb.append(
            """
            <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
            <head>
            <meta charset="utf-8">
            <title>${escapeHtml(note.title)}</title>
            <style>
                body { font-family: 'Noto Sans Bengali', 'Nirmala UI', sans-serif; color: #09090b; line-height: 1.6; padding: 24px; }
                h1 { font-size: 24pt; border-bottom: 2px solid #09090b; padding-bottom: 6px; }
                h2 { font-size: 16pt; margin-top: 18px; color: #18181b; }
                pre, code { font-family: 'JetBrains Mono', monospace; background-color: #f4f4f5; padding: 6px; border: 1px solid #e4e4e7; }
                table { border-collapse: collapse; width: 100%; margin: 12px 0; }
                th, td { border: 1px solid #27272a; padding: 8px; text-align: left; }
                th { background-color: #f4f4f5; font-weight: bold; }
                .math { font-family: 'Cambria Math', serif; font-style: italic; background: #fafafa; padding: 4px 8px; border-left: 3px solid #09090b; }
            </style>
            </head>
            <body>
            <h1>${escapeHtml(note.title)}</h1>
            <p><strong>ফোল্ডার:</strong> ${escapeHtml(note.folder)} | <strong>আপডেট:</strong> ${escapeHtml(note.updatedAt.take(10))}</p>
            """.trimIndent()
        )

        note.sections.forEach { sec ->
            if (sec.title.isNotBlank()) {
                sb.append("<h2>${escapeHtml(sec.title)}</h2>\n")
            }
            sb.append(renderMarkdownToSimpleHtml(sec.content))
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun renderMarkdownToSimpleHtml(markdown: String): String {
        val lines = markdown.split("\n")
        val html = StringBuilder()
        var inTable = false
        var inCode = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (!inCode) {
                    html.append("<pre><code>")
                    inCode = true
                } else {
                    html.append("</code></pre>\n")
                    inCode = false
                }
                continue
            }
            if (inCode) {
                html.append(escapeHtml(line)).append("\n")
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2) {
                // Skip markdown separator row like | :--- | :--- |
                if (trimmed.replace("|", "").replace(":", "").replace("-", "").trim().isEmpty()) {
                    continue
                }
                if (!inTable) {
                    html.append("<table>\n")
                    inTable = true
                }
                val cells = trimmed.trim('|').split("|").map { it.trim() }
                html.append("<tr>")
                cells.forEach { cell ->
                    html.append("<td>").append(escapeHtml(cell)).append("</td>")
                }
                html.append("</tr>\n")
                continue
            } else if (inTable) {
                html.append("</table>\n")
                inTable = false
            }

            when {
                trimmed.startsWith("### ") -> html.append("<h3>").append(escapeHtml(trimmed.removePrefix("### "))).append("</h3>\n")
                trimmed.startsWith("## ") -> html.append("<h2>").append(escapeHtml(trimmed.removePrefix("## "))).append("</h2>\n")
                trimmed.startsWith("# ") -> html.append("<h1>").append(escapeHtml(trimmed.removePrefix("# "))).append("</h1>\n")
                trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4 ->
                    html.append("<div class='math'>").append(escapeHtml(trimmed.removeSurrounding("$$"))).append("</div>\n")
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    html.append("<p>• ").append(escapeHtml(trimmed.substring(2))).append("</p>\n")
                trimmed.isEmpty() -> html.append("<br/>\n")
                else -> html.append("<p>").append(escapeHtml(line)).append("</p>\n")
            }
        }
        if (inTable) html.append("</table>\n")
        if (inCode) html.append("</code></pre>\n")
        return html.toString()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    /**
     * Generates a clean multi-page PDF document directly to the OutputStream using Android's PdfDocument API.
     */
    suspend fun writePdfToStream(context: Context, note: NoteModel, outputStream: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                val pageWidth = 595 // A4 width in PostScript points
                val pageHeight = 842 // A4 height in PostScript points
                val margin = 42f
                val contentWidth = (pageWidth - margin * 2).toInt()

                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(9, 9, 11)
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(113, 113, 122)
                    textSize = 11f
                }

                val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(9, 9, 11)
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(24, 24, 27)
                    textSize = 12f
                }

                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var currentPage = pdfDocument.startPage(pageInfo)
                var canvas: Canvas = currentPage.canvas
                var yOffset = margin

                fun startNewPage() {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    yOffset = margin
                }

                fun drawMultilineText(text: String, paint: TextPaint, spacingAfter: Float = 10f) {
                    if (text.isBlank()) {
                        yOffset += spacingAfter
                        return
                    }
                    val layout = StaticLayout.Builder
                        .obtain(text, 0, text.length, paint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(4f, 1.15f)
                        .setIncludePad(false)
                        .build()

                    for (lineIdx in 0 until layout.lineCount) {
                        val lineTop = layout.getLineTop(lineIdx)
                        val lineBottom = layout.getLineBottom(lineIdx)
                        val lineHeight = (lineBottom - lineTop).toFloat()
                        if (yOffset + lineHeight > pageHeight - margin) {
                            startNewPage()
                        }
                        val lineStart = layout.getLineStart(lineIdx)
                        val lineEnd = layout.getLineEnd(lineIdx)
                        val lineText = text.substring(lineStart, lineEnd).trimEnd('\n')
                        canvas.drawText(lineText, margin, yOffset + lineHeight - paint.descent(), paint)
                        yOffset += lineHeight
                    }
                    yOffset += spacingAfter
                }

                // Draw Header
                drawMultilineText(note.title, titlePaint, 6f)
                drawMultilineText(
                    "ফোল্ডার: ${note.folder}  |  তারিখ: ${note.updatedAt.take(10)}",
                    metaPaint,
                    14f
                )

                val linePaint = Paint().apply {
                    color = Color.rgb(228, 228, 231)
                    strokeWidth = 1f
                }
                canvas.drawLine(margin, yOffset, pageWidth - margin, yOffset, linePaint)
                yOffset += 16f

                // Draw Sections
                note.sections.forEachIndexed { idx, section ->
                    val secHeader = section.title.ifBlank {
                        if (note.sections.size > 1) "সেকশন ${TextNormalizer.toBengaliDigits(idx + 1)}" else ""
                    }
                    if (secHeader.isNotBlank()) {
                        drawMultilineText(secHeader, sectionTitlePaint, 6f)
                    }
                    drawMultilineText(section.content, bodyPaint, 16f)
                }

                pdfDocument.finishPage(currentPage)
                pdfDocument.writeTo(outputStream)
                pdfDocument.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
