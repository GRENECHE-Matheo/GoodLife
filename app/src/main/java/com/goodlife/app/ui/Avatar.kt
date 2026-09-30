package com.goodlife.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

/** Photo de profil ronde, ou initiale si aucune photo. */
@Composable
fun Avatar(jpeg: ByteArray?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(jpeg) { jpeg?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), "Photo de profil", Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "🙂",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

/** Charge une image choisie, la recadre en carré et la réduit à 320 px (JPEG léger, stocké chiffré). */
fun avatarJpegFromUri(context: Context, uri: Uri): ByteArray? = runCatching {
    val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val m = maxOf(info.size.width, info.size.height)
            if (m > 1200) {
                val s = 1200f / m
                d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
        }
    } else {
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
    }
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, 320, 320, true)
    ByteArrayOutputStream().use { out ->
        small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}.getOrNull()
