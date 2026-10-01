package com.nimit.delivery.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import java.text.SimpleDateFormat
import java.util.Locale

private val TP = Color(0xFF0C4AA6)
private val TPD = Color(0xFF083570)
private val TGRAY = Color(0xFFF5F6F8)
private val TGREEN = Color(0xFF16A34A)

private const val CHECK_C = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"
private val STEPS = listOf(
    Triple("pending", "รอรับ", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"),
    Triple("accepted", "รับแล้ว", CHECK_C),
    Triple("picking", "ไปร้าน", "M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9l1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z"),
    Triple("arrived", "ถึงร้าน", "M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z"),
    Triple("delivering", "กำลังส่ง", "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5S10.62 6.5 12 6.5s2.5 1.12 2.5 2.5S13.38 11.5 12 11.5z"),
    Triple("done", "เสร็จสิ้น", CHECK_C)
)
private val STATUS_LABEL = mapOf(
    "pending" to "รอรับออเดอร์", "accepted" to "รับงานแล้ว", "picking" to "ไรเดอร์กำลังไปร้าน", "arrived" to "ถึงร้านแล้ว",
    "delivering" to "ไรเดอร์กำลังส่งมาหาคุณ", "done" to "ส่งสำเร็จ", "rejected" to "ถูกปฏิเสธ"
)

@Composable
fun TrackScreen(session: Session, orderId: String, onBack: () -> Unit, onChat: (String) -> Unit) {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf<Doc?>(null) }
    var missing by remember { mutableStateOf(false) }

    DisposableEffect(orderId) {
        val reg = Firebase.firestore.collection("orders").document(orderId).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            if (snap != null && snap.exists()) { order = snap.data; missing = false } else if (snap != null) missing = true
        }
        onDispose { reg.remove() }
    }

    val o = order
    val status = o?.str("status").orEmpty()
    val curIdx = STEPS.indexOfFirst { it.first == status }

    Column(Modifier.fillMaxSize().background(TGRAY).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(listOf(TP, TPD))).statusBarsPadding().padding(20.dp)) {
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(o?.str("orderId") ?: "NM-", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
                    Text(o?.str("shopName")?.ifEmpty { "-" } ?: if (missing) "ไม่พบออเดอร์นี้" else "ชื่อร้าน", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Text(STATUS_LABEL[status] ?: status.ifEmpty { "รอรับออเดอร์" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                STEPS.forEachIndexed { i, (_, label, icon) ->
                    val done = i < curIdx; val active = i == curIdx
                    if (i > 0) Box(Modifier.weight(1f).padding(top = 13.dp).height(2.dp).background(if (done) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.2f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (done) Color(0xFF4ADE80) else if (active) Color.White else Color.White.copy(alpha = 0.2f))
                                .border(2.dp, if (done) Color(0xFF4ADE80) else if (active) Color.White else Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center
                        ) { PathIcon(icon, if (done) Color.White else if (active) TP else Color.White.copy(alpha = 0.4f), 13.dp) }
                        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else if (done) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, lineHeight = 11.sp)
                    }
                }
            }
        }

        if (o == null) {
            Text(if (missing) "ไม่พบออเดอร์นี้" else "กำลังโหลด...", fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
            return@Column
        }

        Column(Modifier.padding(16.dp).padding(bottom = 14.dp)) {
            if (status == "done") Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp)).background(TGREEN).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PathIcon(CHECK_C, Color.White, 32.dp)
                Text("ส่งออเดอร์เสร็จสิ้น", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                (o["createdAt"] as? Timestamp)?.toDate()?.let { Text("ส่งถึงแล้วเมื่อ " + SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH")).format(it), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)) }
            }

            if (o.str("riderName").isNotEmpty() && status != "pending") InfoCard("ไรเดอร์ของคุณ") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE8F0FE)), contentAlignment = Alignment.Center) { PathIcon("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z", TP, 24.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text(o.str("riderName"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text); Text(o.str("riderPhone").ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionBtn("โทร", Color(0xFFE8F0FE), TP, Modifier.weight(1f)) { if (o.str("riderPhone").isNotEmpty()) try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + o.str("riderPhone")))) } catch (_: Exception) {} }
                    ActionBtn("แชท", Color(0xFFDCFCE7), TGREEN, Modifier.weight(1f)) { onChat(orderId) }
                }
            }

            InfoCard("รายการสินค้า") {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("สินค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.weight(1f))
                    Text("จำนวน", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    Text("ราคา", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End)
                }
                asMapList(o["items"]).forEach { it ->
                    val opts = asMapList(it["options"]).map { op -> op.str("name") }.filter { n -> n.isNotEmpty() }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(it.str("name"), fontSize = 13.sp, color = C.Text)
                            if (opts.isNotEmpty()) Text(opts.joinToString(", "), fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text("x${(num(it["qty"]) ?: 1.0).toInt()}", fontSize = 13.sp, color = C.Text)
                        Text("฿${fmtNum((num(it["price"]) ?: 0.0) * (num(it["qty"]) ?: 1.0))}", fontSize = 13.sp, color = C.Text, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).drawBehind { drawLine(TGRAY, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ยอดรวม", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TP)
                    Text("฿${fmtNum(num(o["grandTotal"]) ?: 0.0)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TP)
                }
            }

            val cus = asMap(o["customer"]) ?: emptyMap()
            InfoCard("ข้อมูลจัดส่ง") {
                InfoRow("ชื่อ", cus.str("name").ifEmpty { "-" })
                InfoRow("โทร", cus.str("phone").ifEmpty { "-" })
                InfoRow("ที่อยู่", cus.str("address").ifEmpty { "-" })
                InfoRow("ชำระเงิน", if (o.str("paymentMethod") == "cash") "เงินสด" else "โอนเงิน")
                InfoRow("ค่าส่ง", if ((num(o["deliveryFee"]) ?: 0.0) > 0) "฿${fmtNum(num(o["deliveryFee"]) ?: 0.0)}" else "ฟรี", last = true)
            }
        }
    }
}

@Composable private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 14.dp).fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TP, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable private fun ActionBtn(label: String, bg: Color, fg: Color, m: Modifier, onClick: () -> Unit) {
    Box(m.height(42.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg) }
}

@Composable private fun InfoRow(l: String, v: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().then(if (last) Modifier else Modifier.drawBehind { drawLine(TGRAY, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, fontSize = 14.sp, color = C.Subtext)
        Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}
