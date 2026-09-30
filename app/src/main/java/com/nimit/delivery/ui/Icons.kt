package com.nimit.delivery.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object N {
    val B900 = Color(0xFF071B3D); val B800 = Color(0xFF0B2C63); val B700 = Color(0xFF0E3B8C)
    val B600 = Color(0xFF1550B8); val B500 = Color(0xFF2E6FE0); val B100 = Color(0xFFE7EFFC); val B50 = Color(0xFFF4F8FE)
    val Amber600 = Color(0xFFD98F1E); val Green600 = Color(0xFF12996B); val Green100 = Color(0xFFDFF4EC)
    val Red500 = Color(0xFFE5484D); val Red100 = Color(0xFFFCE7E8)
    val Ink900 = Color(0xFF101828); val Ink700 = Color(0xFF3A4256); val Ink500 = Color(0xFF667085); val Ink300 = Color(0xFF9AA2B8)
    val Line = Color(0xFFE7EAF2); val Bg = Color(0xFFF5F7FC)
}

object P {
    const val CLOCK = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z"
    const val STAR = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"
    const val HEART = "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"
    const val PIN = "M21 10c0 6-9 12-9 12s-9-6-9-12a9 9 0 0 1 18 0z"
    const val THUMB = "M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"
    const val TAG = "M20 6h-2.18c.11-.31.18-.65.18-1a2 2 0 0 0-2-2c-1.05 0-1.87.62-2.44 1.5L13 6.32 12.44 4.5C11.87 3.62 11.05 3 10 3a2 2 0 0 0-2 2c0 .35.07.69.18 1H6a2 2 0 0 0-2 2v2a1 1 0 0 0 1 1h1v7a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-7h1a1 1 0 0 0 1-1V8a2 2 0 0 0-2-2z"
    const val GRID = "M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z"
    const val FASTFOOD = "M8.1 13.34l2.83-2.83L3.91 3.5c-1.56 1.56-1.56 4.09 0 5.66l4.19 4.18zm6.78-1.81c1.53.71 3.68.21 5.27-1.38 1.91-1.91 2.28-4.65.81-6.12-1.46-1.46-4.2-1.1-6.12.81-1.59 1.59-2.09 3.74-1.38 5.27L3.7 19.87l1.41 1.41L12 14.41l6.88 6.88 1.41-1.41L13.41 13l1.47-1.47z"
    const val RESTAURANT = "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z"
    const val CAFE = "M2 21h18v-2H2v2zM20 8h-2V5h2v3zm0-5H4v10c0 2.21 1.79 4 4 4h6c2.21 0 4-1.79 4-4v-3h2c1.11 0 2-.89 2-2V5c0-1.1-.89-2-2-2z"
    const val CAKE = "M12 6c1.11 0 2-.9 2-2 0-.39-.1-.76-.29-1.08L12 0l-1.71 2.92c-.19.32-.29.69-.29 1.08 0 1.1.9 2 2 2zM3 13v7c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-7c0-1.66-1.34-3-3-3h-5V8h-2v2H6c-1.66 0-3 1.34-3 3zm9 0c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2z"
    const val BAG = "M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 .55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"
    const val SPICY = "M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z"
    const val PLUS = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    const val HOME = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"
    const val HISTORY = "M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
    const val CART = "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1.003 1.003 0 0 0 20 4H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"
    const val SOCIAL = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
    const val BELL = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
    const val MENU = "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"
    const val SEARCH = "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
    const val CHEVRON = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"
    const val TICKET = "M22 10V6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v4c1.1 0 2 .9 2 2s-.9 2-2 2v4c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2v-4c-1.1 0-2-.9-2-2s.9-2 2-2zm-9 7.5h-2v-2h2v2zm0-4.5h-2v-2h2v2zm0-4.5h-2v-2h2v2z"
    const val TRI = "M9 18l6-6-6-6z"
    val presets = mapOf(
        "fastfood" to FASTFOOD, "grid" to GRID, "restaurant" to RESTAURANT, "cafe" to CAFE, "cake" to CAKE,
        "bag" to BAG, "spicy" to SPICY, "tag" to TAG, "star" to STAR, "heart" to HEART, "plus" to PLUS
    )
}

private val pathIconCache = HashMap<String, ImageVector>()

@Composable
fun PathIcon(d: String, tint: Color, size: Dp, stroke: Boolean = false, modifier: Modifier = Modifier) {
    val v = remember(d, stroke) { pathIconCache.getOrPut("$stroke|$d") {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (stroke) null else SolidColor(Color.Black),
            stroke = if (stroke) SolidColor(Color.Black) else null,
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ).build()
    } }
    Icon(v, null, tint = tint, modifier = modifier.size(size))
}
