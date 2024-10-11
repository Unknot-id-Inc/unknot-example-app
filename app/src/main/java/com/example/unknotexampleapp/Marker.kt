package com.example.unknotexampleapp

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.res.ResourcesCompat

private const val mPad = 15f
private const val mWidth = 120
private const val mHeight = 120
private const val mTriangle = 50
fun markerBack(background: Color): Bitmap =
    Bitmap.createBitmap(mWidth, mHeight + mTriangle, Bitmap.Config.ARGB_8888).also { bmp ->
        // marker shape
        val path = Path().apply {
            moveTo(mPad, mPad)
            lineTo(bmp.width - mPad, mPad)
            lineTo(bmp.width - mPad, bmp.height - mPad - mTriangle)
            lineTo(bmp.width / 2f, bmp.height - mPad)
            lineTo(mPad, bmp.height - mPad - mTriangle)
            close()
        }

        Canvas(bmp).apply {
            save()
            // clip inverse marker shape so shadow layer only draws outside shape bounds
            clipOutPath(path)
            drawPath(path,
                Paint().apply {
                    setShadowLayer(10f, 0f, 0f, Color.Black.copy(.5f).toArgb())
                }
            )
            restore()

            // draw and fill marker shape
            drawPath(path,
                Paint().apply {
                    color = background.copy(.7f).toArgb()
                    style = Paint.Style.FILL
                }
            )
        }
    }

@Preview(showBackground = true)
@Composable
fun MarkerPreview() {
    Row {
        Image(
            bitmap = markerBmp(
                LocalContext.current.resources,
                R.drawable.unknot_logo,
                Color.Green
            ).asImageBitmap(),
            contentDescription = null
        )
        Image(
            bitmap = markerBmp(
                LocalContext.current.resources,
                R.drawable.ic_android_black_24dp,
                Color.Red
            ).asImageBitmap(),
            contentDescription = null
        )
    }
}
fun markerBmp(res: Resources, @DrawableRes id: Int, color: Color): Bitmap =
    markerBack(color).also { bmp ->
        val drawable = ResourcesCompat.getDrawable(res, id, null)!!
        val pad = (mPad * 2).toInt()
        drawable.setTint(Color.Black.toArgb())
        drawable.setBounds(pad, pad, mWidth - pad, mHeight - pad)
        drawable.draw(Canvas(bmp))
    }
