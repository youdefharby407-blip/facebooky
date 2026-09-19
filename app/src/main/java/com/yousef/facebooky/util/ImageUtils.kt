package com.yousef.facebooky.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Downscales / crops images before upload so Storage and bandwidth stay small. */
object ImageUtils {

    fun chatImage(context: Context, uri: Uri): ByteArray = jpeg(load(context, uri, 1600), 85)

    fun avatar(context: Context, uri: Uri): ByteArray = jpeg(centerCropSquare(load(context, uri, 640), 384), 88)

    /** Square photo with rounded corners and transparent background, saved as PNG. */
    fun sticker(context: Context, uri: Uri): ByteArray {
        val size = 512
        val src = centerCropSquare(load(context, uri, 900), size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 96f, 96f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun load(context: Context, uri: Uri, maxSize: Int): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = min(1f, maxSize.toFloat() / max(w, h))
                decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Cannot decode image")
            val scale = min(1f, maxSize.toFloat() / max(bmp.width, bmp.height))
            if (scale >= 1f) bmp else Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true
            )
        }
    }

    private fun centerCropSquare(bmp: Bitmap, size: Int): Bitmap {
        val side = min(bmp.width, bmp.height)
        val cropped = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    private fun jpeg(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
