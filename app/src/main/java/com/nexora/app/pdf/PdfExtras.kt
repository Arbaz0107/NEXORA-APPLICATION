package com.nexora.app.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Extra PDF Editor operations. Each reads [src] and writes a new file [out]; the source is never changed. */
object PdfExtras {

    // ---------------- pattern redaction ----------------

    class Preset(val id: String, val label: String, val regex: Regex)

    val presets = listOf(
        Preset("email", "Email addresses", Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
        Preset("phone", "Phone numbers", Regex("(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(?\\d{3,5}\\)?[\\s-]?)\\d{3,4}[\\s-]?\\d{3,4}")),
        Preset("aadhaar", "Aadhaar numbers", Regex("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b")),
        Preset("pan", "PAN numbers", Regex("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b")),
        Preset("card", "Card numbers", Regex("\\b(?:\\d[ -]?){13,16}\\b")),
        Preset("url", "Web links", Regex("(?:https?://|www\\.)[^\\s]+"))
    )

    /** Matches of [regex] inside each line of text, as boxes. */
    fun findRegex(session: LayoutSession, regex: Regex, onPage: (Int) -> Unit = {}): List<Hit> {
        val hits = ArrayList<Hit>()
        for (p in 0 until session.pageCount) {
            onPage(p)
            val lay = session.page(p)
            for (line in lay.lines) {
                for (m in regex.findAll(line.text)) {
                    if (m.value.isEmpty()) continue
                    val idx = m.range.first
                    val end = m.range.last + 1
                    val spanned = line.words.filter { it.start < end && it.start + it.text.length > idx }
                    if (spanned.isEmpty()) continue
                    val box = spanned.map { it.box }.reduce { a, b -> a.union(b) }
                    val s = spanned.first().start
                    val e = spanned.last().start + spanned.last().text.length
                    hits += Hit(p, box, line.text.substring(s, e), idx - s, m.value.length, line.size, line.bold, line.italic, line.family)
                }
            }
        }
        return hits
    }

    // ---------------- watermark, header and footer ----------------

    private fun checkEncodable(font: PDType1Font, text: String) {
        try { font.encode(text) } catch (e: Exception) {
            throw PdfProblem("This text has letters the built-in font can't write. Use English letters, numbers and common symbols.")
        }
    }

    fun watermark(src: File, out: File, text: String, size: Float, opacity: Float, angleDeg: Float, rgb: Int, pages: Set<Int>?) {
        if (text.isBlank()) throw PdfProblem("Type the watermark text first.")
        PdfEngine.open(src).use { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            checkEncodable(font, text)
            val a = Math.toRadians(angleDeg.toDouble())
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
            for (i in 0 until doc.numberOfPages) {
                if (pages != null && i !in pages) continue
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val cx = box.lowerLeftX + box.width / 2f
                val cy = box.lowerLeftY + box.height / 2f
                val tw = font.getStringWidth(text) / 1000f * size
                val x = cx - (tw / 2f) * ca + (size * 0.35f) * sa
                val y = cy - (tw / 2f) * sa - (size * 0.35f) * ca
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState()
                    gs.nonStrokingAlphaConstant = opacity
                    gs.strokingAlphaConstant = opacity
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(r, g, b)
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setTextMatrix(Matrix.getRotateInstance(a, x, y))
                    cs.showText(text)
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    /** [align]: 0 left, 1 centre, 2 right. "{n}" becomes the page number and "{total}" the page count. */
    fun headerFooter(src: File, out: File, text: String, top: Boolean, align: Int, size: Float, skipFirst: Boolean) {
        if (text.isBlank()) throw PdfProblem("Type the text first.")
        PdfEngine.open(src).use { doc ->
            val font = PDType1Font.HELVETICA
            checkEncodable(font, text.replace("{n}", "1").replace("{total}", "1"))
            val total = doc.numberOfPages
            for (i in 0 until total) {
                if (skipFirst && i == 0) continue
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val s = text.replace("{n}", (i + 1).toString()).replace("{total}", total.toString())
                val tw = font.getStringWidth(s) / 1000f * size
                val margin = 28f
                val x = when (align) {
                    0 -> box.lowerLeftX + margin
                    2 -> box.upperRightX - margin - tw
                    else -> box.lowerLeftX + (box.width - tw) / 2f
                }
                val y = if (top) box.upperRightY - margin else box.lowerLeftY + margin - size / 2f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(70, 70, 70)
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(x, y)
                    cs.showText(s)
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    // ---------------- document details ----------------

    class Info(val title: String, val author: String, val subject: String, val keywords: String, val producer: String)

    fun readInfo(src: File): Info = PdfEngine.open(src).use { doc ->
        val i = doc.documentInformation
        Info(i?.title.orEmpty(), i?.author.orEmpty(), i?.subject.orEmpty(), i?.keywords.orEmpty(), i?.producer.orEmpty())
    }

    fun writeInfo(src: File, out: File, title: String, author: String, subject: String, keywords: String) {
        PdfEngine.open(src).use { doc ->
            val i = doc.documentInformation ?: PDDocumentInformation()
            i.title = title.ifBlank { null }
            i.author = author.ifBlank { null }
            i.subject = subject.ifBlank { null }
            i.keywords = keywords.ifBlank { null }
            doc.documentInformation = i
            doc.save(out)
        }
    }

    /** Removes title, author, dates, creator and the hidden XML metadata. */
    fun cleanInfo(src: File, out: File) {
        PdfEngine.open(src).use { doc ->
            doc.documentInformation = PDDocumentInformation()
            doc.documentCatalog.cosObject.removeItem(COSName.METADATA)
            doc.save(out)
        }
    }

    // ---------------- form fields ----------------

    class FormField(val name: String, val kind: String, val value: String, val options: List<String>)

    fun formFields(src: File): List<FormField> = PdfEngine.open(src).use { doc ->
        val form = doc.documentCatalog.acroForm ?: return@use emptyList()
        val out = ArrayList<FormField>()
        for (f in form.fieldTree) {
            try {
                when (f) {
                    is PDTextField -> out += FormField(f.fullyQualifiedName, "text", f.value.orEmpty(), emptyList())
                    is PDCheckBox -> out += FormField(f.fullyQualifiedName, "check", if (f.isChecked) "on" else "", emptyList())
                    is PDChoice -> out += FormField(f.fullyQualifiedName, "choice", f.value.firstOrNull().orEmpty(), f.options.toList())
                    else -> {}
                }
            } catch (_: Throwable) {}
        }
        out
    }

    /** Returns how many fields could not be filled. */
    fun fillForm(src: File, out: File, values: Map<String, String>, lock: Boolean): Int {
        var failed = 0
        PdfEngine.open(src).use { doc ->
            val form = doc.documentCatalog.acroForm ?: throw PdfProblem("This PDF has no form fields.")
            for ((name, v) in values) {
                try {
                    when (val f = form.getField(name)) {
                        is PDTextField -> f.value = v
                        is PDCheckBox -> if (v.isNotEmpty()) f.check() else f.unCheck()
                        is PDChoice -> f.setValue(v)
                        else -> failed++
                    }
                } catch (e: Throwable) { failed++ }
            }
            if (lock) try { form.flatten() } catch (_: Throwable) { failed++ }
            doc.save(out)
        }
        return failed
    }

    // ---------------- bookmarks ----------------

    class Bookmark(val title: String, val page: Int, val level: Int)

    fun bookmarks(src: File): List<Bookmark> = PdfEngine.open(src).use { doc ->
        val root = doc.documentCatalog.documentOutline ?: return@use emptyList()
        val out = ArrayList<Bookmark>()
        fun walk(node: PDOutlineNode, level: Int) {
            for (item in node.children()) {
                val page = try { item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } ?: -1 } catch (_: Throwable) { -1 }
                out += Bookmark(item.title.orEmpty(), page, level)
                if (level < 6) walk(item, level + 1)
            }
        }
        walk(root, 0)
        out
    }

    fun addBookmark(src: File, out: File, title: String, pageIndex: Int) {
        if (title.isBlank()) throw PdfProblem("Type a name for the bookmark.")
        PdfEngine.open(src).use { doc ->
            val cat = doc.documentCatalog
            val outline = cat.documentOutline ?: PDDocumentOutline().also { cat.documentOutline = it }
            val item = PDOutlineItem()
            item.title = title.trim()
            val dest = PDPageFitDestination()
            dest.page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
            item.destination = dest
            outline.addLast(item)
            outline.openNode()
            doc.save(out)
        }
    }

    fun clearBookmarks(src: File, out: File) {
        PdfEngine.open(src).use { doc ->
            doc.documentCatalog.cosObject.removeItem(COSName.OUTLINES)
            doc.save(out)
        }
    }

    // ---------------- links ----------------

    /** Makes each box a tappable link to [url]. Boxes are in the page as shown (top-left origin, points). */
    fun addLinks(src: File, out: File, hits: List<Hit>, url: String) {
        PdfEngine.open(src).use { doc ->
            for (h in hits) {
                val page = doc.getPage(h.page)
                if (page.rotation != 0) continue
                val box = page.cropBox ?: page.mediaBox
                val a = PDAnnotationLink()
                a.rectangle = PDRectangle(
                    box.lowerLeftX + h.box.l, box.lowerLeftY + box.height - h.box.b,
                    h.box.w, h.box.h
                )
                val act = PDActionURI()
                act.uri = url
                a.action = act
                val bs = PDBorderStyleDictionary()
                bs.width = 0f
                a.borderStyle = bs
                val list = page.annotations
                list.add(a)
                page.annotations = list
            }
            doc.save(out)
        }
    }

    // ---------------- trim margins ----------------

    /** Hides [percent] of each side's size on the chosen pages. The hidden part stays in the file, so this is not redaction. */
    fun trimMargins(src: File, out: File, percent: Float, pages: Set<Int>?) {
        PdfEngine.open(src).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                if (pages != null && i !in pages) continue
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val dx = box.width * percent / 100f
                val dy = box.height * percent / 100f
                val nb = PDRectangle(box.lowerLeftX + dx, box.lowerLeftY + dy, box.width - 2 * dx, box.height - 2 * dy)
                page.cropBox = nb
            }
            doc.save(out)
        }
    }

    // ---------------- text ----------------

    fun extractText(src: File): String = PdfEngine.open(src).use { doc -> PDFTextStripper().getText(doc).trim() }
}
