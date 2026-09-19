package com.yousef.facebooky.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin-stroke line icons (Lucide style, ISC-licensed geometry) used across the app,
 * so every screen shares one modern, consistent icon language.
 */
object AppIcons {
    val Phone by lazy {
        icon(
            "phone",
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
        )
    }
    val Video by lazy {
        icon("video", "M22 8l-6 4 6 4V8z", "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z")
    }
    val Music by lazy { icon("music", "M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f)) }
    val Smile by lazy { icon("smile", circle(12f, 12f, 10f), "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01") }
    val Keyboard by lazy {
        icon(
            "keyboard",
            "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M6 8h.01", "M10 8h.01", "M14 8h.01", "M18 8h.01", "M8 12h.01", "M12 12h.01", "M16 12h.01", "M7 16h10",
        )
    }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Mic by lazy {
        icon("mic", "M12 2a3 3 0 0 1 3 3v7a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3")
    }
    val Send by lazy { icon("send", "M22 2 11 13", "M22 2 15 22 11 13 2 9 22 2z") }
    val Reply by lazy { icon("reply", "M9 17 4 12 9 7", "M20 18v-2a4 4 0 0 0-4-4H4") }
    val Copy by lazy {
        icon(
            "copy",
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }
    val Trash by lazy {
        icon(
            "trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11v6", "M14 11v6",
        )
    }
    val EyeOff by lazy {
        icon(
            "eye_off",
            "M9.88 9.88a3 3 0 1 0 4.24 4.24",
            "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
            "M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
            "M2 2l20 20",
        )
    }
    val Close by lazy { icon("close", "M18 6 6 18", "M6 6l12 12") }
    val More by lazy { icon("more", circle(12f, 5f, 1f), circle(12f, 12f, 1f), circle(12f, 19f, 1f)) }
    val User by lazy { icon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f)) }
    val AddUser by lazy {
        icon("add_user", "M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", circle(9f, 7f, 4f), "M19 8v6", "M22 11h-6")
    }
    val Eraser by lazy {
        icon("eraser", "M7 21 3 17a2 2 0 0 1 0-2.8L13.2 4a2 2 0 0 1 2.8 0L21 9a2 2 0 0 1 0 2.8L12 21", "M22 21H7", "M5 11l9 9")
    }
    val Ban by lazy { icon("ban", circle(12f, 12f, 10f), "M4.9 4.9l14.2 14.2") }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
