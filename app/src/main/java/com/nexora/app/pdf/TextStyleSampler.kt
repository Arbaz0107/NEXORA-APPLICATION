package com.nexora.app.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What a piece of existing text looks like, so re-typed text can match it. */
data class FontLook(val family: TextFamily, val bold: Boolean, val italic: Boolean)

object TextStyleSampler {

    private val sansNames = listOf("arial", "helvet", "calibri", "verdana", "tahoma", "roboto", "sans", "segoe", "open", "noto", "lato", "inter", "gothic", "myriad", "frutiger", "futura")
    private val monoNames = listOf("courier", "mono", "consolas", "menlo", "typewriter", "lucida console", "code")
    private val serifNames = listOf("times", "georgia", "garamond", "palatino", "cambria", "minion", "bookman", "century", "serif", "roman", "baskerville", "didot", "charter")

    /** Works out family, weight and slant from a PDF font's name and descriptor. Never throws. */
    fun lookOf(font: PDFont?): FontLook {
        if (font == null) return FontLook(TextFamily.SANS, false, false)
        val raw = try { font.name.orEmpty() } catch (e: Exception) { "" }
        val name = raw.substringAfter('+').lowercase()
        val d = try { font.fontDescriptor } catch (e: Exception) { null }

        val bold = listOf("bold", "black", "heavy", "semibold", "demi", "extrabold").any { name.contains(it) } ||
            (d != null && (runCatching { d.isForceBold }.getOrDefault(false) || runCatching { d.fontWeight >= 700f }.getOrDefault(false)))
        val italic = listOf("italic", "oblique", "ital").any { name.contains(it) } ||
            (d != null && (runCatching { d.isItalic }.getOrDefault(false) || runCatching { d.italicAngle != 0f }.getOrDefault(false)))

        val isSansName = name.contains("sans") || sansNames.any { name.contains(it) }
        val family = when {
            monoNames.any { name.contains(it) } -> TextFamily.MONO
            isSansName -> TextFamily.SANS
            serifNames.any { name.contains(it) } -> TextFamily.SERIF
            d != null && runCatching { d.isFixedPitch }.getOrDefault(false) -> TextFamily.MONO
            d != null && runCatching { d.isSerif }.getOrDefault(false) -> TextFamily.SERIF
            else -> TextFamily.SANS
        }
        return FontLook(family, bold, italic)
    }

    /**
     * Reads the background colour and the ink colour of the text inside [box] (page points) from a rendered
     * page. [scale] is bitmap pixels per point. Returns background to ink, both opaque.
     */
    fun sample(bmp: Bitmap, scale: Float, box: PBox): Pair<Int, Int> {
        fun px(x: Float, y: Float): Int =
            bmp.getPixel((x * scale).toInt().coerceIn(0, bmp.width - 1), (y * scale).toInt().coerceIn(0, bmp.height - 1)) or 0xFF000000.toInt()

        // Background: the most common colour on a ring just outside the text.
        val votes = HashMap<Int, Int>()
        val ring = box.inflate(2f)
        val steps = 24
        for (i in 0..steps) {
            val fx = ring.l + ring.w * i / steps
            val fy = ring.t + ring.h * i / steps
            for (c in listOf(px(fx, ring.t), px(fx, ring.b), px(ring.l, fy), px(ring.r, fy))) {
                val key = ((c shr 20) and 0xF) shl 8 or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)
                votes[key] = (votes[key] ?: 0) + 1
            }
        }
        val topKey = votes.maxByOrNull { it.value }?.key
        val bg = if (topKey == null) 0xFFFFFFFF.toInt() else {
            // Re-read a real pixel of that colour so the cover matches exactly.
            var found = px(ring.l, ring.t)
            loop@ for (i in 0..steps) {
                val fx = ring.l + ring.w * i / steps
                for (c in listOf(px(fx, ring.t), px(fx, ring.b))) {
                    val key = ((c shr 20) and 0xF) shl 8 or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)
                    if (key == topKey) { found = c; break@loop }
                }
            }
            found
        }

        // Ink: average of the pixels furthest from the background inside the box.
        val samples = ArrayList<Pair<Int, Int>>()
        val nx = 60; val ny = 20
        for (iy in 0 until ny) for (ix in 0 until nx) {
            val c = px(box.l + box.w * (ix + 0.5f) / nx, box.t + box.h * (iy + 0.5f) / ny)
            samples += dist(c, bg) to c
        }
        samples.sortByDescending { it.first }
        val take = max(1, samples.size / 12)
        val far = samples.take(take)
        val ink = if (far.first().first < 90) {
            if (luma(bg) > 140) 0xFF111111.toInt() else 0xFFF2F2F2.toInt()
        } else {
            var r = 0; var g = 0; var b = 0
            far.forEach { (_, c) -> r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF }
            (0xFF shl 24) or ((r / far.size) shl 16) or ((g / far.size) shl 8) or (b / far.size)
        }
        return bg to ink
    }

    private fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    private fun luma(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    /** Cheap guard so callers can skip sampling on an absurd box. */
    fun usable(box: PBox) = min(box.w, box.h) > 1f
}
