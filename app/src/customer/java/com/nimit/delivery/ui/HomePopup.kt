package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.nimit.delivery.data.*
import org.json.JSONObject

/** ป้ายเด้งตอนเปิดแอพ: ตั้งค่าจาก settings/popup (เว็บแอดมินชุดเดียวกัน) */
object PopupGate { var shownVer: String? = null }

private fun popupToday(): String {
    val c = java.util.Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

private fun popupVer(c: Doc): String = (c.lng("ver") ?: 0L).toString()

private fun jsonOf(s: String?): JSONObject = try { JSONObject(s ?: "{}") } catch (_: Exception) { JSONObject() }

/** ถึงเวลาแสดงป้ายหรือยัง (เปิดอยู่ + อยู่ในช่วงเวลา + ความถี่ + ไม่ได้กด "ไม่แสดงวันนี้") */
fun popupDue(c: Doc, session: Session): Boolean {
    if (!truthy(c["on"]) || c.str("imgUrl").isEmpty()) return false
    val cal = java.util.Calendar.getInstance()
    val today = popupToday()
    val nowS = today + "T%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    fun nm(v: String, end: Boolean): String = if (v.isEmpty()) "" else if (v.length <= 10) v + (if (end) "T23:59" else "T00:00") else v
    if (c.str("mode") != "manual") {
        val st = nm(c.str("start"), false); val en = nm(c.str("end"), true)
        if (st.isNotEmpty() && nowS < st) return false
        if (en.isNotEmpty() && nowS > en) return false
    }
    val ver = popupVer(c)
    val skip = jsonOf(session.popupSkip)
    if (skip.optString("ver") == ver && skip.optString("day") == today) return false
    val seen = jsonOf(session.popupSeen)
    when (c.str("freq").ifEmpty { "session" }) {
        "session" -> if (PopupGate.shownVer == ver) return false
        "daily" -> if (seen.optString("ver") == ver && seen.optString("day") == today) return false
        "once" -> if (seen.optString("ver") == ver) return false
    }
    return true
}

fun popupMarkShown(c: Doc, session: Session) {
    val ver = popupVer(c)
    PopupGate.shownVer = ver
    session.popupSeen = JSONObject().put("ver", ver).put("day", popupToday()).toString()
}

fun popupSkipToday(c: Doc, session: Session) {
    session.popupSkip = JSONObject().put("ver", popupVer(c)).put("day", popupToday()).toString()
}

@Composable
fun HomePopup(c: Doc, onDismiss: () -> Unit, onSkip: () -> Unit, onLink: (String) -> Unit) {
    val scale = (c.dbl("scale") ?: 88.0).toFloat().coerceIn(30f, 100f)
    val preset = when (c.str("closePos")) { "tr" -> floatArrayOf(94f, 4f); "tl" -> floatArrayOf(6f, 4f); "below" -> floatArrayOf(50f, 108f); else -> null }
    val px = preset?.get(0) ?: (c.dbl("closeX")?.toFloat() ?: 94f)
    val py = preset?.get(1) ?: (c.dbl("closeY")?.toFloat() ?: 4f)
    val sz = (c.dbl("closeSize") ?: 34.0).toFloat().coerceIn(20f, 90f).dp
    val hidden = truthy(c["closeHidden"])
    val link = c.str("linkUrl").trim()
    var aspect by remember { mutableFloatStateOf(0.72f) }
    val cfg = LocalConfiguration.current
    val maxW = cfg.screenWidthDp.dp
    val maxH = cfg.screenHeightDp.dp
    val bw = minOf(maxW * (scale / 100f), maxH * 0.85f * aspect, 520.dp)
    val bh = bw / aspect
    val skipTop = if (py > 100f) bh * ((py - 100f) / 100f) + sz / 2 + 8.dp else 12.dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(bw + 80.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(bw).aspectRatio(aspect).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onDismiss(); if (link.isNotEmpty()) onLink(link)
                    }) {
                        AsyncImage(
                            model = c.str("imgUrl"), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
                            onSuccess = { st ->
                                val d = st.result.drawable
                                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) aspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight
                            }
                        )
                    }
                    Text(
                        "ไม่แสดงวันนี้", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.padding(top = skipTop).clip(RoundedCornerShape(20.dp)).background(Color(0x8C0F0A1E)).clickable { onSkip() }.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
                Box(
                    Modifier.offset(x = 40.dp + bw * (px / 100f) - sz / 2, y = 40.dp + bh * (py / 100f) - sz / 2).size(sz).clip(CircleShape)
                        .background(if (hidden) Color.Transparent else Color(0xC70F172A)).clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!hidden) PathIcon("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z", Color.White, sz * 0.5f)
                }
            }
        }
    }
}
