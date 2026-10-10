package com.nimit.delivery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as GRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** อ่านรูปจากเครื่อง (ย่อไม่เกิน ~2400px และหมุนตาม EXIF) */
fun shopDecodeImage(ctx: Context, uri: Uri): Bitmap? {
    try {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val bmp: Bitmap = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        var deg = 0
        try {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> deg = 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> deg = 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> deg = 270
                }
            }
        } catch (e: Exception) { }
        if (deg == 0) return bmp
        val m = Matrix()
        m.postRotate(deg.toFloat())
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    } catch (e: Exception) {
        return null
    }
}

/** อัปโหลดขึ้น Cloudinary (unsigned preset nimit_menu) เหมือนในเว็บ คืนค่า secure_url */
suspend fun shopUploadImage(bytes: ByteArray): String = withContext(Dispatchers.IO) {
    val boundary = "----nimit" + System.currentTimeMillis()
    val conn = URL("https://api.cloudinary.com/v1_1/ltthcvpf/image/upload").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        conn.outputStream.use { out ->
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\nnimit_menu\r\n").toByteArray())
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"img.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray())
            out.write(bytes)
            out.write(("\r\n--" + boundary + "--\r\n").toByteArray())
        }
        val code: Int = conn.responseCode
        if (code !in 200..299) throw Exception("อัปโหลดไม่สำเร็จ (HTTP " + code + ")")
        val text: String = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(text).getString("secure_url")
    } finally {
        conn.disconnect()
    }
}

/**
 * หน้าครอปรูป (พอร์ตจาก crop engine ในเว็บ)
 * shape: "banner" (กรอบล็อกสัดส่วน ratio ของแบนเนอร์จริง) หรือ "circle" (โปรไฟล์ สี่เหลี่ยมมุมมน 1:1)
 */
@Composable
fun ShopCropOverlay(bitmap: Bitmap, shape: String, ratio: Float, onCancel: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isCircle: Boolean = shape == "circle"
    val r: Float = if (isCircle) 1f else ratio

    val cw: Float = with(density) { 320.dp.toPx() }
    val ch: Float = cw / r
    val natW: Float = bitmap.width.toFloat()
    val natH: Float = bitmap.height.toFloat()
    val scale: Float = max(cw / natW, ch / natH)
    val dispW: Float = natW * scale
    val dispH: Float = natH * scale
    val offX: Float = (cw - dispW) / 2f
    val offY: Float = (ch - dispH) / 2f

    val fw0: Float = if (isCircle) min(cw, ch) * 0.78f else cw * 0.9f
    val fh0: Float = if (isCircle) fw0 else fw0 / r
    var fx by remember { mutableFloatStateOf((cw - fw0) / 2f) }
    var fy by remember { mutableFloatStateOf((ch - fh0) / 2f) }
    var fw by remember { mutableFloatStateOf(fw0) }
    var fh by remember { mutableFloatStateOf(fh0) }
    var uploading by remember { mutableStateOf(false) }

    // สถานะการลาก
    var mode by remember { mutableIntStateOf(0) } // 0 ไม่ได้ลาก, 1 ย้ายกรอบ, 2 ปรับขนาด
    var handle by remember { mutableStateOf("") }
    var totDx by remember { mutableFloatStateOf(0f) }
    var totDy by remember { mutableFloatStateOf(0f) }
    var sfx by remember { mutableFloatStateOf(0f) }
    var sfy by remember { mutableFloatStateOf(0f) }
    var sfw by remember { mutableFloatStateOf(0f) }
    var sfh by remember { mutableFloatStateOf(0f) }
    val hit: Float = with(density) { 28.dp.toPx() }
    val minW: Float = with(density) { 60.dp.toPx() }
    val rad: Float = if (isCircle) fw * 0.22f else 0f

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isCircle) "ปรับรูปโปรไฟล์ร้าน" else "ปรับรูปแบนเนอร์", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
            Text("ลากเพื่อย้าย · ลากมุมเพื่อปรับขนาด", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
            Box(
                Modifier.width(320.dp).height(with(density) { ch.toDp() }).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                    .pointerInput(cw, ch) {
                        detectDragGestures(
                            onDragStart = { o ->
                                totDx = 0f; totDy = 0f
                                val corners = listOf(
                                    "tl" to Offset(fx, fy), "tr" to Offset(fx + fw, fy),
                                    "bl" to Offset(fx, fy + fh), "br" to Offset(fx + fw, fy + fh)
                                )
                                val hh = corners.firstOrNull { (_, p) -> kotlin.math.abs(p.x - o.x) <= hit && kotlin.math.abs(p.y - o.y) <= hit }
                                if (hh != null) {
                                    mode = 2; handle = hh.first
                                    sfx = fx; sfy = fy; sfw = fw; sfh = fh
                                } else if (o.x >= fx && o.x <= fx + fw && o.y >= fy && o.y <= fy + fh) {
                                    mode = 1
                                    sfx = fx; sfy = fy
                                } else mode = 0
                            },
                            onDragEnd = { mode = 0 },
                            onDragCancel = { mode = 0 }
                        ) { change, amount ->
                            change.consume()
                            totDx += amount.x; totDy += amount.y
                            if (mode == 1) {
                                fx = max(0f, min(cw - fw, sfx + totDx))
                                fy = max(0f, min(ch - fh, sfy + totDy))
                            } else if (mode == 2) {
                                val growRight: Boolean = handle == "br" || handle == "tr"
                                val growDown: Boolean = handle == "br" || handle == "bl"
                                val anchorX: Float = if (growRight) sfx else sfx + sfw
                                val anchorY: Float = if (growDown) sfy else sfy + sfh
                                var wantW: Float = max(minW, if (growRight) sfw + totDx else sfw - totDx)
                                val maxWByX: Float = if (growRight) cw - anchorX else anchorX
                                val maxHByY: Float = if (growDown) ch - anchorY else anchorY
                                wantW = min(wantW, min(maxWByX, maxHByY * r))
                                wantW = max(minW, wantW)
                                val wantH: Float = wantW / r
                                fw = wantW; fh = wantH
                                fx = if (growRight) anchorX else anchorX - wantW
                                fy = if (growDown) anchorY else anchorY - wantH
                            }
                        }
                    }
            ) {
                Image(
                    bitmap.asImageBitmap(), null,
                    Modifier.offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }.size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() }),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(Modifier.fillMaxSize()) {
                    val p = Path()
                    p.fillType = PathFillType.EvenOdd
                    p.addRect(GRect(0f, 0f, size.width, size.height))
                    p.addRoundRect(RoundRect(fx, fy, fx + fw, fy + fh, CornerRadius(rad)))
                    drawPath(p, Color.Black.copy(alpha = 0.55f))
                    drawRoundRect(Color.White, Offset(fx, fy), Size(fw, fh), CornerRadius(rad), style = Stroke(2.dp.toPx()))
                }
                for ((hx, hy) in listOf(fx to fy, fx + fw to fy, fx to fy + fh, fx + fw to fy + fh)) {
                    val half: Float = with(density) { 10.dp.toPx() }
                    Box(
                        Modifier.offset { IntOffset((hx - half).roundToInt(), (hy - half).roundToInt()) }
                            .size(20.dp).clip(CircleShape).background(Color.White).border(2.dp, C.Primary, CircleShape)
                    )
                }
            }
            Row(Modifier.padding(top = 16.dp).width(320.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f))
                        .clickable(enabled = !uploading) { onCancel() },
                    contentAlignment = Alignment.Center
                ) { Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Primary)
                        .clickable(enabled = !uploading) {
                            uploading = true
                            val sx: Float = natW / dispW
                            val sy: Float = natH / dispH
                            val cropX: Float = (fx - offX) * sx
                            val cropY: Float = (fy - offY) * sy
                            val cropW: Float = fw * sx
                            val cropH: Float = fh * sy
                            var outW = 800
                            var outH = 800
                            if (!isCircle) { outW = 1200; outH = (outW / r).roundToInt() }
                            scope.launch {
                                try {
                                    val bytes: ByteArray = withContext(Dispatchers.Default) {
                                        val l: Int = cropX.roundToInt().coerceIn(0, bitmap.width - 1)
                                        val t: Int = cropY.roundToInt().coerceIn(0, bitmap.height - 1)
                                        val rr: Int = (cropX + cropW).roundToInt().coerceIn(l + 1, bitmap.width)
                                        val bb: Int = (cropY + cropH).roundToInt().coerceIn(t + 1, bitmap.height)
                                        val out: Bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                                        android.graphics.Canvas(out).drawBitmap(bitmap, Rect(l, t, rr, bb), Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
                                        val bos = ByteArrayOutputStream()
                                        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                                        bos.toByteArray()
                                    }
                                    val url: String = shopUploadImage(bytes)
                                    onDone(url)
                                } catch (e: Exception) {
                                    shopToast(ctx, "อัปโหลดรูปไม่สำเร็จ: " + (e.message ?: "") + " ลองใหม่อีกครั้งครับ")
                                } finally {
                                    uploading = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(if (uploading) "กำลังอัปโหลด..." else "ใช้รูปนี้", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            }
        }
    }
}
