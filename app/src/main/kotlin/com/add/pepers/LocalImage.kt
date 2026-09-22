package com.add.pepers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

internal fun decodeOrientedBitmap(path: String): Bitmap? {
    if (path.isBlank()) return null

    return try {
        val source = BitmapFactory.decodeFile(path) ?: return null
        val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.media.ExifInterface(path).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } else {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                matrix.setScale(-1f, 1f)

            android.media.ExifInterface.ORIENTATION_ROTATE_180 ->
                matrix.setRotate(180f)

            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                matrix.setScale(1f, -1f)

            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            android.media.ExifInterface.ORIENTATION_ROTATE_90 ->
                matrix.setRotate(90f)

            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            android.media.ExifInterface.ORIENTATION_ROTATE_270 ->
                matrix.setRotate(-90f)
        }

        if (matrix.isIdentity) {
            source
        } else {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true
            ).also {
                if (it !== source) source.recycle()
            }
        }
    } catch (_: Exception) {
        BitmapFactory.decodeFile(path)
    }
}

@Composable
internal fun LocalProfileImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = remember(path) {
        decodeOrientedBitmap(path)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
