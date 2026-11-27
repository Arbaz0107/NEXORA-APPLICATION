package com.nexora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.ui.theme.Neon

data class NavTab(val route: String, val label: String, val icon: ImageVector)

/**
 * Space a screen leaves at the bottom of its list so the last item can scroll clear of the floating
 * bar. Zero where the bar isn't shown.
 */
val LocalNavBarClearance = compositionLocalOf { 0.dp }

/**
 * Floating navigation: a pill of icon tabs (the selected one sits in a lime circle, no label) and a
 * separate lime "+" button. The bar overlays the content; a soft fade behind it keeps it readable
 * while lists scroll underneath. Tap "+" to save a link, long-press it for more ways to add.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexoraNavBar(
    tabs: List<NavTab>,
    selectedRoute: String?,
    floatingOn: Boolean,
    shortcutKeys: String,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
    onShortcut: (PlusAction) -> Unit,
    onSaveShortcuts: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val light = bg.luminance() > 0.5f
    val pill = if (light) Color(0xFF111111) else Color(0xFF1D1D1D)
    val pillBorder = if (light) Color(0xFF111111) else Color(0xFF2A2A2A)
    val idle = Color(0xFFA0A0A0)
    var menu by remember { mutableStateOf(false) }
    var customise by remember { mutableStateOf(false) }
    val shortcuts = remember(shortcutKeys) { PlusAction.parse(shortcutKeys) }

    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.92f)))))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).height(60.dp).clip(CircleShape).background(pill).border(1.dp, pillBorder, CircleShape)
            ) {
                val ballSize = 46.dp
                val slot = (maxWidth - 8.dp) / tabs.size
                val index = tabs.indexOfFirst { it.route == selectedRoute }

                // The lime ball glides to the chosen tab with a spring, stretching as it goes and settling with a wobble.
                val ballX by animateDpAsState(
                    4.dp + slot * index.coerceAtLeast(0) + (slot - ballSize) / 2,
                    spring(dampingRatio = 0.55f, stiffness = 380f), label = "nav-ball-x"
                )
                val ballAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, tween(180), label = "nav-ball-alpha")
                val stretch = remember { Animatable(1f) }
                LaunchedEffect(index) {
                    if (index >= 0) {
                        stretch.animateTo(1.45f, tween(110))
                        stretch.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
                    }
                }
                Box(
                    Modifier.offset(x = ballX).align(Alignment.CenterStart).size(ballSize)
                        .graphicsLayer {
                            scaleX = stretch.value
                            scaleY = 1f + (1f - stretch.value) * 0.6f
                            alpha = ballAlpha
                        }
                        .clip(CircleShape).background(Neon.lime)
                )

                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { t ->
                        val selected = t.route == selectedRoute
                        val tint by animateColorAsState(if (selected) Neon.ink else idle, tween(200), label = "nav-icon-tint")
                        val pop by animateFloatAsState(
                            if (selected) 1.15f else 1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "nav-icon-pop"
                        )
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(t.route) })
                                .semantics { contentDescription = t.label },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(t.icon, null, Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }, tint = tint)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                val turn by animateFloatAsState(if (menu) 45f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "plus-turn")
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Neon.lime)
                        .combinedClickable(
                            onClick = onSave, onClickLabel = "Save a link",
                            onLongClick = { menu = true }, onLongClickLabel = "Open your shortcuts"
                        )
                        .semantics { contentDescription = "Save link" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(26.dp).graphicsLayer { rotationZ = turn }, tint = Neon.ink)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    shortcuts.forEach { a ->
                        val label = if (a == PlusAction.FLOATING) (if (floatingOn) "Turn floating button off" else "Turn floating button on") else a.label
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(a.icon, null, Modifier.size(20.dp)) },
                            onClick = { menu = false; onShortcut(a) }
                        )
                    }
                    if (shortcuts.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Customise shortcuts") },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp)) },
                        onClick = { menu = false; customise = true }
                    )
                }
            }
        }
    }

    if (customise) {
        CustomiseShortcutsDialog(
            current = shortcuts,
            onDismiss = { customise = false },
            onSave = { onSaveShortcuts(PlusAction.join(it)); customise = false }
        )
    }
}

/** Tick the shortcuts you want on the + button's long-press menu and move them up or down to set the order. */
@Composable
fun CustomiseShortcutsDialog(current: List<PlusAction>, onDismiss: () -> Unit, onSave: (List<PlusAction>) -> Unit) {
    // Chosen ones first in their saved order, then everything else.
    var order by remember { mutableStateOf(current + PlusAction.entries.filter { it !in current }) }
    var on by remember { mutableStateOf(current.toSet()) }

    fun move(a: PlusAction, by: Int) {
        val i = order.indexOf(a)
        val j = i + by
        if (j !in order.indices) return
        order = order.toMutableList().also { it[i] = it[j]; it[j] = a }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your shortcuts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Long-press + to see these. Tick what you want and use the arrows to reorder.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                order.forEach { a ->
                    val checked = a in on
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { on = if (it) on + a else on - a })
                        Icon(a.icon, null, Modifier.size(20.dp))
                        Text(a.label, Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                        IconButton(onClick = { move(a, -1) }, Modifier.size(36.dp), enabled = order.first() != a) { Icon(Icons.Outlined.ArrowUpward, "Move ${a.label} up", Modifier.size(18.dp)) }
                        IconButton(onClick = { move(a, 1) }, Modifier.size(36.dp), enabled = order.last() != a) { Icon(Icons.Outlined.ArrowDownward, "Move ${a.label} down", Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order.filter { it in on }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
