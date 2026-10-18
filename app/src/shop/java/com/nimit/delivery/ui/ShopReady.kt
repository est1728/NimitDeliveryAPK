package com.nimit.delivery.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** ความพร้อมรับออเดอร์ของเครื่องนี้ (แจ้งเตือนทั้งแอพ / ช่องเสียงออเดอร์ / ไม่จำกัดแบตเตอรี่) */
class ShopReadiness(val notif: Boolean, val channel: Boolean, val battery: Boolean) {
    val allOk: Boolean get() = notif && channel && battery
}

fun shopReadiness(ctx: Context): ShopReadiness {
    ShopAlertService.ensureChannels(ctx)
    val notif: Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    var channel = true
    if (Build.VERSION.SDK_INT >= 26) {
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel("shop_order_alert")
        if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) channel = false
    }
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val battery: Boolean = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    return ShopReadiness(notif, channel, battery)
}

private fun go(ctx: Context, i: Intent) {
    try { ctx.startActivity(i) } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) } catch (e2: Exception) { }
    }
}

fun shopOpenNotifSettings(ctx: Context) {
    go(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
}

fun shopOpenChannelSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 26) go(ctx, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, "shop_order_alert"))
    else shopOpenNotifSettings(ctx)
}

fun shopOpenBatterySettings(ctx: Context) {
    // ขึ้นหน้าต่างระบบ "อนุญาตให้แอพทำงานเบื้องหลังตลอดเวลา?" ของแอพนี้โดยตรง ถ้าเครื่องไม่รองรับ → หน้าข้อมูลแอพ (มีเมนูแบตเตอรี่)
    go(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
}

/** อ่านสถานะใหม่ทุกครั้งที่กลับเข้าแอพ (เช่น กลับจากหน้าตั้งค่า) */
@Composable
private fun rememberReadiness(): ShopReadiness {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    tick.hashCode()
    return shopReadiness(ctx)
}

/** ป๊อบอัพพาไปหน้าตั้งค่าโดยตรงเมื่อแจ้งเตือนถูกปิด / แบตเตอรี่จำกัดอยู่ (ถามครั้งละอย่าง ปิดไว้ทีหลังได้จนกว่าจะเปิดแอพใหม่) */
@Composable
fun ShopReadyGate() {
    val ctx = LocalContext.current
    val r: ShopReadiness = rememberReadiness()
    val prefs = remember { ctx.getSharedPreferences("nimit_shop", Context.MODE_PRIVATE) }
    var laterNotif by remember { mutableStateOf(false) }
    var laterBattery by remember { mutableStateOf(false) }
    // Android 13+: รอให้หน้าต่างขอสิทธิ์ของระบบถูกตอบก่อน ค่อยพาไปหน้าตั้งค่า
    val asked: Boolean = Build.VERSION.SDK_INT < 33 || prefs.getBoolean("notifAsked", false)

    if (asked && (!r.notif || !r.channel) && !laterNotif) {
        val tAll: Boolean = !r.notif
        ReadyDialog(
            title = "เปิดการแจ้งเตือนเพื่อรับออเดอร์",
            text = if (tAll) "การแจ้งเตือนของแอพปิดอยู่ จะไม่ได้ยินเสียงเตือนเมื่อมีออเดอร์ใหม่ กดปุ่มด้านล่างเพื่อไปหน้าตั้งค่าแล้วเปิดการแจ้งเตือน"
                   else "ช่อง \"ออเดอร์ใหม่\" ถูกปิดไว้ จะไม่เห็นแจ้งเตือนเมื่อมีออเดอร์ใหม่ กดปุ่มด้านล่างเพื่อไปเปิดช่องนี้",
            ok = "ไปหน้าตั้งค่า", later = "ไว้ทีหลัง",
            onOk = { if (tAll) shopOpenNotifSettings(ctx) else shopOpenChannelSettings(ctx) },
            onLater = { laterNotif = true }
        )
    } else if (asked && r.notif && r.channel && !r.battery && !laterBattery) {
        ReadyDialog(
            title = "ตั้งแอพให้ไม่ถูกจำกัดแบตเตอรี่",
            text = "เครื่องบางรุ่นปิดแอพที่อยู่เบื้องหลังเพื่อประหยัดแบตเตอรี่ ทำให้เสียงเตือนออเดอร์ไม่ดังตอนปิดจอ กดปุ่มด้านล่างแล้วเลือก \"อนุญาต\" ในหน้าต่างที่ขึ้นมา",
            ok = "ไปหน้าตั้งค่า", later = "ไว้ทีหลัง",
            onOk = { shopOpenBatterySettings(ctx) },
            onLater = { laterBattery = true }
        )
    }
}

@Composable
private fun ReadyDialog(title: String, text: String, ok: String, later: String, onOk: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(text, fontSize = 14.sp, color = C.Subtext, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Primary).clickable { onOk() }, contentAlignment = Alignment.Center) {
                Text(ok, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Text(later, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onLater() }.padding(top = 12.dp, bottom = 4.dp))
        }
    }
}

/** การ์ดใน sidebar: สรุปว่าเครื่องนี้พร้อมรับออเดอร์ครบไหม กดแถวที่ขาดเพื่อไปหน้าตั้งค่า */
@Composable
fun ShopReadyCard() {
    val ctx = LocalContext.current
    val r: ShopReadiness = rememberReadiness()
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ความพร้อมรับออเดอร์", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
            Text(if (r.allOk) "พร้อมครบ" else "ยังไม่ครบ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (r.allOk) ShopGreen else ShopRed)
        }
        Spacer(Modifier.height(6.dp))
        ReadyRow("การแจ้งเตือนของแอพ", r.notif) { shopOpenNotifSettings(ctx) }
        ReadyRow("ช่องแจ้งเตือน \"ออเดอร์ใหม่\"", r.channel) { shopOpenChannelSettings(ctx) }
        ReadyRow("ไม่จำกัดแบตเตอรี่", r.battery) { shopOpenBatterySettings(ctx) }
    }
}

@Composable
private fun ReadyRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !ok) { onFix() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✕", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (ok) ShopGreen else ShopRed, modifier = Modifier.width(20.dp))
        Text(label, fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
        if (!ok) Text("ไปตั้งค่า", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
    }
}
