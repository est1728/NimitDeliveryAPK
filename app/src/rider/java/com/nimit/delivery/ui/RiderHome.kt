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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.nimit.delivery.data.riderErrorText
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
private const val MAIL_PATH = "M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z"
private const val PIN_PATH = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"
private const val BELL_PATH = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
private const val CHECK_PATH = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"
private const val CAL_PATH = "M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"
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
                PathIcon(BELL_PATH, Color.White, 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(banner, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    val j: Ord? = job
    if (j != null) {
        JobSheet(j, accepting, { job = null }) {
            accepting = true
            scope.launch {
                try {
                    RiderApi.acceptJob(j.id, store.riderId, riderName, riderPhone)
                    store.banner.value = "รับงานแล้ว " + j.orderId
                } catch (e: Exception) {
                    Toast.makeText(ctx, riderErrorText(e), Toast.LENGTH_SHORT).show()
                }
                accepting = false
                job = null
            }
        }
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
private fun PingDot() {
    val p = rememberInfiniteTransition(label = "ping").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))), label = "pingP"
    )
    Box(
        Modifier.size(8.dp)
            .graphicsLayer { val k = (p.value / 0.75f).coerceAtMost(1f); scaleX = 1f + k; scaleY = 1f + k; alpha = 1f - k }
            .clip(CircleShape).background(Color(0xFF4ADE80))
    )
}

@Composable
private fun NewJobSection(jobs: List<Ord>, onPick: (Ord) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0C4AA6), Color(0xFF083570)))).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
        ) {
            PingDot()
            Text("งานใหม่", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 1.sp)
            PingDot()
        }
        jobs.forEach { j -> NewJobCard(j) { onPick(j) } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF0C4AA6)))
    }
}

@Composable
private fun NewJobCard(o: Ord, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "badge").animateFloat(1f, 0.6f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "badgeA")
    Column(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)
            .shadow(3.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (o.orderId.isNotEmpty()) o.orderId else o.id, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
            Text("งานใหม่!", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = pulse.value }.clip(RoundedCornerShape(20.dp)).background(C.Primary).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(o.s("shopName").ifEmpty { "-" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Spacer(Modifier.height(3.dp))
        Text(o.cust("address").ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE8EEFF)))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(money(o.n("grandTotal")), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
            Text("รายได้ " + money(o.n("riderEarn")), fontSize = 12.sp, color = C.Subtext)
        }
    }
}

@Composable
private fun JobRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = C.Subtext)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
private fun JobSheet(j: Ord, accepting: Boolean, onDismiss: () -> Unit, onAccept: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0x80000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("งานใหม่!", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(if (j.orderId.isNotEmpty()) j.orderId else j.id, fontSize = 13.sp, color = C.Subtext)
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5F6F8)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JobRow("ร้านค้า", j.s("shopName").ifEmpty { "-" }, C.Text)
                    JobRow("ลูกค้า", if (j.cust("name").isNotEmpty()) j.cust("name") else j.cust("phone").ifEmpty { "-" }, C.Text)
                    JobRow("ที่อยู่", j.cust("address").ifEmpty { "-" }, C.Text)
                    JobRow("รายได้ไรเดอร์", money(j.n("riderEarn")), Color(0xFF16A34A))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) { Text("ไม่รับ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Subtext) }
                    Row(
                        Modifier.weight(2f).height(52.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x4D16A34A), spotColor = Color(0x4D16A34A))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF15803D))))
                            .clickable(enabled = !accepting) { onAccept() },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (accepting) {
                            Text("กำลังรับ...", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        } else {
                            PathIcon(CHECK_PATH, Color.White, 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("รับงาน", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(path: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { PathIcon(path, C.Subtext, 15.dp) }
        Text(text, fontSize = 13.sp, color = C.Text)
    }
}

@Composable
private fun OrderCard(o: Ord, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x120F172A), spotColor = Color(0x120F172A))
            .clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onClick() }
    ) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFE8F0FE), Color(0xFFF0F6FF)))).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(o.orderId, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
            Text(fmtTime(o.created), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MetaRow(MAIL_PATH, o.s("shopName").ifEmpty { "-" })
            MetaRow(PIN_PATH, o.cust("address").take(32).ifEmpty { "-" })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F3F6)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(o.qty().toString() + " รายการ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext)
            Text("รายได้ " + money(o.n("riderEarn")), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 14.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun DateDivider(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFFE8EDF2)).padding(horizontal = 16.dp, vertical = 7.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun EmptyOrders() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            PathIcon(BAG_PATH, Color(0xFFCBD5E1), 34.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text("ยังไม่มีออเดอร์", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Spacer(Modifier.height(4.dp))
        Text("รอออเดอร์ที่แอดมินโยนให้", fontSize = 13.sp, color = C.Subtext)
    }
}

@Composable
private fun OrdersTab(store: RiderStore, onPickJob: (Ord) -> Unit, onOpen: (String) -> Unit) {
    val pending: List<Ord> = store.pending.value
    val active: List<Ord> = store.myOrders.value.filter { ACTIVE_STATUSES.contains(it.status) }.sortedByDescending { it.created?.time ?: 0L }
    val rows = ArrayList<Pair<String?, Ord>>()
    var last = ""
    for (o in active) {
        val ds: String = fmtDate(o.created)
        val show: Boolean = ds.isNotEmpty() && ds != last
        if (show) last = ds
        rows.add(Pair(if (show) ds else null, o))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (pending.isNotEmpty()) item { NewJobSection(pending, onPickJob) }
        if (active.isEmpty()) item { EmptyOrders() }
        items(rows) { r: Pair<String?, Ord> ->
            Column(Modifier.fillMaxWidth()) {
                val d: String? = r.first
                if (d != null) DateDivider(d)
                OrderCard(r.second) { onOpen(r.second.id) }
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
                    PathIcon(CAL_PATH, C.Primary, 24.dp)
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
