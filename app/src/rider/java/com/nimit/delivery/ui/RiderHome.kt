package com.nimit.delivery.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ACTIVE_STATUSES
import com.nimit.delivery.data.Ord
import com.nimit.delivery.data.RiderApi
import com.nimit.delivery.data.RiderStore
import com.nimit.delivery.data.STATUS_TH
import com.nimit.delivery.data.fmtDate
import com.nimit.delivery.data.fmtTime
import com.nimit.delivery.data.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

private const val CHAT_PATH = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"
private const val BAG_PATH = "M20 6h-2.18c.11-.31.18-.65.18-1a2.996 2.996 0 0 0-5.5-1.65l-.5.67-.5-.68C10.96 2.54 10.05 2 9 2 7.34 2 6 3.34 6 5c0 .35.07.69.18 1H4c-1.11 0-2 .89-2 2v11c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2z"
private const val MONEY_PATH = "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"
private const val MENU_PATH = "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"
private const val DOTS_PATH = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"

@Composable
fun RiderHome(
    store: RiderStore,
    onOpenOrder: (String, Boolean) -> Unit,
    onOpenChat: (String) -> Unit,
    onStatement: () -> Unit,
    onWallet: () -> Unit,
    onShift: () -> Unit,
    onAccount: () -> Unit,
    onReviews: () -> Unit,
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current
    val notifOn = remember { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("orders") }
    var bottom by remember { mutableStateOf("food") }
    var moreOpen by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Ord?>(null) }
    var accepting by remember { mutableStateOf(false) }
    val rider: Map<String, Any> = store.rider.value
    val riderName: String = rider["name"]?.toString() ?: "ไรเดอร์"
    val riderPhone: String = rider["phone"]?.toString() ?: ""
    val unreadTotal: Int = store.unread.values.fold(0) { a: Int, b: Int -> a + b }
    val banner: String? = store.banner.value
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(4000L)
            store.banner.value = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8EEF7))) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0C4AA6), Color(0xFF083570))))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(38.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(riderName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                            Text("ออนไลน์", color = Color(0xFF86EFAC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("การแจ้งเตือน: " + (if (notifOn) "เปิดอยู่" else "ปิดอยู่"), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { moreOpen = true },
                        contentAlignment = Alignment.Center
                    ) { PathIcon(MENU_PATH, Color.White, 18.dp) }
                }
            }
            if (bottom == "food") {
                Row(Modifier.fillMaxWidth().background(Color(0xFF083570))) {
                    TabBtn("ออเดอร์", tab == "orders", Modifier.weight(1f)) { tab = "orders" }
                    TabBtn("ประวัติ", tab == "history", Modifier.weight(1f)) { tab = "history" }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (bottom == "messages") {
                    ChatListTab(store, onOpenChat)
                } else if (tab == "orders") {
                    OrdersTab(store, { o: Ord -> job = o }, { id: String -> onOpenOrder(id, false) })
                } else {
                    HistoryTab(store, onOpenOrder)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDDE5F0)))
            Row(Modifier.fillMaxWidth().background(Color.White)) {
                NavItem(Modifier.weight(1f), "bag", "รายการ", bottom == "food", 0) { bottom = "food" }
                NavItem(Modifier.weight(1f), "money", "รายได้", false, 0) { onStatement() }
                NavItem(Modifier.weight(1f), "chat", "ข้อความ", bottom == "messages", unreadTotal) { bottom = "messages" }
                NavItem(Modifier.weight(1f), "more", "เพิ่มเติม", false, 0) { moreOpen = true }
            }
        }

        if (banner != null) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(C.PrimaryDark).clickable { store.banner.value = null }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔔", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(banner, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    val j: Ord? = job
    if (j != null) {
        AlertDialog(
            onDismissRequest = { job = null },
            title = { Text("งานใหม่! " + j.orderId) },
            text = {
                Column {
                    KV("ร้านค้า", j.s("shopName"))
                    KV("ลูกค้า", if (j.cust("name").isNotEmpty()) j.cust("name") else j.cust("phone"))
                    KV("ที่อยู่", j.cust("address"))
                    KV("รายได้ไรเดอร์", money(j.n("riderEarn")))
                }
            },
            confirmButton = {
                TextButton(enabled = !accepting, onClick = {
                    accepting = true
                    scope.launch {
                        try {
                            RiderApi.acceptJob(j.id, store.riderId, riderName, riderPhone)
                            store.banner.value = "รับงานแล้ว " + j.orderId
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                        }
                        accepting = false
                        job = null
                    }
                }) { Text(if (accepting) "กำลังรับ..." else "✓ รับงาน") }
            },
            dismissButton = { TextButton(onClick = { job = null }) { Text("ไม่รับ") } }
        )
    }

    if (moreOpen) {
        AlertDialog(
            onDismissRequest = { moreOpen = false },
            title = { Text(riderName) },
            text = {
                Column {
                    MenuRow("กระเป๋าเงิน") { moreOpen = false; onWallet() }
                    MenuRow("กะงาน") { moreOpen = false; onShift() }
                    MenuRow("บัญชีของฉัน") { moreOpen = false; onAccount() }
                    MenuRow("รีวิวของฉัน") { moreOpen = false; onReviews() }
                    MenuRow("ออกจากระบบ") { moreOpen = false; onLogout() }
                }
            },
            confirmButton = { TextButton(onClick = { moreOpen = false }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun TabBtn(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) Color.White else Color.White.copy(alpha = 0.62f))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(if (on) Color.White else Color.Transparent))
    }
}

@Composable
private fun NavItem(modifier: Modifier, icon: String, label: String, on: Boolean, badge: Int, onClick: () -> Unit) {
    val tint: Color = if (on) C.Primary else C.Subtext
    Column(
        modifier.clickable { onClick() }.padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            PathIcon(when (icon) { "bag" -> BAG_PATH; "money" -> MONEY_PATH; "chat" -> CHAT_PATH; else -> DOTS_PATH }, tint, 22.dp)
            if (badge > 0) {
                Text(badge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFFEF4444)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.width(96.dp))
        Text(if (v.isEmpty()) "-" else v, fontSize = 13.sp, color = C.Text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 16.sp, color = C.Text, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp))
}

@Composable
private fun CardBox(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onClick() }.padding(14.dp), content = content)
}

@Composable
private fun OrdersTab(store: RiderStore, onPickJob: (Ord) -> Unit, onOpen: (String) -> Unit) {
    val pending: List<Ord> = store.pending.value
    val active: List<Ord> = store.myOrders.value.filter { ACTIVE_STATUSES.contains(it.status) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pending.isNotEmpty()) {
            item { Text("🔔 งานใหม่", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
            items(pending) { o: Ord ->
                CardBox({ onPickJob(o) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(o.orderId, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text("งานใหม่!", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF4444)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(o.s("shopName").ifEmpty { "-" }, fontSize = 14.sp, color = C.Text)
                    Text(o.cust("address").ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(money(o.n("grandTotal")), fontSize = 13.sp, color = C.Text)
                        Text("รายได้ " + money(o.n("riderEarn")), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                    }
                }
            }
        }
        if (pending.isEmpty() && active.isEmpty()) {
            item { Text("ยังไม่มีออเดอร์", color = C.Subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
        }
        items(active) { o: Ord ->
            CardBox({ onOpen(o.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(o.orderId, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                    Text(fmtDate(o.created) + " " + fmtTime(o.created), fontSize = 12.sp, color = C.Subtext)
                }
                Spacer(Modifier.height(4.dp))
                Text(o.s("shopName").ifEmpty { "-" }, fontSize = 14.sp, color = C.Text)
                Text(o.cust("address").take(32).ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(o.qty().toString() + " รายการ · " + (STATUS_TH[o.status] ?: ""), fontSize = 12.sp, color = C.Subtext)
                    Text("รายได้ " + money(o.n("riderEarn")), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(store: RiderStore, onOpen: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var y by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var m by remember { mutableStateOf(now.get(Calendar.MONTH)) }
    var d by remember { mutableStateOf(now.get(Calendar.DAY_OF_MONTH)) }
    val dayOrders: List<Ord> = store.myOrders.value.filter { sameDay(it.created, y, m, d) && (it.status == "done" || it.status == "rejected") }
    val done: List<Ord> = dayOrders.filter { it.status == "done" }
    val cancelled: Int = dayOrders.size - done.size
    var gross = 0.0
    var net = 0.0
    for (o in done) { gross += o.n("grandTotal"); net += o.n("riderEarn") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val dlg = DatePickerDialog(ctx, DatePickerDialog.OnDateSetListener { _, yy, mm, dd -> y = yy; m = mm; d = dd }, y, m, d)
                        dlg.datePicker.maxDate = System.currentTimeMillis()
                        dlg.show()
                    },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("เลือกวันที่", fontSize = 12.sp, color = C.Subtext)
                        Text(d.toString() + " " + TH_MONTHS[m] + " " + (y + 543), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                    Text("📅", fontSize = 24.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("รายได้รวม", money(gross), C.Text, Modifier.weight(1f))
                    Stat("รายได้สุทธิ", money(net), Color(0xFF16A34A), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("สำเร็จ", done.size.toString(), Color(0xFF16A34A), Modifier.weight(1f))
                    Stat("ยกเลิก", cancelled.toString(), Color(0xFFEF4444), Modifier.weight(1f))
                }
            }
        }
        if (dayOrders.isEmpty()) {
            item { Text("ไม่มีประวัติวันนี้", color = C.Subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
        }
        items(dayOrders) { o: Ord ->
            val isDone: Boolean = o.status == "done"
            CardBox({ onOpen(o.id, true) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(o.orderId, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text(fmtTime(o.created), fontSize = 12.sp, color = C.Subtext)
                    }
                    Text(if (isDone) "+" + money(o.n("riderEarn")) else "ยกเลิก", fontWeight = FontWeight.ExtraBold,
                        color = if (isDone) Color(0xFF16A34A) else Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.Gray).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = C.Subtext)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun ChatListTab(store: RiderStore, onOpenChat: (String) -> Unit) {
    val metas = remember { mutableStateMapOf<String, Map<String, Any>>() }
    val orders: List<Ord> = store.myOrders.value.take(30)
    LaunchedEffect(orders.size) {
        for (o in orders) {
            try {
                val s: DocumentSnapshot = FirebaseFirestore.getInstance().collection("chats").document(o.id).get().await()
                val md: Map<String, Any>? = s.data
                if (md != null) metas[o.id] = md
            } catch (e: Exception) { }
        }
    }
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ยังไม่มีข้อความ", color = C.Subtext, fontSize = 14.sp) }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(orders) { o: Ord ->
                val meta: Map<String, Any> = metas[o.id] ?: emptyMap()
                val unread: Int = (meta["unread_rider"] as? Number)?.toInt() ?: 0
                val last: String = meta["lastMessage"]?.toString() ?: "แตะเพื่อเปิดแชท"
                Row(Modifier.fillMaxWidth().background(Color.White).clickable { onOpenChat(o.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(C.PrimaryLight), contentAlignment = Alignment.Center) {
                        PathIcon(CHAT_PATH, C.Primary, 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(o.orderId, fontWeight = FontWeight.ExtraBold, color = C.Text, fontSize = 14.sp)
                            Text(STATUS_TH[o.status] ?: "", fontSize = 11.sp, color = C.Subtext)
                        }
                        Text(last, fontSize = 12.sp, maxLines = 1, color = if (unread > 0) C.Text else C.Subtext,
                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color(0xFFEF4444)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                HorizontalDivider(color = Color(0xFFF5F6F8))
            }
        }
    }
}
