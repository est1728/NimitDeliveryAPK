#!/bin/bash
# ปรับหน้าออเดอร์ให้เหมือนเว็บ: หัวมีปุ่มเมนู + "+ สร้างออเดอร์" + "ดูสถิติ" พับเก็บได้ (ปิดไว้ก่อน) เมนูล่างมีไอคอนแบบเดียวกับเว็บ
# และเปลี่ยนข้อความล็อกอินกลับเป็นแบบทั่วไป (เลิกวินิจฉัย)  รันซ้ำได้  ต้องรัน admin-detail-v1.sh มาก่อน
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
[ -f $J/ui/AdminOrderDetail.kt ] || die "ไม่เจอ AdminOrderDetail.kt (ต้องรัน admin-detail-v1.sh ก่อน)"
[ -f $J/ui/AdminOrdersScreen.kt ] || die "ไม่เจอ AdminOrdersScreen.kt"
R=$J/data/AdminRepo.kt
[ -f $R ] || die "ไม่เจอ AdminRepo.kt"

# ---------- ล็อกอิน: กลับเป็นข้อความทั่วไป ----------
if grep -q '\[1\]' $R; then
  grep -q 'private const val PREF = "nimit_admin"' $R || die "AdminRepo: ไม่เจอจุดยึด PREF"
  sed -i 's|^    private const val PREF = "nimit_admin"$|    private const val PREF = "nimit_admin"\n    private const val BAD = "เบอร์หรือรหัสไม่ถูกต้อง"|' $R
  sed -i 's|return "\[1\] [^"]*"[^?]*$|return BAD|' $R
  sed -i 's|return "\[2\] [^"]*"|return BAD|; s|return "\[3\] [^"]*"[^"]*$|return BAD|; s|return "\[4\] [^"]*"[^"]*"[^"]*"[^"]*$|return BAD|' $R
  echo "login: เปลี่ยนกลับเป็นข้อความทั่วไป (ตรวจบรรทัดด้านล่าง)"
  grep -n 'BAD' $R
else
  echo "login: ไม่มีข้อความวินิจฉัย ข้าม"
fi

# ---------- ไอคอน (พาธเดียวกับที่เว็บใช้) ----------
cat > $J/ui/AdminIcons.kt <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun adminVec(name: String, d: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        .build()

val AdminIcOrders: ImageVector = adminVec("a_orders", "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z")
val AdminIcHistory: ImageVector = adminVec("a_history", "M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18z")
val AdminIcShops: ImageVector = adminVec("a_shops", "M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z")
val AdminIcRiders: ImageVector = adminVec("a_riders", "M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9l1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z")
val AdminIcCustomers: ImageVector = adminVec("a_customers", "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z")
val AdminIcSettings: ImageVector = adminVec("a_settings", "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z")
val AdminIcMenu: ImageVector = adminVec("a_menu", "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z")
KT_EOF

# ---------- หน้าออเดอร์ (หัวแบบเว็บ) ----------
cat > $J/ui/AdminOrdersScreen.kt <<'KT_EOF'
package com.nimit.delivery.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.ADMIN_STATUS_TH
import com.nimit.delivery.data.AdminOrd
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val AGreen = Color(0xFF16A34A)
private val ARed = Color(0xFFDC2626)
private val AOrange = Color(0xFFEA580C)
private val AOrangeL = Color(0xFFFFF7ED)

private val AdminTabs: List<Pair<String, String>> = listOf(
    "all" to "ทั้งหมด", "pending" to "รอรับ", "accepted" to "รับแล้ว", "picking" to "ไปร้าน", "delivering" to "กำลังส่ง"
)

private fun adminPill(st: String): Pair<Color, Color> = when (st) {
    "pending" -> Pair(AOrangeL, AOrange)
    "accepted", "picking" -> Pair(C.PrimaryLight, C.Primary)
    "arrived" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
    "delivering" -> Pair(Color(0xFFDCFCE7), AGreen)
    "done" -> Pair(Color(0xFFF1F5F9), C.Subtext)
    "rejected" -> Pair(Color(0xFFFEE2E2), ARed)
    else -> Pair(Color(0xFFF1F5F9), C.Subtext)
}

private fun adminTimeText(o: AdminOrd): String {
    val d = o.created ?: return "-"
    return SimpleDateFormat("HH:mm", Locale("th", "TH")).format(d)
}

private fun adminIsToday(o: AdminOrd): Boolean {
    val d = o.created ?: return false
    val a = Calendar.getInstance().apply { time = d }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun AdminOrdersPage(
    s: AdminSession,
    store: AdminOrdersStore,
    onOpen: (String) -> Unit,
    onMenu: () -> Unit,
    onCreate: () -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    var showStats by remember { mutableStateOf(false) }
    val scope: Set<String> = store.shopIds.value
    val all: List<AdminOrd> = store.orders.value.filter { scope.contains(it.s("shopId")) }
    val list: List<AdminOrd> = if (filter == "all") all else all.filter { it.status == filter }
    val today: List<AdminOrd> = all.filter { adminIsToday(it) }
    val pend: Int = all.count { it.status == "pending" }
    val rev: Long = Math.round(today.sumOf { it.n("grandTotal") })

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        // หัว (ตาม .dash-top)
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("จัดการระบบ", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    Text("ออเดอร์วันนี้", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable { onMenu() },
                    contentAlignment = Alignment.Center
                ) { Icon(AdminIcMenu, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(AGreen, Color(0xFF15803D))))
                        .clickable { onCreate() }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ สร้างออเดอร์", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            }
            // ปุ่มพับ/แสดงสถิติ (ปิดไว้ก่อน)
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f))
                        .clickable { showStats = !showStats }.padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(if (showStats) "▲ ซ่อนสถิติ" else "▼ ดูสถิติ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (showStats) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminStat("Active", all.size.toString(), Modifier.weight(1f))
                    AdminStat("วันนี้", today.size.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminStat("รายได้", "฿" + rev, Modifier.weight(1f))
                    AdminStat("รอรับ", pend.toString(), Modifier.weight(1f))
                }
            }
        }
        // แท็บสถานะ (ตาม .ftabs)
        Row(
            Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for ((k, label) in AdminTabs) {
                val on = filter == k
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (on) C.Primary else C.Gray)
                        .clickable { filter = k }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext) }
            }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list.isEmpty()) {
                item {
                    val err: String? = store.error.value
                    val msg: String = when {
                        err != null -> "โหลดไม่ได้: " + err
                        store.ready.value && scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"
                        else -> "ไม่มีออเดอร์"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            items(list, key = { it.id }) { o -> AdminOrderCard(o) { onOpen(o.id) } }
        }
    }
}

@Composable
private fun AdminStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.13f)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun AdminDot(on: Boolean) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) AGreen else ARed))
}

// การ์ดออเดอร์ ตาม .ocard ของ admin.html
@Composable
private fun AdminOrderCard(o: AdminOrd, onClick: () -> Unit) {
    val st = o.status
    val pill = adminPill(st)
    val viewing: Boolean = o.date("riderViewingAt")?.let { System.currentTimeMillis() - it.time < 20000L } ?: false
    val hasRider: Boolean = o.s("riderId").isNotEmpty()
    val addrRaw = o.cust("address")
    val addr = if (addrRaw.isEmpty()) "-" else if (addrRaw.length > 30) addrRaw.substring(0, 30) + "..." else addrRaw

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#" + o.orderId, fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.Primary, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            if (hasRider) {
                AdminDot(o.date("riderReadAt") != null)
                Spacer(Modifier.width(5.dp))
                AdminDot(viewing)
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pill.first).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(ADMIN_STATUS_TH[st] ?: st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pill.second)
            }
        }
        HorizontalDivider(color = Color(0xFFF5F6F8))
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.qty().toString() + " รายการ", fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("฿" + Math.round(o.n("grandTotal")), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                }
            }
            Text(if (o.s("shopName").isEmpty()) "-" else o.s("shopName"), fontSize = 13.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(addr, fontSize = 13.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(adminTimeText(o), fontSize = 13.sp, color = C.Text)
                Spacer(Modifier.width(8.dp))
                val rn = o.s("riderName")
                if (rn.isNotEmpty()) {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(rn, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                    }
                } else {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(AOrangeL).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("ไม่มีไรเดอร์", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AOrange)
                    }
                }
            }
        }
    }
}
KT_EOF

# ---------- นำทางหลัก + เมนูล่างแบบมีไอคอน ----------
cat > $J/ui/AdminNav.kt <<'KT_EOF'
package com.nimit.delivery.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminRepo
import com.nimit.delivery.data.AdminSession

// เริ่มต้นตัดสินจากเซสชันที่เก็บไว้ทันที (อ่านในเครื่อง ไม่มีหน้า "ตรวจสอบสิทธิ์")
// มีเซสชัน = เข้าหน้าแอดมินเลย / ไม่มี = หน้าล็อกอิน
@Composable
fun AdminNav() {
    val ctx = LocalContext.current
    var session by remember { mutableStateOf(AdminRepo.load(ctx)) }
    val s = session
    if (s == null) {
        AdminLoginScreen(onDone = { session = AdminRepo.load(ctx) })
    } else {
        AdminHome(s, onLogout = { AdminRepo.clear(ctx); session = null })
    }
    AdminUpdatePrompt()
}

private val AdminMenu: List<String> = listOf("ออเดอร์", "ประวัติ", "ร้านค้า", "ไรเดอร์", "ลูกค้า", "ตั้งค่า")
private val AdminMenuIcons: List<ImageVector> = listOf(AdminIcOrders, AdminIcHistory, AdminIcShops, AdminIcRiders, AdminIcCustomers, AdminIcSettings)

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var chatId by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val store = remember(s.franchiseId) { AdminOrdersStore(s) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    val cid = chatId
    val oid = openId
    if (cid != null) {
        BackHandler { chatId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) { ChatScreen(cid, "admin", { chatId = null }) }
    } else if (oid != null) {
        BackHandler { openId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid })
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Gray)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 0) {
                    AdminOrdersPage(
                        s, store,
                        onOpen = { openId = it },
                        onMenu = { showMenu = true },
                        onCreate = { Toast.makeText(ctx, "หน้าสร้างออเดอร์ยังไม่เปิดใช้", Toast.LENGTH_SHORT).show() }
                    )
                } else {
                    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                        Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F2F5))
            Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                for (i in AdminMenu.indices) {
                    val on = tab == i
                    val col = if (on) C.Primary else C.Subtext
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(top = 8.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(AdminMenuIcons[i], null, tint = col, modifier = Modifier.size(24.dp))
                        Text(AdminMenu[i], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("เมนู") },
            text = {
                Column {
                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(s.phone, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false; onLogout() }) { Text("ออกจากระบบ", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text("ปิด") } }
        )
    }
}
KT_EOF

echo "---- ผลตรวจ ----"
for f in $J/ui/AdminIcons.kt $J/ui/AdminOrdersScreen.kt $J/ui/AdminNav.kt $R; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -c 'return BAD' $R | sed 's/^/จุดที่คืนข้อความทั่วไป: /'
grep -c '\[[1-4]\]' $R | sed 's/^/ข้อความวินิจฉัยที่เหลือ: /'
echo "เสร็จ"
