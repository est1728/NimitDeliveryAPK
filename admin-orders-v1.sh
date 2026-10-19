#!/bin/bash
# หน้าออเดอร์ของแอปแอดมิน (ถอดจาก admin.html): สถิติ + แท็บสถานะ + การ์ดออเดอร์ + เมนูล่าง
# ออเดอร์แสดงเฉพาะร้านที่อยู่ในเขตของผู้ใช้ (รวมเขตที่ใช้หลังบ้านร่วมกัน)  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }

J=app/src/admin/java/com/nimit/delivery
[ -f $J/ui/AdminLogin.kt ] || die "ไม่เจอ $J/ui/AdminLogin.kt (ต้องรัน admin-setup-v1.sh ก่อน)"
[ -f $J/data/AdminRepo.kt ] || die "ไม่เจอ $J/data/AdminRepo.kt"
grep -q 'class AdminSession' $J/data/AdminRepo.kt || die "AdminRepo.kt ไม่มี AdminSession"
grep -q 'fun AdminUpdatePrompt' $J/ui/AdminUpdate.kt || die "ไม่เจอ AdminUpdatePrompt"

# ---------- ข้อมูลออเดอร์ ----------
cat > $J/data/AdminOrders.kt <<'KT_EOF'
package com.nimit.delivery.data

import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

val ADMIN_ACTIVE: List<String> = listOf("pending", "accepted", "picking", "arrived", "delivering")
val ADMIN_STATUS_TH: Map<String, String> = mapOf(
    "pending" to "รอรับ", "accepted" to "รับแล้ว", "picking" to "ไปร้าน", "arrived" to "ถึงร้าน",
    "delivering" to "กำลังส่ง", "done" to "เสร็จสิ้น", "rejected" to "ยกเลิก"
)

class AdminOrd(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
    fun date(k: String): Date? = (d[k] as? Timestamp)?.toDate()
    fun cust(k: String): String {
        val m = d["customer"] as? Map<*, *> ?: return ""
        return m[k]?.toString() ?: ""
    }
    fun qty(): Int {
        val l = d["items"] as? List<*> ?: return 0
        var t = 0
        for (i in l) {
            val m = i as? Map<*, *> ?: continue
            t += (m["qty"] as? Number)?.toInt() ?: 0
        }
        return t
    }
    val status: String get() = s("status")
    val orderId: String get() = if (s("orderId").isEmpty()) id else s("orderId")
    val created: Date? get() = date("createdAt")
}

/*
 * สถานะสดของหน้าออเดอร์: ฟังออเดอร์ที่ยังไม่จบ (เหมือน listenOrders ใน admin.html)
 * และฟังร้านที่อยู่ในเขตของแอดมิน (shops.franchiseIds) เพื่อกรองเฉพาะออเดอร์ของร้านในกลุ่มเขต
 */
class AdminOrdersStore(private val session: AdminSession) {
    val orders = mutableStateOf<List<AdminOrd>>(emptyList())
    val shopIds = mutableStateOf<Set<String>>(emptySet())
    val ready = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val regs = ArrayList<ListenerRegistration>()
    private val chunkShops = HashMap<Int, Set<String>>()
    private var job: Job? = null

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                // เขตทั้งหมดในกลุ่มหลังบ้านเดียวกัน (ไม่ผูก = มีแค่เขตตัวเอง)
                val g = db.collection("franchises").whereEqualTo("backendGroupId", session.groupId).get().await()
                val ids = ArrayList<String>()
                for (x in g.documents) ids.add(x.id)
                if (!ids.contains(session.franchiseId)) ids.add(session.franchiseId)
                var idx = 0
                for (chunk in ids.chunked(10)) {
                    val i = idx
                    idx++
                    regs.add(db.collection("shops").whereArrayContainsAny("franchiseIds", chunk).addSnapshotListener { snap, e ->
                        if (e != null) error.value = e.message
                        if (snap != null) {
                            chunkShops[i] = snap.documents.map { it.id }.toSet()
                            shopIds.value = chunkShops.values.flatten().toSet()
                        }
                    })
                }
                regs.add(db.collection("orders").whereIn("status", ADMIN_ACTIVE).addSnapshotListener { snap, e ->
                    if (e != null) error.value = e.message
                    if (snap != null) {
                        orders.value = snap.documents
                            .map { x -> AdminOrd(x.id, x.data ?: emptyMap()) }
                            .sortedBy { it.created?.time ?: 0L }
                    }
                })
                ready.value = true
            } catch (e: Exception) {
                error.value = e.message ?: "โหลดข้อมูลไม่สำเร็จ"
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        for (r in regs) r.remove()
        regs.clear()
        chunkShops.clear()
    }
}
KT_EOF

# ---------- หน้าออเดอร์ ----------
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
fun AdminOrdersPage(s: AdminSession, store: AdminOrdersStore) {
    var filter by remember { mutableStateOf("all") }
    val scope: Set<String> = store.shopIds.value
    val all: List<AdminOrd> = store.orders.value.filter { scope.contains(it.s("shopId")) }
    val list: List<AdminOrd> = if (filter == "all") all else all.filter { it.status == filter }
    val today: List<AdminOrd> = all.filter { adminIsToday(it) }
    val pend: Int = all.count { it.status == "pending" }
    val rev: Long = Math.round(today.sumOf { it.n("grandTotal") })

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        // หัว + สถิติ (ตาม .dash-top)
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("จัดการระบบ · " + s.name, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text("ออเดอร์วันนี้", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminStat("Active", all.size.toString(), Modifier.weight(1f))
                AdminStat("วันนี้", today.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminStat("รายได้", "฿" + rev, Modifier.weight(1f))
                AdminStat("รอรับ", pend.toString(), Modifier.weight(1f))
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
            items(list, key = { it.id }) { o -> AdminOrderCard(o) }
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
private fun AdminOrderCard(o: AdminOrd) {
    val st = o.status
    val pill = adminPill(st)
    val viewing: Boolean = o.date("riderViewingAt")?.let { System.currentTimeMillis() - it.time < 20000L } ?: false
    val hasRider: Boolean = o.s("riderId").isNotEmpty()
    val addrRaw = o.cust("address")
    val addr = if (addrRaw.isEmpty()) "-" else if (addrRaw.length > 30) addrRaw.substring(0, 30) + "..." else addrRaw

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
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

# ---------- นำทางหลัก + เมนูล่าง (แทนหน้าแรกชั่วคราวเดิม) ----------
cat > $J/ui/AdminNav.kt <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val store = remember(s.franchiseId) { AdminOrdersStore(s) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            if (tab == 0) {
                AdminOrdersPage(s, store)
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
                    }
                    if (tab == 5) {
                        Box(
                            Modifier.padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp)).background(C.Primary)
                                .clickable { onLogout() }.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("ออกจากระบบ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
            for (i in AdminMenu.indices) {
                val on = tab == i
                Column(
                    Modifier.weight(1f).clickable { tab = i }.padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) C.Primary else Color.Transparent))
                    Text(
                        AdminMenu[i], fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (on) C.Primary else C.Subtext, modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}
KT_EOF

echo "---- ผลตรวจ ----"
for f in $J/data/AdminOrders.kt $J/ui/AdminOrdersScreen.kt $J/ui/AdminNav.kt; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -c 'fun AdminHome' $J/ui/AdminNav.kt | sed 's/^/AdminHome ซ้ำกี่ที่: /'
grep -rn 'fun AdminHome' app/src/admin | wc -l | sed 's/^/AdminHome ทั้งโฟลเดอร์: /'
echo "เสร็จ"
