package com.nexora.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.MainViewModel
import com.nexora.app.data.DownloadEntity
import com.nexora.app.data.DownloadQuality
import com.nexora.app.data.DownloadStatus
import com.nexora.app.data.LinkEntity
import com.nexora.app.ui.components.AppMark
import com.nexora.app.ui.components.BrandWordmark
import com.nexora.app.ui.components.VideoGlyph
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import com.nexora.app.ui.components.LocalNavBarClearance
import com.nexora.app.ui.theme.Neon
import com.nexora.app.util.Platform
import java.util.Calendar
import java.util.Locale

private val VIDEO_PLATFORMS = setOf(Platform.YOUTUBE, Platform.INSTAGRAM, Platform.FACEBOOK, Platform.NETFLIX)

/**
 * The Home tab: a bold vault summary, one-tap quick actions, what to pick up again, and how much
 * space downloads use. The full list, filters and bulk actions live on the Links tab.
 */
@Composable
fun DashboardScreen(vm: MainViewModel, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val all by vm.allLinks.collectAsState()
    val downloads by vm.downloads.collectAsState()

    val pinnedCount = remember(all) { all.count { it.isPinned } }
    val week = remember(all) { weeklyCounts(all) }
    val continueLinks = remember(all) { pickContinue(all) }
    val storage = remember(downloads) { storageBreakdown(downloads) }
    val thumbColors = listOf(Neon.coral, Neon.teal)

    fun pasteAndSave() {
        val copied = readClipboardForAdd(context)
        navigate(if (copied != null) "add?url=" + android.net.Uri.encode(copied) else "add")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalNavBarClearance.current)) {
        item(key = "d-brand") {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BrandWordmark()
                    Text("Save it once. Find it forever.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppMark()
            }
        }

        item(key = "d-hero") { VaultHero(all.size, pinnedCount, week) { navigate("links") } }

        item(key = "d-quick") {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickAction("Save link", Icons.Outlined.Add, Neon.lime) { pasteAndSave() }
                QuickAction("Video", null, Neon.coral, glyph = { VideoGlyph(Neon.ink) }) { navigate("downloads") }
                QuickAction("PDF", Icons.Outlined.PictureAsPdf, Neon.teal) { navigate("pdftools") }
                QuickAction("Image", Icons.Outlined.Image, Neon.green) { navigate("images") }
            }
        }

        if (all.isEmpty()) {
            item(key = "d-empty") {
                Text(
                    "Tap Save link to add your first one. Your recent links will show up here.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        } else {
            item(key = "d-h-continue") { SectionRow("Continue", "See all") { navigate("all_links") } }
            item(key = "d-continue") {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    continueLinks.forEachIndexed { i, link ->
                        ContinueCard(link, thumbColors[i % thumbColors.size], Modifier.weight(1f).fillMaxHeight()) { navigate("detail/${link.id}") }
                    }
                    if (continueLinks.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (storage.total > 0) {
            item(key = "d-storage") { StorageCard(storage) { navigate("downloads") } }
        }
    }
}

@Composable
private fun VaultHero(total: Int, pinned: Int, week: List<Int>, onClick: () -> Unit) {
    val maxCount = (week.maxOrNull() ?: 0).coerceAtLeast(1)
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Neon.lime)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 44.dp, y = (-4).dp)) {
            val s = size.width / 108f
            poly(Neon.coral, s, 30.73f, 29f, 44.52f, 29f, 44.52f, 79f, 30.73f, 79f)
            poly(Neon.teal, s, 63.48f, 29f, 77.27f, 29f, 77.27f, 79f, 63.48f, 79f)
            poly(Neon.ink, s, 30.73f, 29f, 44.52f, 29f, 77.27f, 79f, 63.48f, 79f)
        }
        Column(Modifier.padding(20.dp)) {
            Text("Your vault", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Neon.ink.copy(alpha = 0.7f))
            Text("$total", fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, color = Neon.ink)
            Text(
                if (pinned > 0) "links saved · $pinned pinned" else "links saved",
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Neon.ink.copy(alpha = 0.75f)
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp)) {
            Row(Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, c ->
                    val h = if (c == 0) 6.dp else (8f + 22f * c / maxCount).dp
                    Box(
                        Modifier.width(12.dp).height(h).clip(RoundedCornerShape(4.dp))
                            .background(if (i == week.lastIndex) Neon.ink else Neon.ink.copy(alpha = 0.28f))
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Saved this week", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Neon.ink.copy(alpha = 0.7f))
        }
    }
}

/** Draws one polygon from x,y pairs given in the 108-unit logo grid. */
private fun DrawScope.poly(color: Color, scale: Float, vararg p: Float) {
    val path = Path().apply {
        moveTo(p[0] * scale, p[1] * scale)
        var i = 2
        while (i < p.size) {
            lineTo(p[i] * scale, p[i + 1] * scale)
            i += 2
        }
        close()
    }
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = 1.7f * scale, join = StrokeJoin.Round))
}

@Composable
private fun QuickAction(label: String, icon: ImageVector?, color: Color, glyph: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(color), contentAlignment = Alignment.Center) {
            if (glyph != null) glyph() else if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = Neon.ink)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionRow(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ContinueCard(link: LinkEntity, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val platform = Platform.fromKey(link.platform)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp)).background(color)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = 0.28f), radius = size.height * 0.55f, center = Offset(size.width * 0.85f, size.height * 0.15f))
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(if (platform in VIDEO_PLATFORMS) Icons.Filled.PlayArrow else Icons.Outlined.Link, null, Modifier.size(19.dp), tint = Neon.ink)
            }
            Text(
                platform.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Neon.ink).padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)) {
            Text(
                link.title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(link.domain, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StorageCard(s: Storage, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val parts = listOf(
        Triple("Video", s.video, Neon.coral),
        Triple("Audio", s.audio, Neon.teal),
        Triple("Files", s.files, Neon.lime)
    ).filter { it.second > 0 }
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads on this phone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(formatSize(s.total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEach { (_, bytes, color) ->
                Box(Modifier.weight(bytes.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            parts.forEach { (label, _, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private class Storage(val video: Long, val audio: Long, val files: Long) {
    val total: Long get() = video + audio + files
}

private fun storageBreakdown(list: List<DownloadEntity>): Storage {
    var video = 0L
    var audio = 0L
    var files = 0L
    list.filter { it.status == DownloadStatus.DONE }.forEach {
        val q = DownloadQuality.fromKey(it.quality)
        when {
            q == DownloadQuality.FILE -> files += it.sizeBytes
            q.audio -> audio += it.sizeBytes
            else -> video += it.sizeBytes
        }
    }
    return Storage(video, audio, files)
}

/** Links opened most recently first, topped up with the newest saves, two in total. */
private fun pickContinue(all: List<LinkEntity>): List<LinkEntity> {
    val opened = all.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }
    val newest = all.sortedByDescending { it.createdAt }
    return (opened + newest).distinctBy { it.id }.take(2)
}

/** Links saved on each of the last 7 days, oldest first, today last. */
private fun weeklyCounts(all: List<LinkEntity>): List<Int> {
    val day = 24L * 60 * 60 * 1000
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return (6 downTo 0).map { back ->
        val start = today - back * day
        all.count { it.createdAt >= start && it.createdAt < start + day }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}
