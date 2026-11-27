package com.nexora.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a user can assign to a category. Keys are what is stored in the database and backups. */
object CategoryIcons {
    val all: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "folder" to Icons.Outlined.Folder,
        "school" to Icons.Outlined.School,
        "work" to Icons.Outlined.Work,
        "person" to Icons.Outlined.Person,
        "group" to Icons.Outlined.Group,
        "shopping" to Icons.Outlined.ShoppingCart,
        "movie" to Icons.Outlined.Movie,
        "build" to Icons.Outlined.Build,
        "finance" to Icons.Outlined.AccountBalance,
        "star" to Icons.Outlined.Star,
        "book" to Icons.Outlined.MenuBook,
        "home" to Icons.Outlined.Home,
        "code" to Icons.Outlined.Code,
        "music" to Icons.Outlined.MusicNote,
        "travel" to Icons.Outlined.Flight,
        "heart" to Icons.Outlined.FavoriteBorder,
        "game" to Icons.Outlined.SportsEsports,
        "news" to Icons.Outlined.Article,
        "cloud" to Icons.Outlined.Cloud,
        "bookmark" to Icons.Outlined.BookmarkBorder,
        "location" to Icons.Outlined.LocationOn,
        "map" to Icons.Outlined.Map,
        "food" to Icons.Outlined.Restaurant,
        "cafe" to Icons.Outlined.LocalCafe,
        "fastfood" to Icons.Outlined.Fastfood,
        "fitness" to Icons.Outlined.FitnessCenter,
        "health" to Icons.Outlined.LocalHospital,
        "car" to Icons.Outlined.DirectionsCar,
        "bike" to Icons.Outlined.DirectionsBike,
        "hotel" to Icons.Outlined.Hotel,
        "beach" to Icons.Outlined.BeachAccess,
        "camera" to Icons.Outlined.PhotoCamera,
        "video" to Icons.Outlined.Videocam,
        "mic" to Icons.Outlined.Mic,
        "art" to Icons.Outlined.Palette,
        "fashion" to Icons.Outlined.Checkroom,
        "bag" to Icons.Outlined.ShoppingBag,
        "gift" to Icons.Outlined.CardGiftcard,
        "money" to Icons.Outlined.Payments,
        "receipt" to Icons.Outlined.Receipt,
        "calculate" to Icons.Outlined.Calculate,
        "event" to Icons.Outlined.Event,
        "idea" to Icons.Outlined.Lightbulb,
        "science" to Icons.Outlined.Science,
        "pets" to Icons.Outlined.Pets,
        "nature" to Icons.Outlined.Eco,
        "sports" to Icons.Outlined.SportsSoccer,
        "phone" to Icons.Outlined.PhoneAndroid,
        "laptop" to Icons.Outlined.Laptop,
        "web" to Icons.Outlined.Public,
        "lock" to Icons.Outlined.Lock,
        "translate" to Icons.Outlined.Translate,
        "rocket" to Icons.Outlined.RocketLaunch,
        "bolt" to Icons.Outlined.Bolt,
        "flag" to Icons.Outlined.Flag,
        "pin" to Icons.Outlined.PushPin,
        "tag" to Icons.Outlined.Sell
    )

    fun get(key: String): ImageVector = all[key] ?: Icons.Outlined.Folder
}
