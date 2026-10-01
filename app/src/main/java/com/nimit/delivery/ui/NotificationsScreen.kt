package com.nimit.delivery.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Calendar

private class NItem(val key: String, val order: Boolean, val title: String, val msg: String, val ms: Long, val docId: String? = null, val status: String = "")

private val MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private val ORDER_MSG = mapOf(
    "pending" to "ส่งออเดอร์เรียบร้อย รอร้านรับออเดอร์",
    "accepted" to "ร้านรับออเดอร์แล้ว กำลังเตรียมอาหารให้คุณ",
    "picking" to "ไรเดอร์กำลังเดินทางไปที่ร้าน",
    "arrived" to "ไรเดอร์ถึงร้านแล้ว กำลังรับอาหาร",
    "delivering" to "ไรเดอร์กำลังนำส่งอาหารมาหาคุณ เตรียมรับได้เลย",
    "done" to "ส่งอาหารสำเร็จ ขอบคุณที่ใช้บริการ Nimit Delivery",
    "rejected" to "ร้านไม่สามารถรับออเดอร์นี้ได้ ขออภัยในความไม่สะดวก",
    "cancelled" to "ออเดอร์นี้ถูกยกเลิก"
)

private fun fmtDate(ms: Long): String {
    if (ms <= 0) return ""
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%d %s %d · %02d:%02d น.".format(c.get(Calendar.DAY_OF_MONTH), MONTHS[c.get(Calendar.MONTH)], c.get(Calendar.YEAR) + 543, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun dayLabel(ms: Long): String {
    if (ms <= 0) return "ก่อนหน้านี้"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val n = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(c, n)) return "วันนี้"
    n.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(c, n)) return "เมื่อวาน"
    return "%d %s %d".format(c.get(Calendar.DAY_OF_MONTH), MONTHS[c.get(Calendar.MONTH)], c.get(Calendar.YEAR) + 543)
}

@Composable
fun NotificationsScreen(session: Session, onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    val prevRead = remember { session.notifLastRead?.toLongOrNull() ?: 0L }
    var items by remember { mutableStateOf<List<NItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    val phone = remember { try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() } }

    LaunchedEffect(Unit) {
        try {
            val db = Firebase.firestore
            val (news, orders) = coroutineScope {
                val n = async { db.collection("notifications").get().await().documents.map { d ->
                    NItem("n" + d.id, false, d.getString("title").orEmpty(), d.getString("message").orEmpty(), (d.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L) } }
                val o = async {
                    if (phone.isEmpty()) emptyList() else try {
                        db.collection("orders").whereEqualTo("customer.phone", phone).get().await().documents.map { d ->
                            val st = d.getString("status").orEmpty()
                            val t = ((d.get("updatedAt") as? Timestamp) ?: (d.get("createdAt") as? Timestamp))?.toDate()?.time ?: 0L
                            NItem("o" + d.id, true, "ออเดอร์ ${d.getString("orderId").orEmpty()} · ${d.getString("shopName").orEmpty()}", ORDER_MSG[st] ?: st, t, d.id, st)
                        }
                    } catch (_: Exception) { emptyList() }
                }
                n.await() to o.await()
            }
            items = (news + orders).sortedByDescending { it.ms }
            // เข้ามาหน้านี้แล้วถือว่าอ่านทั้งหมด (เหมือนเว็บ) — ล้างตัวเลขที่หน้าช้อป
            session.notifLastRead = System.currentTimeMillis().toString()
        } catch (e: Exception) { error = e.message ?: "เกิดข้อผิดพลาด ลองเข้าหน้านี้ใหม่" }
    }

    Column(Modifier.fillMaxSize().background(N.Bg)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(listOf(N.B900, N.B700))).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 17.dp, stroke = true) }
                Spacer(Modifier.width(12.dp))
                Text("การแจ้งเตือน", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        val list = items
        when {
            error != null -> StateBox("โหลดไม่สำเร็จ", error!!)
            list == null -> Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = N.B700, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                Text("กำลังโหลด...", fontSize = 13.sp, color = N.Ink500, modifier = Modifier.padding(top = 14.dp))
            }
            list.isEmpty() -> StateBox("ยังไม่มีการแจ้งเตือน", "เมื่อมีข่าวสาร โปรโมชัน หรือสถานะออเดอร์ใหม่ จะขึ้นที่นี่ครับ")
            else -> {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "ทั้งหมด", "order" to "ออเดอร์", "news" to "ข่าวสาร").forEach { (k, label) ->
                        val on = filter == k
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else N.Ink700,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) N.B700 else Color.White).border(1.dp, if (on) N.B700 else N.Line, RoundedCornerShape(20.dp)).clickable { filter = k }.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                val shown = list.filter { filter == "all" || (filter == "order") == it.order }
                if (shown.isEmpty()) StateBox(if (filter == "order") "ยังไม่มีแจ้งเตือนออเดอร์" else "ยังไม่มีข่าวสาร", "")
                else {
                    val grouped = shown.groupBy { dayLabel(it.ms) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        grouped.forEach { (day, group) ->
                            item(key = "h$day") { Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = N.Ink500, modifier = Modifier.padding(top = 6.dp, start = 2.dp)) }
                            items(group, key = { it.key }) { it ->
                                val unread = it.ms > prevRead
                                val ic = if (it.order) when (it.status) { "done" -> N.Green600; "rejected", "cancelled" -> N.Red500; "pending" -> N.Amber600; else -> N.B600 } else N.B700
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, if (unread) N.B500.copy(alpha = 0.45f) else N.Line, RoundedCornerShape(16.dp))
                                        .then(if (it.order && it.docId != null) Modifier.clickable { onOpenOrder(it.docId) } else Modifier).padding(start = 14.dp, end = 15.dp, top = 14.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(Modifier.size(38.dp).clip(CircleShape).background(ic.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { PathIcon(if (it.order) P.BAG else P.BELL, ic, 20.dp) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(it.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = N.Ink900, modifier = Modifier.weight(1f))
                                            if (unread) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(N.B500))
                                        }
                                        if (it.msg.isNotEmpty()) Text(it.msg, fontSize = 12.5.sp, color = N.Ink500, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                                        if (it.ms > 0) Text(fmtDate(it.ms), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = N.Ink300, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun StateBox(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { PathIcon(P.BELL, N.Ink300, 28.dp) }
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = N.Ink900, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = N.Ink500, textAlign = TextAlign.Center, lineHeight = 20.8.sp)
    }
}
