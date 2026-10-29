#!/bin/bash
# หน้า "ร้านค้า" ของแอปแอดมิน (ถอดจาก shopsPage ใน admin.html): รายการร้านในเขต + ป้ายเขต + กรองเขต
# เปิด/ปิดร้าน (เลือกปิดยาว/ปิดชั่วคราว + เหตุผล + บันทึกประวัติ), สวิตช์ร้านฝากซื้อ, เปิดหน้าร้าน/จัดการเมนูบนเว็บ, คัดลอกลิงก์
# ยังไม่มี: เพิ่ม/แก้ไข/ลบร้าน และหน้าจัดการเมนู (shop-manage.html)  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
N=$J/ui/AdminNav.kt
[ -f $N ] || die "ไม่เจอ $N"
grep -q 'AdminHistoryPage' $N || die "AdminNav.kt ยังไม่ผ่าน admin-history-v1.sh"
[ -f $J/data/AdminOrders.kt ] || die "ไม่เจอ AdminOrders.kt"

# ---------- ข้อมูล ----------
cat > $J/data/AdminShops.kt <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import kotlinx.coroutines.tasks.await

class AdminShop(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    val name: String get() = s("name")
    val isOpen: Boolean get() = d["isOpen"] != false
    val isProxy: Boolean get() = s("shopType") == "proxy"
    val slug: String get() = if (s("slug").isEmpty()) id else s("slug")

    /** เขตของร้าน: franchiseIds (หลายเขต) หรือ franchiseId (ฟิลด์เก่า เขตเดียว) */
    fun zones(): List<String> {
        val a = (d["franchiseIds"] as? List<*>)?.mapNotNull { it as? String }
        if (a != null && a.isNotEmpty()) return a
        val one = s("franchiseId")
        return if (one.isNotEmpty()) listOf(one) else emptyList()
    }
}

object AdminShopsApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun listen(onChange: (List<AdminShop>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("shops").addSnapshotListener { snap, e ->
            if (e != null) onError(e.message ?: "โหลดร้านค้าไม่ได้")
            if (snap != null) onChange(snap.documents.map { AdminShop(it.id, it.data ?: emptyMap()) })
        }
    }

    suspend fun open(id: String) {
        val m = HashMap<String, Any?>()
        m["isOpen"] = true
        m["closeReason"] = ""
        m["closeType"] = null
        m["closeUntil"] = null
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
        log(id, "open", "", null)
    }

    suspend fun close(id: String, reason: String, type: String, until: Long?) {
        val m = HashMap<String, Any?>()
        m["isOpen"] = false
        m["closeReason"] = reason
        m["closeType"] = type
        m["closeUntil"] = until
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
        log(id, "close", reason, type)
    }

    private suspend fun log(id: String, action: String, reason: String, closeType: String?) {
        val l = HashMap<String, Any?>()
        l["action"] = action
        l["reason"] = reason
        if (closeType != null) l["closeType"] = closeType
        l["at"] = FieldValue.serverTimestamp()
        l["by"] = "Admin"
        try { db.collection("shops").document(id).collection("statusLogs").add(l).await() } catch (e: Exception) { }
    }

    suspend fun setType(id: String, type: String) {
        val m = HashMap<String, Any>()
        m["shopType"] = type
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
    }

    /** เวลาเปิดร้านรอบถัดไปตามเวลา/วันที่ตั้งอัตโนมัติ (ใช้ตอนปิดยาว) ไม่ได้ตั้งไว้ = null (ปิดค้างจนกดเปิดเอง) */
    fun nextOpenTime(s: AdminShop): Long? {
        if (s.d["autoSchedule"] != true) return null
        val so = s.s("scheduleOpen")
        if (so.isEmpty() || s.s("scheduleClose").isEmpty()) return null
        val raw = (s.d["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val days: List<Int> = if (raw.isEmpty()) (0..6).toList() else raw
        val parts = so.split(":")
        val oh: Int = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val om: Int = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now: Long = System.currentTimeMillis()
        for (add in 0..8) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, add)
            c.set(Calendar.HOUR_OF_DAY, oh)
            c.set(Calendar.MINUTE, om)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) continue
            if (days.contains(c.get(Calendar.DAY_OF_WEEK) - 1)) return c.timeInMillis
        }
        return null
    }
}
KT_EOF

# ---------- หน้าจอ ----------
cat > $J/ui/AdminShops.kt <<'KT_EOF'
package com.nimit.delivery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminShop
import com.nimit.delivery.data.AdminShopsApi
import com.nimit.delivery.data.adminErrorText
import kotlinx.coroutines.launch

private val SGreen = Color(0xFF16A34A)
private val SRed = Color(0xFFDC2626)
private const val WEB_BASE = "https://nimitdelivery.vercel.app"

private fun sToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun sOpenUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { sToast(ctx, "เปิดลิงก์ไม่ได้") }
}

@Composable
fun AdminShopsPage(store: AdminOrdersStore) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shops by remember { mutableStateOf<List<AdminShop>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoneFilter by remember { mutableStateOf("") }
    var closeFor by remember { mutableStateOf<AdminShop?>(null) }
    var typeFor by remember { mutableStateOf<AdminShop?>(null) }
    var stage by remember { mutableStateOf(0) }
    var closeType by remember { mutableStateOf("long") }
    var reason by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }

    DisposableEffect(Unit) {
        val reg = AdminShopsApi.listen({ shops = it }, { error = it })
        onDispose { reg.remove() }
    }

    val scopeIds: Set<String> = store.shopIds.value
    val zoneIds: List<String> = store.zoneIds.value
    val list: List<AdminShop>? = shops
        ?.filter { scopeIds.contains(it.id) && (zoneFilter.isEmpty() || it.zones().contains(zoneFilter)) }
        ?.sortedBy { it.name }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ร้านค้า", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
            if (list != null) Text(list.size.toString() + " ร้าน", fontSize = 12.sp, color = C.Subtext)
        }
        if (zoneIds.size > 1) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SChip("ทุกเขต", zoneFilter.isEmpty()) { zoneFilter = "" }
                for (z in zoneIds) SChip(store.zoneNames.value[z] ?: z, zoneFilter == z) { zoneFilter = z }
            }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list == null || list.isEmpty()) {
                item {
                    val msg: String = when {
                        error != null -> "โหลดร้านค้าไม่ได้: " + error
                        list == null -> "กำลังโหลด..."
                        else -> "ไม่พบร้านค้าในเขตที่เลือก"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            if (list != null) {
                items(list, key = { it.id }) { s ->
                    ShopCard(
                        s, store,
                        onToggleOpen = {
                            if (s.isOpen) {
                                closeFor = s; stage = 0; closeType = "long"; reason = ""; hours = "0"; minutes = "30"
                            } else {
                                scope.launch {
                                    try { AdminShopsApi.open(s.id); sToast(ctx, "เปิดร้านแล้ว") } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ") }
                                }
                            }
                        },
                        onToggleType = { typeFor = s }
                    )
                }
            }
        }
    }

    // ===== ปิดร้าน: เลือกรูปแบบ แล้วใส่เหตุผล =====
    val cf = closeFor
    if (cf != null) {
        AlertDialog(
            onDismissRequest = { closeFor = null },
            title = { Text("ปิดร้าน") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stage == 0) {
                        Text("เลือกรูปแบบการปิดร้าน", fontSize = 13.sp, color = C.Subtext)
                        SChoice("ปิดยาว", "ปิดจนกว่าจะถึงรอบเปิดร้านถัดไปตามเวลาที่ตั้งไว้ (ถ้าร้านไม่ได้เปิดเวลาเปิด-ปิดอัตโนมัติ จะปิดค้างจนกว่าจะกดเปิดเอง)") {
                            closeType = "long"; stage = 1
                        }
                        SChoice("ปิดชั่วคราว", "ตั้งเวลานับถอยหลัง ครบเวลาแล้วร้านเปิดเองอัตโนมัติ") {
                            closeType = "temp"; stage = 1
                        }
                    } else {
                        Text("‹ กลับ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.clickable { stage = 0 })
                        if (closeType == "temp") {
                            Text("ปิดนานเท่าไหร่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)
                                )
                                Text("ชม.", fontSize = 13.sp, color = C.Subtext)
                                OutlinedTextField(
                                    value = minutes, onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)
                                )
                                Text("นาที", fontSize = 13.sp, color = C.Subtext)
                            }
                        }
                        Text("กรุณาระบุเหตุผลที่ปิดร้าน (บังคับ)", fontSize = 12.sp, color = C.Subtext)
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (stage == 1) {
                    TextButton(onClick = {
                        val r = reason.trim()
                        if (r.isEmpty()) {
                            sToast(ctx, "กรุณาใส่เหตุผล")
                        } else {
                            var until: Long? = null
                            var ok = true
                            if (closeType == "temp") {
                                val h = hours.toIntOrNull() ?: 0
                                val m = minutes.toIntOrNull() ?: 0
                                if (h <= 0 && m <= 0) {
                                    sToast(ctx, "กรุณาตั้งเวลาปิดชั่วคราวอย่างน้อย 1 นาที")
                                    ok = false
                                } else {
                                    until = System.currentTimeMillis() + (h * 3600L + m * 60L) * 1000L
                                }
                            } else {
                                until = AdminShopsApi.nextOpenTime(cf)
                            }
                            if (ok) {
                                val type = closeType
                                val sid = cf.id
                                closeFor = null
                                scope.launch {
                                    try { AdminShopsApi.close(sid, r, type, until); sToast(ctx, "ปิดร้านแล้ว") } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ") }
                                }
                            }
                        }
                    }) { Text("ปิดร้าน", color = SRed) }
                }
            },
            dismissButton = { TextButton(onClick = { closeFor = null }) { Text("ยกเลิก") } }
        )
    }

    // ===== สลับร้านฝากซื้อ/ร้านปกติ =====
    val tf = typeFor
    if (tf != null) {
        val toProxy: Boolean = !tf.isProxy
        AlertDialog(
            onDismissRequest = { typeFor = null },
            title = { Text(if (toProxy) "ตั้งเป็นร้านฝากซื้อ" else "เปลี่ยนกลับเป็นร้านปกติ") },
            text = {
                Text(
                    if (toProxy) "ตั้งร้านนี้เป็น \"ร้านฝากซื้อ\" ใช่หรือไม่?\nราคาสินค้าจะถือเป็นราคาสุทธิ ไม่บวก GP"
                    else "เปลี่ยนร้านนี้กลับเป็น \"ร้านปกติ\" ใช่หรือไม่?\nราคาสินค้าจะกลับไปคิด GP ตามปกติ"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = tf.id
                    typeFor = null
                    scope.launch {
                        try {
                            AdminShopsApi.setType(id, if (toProxy) "proxy" else "normal")
                            sToast(ctx, if (toProxy) "ตั้งเป็นร้านฝากซื้อแล้ว" else "ตั้งเป็นร้านปกติแล้ว")
                        } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ: " + adminErrorText(e)) }
                    }
                }) { Text("ยืนยัน") }
            },
            dismissButton = { TextButton(onClick = { typeFor = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun SChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) C.Primary else C.Gray).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext, maxLines = 1) }
}

@Composable
private fun SChoice(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(sub, fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ShopCard(s: AdminShop, store: AdminOrdersStore, onToggleOpen: () -> Unit, onToggleType: () -> Unit) {
    val ctx = LocalContext.current
    val zones: List<String> = s.zones()
    val link: String = WEB_BASE + "/shop-admin.html?id=" + s.slug
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Primary, Color(0xFF1565C0)))),
                contentAlignment = Alignment.Center
            ) { Text(s.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.s("phone").isNotEmpty()) Text(s.s("phone"), fontSize = 12.sp, color = C.Subtext)
                val addr: String = s.s("address").ifEmpty { s.s("desc") }
                if (addr.isNotEmpty()) Text(addr, fontSize = 12.sp, color = C.Subtext, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                if (zones.isEmpty()) {
                    SBadge("ร้านกลาง (ไม่มีเขต)", Color(0xFFF1F5F9), C.Subtext)
                } else {
                    for (z in zones) SBadge("เขต: " + (store.zoneNames.value[z] ?: z), Color(0xFFE8F0FE), C.Primary)
                }
                if (!s.isOpen && s.s("closeReason").isNotEmpty()) {
                    Text("เหตุผลที่ปิด: " + s.s("closeReason"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SRed, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (s.isOpen) "เปิด" else "ปิด", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (s.isOpen) SGreen else SRed)
                Text("สถานะ", fontSize = 10.sp, color = C.Subtext)
            }
        }
        HorizontalDivider(color = Color(0xFFF5F6F8))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = s.isOpen, onCheckedChange = { onToggleOpen() },
                colors = SwitchDefaults.colors(checkedTrackColor = SGreen, checkedThumbColor = Color.White)
            )
            Text(if (s.isOpen) "เปิด" else "ปิด", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.width(14.dp))
            Switch(
                checked = s.isProxy, onCheckedChange = { onToggleType() },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Primary, checkedThumbColor = Color.White)
            )
            Text("ฝากซื้อ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (s.isProxy) C.Primary else C.Subtext, modifier = Modifier.padding(start = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SBtn("เปิดหน้าร้าน", Modifier.weight(1f)) { sOpenUrl(ctx, link) }
            SBtn("จัดการเมนู (เว็บ)", Modifier.weight(1f)) { sOpenUrl(ctx, WEB_BASE + "/shop-manage.html?id=" + s.slug) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(link, fontSize = 10.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "คัดลอก", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                modifier = Modifier.clickable {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("shop-link", link))
                        sToast(ctx, "คัดลอกลิงก์แล้ว")
                    } catch (e: Exception) { sToast(ctx, "คัดลอกไม่ได้") }
                }.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SBadge(text: String, bg: Color, fg: Color) {
    Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun SBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
}
KT_EOF
echo "AdminShops.kt (data+ui): เขียนแล้ว"

# ---------- ต่อเข้าแท็บ "ร้านค้า" ----------
if grep -q 'AdminShopsPage' $N; then
  echo "AdminNav: ต่อแล้ว ข้าม"
else
  grep -q 'onRecalled = { tab = 0 }) else Text(' $N || die "AdminNav: ไม่เจอจุดยึดแท็บ"
  sed -i 's|onRecalled = { tab = 0 }) else Text(|onRecalled = { tab = 0 }) else if (tab == 2) AdminShopsPage(store) else Text(|' $N
  grep -q 'AdminShopsPage' $N || die "ต่อ AdminNav ไม่สำเร็จ"
  echo "AdminNav: ต่อแล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $J/data/AdminShops.kt $J/ui/AdminShops.kt $N; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
echo "เสร็จ"
