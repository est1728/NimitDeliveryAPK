package com.nimit.delivery.ui

import android.content.Context
import android.media.RingtoneManager
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ShopGreen = Color(0xFF16A34A)
val ShopGreenLight = Color(0xFFDCFCE7)
val ShopRed = Color(0xFFDC2626)
val ShopRedLight = Color(0xFFFEE2E2)
val ShopOrange = Color(0xFFEA580C)
val ShopOrangeLight = Color(0xFFFFF7ED)

fun shopToast(ctx: Context, msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

/** เสียงเตือนออเดอร์ใหม่ */
object ShopSound {
    fun alert(ctx: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(ctx.applicationContext, uri)?.play()
        } catch (e: Exception) { }
    }
}

/** สวิตช์ตามสไตล์ใน HTML (ราง 44x24 ปุ่มกลม 18) */
@Composable
fun ShopSwitch(on: Boolean, onColor: Color, offColor: Color, w: Dp = 44.dp, h: Dp = 24.dp, onClick: () -> Unit) {
    val thumb = h - 6.dp
    val x by animateDpAsState(if (on) w - thumb - 3.dp else 3.dp, tween(200), label = "sw")
    Box(
        Modifier.width(w).height(h).clip(RoundedCornerShape(h / 2)).background(if (on) onColor else offColor).clickable { onClick() }
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(thumb).clip(CircleShape).background(Color.White))
    }
}

/** ป้ายสถานะ (pill) ตามสีใน HTML */
@Composable
fun ShopPill(status: String, modifier: Modifier = Modifier) {
    val bg: Color
    val fg: Color
    when (status) {
        "pending" -> { bg = ShopOrangeLight; fg = ShopOrange }
        "accepted", "picking" -> { bg = C.PrimaryLight; fg = C.Primary }
        "arrived" -> { bg = Color(0xFFFEF3C7); fg = Color(0xFFD97706) }
        "delivering" -> { bg = ShopGreenLight; fg = ShopGreen }
        "rejected" -> { bg = ShopRedLight; fg = ShopRed }
        else -> { bg = Color(0xFFF1F5F9); fg = C.Subtext }
    }
    val label = com.nimit.delivery.data.SHOP_STATUS_TH[status] ?: status
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

object ShopP {
    const val DOLLAR = "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"
    const val MORE = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
    const val PERSON = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
    const val SHOP = "M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z"
    const val EDIT = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    const val SHARE = "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L7.04 9.81C6.5 9.31 5.79 9 5 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"
    const val IMAGE = "M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zm-9-7l-3 3.72L7 13l-4 5h18l-5-6.28z"
    const val LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"
    const val PHONE = "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"
}
