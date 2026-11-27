package com.nexora.app.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Structural clean-up that shrinks PDFs which are mostly text and fonts, where there are no big pictures to
 * re-encode: identical streams (fonts, images, content) stored many times are merged into one, streams are
 * re-compressed with the strongest Deflate setting, and unused extras (thumbnails, editor data) are dropped.
 * Every step is best-effort: a failure leaves the document as it was.
 */
object PdfOptimiser {

    fun run(doc: PDDocument) {
        try { dropExtras(doc) } catch (e: Throwable) { /* keep going */ }
        val unique = ArrayList<COSStream>()
        try { mergeDuplicates(doc, unique) } catch (e: Throwable) { /* keep going */ }
        for (s in unique) try { recompress(s) } catch (e: Throwable) { /* leave this stream alone */ }
    }

    private fun dropExtras(doc: PDDocument) {
        for (page in doc.pages) {
            page.cosObject.removeItem(COSName.THUMB)
            page.cosObject.removeItem(COSName.getPDFName("PieceInfo"))
        }
        doc.documentCatalog.cosObject.removeItem(COSName.getPDFName("PieceInfo"))
    }

    /** Walks everything reachable from the catalog and points repeated, identical streams at a single copy. */
    private fun mergeDuplicates(doc: PDDocument, unique: MutableList<COSStream>) {
        val canon = HashMap<String, COSStream>()
        val seen = IdentityHashMap<COSBase, Boolean>()

        fun canonicalFor(s: COSStream): COSStream {
            val key = keyOf(s) ?: return s
            val first = canon[key]
            if (first == null) { canon[key] = s; return s }
            return first
        }

        fun visit(start: COSBase?) {
            val base = if (start is COSObject) start.getObject() else start
            if (base == null || seen.put(base, true) != null) return
            if (base is COSStream) unique.add(base)
            when (base) {
                is COSDictionary -> for (k in base.keySet().toList()) {
                    val item = base.getItem(k)
                    val target = if (item is COSObject) item.getObject() else item
                    if (item is COSObject && target is COSStream) {
                        val c = canonicalFor(target)
                        if (c !== target) { base.setItem(k, c); visit(c); continue }
                    }
                    visit(item)
                }
                is COSArray -> for (i in 0 until base.size()) {
                    val item = base.get(i)
                    val target = if (item is COSObject) item.getObject() else item
                    if (item is COSObject && target is COSStream) {
                        val c = canonicalFor(target)
                        if (c !== target) { base.set(i, c); visit(c); continue }
                    }
                    visit(item)
                }
                else -> {}
            }
        }
        visit(doc.documentCatalog.cosObject)
    }

    /** A fingerprint of a stream's settings and raw bytes, or null if its dictionary points at other objects. */
    private fun keyOf(s: COSStream): String? {
        val sb = StringBuilder()
        for ((k, v) in s.entrySet().sortedBy { it.key.name }) {
            if (k == COSName.LENGTH) continue
            sb.append(k.name).append('=').append(plain(v) ?: return null).append(';')
        }
        val md = MessageDigest.getInstance("SHA-256")
        s.createRawInputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return sb.toString() + md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun plain(v: COSBase?): String? = when (v) {
        is COSName -> "/" + v.name
        is COSInteger -> v.longValue().toString()
        is COSFloat -> v.floatValue().toString()
        is COSBoolean -> v.value.toString()
        is COSString -> "(" + v.string + ")"
        is COSArray -> (0 until v.size()).joinToString(",", "[", "]") { plain(v.get(it)) ?: return null }
        else -> null
    }

    /** Re-packs a text-like stream with Deflate level 9 if that makes it smaller. */
    private fun recompress(s: COSStream) {
        val subtype = s.getNameAsString(COSName.SUBTYPE)
        if (subtype == "Image" || subtype == "XML" || s.getItem(COSName.DECODE_PARMS) != null) return
        val filter = s.getDictionaryObject(COSName.FILTER)
        val onlyFlate = when (filter) {
            null -> false
            is COSName -> filter == COSName.FLATE_DECODE
            is COSArray -> filter.size() == 1 && filter.getObject(0) == COSName.FLATE_DECODE
            else -> return
        }
        val uncompressed = filter == null
        if (!uncompressed && !onlyFlate) return
        val rawLen = s.createRawInputStream().use { it.readBytes().size }
        if (rawLen < 64 || rawLen > 20_000_000) return
        val plainBytes = (if (uncompressed) s.createRawInputStream() else s.createInputStream()).use { it.readBytes() }
        val packed = ByteArrayOutputStream()
        DeflaterOutputStream(packed, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(plainBytes) }
        if (packed.size() >= rawLen) return
        s.createRawOutputStream().use { it.write(packed.toByteArray()) }
        s.setItem(COSName.FILTER, COSName.FLATE_DECODE)
    }
}
