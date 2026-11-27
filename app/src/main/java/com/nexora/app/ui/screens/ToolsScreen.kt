package com.nexora.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.image.ImageTool
import com.nexora.app.pdf.PdfTool
import com.nexora.app.ui.components.LocalNavBarClearance
import com.nexora.app.ui.components.ScreenTitle
import com.nexora.app.ui.theme.Neon
import com.nexora.app.ui.theme.Radius

private enum class ToolGroup(val label: String) { ALL("All"), PDF("PDF"), IMAGES("Images") }

/** One tile in the grid. [route] is the navigation target; the grid itself has no idea what a tool does. */
private data class ToolItem(val title: String, val icon: ImageVector, val tint: Color, val route: String, val group: ToolGroup)

private val allTools: List<ToolItem> by lazy {
    buildList {
        add(ToolItem("PDF Editor", Icons.Outlined.Edit, Neon.lime, "pdfeditor", ToolGroup.PDF))
        PdfTool.entries.forEach { add(ToolItem(it.title, it.icon, it.tint, "pdftool/${it.name}", ToolGroup.PDF)) }
        ImageTool.entries.forEach { add(ToolItem(it.title, it.icon, it.tint, "imagetool/${it.name}", ToolGroup.IMAGES)) }
    }
}

/** The Tools tab: search, a PDF / Images filter and one grid holding every tool. Everything runs on the phone. */
@Composable
fun ToolsScreen(navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(ToolGroup.ALL) }

    val shown = allTools.filter {
        (group == ToolGroup.ALL || it.group == group) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalNavBarClearance.current),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Tools", subtitle = "Everything here runs on your phone.", modifier = Modifier.offset(x = (-16).dp))
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search ${allTools.size} tools") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
                },
                shape = Radius.pill,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolGroup.entries.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = { Text(g.label, fontWeight = FontWeight.SemiBold) },
                        shape = Radius.pill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No tool matches \"${query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(shown, key = { it.route }) { t -> ToolCell(t) { navigate(t.route) } }
        }

        // Full lists keep their recent tools and file history.
        item(key = "all-pdf", span = { GridItemSpan(maxLineSpan) }) {
            SectionLink("PDF tools", "Recent files and every PDF tool", Icons.Outlined.PictureAsPdf, Neon.coral) { navigate("pdftools") }
        }
        item(key = "all-img", span = { GridItemSpan(maxLineSpan) }) {
            SectionLink("Image tools", "Recent files and every image tool", Icons.Outlined.Image, Neon.teal) { navigate("images") }
        }
    }
}

@Composable
private fun ToolCell(t: ToolItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(t.tint), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, Modifier.size(26.dp), tint = Neon.ink)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            t.title,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionLink(title: String, body: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = Neon.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
    }
}
