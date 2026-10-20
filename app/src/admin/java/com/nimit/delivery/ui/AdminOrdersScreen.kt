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
                        !store.ready.value -> "กำลังโหลดข้อมูลเขต..."
                        scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"
                        else -> "ไม่มีออเดอร์"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            items(list, key = { it.id }) { o -> AdminOrderCard(o, store.zoneLabel(o.s("shopId"))) { onOpen(o.id) } }
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
private fun AdminOrderCard(o: AdminOrd, zone: String, onClick: () -> Unit) {
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
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("#" + o.orderId, fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.Primary, letterSpacing = 1.sp)
                if (zone.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(zone, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
