package com.nexora.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Things the + button's long-press menu can offer. [route] is where the shortcut goes; the first three
 * and Search are handled specially by the app. The person picks and orders these in Customise.
 */
enum class PlusAction(val key: String, val label: String, val icon: ImageVector, val route: String? = null) {
    SAVE("save", "Save link", Icons.Outlined.Add),
    DOWNLOAD("download", "Download link", Icons.Outlined.Download),
    FLOATING("floating", "Floating button on/off", Icons.Outlined.PictureInPictureAlt),
    SEARCH("search", "Search links", Icons.Outlined.Search),
    FAVORITES("favorites", "Favorites", Icons.Outlined.Star, "favorites"),
    PINNED("pinned", "Pinned links", Icons.Outlined.PushPin, "pinned"),
    CATEGORIES("categories", "Categories", Icons.Outlined.Category, "categories"),
    CLEANUP("cleanup", "Clean up links", Icons.Outlined.CleaningServices, "cleanup"),
    DOWNLOADS("downloads", "Downloads", Icons.Outlined.FolderOpen, "downloads"),
    PDF_TOOLS("pdftools", "PDF tools", Icons.Outlined.PictureAsPdf, "pdftools"),
    IMAGE_TOOLS("images", "Image tools", Icons.Outlined.Image, "images"),
    TOOLS("tools", "All tools", Icons.Outlined.Apps, "tools");

    companion object {
        const val DEFAULT = "save,download,floating"

        fun fromKey(k: String): PlusAction? = entries.firstOrNull { it.key == k }

        /** The chosen shortcuts in the order saved; unknown keys are ignored. */
        fun parse(stored: String?): List<PlusAction> =
            (stored ?: DEFAULT).split(',').mapNotNull { fromKey(it.trim()) }.distinct()

        fun join(list: List<PlusAction>): String = list.joinToString(",") { it.key }
    }
}
