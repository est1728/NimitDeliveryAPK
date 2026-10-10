package com.nimit.delivery.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.NEXT_LABEL
import com.nimit.delivery.data.NEXT_STATUS
import com.nimit.delivery.data.Ord
import com.nimit.delivery.data.RiderApi
import com.nimit.delivery.data.RiderStore
import com.nimit.delivery.data.fmtDateTime
import com.nimit.delivery.data.money
import com.nimit.delivery.data.riderErrorText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OD_BACK = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
private const val OD_INFO = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"
private const val OD_STORE = "M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z"
private const val OD_USER = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
private const val OD_LIST = "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"
private const val OD_MONEY = "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"
private const val OD_PHONE = "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"
private const val OD_NAV = "M21.71 11.29l-9-9c-.39-.39-1.02-.39-1.41 0l-9 9c-.39.39-.39 1.02 0 1.41l9 9c.39.39 1.02.39 1.41 0l9-9c.39-.38.39-1.01 0-1.41zM14 14.5V12h-4v3H8v-4c0-.55.45-1 1-1h5V7.5l3.5 3.5-3.5 3.5z"
private const val OD_CHAT = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"

@Composable
private fun OdTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0C4AA6), Color(0xFF083570)))).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { PathIcon(OD_BACK, Color.White, 22.dp) }
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun Sec(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x120F172A), spotColor = Color(0x120F172A))
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFEAF1FC)).padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PathIcon(icon, C.Primary, 18.dp)
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { content() }
    }
}

@Composable
private fun IRow(label: String, value: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, fontSize = 15.sp, color = C.Subtext, modifier = Modifier.width(112.dp))
        Text(if (value.isEmpty()) "-" else value, fontSize = 15.sp, color = C.Text, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F4F9)))
}

@Composable
private fun PRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = C.Subtext, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = C.Text)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F4F9)))
}

@Composable
private fun ActBtn(label: String, icon: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).clickable { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        PathIcon(icon, color, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun RiderOrderScreen(store: RiderStore, orderId: String, fromHistory: Boolean, onBack: () -> Unit, onChat: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val o: Ord? = store.myOrders.value.firstOrNull { it.id == orderId }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val riderName: String = store.rider.value["name"]?.toString() ?: ""
    val chatUnread: Int = store.unread[orderId] ?: 0

    LaunchedEffect(orderId) {
        RiderApi.markRead(orderId)
        while (true) {
            RiderApi.setViewing(orderId, true)
            delay(10000L)
        }
    }
    DisposableEffect(orderId) { onDispose { RiderApi.setViewing(orderId, false) } }

    fun dial(phone: String) {
        if (phone.isNotEmpty()) ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)))
    }
    fun navTo(lat: Double?, lng: Double?) {
        if (lat != null && lng != null) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng)))
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFE3EAF5))) {
        OdTopBar(o?.orderId ?: "ออเดอร์", onBack)
        if (o == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ไม่พบออเดอร์", fontSize = 15.sp, color = C.Subtext) }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Sec("รายละเอียด", OD_INFO) {
                    IRow("รหัสคำสั่งซื้อ", o.orderId, false)
                    IRow("จำนวน", o.qty().toString() + " รายการ", false)
                    IRow("จากร้าน", o.s("shopName"), false)
                    IRow("ชำระเงิน", if (o.s("paymentMethod") == "cash") "เงินสด" else "โอนเงิน", false)
                    IRow("เวลา", fmtDateTime(o.created), true)
                }
                Sec("ข้อมูลร้านค้า", OD_STORE) {
                    IRow("ชื่อ", o.s("shopName"), false)
                    IRow("โทร", o.s("shopPhone"), false)
                    IRow("ที่อยู่", o.s("shopAddress"), true)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActBtn("โทร", OD_PHONE, C.Primary, Modifier.weight(1f)) { dial(o.s("shopPhone")) }
                        ActBtn("นำทาง", OD_NAV, C.Primary, Modifier.weight(1f)) { navTo(o.n("shopLat").takeIf { it != 0.0 }, o.n("shopLng").takeIf { it != 0.0 }) }
                    }
                }
                if (fromHistory) {
                    Sec("ข้อมูลลูกค้า", OD_USER) { Text("ข้อมูลลูกค้าถูกซ่อนหลังจบงาน", fontSize = 15.sp, color = C.Subtext, modifier = Modifier.padding(vertical = 12.dp)) }
                } else {
                    Sec("ข้อมูลลูกค้า", OD_USER) {
                        IRow("ชื่อ", o.cust("name"), false)
                        IRow("โทร", o.cust("phone"), false)
                        IRow("ที่อยู่", o.cust("address"), true)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActBtn("โทร", OD_PHONE, C.Primary, Modifier.weight(1f)) { dial(o.cust("phone")) }
                            ActBtn("นำทาง", OD_NAV, C.Primary, Modifier.weight(1f)) { navTo(o.custNum("lat"), o.custNum("lng")) }
                            ActBtn(if (chatUnread > 0) "แชท (" + chatUnread + ")" else "แชท", OD_CHAT, Color(0xFF16A34A), Modifier.weight(1f)) { onChat() }
                        }
                    }
                }
                Sec("สรุปคำสั่งซื้อ", OD_LIST) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                        Text("สินค้า", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.weight(1f))
                        Text("จำนวน", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                        Text("ราคา", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
                    }
                    for (i in o.items()) {
                        val qty: Int = (i["qty"] as? Number)?.toInt() ?: 0
                        val price: Double = (i["price"] as? Number)?.toDouble() ?: 0.0
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(i["name"]?.toString() ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp, color = C.Text)
                                val cat: String = i["category"]?.toString() ?: ""
                                if (cat.isNotEmpty()) Text("หมวด: " + cat, fontSize = 12.sp, lineHeight = 18.sp, color = C.Subtext)
                                for (op in com.nimit.delivery.data.asList(i["options"])) {
                                    val om: Map<String, Any?> = com.nimit.delivery.data.asMap(op)
                                    val nm: String = if (op is String) op else (om["name"]?.toString() ?: "")
                                    val pr: Double = (om["price"] as? Number)?.toDouble() ?: 0.0
                                    Text("- " + nm + (if (pr > 0.0) " +" + money(pr) else ""), fontSize = 13.sp, lineHeight = 20.sp, color = C.Subtext)
                                }
                                val note: String = i["note"]?.toString() ?: ""
                                if (note.isNotEmpty()) Text("หมายเหตุ: " + note, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                            }
                            Text("x" + qty, fontSize = 14.sp, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                            Text(money(price * qty), fontSize = 15.sp, color = C.Text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F4F9)))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("รวม", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text(money(o.n("subtotal")), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                }
                Sec("สรุปราคา", OD_MONEY) {
                    PRow("เรียกเก็บลูกค้า", money(o.n("grandTotal")))
                    PRow("จ่ายร้าน (ต้นทุน)", money(o.n("baseCost")))
                    PRow("หักจากกระเป๋า", money(o.n("walletCut")))
                    PRow("ค่าส่ง", money(o.n("deliveryFee")))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFFDCFCE7)))
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("รายได้รอบนี้", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A), modifier = Modifier.weight(1f))
                        Text(money(o.n("riderEarn")), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                    }
                }
            }
            if (!fromHistory) {
                val isDone: Boolean = o.status == "done"
                Box(Modifier.fillMaxWidth().background(Color(0xFFEEF3FA)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                    GradientButton(
                        text = if (isDone) "เสร็จสิ้นแล้ว" else (NEXT_LABEL[o.status] ?: "ถัดไป"),
                        enabled = !isDone && !busy
                    ) { confirm = true }
                }
            }
        }
    }

    val cur: Ord? = o
    if (confirm && cur != null) {
        val next: String? = NEXT_STATUS[cur.status]
        AlertDialog(
            onDismissRequest = { if (!busy) confirm = false },
            title = { Text(NEXT_LABEL[cur.status] ?: "ยืนยัน") },
            text = { Text(cur.orderId + " · " + cur.s("shopName")) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (next != null) {
                        busy = true
                        scope.launch {
                            try {
                                if (next == "done") {
                                    if (RiderApi.completeDelivery(cur.id, store.riderId, riderName)) RiderApi.notifyCustomerAsync(cur, next)
                                } else {
                                    RiderApi.advance(cur.id, cur.status, next)
                                    RiderApi.notifyCustomerAsync(cur, next)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(ctx, riderErrorText(e), Toast.LENGTH_SHORT).show()
                            }
                            confirm = false
                            busy = false
                            if (next == "done") {
                                delay(350L)
                                onBack()
                            }
                        }
                    }
                }) { Text(if (busy) "กำลังบันทึก..." else "ยืนยัน") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun HorizontalLine() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
    Spacer(Modifier.height(6.dp))
}
