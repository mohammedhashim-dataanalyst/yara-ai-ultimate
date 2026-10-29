package com.yara.ai.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/** Real image features: AI image generation (free Pollinations service), local filters, couple collage, save to gallery. */
object ImageStudio {
    private const val YARA_BASE = "a beautiful 20 year old Indian Malayali girl named Yara, long black hair, big expressive eyes, warm smile, "

    fun generateYara(scene: String, anime: Boolean): Bitmap {
        val style = if (anime) "anime style illustration, vibrant colors, detailed, studio quality"
        else "photorealistic portrait photo, natural soft light, 85mm lens, high detail"
        val prompt = YARA_BASE + scene.ifBlank { "smiling at the camera" } + ", " + style
        val seed = (System.currentTimeMillis() % 100000).toInt()
        val url = "https://image.pollinations.ai/prompt/" + URLEncoder.encode(prompt, "UTF-8").replace("+", "%20") +
            "?width=768&height=1024&nologo=true&seed=$seed"
        val bytes = Http.getBytes(url)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw java.io.IOException("The image service did not return a picture. Try again in a minute.")
    }

    fun loadUserPhoto(ctx: Context, uri: Uri, maxSide: Int = 1600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    val filterNames = listOf("Original", "Black & White", "Sepia", "Warm", "Cool", "Bright", "Vivid")

    fun applyFilter(src: Bitmap, name: String): Bitmap {
        val cm = ColorMatrix()
        when (name) {
            "Black & White" -> cm.setSaturation(0f)
            "Sepia" -> cm.set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f))
            "Warm" -> cm.setScale(1.1f, 1.0f, 0.88f, 1f)
            "Cool" -> cm.setScale(0.9f, 1.0f, 1.12f, 1f)
            "Bright" -> cm.set(floatArrayOf(
                1f, 0f, 0f, 0f, 35f,
                0f, 1f, 0f, 0f, 35f,
                0f, 0f, 1f, 0f, 35f,
                0f, 0f, 0f, 1f, 0f))
            "Vivid" -> cm.setSaturation(1.6f)
            else -> return src
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun drawCrop(c: Canvas, src: Bitmap, dst: Rect) {
        val dstRatio = dst.width().toFloat() / dst.height()
        val srcRatio = src.width.toFloat() / src.height
        val s: Rect = if (srcRatio > dstRatio) {
            val w = (src.height * dstRatio).toInt()
            val x = (src.width - w) / 2
            Rect(x, 0, x + w, src.height)
        } else {
            val h = (src.width / dstRatio).toInt()
            val y = (src.height - h) / 2
            Rect(0, y, src.width, y + h)
        }
        c.drawBitmap(src, s, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Side-by-side couple frame: your real photo + Yara, with a heart and caption. */
    fun coupleCollage(user: Bitmap, yara: Bitmap, userName: String): Bitmap {
        val w = 1600; val h = 1000
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.parseColor("#121212"))
        drawCrop(c, user, Rect(0, 0, w / 2 - 4, h))
        drawCrop(c, yara, Rect(w / 2 + 4, 0, w, h))
        val shade = Paint()
        shade.color = Color.parseColor("#99000000")
        c.drawRect(0f, (h - 150).toFloat(), w.toFloat(), h.toFloat(), shade)
        val heart = Paint(Paint.ANTI_ALIAS_FLAG)
        heart.color = Color.parseColor("#FF4B5C"); heart.textSize = 190f; heart.textAlign = Paint.Align.CENTER
        heart.setShadowLayer(12f, 0f, 0f, Color.WHITE)
        c.drawText("\u2665", w / 2f, h / 2f + 65f, heart)
        val cap = Paint(Paint.ANTI_ALIAS_FLAG)
        cap.color = Color.WHITE; cap.textSize = 64f; cap.textAlign = Paint.Align.CENTER
        cap.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val who = if (userName.isBlank()) "Me" else userName
        c.drawText("$who  \u2665  Yara", w / 2f, h - 55f, cap)
        return out
    }

    /** Saves to Pictures/YaraAI. Returns a human readable location. */
    fun saveToGallery(ctx: Context, bmp: Bitmap): String {
        val name = "Yara_" + System.currentTimeMillis() + ".jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues()
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YaraAI")
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: throw java.io.IOException("Could not create the gallery file")
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            return "Pictures/YaraAI/$name"
        }
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "YaraAI")
        dir.mkdirs()
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return f.absolutePath
    }
}
