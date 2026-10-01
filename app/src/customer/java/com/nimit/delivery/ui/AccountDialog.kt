package com.nimit.delivery.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nimit.delivery.data.Config
import com.nimit.delivery.data.LoginFlow
import com.nimit.delivery.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ───────────────────────── สลับบัญชี (ป๊อบอัพสไตล์ Google) ─────────────────────────
private class Acc(val phone: String, val name: String, val address: String, val lat: Any?, val lng: Any?)

private fun buildAccounts(s: Session): List<Acc> {
    val out = mutableListOf<Acc>()
    try {
        val a = JSONArray(s.savedAccounts ?: "[]")
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.optString("phone").isNotEmpty()) out += Acc(o.optString("phone"), o.optString("name"), o.optString("address"), o.opt("lat"), o.opt("lng")) }
    } catch (_: Exception) {}
    val cur = s.customerPhone.orEmpty()
    if (cur.isNotEmpty() && out.none { it.phone == cur }) {
        val c = try { JSONObject(s.customerData ?: "{}") } catch (_: Exception) { JSONObject() }
        out += Acc(cur, c.optString("name"), c.optString("address"), c.opt("lat"), c.opt("lng"))
    }
    return out.sortedByDescending { it.phone == cur }
}

@Composable
fun AccountDialog(session: Session, onDismiss: () -> Unit, onAdd: () -> Unit, onSwitched: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = session.customerPhone.orEmpty()
    val accounts = remember { buildAccounts(session) }
    var busy by remember { mutableStateOf<String?>(null) }
    val palette = listOf(N.B600, N.Green600, N.Amber600, Color(0xFF8B5CF6), N.Red500)

    fun switchTo(a: Acc) {
        if (a.phone == current) { onDismiss(); return }
        session.customerPhone = a.phone
        session.clearCustomerCache()
        // ถ้าเปิด OTP และบัญชีนี้ยังไม่เคยยืนยันบนเครื่องนี้ ให้ไปหน้าเข้าสู่ระบบ (จะขอ OTP ต่อ)
        if (Config.OTP_ENABLED && session.verifiedPhone != a.phone) { onAdd(); return }
        busy = a.phone
        scope.launch {
            val registered = LoginFlow.route(session, a.phone)
            if (!registered) {   // ดึงจากเซิร์ฟเวอร์ไม่ได้ ใช้ข้อมูลที่จำไว้ในเครื่อง
                session.customerData = JSONObject().put("name", a.name).put("phone", a.phone).put("address", a.address).put("lat", a.lat ?: JSONObject.NULL).put("lng", a.lng ?: JSONObject.NULL).toString()
                if (a.address.isNotEmpty()) session.addresses = JSONArray().put(JSONObject().put("name", a.name).put("phone", a.phone).put("address", a.address).put("lat", a.lat ?: JSONObject.NULL).put("lng", a.lng ?: JSONObject.NULL)).toString().also { session.defaultAddress = "0" }
                session.newCustomer = null
            }
            busy = null
            onSwitched()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("บัญชี", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = N.Ink900)
                    Text("เลือกบัญชีที่ต้องการใช้งาน", fontSize = 12.sp, color = N.Ink500, modifier = Modifier.padding(top = 2.dp))
                }
                Text("✕", fontSize = 16.sp, color = N.Ink500, modifier = Modifier.clip(CircleShape).clickable { onDismiss() }.padding(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            accounts.forEach { a ->
                val isCur = a.phone == current
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(if (isCur) N.B50 else Color.White)
                        .clickable(enabled = busy == null) { switchTo(a) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(palette[(a.phone.lastOrNull()?.digitToIntOrNull() ?: 0) % palette.size]), contentAlignment = Alignment.Center) {
                        Text(a.name.trim().take(1).uppercase().ifEmpty { "#" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name.ifEmpty { "ไม่มีชื่อ" }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.phone, fontSize = 12.sp, color = N.Ink500, modifier = Modifier.padding(top = 1.dp))
                    }
                    if (busy == a.phone) Text("กำลังสลับ…", fontSize = 11.sp, color = N.B700)
                    else if (isCur) PathIcon("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z", N.B600, 22.dp)
                }
            }
            if (accounts.isEmpty()) Text("ยังไม่มีบัญชีที่บันทึกไว้", fontSize = 13.sp, color = N.Ink500, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = N.Line)
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).clickable { onAdd() }.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(N.B100), contentAlignment = Alignment.Center) { PathIcon(P.PLUS, N.B700, 22.dp) }
                Spacer(Modifier.width(12.dp)); Text("เพิ่มบัญชีอื่น", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = N.Ink900)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).clickable { onLogout() }.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(N.Red100), contentAlignment = Alignment.Center) { PathIcon("M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z", N.Red500, 20.dp) }
                Spacer(Modifier.width(12.dp)); Text("ออกจากระบบ", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = N.Red500)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ───────────────────────── สวิตช์แจ้งเตือน ─────────────────────────
private fun Context.findActivity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }
private fun Context.prefs() = getSharedPreferences("nimit", Context.MODE_PRIVATE)
private fun Context.openNotifSettings() {
    try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}
private fun Context.notifGranted(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** ขอสิทธิ์แจ้งเตือนครั้งแรกที่เข้าหน้าช้อป (Android 13+) — ถามครั้งเดียว ถ้าปฏิเสธไปเปิดทีหลังได้จากสวิตช์ใน sidebar */
@Composable
fun NotifPermissionOnce() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !ctx.notifGranted() && !ctx.prefs().getBoolean("notifAsked", false)) {
            ctx.prefs().edit().putBoolean("notifAsked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun SidebarNotifRow() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1200); tick++ } }   // รีเฟรชสถานะหลังกลับมาจากหน้าตั้งค่าเครื่อง
    @Suppress("UNUSED_VARIABLE") val t = tick
    val granted = ctx.notifGranted()
    val pref = ctx.prefs().getBoolean("notifEnabled", true)
    val on = granted && pref
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        ctx.prefs().edit().putBoolean("notifAsked", true).apply()
        if (ok) { ctx.prefs().edit().putBoolean("notifEnabled", true).apply(); tick++ }
        else if (ctx.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false) ctx.openNotifSettings()
    }
    fun toggle(want: Boolean) {
        if (!want) { ctx.prefs().edit().putBoolean("notifEnabled", false).apply(); tick++; return }
        ctx.prefs().edit().putBoolean("notifEnabled", true).apply(); tick++
        if (ctx.notifGranted()) return
        val act = ctx.findActivity()
        val canAsk = Build.VERSION.SDK_INT >= 33 && (act?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true || !ctx.prefs().getBoolean("notifAsked", false))
        if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.openNotifSettings()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("การแจ้งเตือน", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = N.Ink900)
                Text(if (!granted) "ปิดอยู่ในเครื่อง · เปิดสวิตช์เพื่อตั้งค่า" else if (pref) "เปิดอยู่ · แจ้งสถานะออเดอร์และข่าวสาร" else "ปิดอยู่", fontSize = 11.5.sp, color = N.Ink500)
            }
            Switch(checked = on, onCheckedChange = { toggle(it) }, colors = SwitchDefaults.colors(checkedTrackColor = N.B600, checkedThumbColor = Color.White))
        }
        HorizontalDivider(color = N.Line)
    }
}
