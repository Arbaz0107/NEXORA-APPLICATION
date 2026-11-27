package com.nexora.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.pdf.PdfExtras
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "More" sheet of the PDF Editor: signature and stamps, pattern redaction, watermark, header/footer, forms,
 * bookmarks, links, document details, trimming and text extraction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorExtrasSheet(ctl: EditorController, currentPage: Int, onClose: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
    val title = when (page) {
        "sign" -> "Signature and stamps"; "pattern" -> "Redact by pattern"; "mark" -> "Watermark"
        "hf" -> "Header and footer"; "form" -> "Fill form"; "bm" -> "Bookmarks"; "link" -> "Add links"
        "info" -> "Document details"; "trim" -> "Trim margins"; "text" -> "Extract text"; else -> "More tools"
    }
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != null) IconButton(onClick = { page = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        when (page) {
            null -> {
                @Composable
                fun item(id: String, label: String, hint: String, icon: ImageVector) {
                    Surface(
                        Modifier.fillMaxWidth().clickable { page = id }, shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item("sign", "Signature and stamps", "Draw a signature, add APPROVED / PAID, initials, date", Icons.Outlined.Draw)
                item("pattern", "Redact by pattern", "Hide all emails, phone numbers, Aadhaar, PAN, cards at once", Icons.Outlined.VisibilityOff)
                item("mark", "Watermark", "DRAFT, CONFIDENTIAL or your own text on every page", Icons.Outlined.BrandingWatermark)
                item("hf", "Header and footer", "Text with page numbers at the top or bottom", Icons.Outlined.VerticalAlignBottom)
                item("form", "Fill form", "Fill in the form fields of this PDF", Icons.Outlined.EditNote)
                item("bm", "Bookmarks", "See and add bookmarks (the outline)", Icons.Outlined.Bookmarks)
                item("link", "Add links", "Make any word or phrase a tappable web link", Icons.Outlined.Link)
                item("info", "Document details", "Edit or clear title, author and hidden data", Icons.Outlined.Info)
                item("trim", "Trim margins", "Cut a border off every page", Icons.Outlined.Crop)
                item("text", "Extract text", "Copy or share all the text in this PDF", Icons.Outlined.TextSnippet)
            }
            "sign" -> SignPanel(ctl, currentPage, onClose)
            "pattern" -> PatternPanel(ctl, onClose)
            "mark" -> WatermarkPanel(ctl, onClose)
            "hf" -> HeaderFooterPanel(ctl, onClose)
            "form" -> FormPanel(ctl, onClose)
            "bm" -> BookmarkPanel(ctl, currentPage)
            "link" -> LinkPanel(ctl, onClose)
            "info" -> InfoPanel(ctl, onClose)
            "trim" -> TrimPanel(ctl, onClose)
            "text" -> TextPanel(ctl)
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

// =================================================================================================
//  Signature and stamps
// =================================================================================================

private fun signatureFile(context: Context) = File(context.filesDir, "signature.png")

/** Draws [strokes] onto a transparent picture cropped to the ink. */
private fun renderSignature(strokes: List<List<Offset>>, ink: Int, w: Int, h: Int): Bitmap? {
    val pts = strokes.flatten()
    if (pts.size < 2 || w <= 0 || h <= 0) return null
    val big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(big)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        if (s.size == 1) { c.drawPoint(s[0].x, s[0].y, p); continue }
        val path = android.graphics.Path()
        path.moveTo(s[0].x, s[0].y)
        for (i in 1 until s.size) path.lineTo(s[i].x, s[i].y)
        c.drawPath(path, p)
    }
    val pad = 10
    val l = (pts.minOf { it.x }.toInt() - pad).coerceAtLeast(0)
    val t = (pts.minOf { it.y }.toInt() - pad).coerceAtLeast(0)
    val r = (pts.maxOf { it.x }.toInt() + pad).coerceAtMost(w)
    val b = (pts.maxOf { it.y }.toInt() + pad).coerceAtMost(h)
    if (r - l < 4 || b - t < 4) { big.recycle(); return null }
    val out = Bitmap.createBitmap(big, l, t, r - l, b - t)
    if (out !== big) big.recycle()
    return out
}

private fun stampBitmap(text: String, argb: Int): Bitmap {
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; textSize = 72f; isFakeBoldText = true }
    val tw = tp.measureText(text)
    val padX = 36f; val padY = 24f
    val w = (tw + padX * 2).toInt().coerceAtLeast(40)
    val h = (72f * 1.15f + padY * 2).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; style = Paint.Style.STROKE; strokeWidth = 8f }
    c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), 18f, 18f, bp)
    c.drawText(text, padX, padY + 72f * 0.88f, tp)
    return bmp
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignPanel(ctl: EditorController, currentPage: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var inkBlue by remember { mutableStateOf(true) }
    var remember_ by remember { mutableStateOf(true) }
    val hasSaved = remember { signatureFile(context).exists() }
    var custom by remember { mutableStateOf("") }
    val ink = if (inkBlue) 0xFF1A3FA6.toInt() else 0xFF111111.toInt()

    Text("Draw with your finger", fontWeight = FontWeight.SemiBold)
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(BorderStroke(1.dp, Color(0x55888888)), RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> strokes = strokes + listOf(listOf(o)) },
                    onDrag = { change, _ ->
                        change.consume()
                        val last = strokes.lastOrNull() ?: return@detectDragGestures
                        strokes = strokes.dropLast(1) + listOf(last + change.position)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (s in strokes) {
                for (i in 1 until s.size) drawLine(Color(ink), s[i - 1], s[i], 7f, StrokeCap.Round)
                if (s.size == 1) drawCircle(Color(ink), 3.5f, s[0])
            }
        }
        if (strokes.isEmpty()) Text("Sign here", Modifier.align(Alignment.Center), color = Color(0x55000000))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(inkBlue, { inkBlue = true }, label = { Text("Blue ink") })
        FilterChip(!inkBlue, { inkBlue = false }, label = { Text("Black ink") })
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(remember_, { remember_ = it }); Spacer(Modifier.width(8.dp)); Text("Remember it on this phone", fontSize = 13.sp)
    }
    Button(
        onClick = {
            val bmp = renderSignature(strokes, ink, size.width, size.height)
            if (bmp == null) Toast.makeText(context, "Draw your signature first.", Toast.LENGTH_SHORT).show()
            else scope.launch {
                if (remember_) try { signatureFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Throwable) {}
                ctl.addBitmap(bmp, currentPage, 150f)
                Toast.makeText(context, "Placed. Drag to move, pull a corner to resize.", Toast.LENGTH_LONG).show()
                onClose()
            }
        },
        enabled = strokes.isNotEmpty(), modifier = Modifier.fillMaxWidth()
    ) { Text("Place signature on page ${currentPage + 1}") }
    if (hasSaved) OutlinedButton(
        onClick = {
            scope.launch {
                val b = android.graphics.BitmapFactory.decodeFile(signatureFile(context).path)
                if (b == null) Toast.makeText(context, "Saved signature couldn't be read.", Toast.LENGTH_SHORT).show()
                else { ctl.addBitmap(b, currentPage, 150f); onClose() }
            }
        }, modifier = Modifier.fillMaxWidth()
    ) { Text("Use my saved signature") }

    HorizontalDivider()
    Text("Stamps", fontWeight = FontWeight.SemiBold)
    val today = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("APPROVED", 0xFF1B8A3A.toInt(), 0), Triple("PAID", 0xFF1B8A3A.toInt(), 0), Triple("DRAFT", 0xFF6B6B6B.toInt(), 0),
            Triple("CONFIDENTIAL", 0xFFC62828.toInt(), 0), Triple("REJECTED", 0xFFC62828.toInt(), 0), Triple("COPY", 0xFF1A3FA6.toInt(), 0),
            Triple(today, 0xFF1A3FA6.toInt(), 0)
        ).forEach { (t, col, _) ->
            AssistChip(onClick = { scope.launch { ctl.addBitmap(stampBitmap(t, col), currentPage, 130f); onClose() } }, label = { Text(t) })
        }
    }
    OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Your own stamp (initials, name…)") })
    OutlinedButton(
        onClick = { scope.launch { ctl.addBitmap(stampBitmap(custom.trim(), 0xFF1A3FA6.toInt()), currentPage, 130f); onClose() } },
        enabled = custom.isNotBlank(), modifier = Modifier.fillMaxWidth()
    ) { Text("Place stamp") }
}

// =================================================================================================
//  Pattern redaction
// =================================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(setOf("email", "phone")) }
    var custom by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    Hint("Finds the text and puts a black redaction box over each match. Boxes become permanent when you save: the page is turned into a picture and the hidden text is gone. You can still undo before saving.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PdfExtras.presets.forEach { p ->
            FilterChip(p.id in chosen, { chosen = if (p.id in chosen) chosen - p.id else chosen + p.id }, label = { Text(p.label) })
        }
    }
    OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Or your own words / pattern (optional)") })
    Button(
        onClick = {
            val regexes = ArrayList<Regex>()
            PdfExtras.presets.filter { it.id in chosen }.forEach { regexes += it.regex }
            if (custom.isNotBlank()) regexes += try { Regex(custom.trim(), RegexOption.IGNORE_CASE) } catch (_: Throwable) { Regex(Regex.escape(custom.trim()), RegexOption.IGNORE_CASE) }
            if (regexes.isEmpty()) { status = "Choose something to hide."; return@Button }
            working = true
            scope.launch {
                var total = 0
                for (r in regexes) total += ctl.redactMatches(r)
                working = false
                if (total == 0) status = "Nothing matched. If this PDF is a scan, run OCR first."
                else { Toast.makeText(context, "$total redaction box${if (total == 1) "" else "es"} added. Check them, then save.", Toast.LENGTH_LONG).show(); onClose() }
            }
        },
        enabled = !working, modifier = Modifier.fillMaxWidth()
    ) { Text(if (working) "Searching…" else "Find and redact") }
    if (ctl.hits.isNotEmpty()) OutlinedButton(
        onClick = { val n = ctl.redactHits(); Toast.makeText(context, "$n redaction boxes added.", Toast.LENGTH_SHORT).show(); onClose() },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Redact all ${ctl.hits.size} search results from Find") }
    if (status.isNotEmpty()) Text(status, fontSize = 13.sp)
    Hint("Phone number matching is broad on purpose, so look over the boxes before saving.")
}

// =================================================================================================
//  Watermark, header/footer
// =================================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatermarkPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var size by remember { mutableFloatStateOf(64f) }
    var opacity by remember { mutableFloatStateOf(0.18f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var colorIdx by remember { mutableIntStateOf(0) }
    val colors = listOf("Grey" to 0x808080, "Red" to 0xC62828, "Blue" to 0x1A3FA6, "Black" to 0x000000)

    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Watermark text") })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("DRAFT", "CONFIDENTIAL", "COPY", "SAMPLE", "DO NOT COPY").forEach { AssistChip(onClick = { text = it }, label = { Text(it) }) }
    }
    Text("Size ${size.toInt()}", fontSize = 13.sp); Slider(size, { size = it }, valueRange = 20f..140f)
    Text("Strength ${(opacity * 100).toInt()}%", fontSize = 13.sp); Slider(opacity, { opacity = it }, valueRange = 0.05f..0.8f)
    Text("Angle ${angle.toInt()}°", fontSize = 13.sp); Slider(angle, { angle = it }, valueRange = 0f..90f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, (n, _) -> FilterChip(colorIdx == i, { colorIdx = i }, label = { Text(n) }) }
    }
    Button(
        onClick = { scope.launch { if (ctl.fileEdit("Watermark", { a, b -> PdfExtras.watermark(a, b, text.trim(), size, opacity, angle, colors[colorIdx].second, null); 0 }) != null) onClose() } },
        enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()
    ) { Text("Add to every page") }
    Hint("Marks you added so far are written into the pages first. You can still undo this step.")
}

@Composable
private fun HeaderFooterPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Page {n} of {total}") }
    var top by remember { mutableStateOf(false) }
    var align by remember { mutableIntStateOf(1) }
    var skipFirst by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(10f) }

    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Text") }, supportingText = { Text("{n} = page number, {total} = number of pages") })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!top, { top = false }, label = { Text("Footer") }); FilterChip(top, { top = true }, label = { Text("Header") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Left", "Centre", "Right").forEachIndexed { i, n -> FilterChip(align == i, { align = i }, label = { Text(n) }) }
    }
    Text("Size ${size.toInt()}", fontSize = 13.sp); Slider(size, { size = it }, valueRange = 7f..20f)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(skipFirst, { skipFirst = it }); Spacer(Modifier.width(8.dp)); Text("Skip the first page", fontSize = 13.sp) }
    Button(
        onClick = { scope.launch { if (ctl.fileEdit("Header/footer", { a, b -> PdfExtras.headerFooter(a, b, text.trim(), top, align, size, skipFirst); 0 }) != null) onClose() } },
        enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()
    ) { Text("Add to every page") }
}

// =================================================================================================
//  Forms
// =================================================================================================

@Composable
private fun FormPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fields by remember { mutableStateOf<List<PdfExtras.FormField>?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var lock by remember { mutableStateOf(true) }

    LaunchedEffect(ctl.current?.file) {
        val list = ctl.inspect { PdfExtras.formFields(it) } ?: emptyList()
        values.clear(); list.forEach { values[it.name] = it.value }
        fields = list
    }
    val list = fields
    if (list == null) { CircularProgressIndicator(); return }
    if (list.isEmpty()) { Text("This PDF has no form fields to fill. Use the Text tool to type onto the page instead."); return }
    Hint("${list.size} field${if (list.size == 1) "" else "s"} found.")
    list.forEach { f ->
        when (f.kind) {
            "check" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(values[f.name].orEmpty().isNotEmpty(), { values[f.name] = if (it) "on" else "" })
                Text(f.name, fontSize = 14.sp)
            }
            "choice" -> {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("${f.name}: ${values[f.name].orEmpty().ifEmpty { "choose" }}", maxLines = 1) }
                    DropdownMenu(open, { open = false }) {
                        f.options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { values[f.name] = o; open = false }) }
                    }
                }
            }
            else -> OutlinedTextField(values[f.name].orEmpty(), { values[f.name] = it }, Modifier.fillMaxWidth(), label = { Text(f.name) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(lock, { lock = it }); Spacer(Modifier.width(8.dp)); Text("Lock the answers into the page", fontSize = 13.sp) }
    Hint("Locking makes the answers part of the page so every viewer shows them, but they can no longer be edited as a form.")
    Button(
        onClick = {
            scope.launch {
                val failed = ctl.fileEdit("Fill form", { a, b -> PdfExtras.fillForm(a, b, values.toMap(), lock) })
                if (failed != null) {
                    if (failed > 0) Toast.makeText(context, "$failed field${if (failed == 1) "" else "s"} couldn't be filled.", Toast.LENGTH_LONG).show()
                    onClose()
                }
            }
        }, modifier = Modifier.fillMaxWidth()
    ) { Text("Apply answers") }
}

// =================================================================================================
//  Bookmarks
// =================================================================================================

@Composable
private fun BookmarkPanel(ctl: EditorController, currentPage: Int) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<PdfExtras.Bookmark>?>(null) }
    var name by remember { mutableStateOf("") }
    val file = ctl.current?.file
    LaunchedEffect(file) { list = ctl.inspect { PdfExtras.bookmarks(it) } ?: emptyList() }

    val l = list
    if (l == null) CircularProgressIndicator()
    else if (l.isEmpty()) Hint("No bookmarks in this PDF yet.")
    else l.forEach { b ->
        Text("${"    ".repeat(b.level)}• ${b.title}" + if (b.page >= 0) "   (page ${b.page + 1})" else "", fontSize = 14.sp)
    }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New bookmark name") })
    Button(
        onClick = { scope.launch { if (ctl.fileEdit("Add bookmark", { a, b -> PdfExtras.addBookmark(a, b, name, currentPage); 0 }, baseOnly = true) != null) name = "" } },
        enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()
    ) { Text("Add bookmark to page ${currentPage + 1}") }
    if (!l.isNullOrEmpty()) TextButton(onClick = { scope.launch { ctl.fileEdit("Clear bookmarks", { a, b -> PdfExtras.clearBookmarks(a, b); 0 }, baseOnly = true) } }) {
        Text("Remove all bookmarks", color = MaterialTheme.colorScheme.error)
    }
}

// =================================================================================================
//  Links
// =================================================================================================

@Composable
private fun LinkPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    Hint("Every place this text appears on a page becomes a tappable link. Links on rotated pages are skipped.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Text to turn into a link") })
    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Web address") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri))
    Button(
        onClick = {
            scope.launch {
                val found = ctl.findBoxes(query)
                if (found.isEmpty()) { Toast.makeText(context, "That text wasn't found.", Toast.LENGTH_SHORT).show(); return@launch }
                if (ctl.fileEdit("Add links", { a, b -> PdfExtras.addLinks(a, b, found, url.trim()); 0 }, baseOnly = true) != null) {
                    Toast.makeText(context, "${found.size} link${if (found.size == 1) "" else "s"} added.", Toast.LENGTH_SHORT).show(); onClose()
                }
            }
        },
        enabled = query.isNotBlank() && url.trim().length > 8, modifier = Modifier.fillMaxWidth()
    ) { Text("Add link") }
}

// =================================================================================================
//  Details, trim, text
// =================================================================================================

@Composable
private fun InfoPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<PdfExtras.Info?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    LaunchedEffect(ctl.current?.file) {
        val i = ctl.inspect { PdfExtras.readInfo(it) }
        if (i != null) { info = i; title = i.title; author = i.author; subject = i.subject; keywords = i.keywords }
    }
    if (info == null) { CircularProgressIndicator(); return }
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") })
    OutlinedTextField(author, { author = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Author") })
    OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Subject") })
    OutlinedTextField(keywords, { keywords = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Keywords") })
    info?.producer?.takeIf { it.isNotBlank() }?.let { Hint("Made with: $it") }
    Button(
        onClick = { scope.launch { if (ctl.fileEdit("Document details", { a, b -> PdfExtras.writeInfo(a, b, title, author, subject, keywords); 0 }, baseOnly = true) != null) onClose() } },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save details") }
    OutlinedButton(
        onClick = { scope.launch { if (ctl.fileEdit("Clear details", { a, b -> PdfExtras.cleanInfo(a, b); 0 }, baseOnly = true) != null) onClose() } },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Clear all hidden details") }
    Hint("Clearing removes title, author, dates, the app name and hidden XML data. Use it before sharing a file you don't want traced back to you.")
}

@Composable
private fun TrimPanel(ctl: EditorController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pct by remember { mutableFloatStateOf(5f) }
    Hint("Cuts the same amount off every side of every page.")
    Text("${pct.toInt()}% of each side", fontSize = 13.sp)
    Slider(pct, { pct = it }, valueRange = 1f..30f)
    Button(onClick = { scope.launch { if (ctl.fileEdit("Trim margins", { a, b -> PdfExtras.trimMargins(a, b, pct, null); 0 }) != null) onClose() } }, modifier = Modifier.fillMaxWidth()) { Text("Trim all pages") }
    Hint("The trimmed part is hidden, not deleted. To remove something for good, use Redact.")
}

@Composable
private fun TextPanel(ctl: EditorController) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ctl.current?.file) { text = ctl.inspect { PdfExtras.extractText(it) } ?: "" }
    val t = text
    if (t == null) { CircularProgressIndicator(); return }
    if (t.isBlank()) { Text("This PDF has no selectable text. If it is a scan, run OCR first."); return }
    Hint("${t.length} characters")
    Surface(Modifier.fillMaxWidth().heightIn(max = 260.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Text(t.take(6000) + if (t.length > 6000) "\n…" else "", Modifier.padding(12.dp).verticalScroll(rememberScrollState()), fontSize = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("PDF text", t))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy all") }
        OutlinedButton(onClick = {
            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }
            context.startActivity(Intent.createChooser(i, "Share text").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Share") }
    }
}
