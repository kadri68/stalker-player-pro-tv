package com.kadri.stalkerplayerpro.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import org.json.JSONObject

object QrCodeHelper {

    fun encodePortalToPayload(portal: PortalEntity): String {
        return JSONObject().apply {
            put("name", portal.name)
            put("protocol", portal.protocol)
            put("url", portal.url)
            put("mac", portal.mac)
            put("username", portal.username)
            put("password", portal.password)
        }.toString()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 360): Bitmap? {
        return runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }

    fun decodeQrFromImageUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeStream(stream, null, options) ?: return null
                val width = bmp.width
                val height = bmp.height
                val pixels = IntArray(width * height)
                bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                bmp.recycle()

                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                MultiFormatReader().decode(binaryBitmap).text
            }
        }.getOrNull()
    }
}

@Composable
fun PortalQrDialog(
    portal: PortalEntity,
    isAr: Boolean,
    onDismiss: () -> Unit
) {
    val payload = remember(portal) { QrCodeHelper.encodePortalToPayload(portal) }
    val qrBitmap = remember(payload) { QrCodeHelper.generateQrBitmap(payload, 340) }

    TvModalDialog(
        title = if (isAr) "رمز QR للحساب" else "Account QR Code",
        onDismiss = onDismiss,
        maxWidth = 440.dp
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = portal.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(230.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LtrText(
                text = portal.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (portal.mac.isNotBlank()) {
                LtrText(
                    text = "MAC: ${portal.mac}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
