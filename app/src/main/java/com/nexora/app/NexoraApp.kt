package com.nexora.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexora.app.ui.components.LocalNavBarClearance
import com.nexora.app.ui.components.NavTab
import com.nexora.app.ui.components.NexoraLinkIcon
import com.nexora.app.ui.components.NexoraNavBar
import com.nexora.app.ui.screens.*
import com.nexora.app.ui.theme.Neon
import com.nexora.app.ui.theme.Radius
import com.nexora.app.util.findFragmentActivity
import kotlinx.coroutines.launch

private val tabs = listOf(
    NavTab("home", "Home", Icons.Outlined.Home),
    NavTab("links", "Links", NexoraLinkIcon),
    NavTab("downloads", "Downloads", Icons.Outlined.Download),
    NavTab("tools", "Tools", Icons.Outlined.Apps),
    NavTab("settings", "Settings", Icons.Outlined.Settings)
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Screens that show the bar: the tabs, plus the two tool lists opened from the Tools tab. */
private val barRoutes = tabRoutes + setOf("images", "pdftools")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NexoraApp(vm: MainViewModel, sharedSession: MutableState<Boolean>) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val settings by vm.settings.collectAsState()
    var searchSignal by remember { mutableIntStateOf(0) }

    fun goTab(r: String) {
        if (r == "home") {
            // Home is the bottom of the stack. Going back to it means popping everything above it. Navigating "to" it with
            // saveState/restoreState could restore a stale copy of a screen opened from Home and leave Home unreachable.
            if (!navController.popBackStack("home", false) && navController.currentDestination?.route != "home") {
                navController.navigate("home") { launchSingleTop = true }
            }
            return
        }
        navController.navigate(r) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val navigate: (String) -> Unit = { r -> if (r in tabRoutes) goTab(r) else navController.navigate(r) }

    /** Leaves the Add screen. After a Share-sheet save, return to the app the user came from. */
    fun leaveAdd(saved: Boolean) {
        navController.popBackStack()
        if (sharedSession.value) {
            sharedSession.value = false
            if (saved) Toast.makeText(context, "Saved to Nexora", Toast.LENGTH_SHORT).show()
            context.findFragmentActivity()?.moveTaskToBack(true)
        }
    }

    // Snackbar messages from anywhere in the app.
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
            }
        }
    }

    // Share target and launcher shortcuts.
    LaunchedEffect(vm.pendingNav) {
        when (val p = vm.pendingNav) {
            is PendingNav.Add -> navController.navigate(if (p.url != null) "add?url=" + android.net.Uri.encode(p.url) else "add") { launchSingleTop = true }
            PendingNav.Search -> { vm.homeFilters = Filters(); goTab("links"); searchSignal++ }
            PendingNav.Favorites -> navController.navigate("favorites") { launchSingleTop = true }
            PendingNav.Pinned -> { vm.homeFilters = Filters(pinnedOnly = true); goTab("links") }
            PendingNav.Downloads -> goTab("downloads")
            null -> {}
        }
        if (vm.pendingNav != null) vm.pendingNav = null
    }

    // With edge-to-edge the window no longer resizes for the keyboard, so every screen shrinks above it
    // here (see imePadding on the NavHost), and the floating bar steps out of the way while typing.
    val imeVisible = WindowInsets.isImeVisible
    val showBar = route in barRoutes && !imeVisible
    val selectedTab = when (route) {
        "images", "pdftools" -> "tools"
        else -> route
    }
    val barClearance = if (showBar) 104.dp else 0.dp
    val overlayAllowed = android.provider.Settings.canDrawOverlays(context)
    val floatingOn = settings?.floatingButton == true && overlayAllowed

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (showBar) 96.dp else 0.dp)) }
    ) { inner ->
        CompositionLocalProvider(LocalNavBarClearance provides barClearance) {
        NavHost(
            navController = navController, startDestination = "home",
            modifier = Modifier.padding(inner).consumeWindowInsets(inner).imePadding(),
            // Tabs cross-fade; pushed screens (Add, Details, ...) slide in a short, soft distance.
            enterTransition = {
                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes)
                    fadeIn(tween(200, delayMillis = 60))
                else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }
            },
            exitTransition = {
                if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes)
                    fadeOut(tween(100))
                else fadeOut(tween(140))
            },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 12 } }
        ) {
            composable("home") { DashboardScreen(vm, navigate) }
            composable("links") { HomeScreen(vm, navigate, searchSignal) }
            composable("categories") { CategoriesScreen(vm, navigate, onBack = { navController.popBackStack() }) }
            composable("images") { com.nexora.app.image.ImageToolsScreen(navigate) }
            composable(
                "imagetool/{id}?url={url}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                com.nexora.app.image.ImageToolScreen(
                    entry.arguments?.getString("id").orEmpty(), entry.arguments?.getString("url"), onBack = { navController.popBackStack() }
                )
            }
            composable("imageaction?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                com.nexora.app.image.ImageQuickActionScreen(entry.arguments?.getString("url").orEmpty(), navigate, onBack = { navController.popBackStack() })
            }
            composable("favorites") {
                LinksScreen(
                    vm, "Favorites", vm.favoritesFilters, { vm.favoritesFilters = it.copy(favoritesOnly = true) }, navigate,
                    emptyTitle = "No favorites yet", emptyBody = "Tap the star on any link, or swipe a link to the right, to keep it here.",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") { SettingsScreen(vm, navigate) }

            composable(
                "add?url={url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                AddEditScreen(
                    vm, editId = null, initialUrl = entry.arguments?.getString("url"),
                    onBack = { leaveAdd(false) }, onSaved = { leaveAdd(true) },
                    onOpenExisting = { l -> navController.popBackStack(); navController.navigate("detail/${l.id}") }
                )
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                AddEditScreen(
                    vm, editId = entry.arguments?.getLong("id"), initialUrl = null,
                    onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() },
                    onOpenExisting = { l -> navController.popBackStack(); navController.navigate("detail/${l.id}") }
                )
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                DetailScreen(
                    vm, entry.arguments?.getLong("id") ?: -1L, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("edit/$it") }, onOpenDownloads = { goTab("downloads") },
                    onImageTools = { u -> navController.navigate("imageaction?url=" + android.net.Uri.encode(u)) }
                )
            }
            composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: UNCATEGORIZED
                val cats by vm.categories.collectAsState()
                var f by remember { mutableStateOf(Filters(categoryId = id)) }
                val name = if (id == UNCATEGORIZED) "Uncategorized" else cats.firstOrNull { it.id == id }?.name ?: "Category"
                LinksScreen(
                    vm, name, f, { f = it.copy(categoryId = id) }, navigate,
                    emptyTitle = "Nothing here yet", emptyBody = "Links you save in this category will appear here.",
                    onBack = { navController.popBackStack() }, lockCategory = true
                )
            }
            composable("all_links") {
                var f by remember { mutableStateOf(Filters()) }
                LinksScreen(
                    vm, "All links", f, { f = it }, navigate,
                    emptyTitle = "No links yet", emptyBody = "Tap + to save your first link.",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("pinned") {
                var f by remember { mutableStateOf(Filters(pinnedOnly = true)) }
                LinksScreen(
                    vm, "Pinned", f, { f = it.copy(pinnedOnly = true) }, navigate,
                    emptyTitle = "No pinned links yet", emptyBody = "Pin a link to keep it at the top of Home.",
                    onBack = { navController.popBackStack() }
                )
            }
            composable("downloads") { DownloadsScreen(vm) }
            composable("tools") { ToolsScreen(navigate) }
            composable("pdftools") { PdfToolsScreen(navigate, onBack = { navController.popBackStack() }) }
            composable("pdftool/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                PdfToolScreen(
                    entry.arguments?.getString("id").orEmpty(), onBack = { navController.popBackStack() },
                    onEdit = { uri -> navController.navigate("pdfeditor?uri=" + android.net.Uri.encode(uri)) }
                )
            }
            composable(
                "pdfeditor?uri={uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                PdfEditorScreen(entry.arguments?.getString("uri"), onBack = { navController.popBackStack() })
            }
            composable("cleanup") { CleanupScreen(vm, navigate, onBack = { navController.popBackStack() }) }
            composable("manage_categories") { ManageCategoriesScreen(vm, onBack = { navController.popBackStack() }) }
            composable("manage_tags") {
                ManageTagsScreen(vm, onBack = { navController.popBackStack() }, onShowTag = { tag ->
                    vm.homeFilters = Filters(tag = tag); goTab("links")
                })
            }
            composable("backup") { DataScreen(vm, backupMode = true, onBack = { navController.popBackStack() }) }
            composable("transfer") { DataScreen(vm, backupMode = false, onBack = { navController.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
            composable("privacy_info") { PrivacyInfoScreen(onBack = { navController.popBackStack() }) }
        }
        }
    }

    // The floating bar sits on top of the content; a soft fade behind it keeps it readable.
    if (showBar) {
        NexoraNavBar(
            tabs = tabs, selectedRoute = selectedTab, floatingOn = floatingOn,
            onSelect = { r ->
                if (selectedTab != r) goTab(r)
                else if (route != r && !navController.popBackStack(r, false)) goTab(r)
            },
            onSave = {
                val copied = readClipboardForAdd(context)
                navController.navigate(if (copied != null) "add?url=" + android.net.Uri.encode(copied) else "add") { launchSingleTop = true }
            },
            shortcutKeys = settings?.plusShortcuts ?: com.nexora.app.ui.components.PlusAction.DEFAULT,
            onSaveShortcuts = { vm.setPlusShortcuts(it) },
            onShortcut = { a ->
                when (a) {
                    com.nexora.app.ui.components.PlusAction.SAVE -> {
                        val copied = readClipboardForAdd(context)
                        navController.navigate(if (copied != null) "add?url=" + android.net.Uri.encode(copied) else "add") { launchSingleTop = true }
                    }
                    com.nexora.app.ui.components.PlusAction.DOWNLOAD -> { vm.openDownloadAdd = true; goTab("downloads") }
                    com.nexora.app.ui.components.PlusAction.FLOATING -> when {
                        floatingOn -> vm.setFloatingButton(false)
                        overlayAllowed -> vm.setFloatingButton(true)
                        else -> goTab("settings")
                    }
                    com.nexora.app.ui.components.PlusAction.SEARCH -> { vm.homeFilters = Filters(); goTab("links"); searchSignal++ }
                    com.nexora.app.ui.components.PlusAction.PINNED -> { vm.homeFilters = Filters(pinnedOnly = true); goTab("links") }
                    else -> a.route?.let { r ->
                        if (tabs.any { it.route == r }) goTab(r) else navController.navigate(r) { launchSingleTop = true }
                    }
                }
            },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }

    // Lock overlay: the app stays composed underneath so nothing is lost when relocking.
    val locked = settings?.appLockEnabled == true && !vm.unlocked
    if (locked) {
        BackHandler { /* back on the lock screen leaves the app via the default behaviour */ context.findFragmentActivity()?.moveTaskToBack(true) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LockScreen(vm) }
    }
    }
}
