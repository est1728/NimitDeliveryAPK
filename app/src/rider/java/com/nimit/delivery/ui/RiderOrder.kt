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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.NEXT_LABEL
import com.nimit.delivery.data.NEXT_STATUS
import com.nimit.delivery.data.Ord
import com.nimit.delivery.data.RiderApi
import com.nimit.delivery.data.RiderStore
import com.nimit.delivery.data.fmtDateTime
import com.nimit.delivery.data.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Sec(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun IRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.width(100.dp))
        Text(if (value.isEmpty()) "-" else value, fontSize = 13.sp, color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActBtn(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).clickable { onClick() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
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

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar(o?.orderId ?: "ออเดอร์", onBack)
        if (o == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ไม่พบออเดอร์", color = C.Subtext) }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Sec("รายละเอียด") {
                    IRow("รหัสคำสั่งซื้อ", o.orderId)
                    IRow("จำนวน", o.qty().toString() + " รายการ")
                    IRow("จากร้าน", o.s("shopName"))
                    IRow("ชำระเงิน", if (o.s("paymentMethod") == "cash") "เงินสด" else "โอนเงิน")
                    IRow("เวลา", fmtDateTime(o.created))
                }
                Sec("ข้อมูลร้านค้า") {
                    IRow("ชื่อ", o.s("shopName"))
                    IRow("โทร", o.s("shopPhone"))
                    IRow("ที่อยู่", o.s("shopAddress"))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActBtn("โทร", C.Primary, Modifier.weight(1f)) { dial(o.s("shopPhone")) }
                        ActBtn("นำทาง", C.Primary, Modifier.weight(1f)) { navTo(o.n("shopLat").takeIf { it != 0.0 }, o.n("shopLng").takeIf { it != 0.0 }) }
                    }
                }
                if (fromHistory) {
                    Sec("ข้อมูลลูกค้า") { Text("ข้อมูลลูกค้าถูกซ่อนหลังจบงาน", fontSize = 13.sp, color = C.Subtext) }
                } else {
                    Sec("ข้อมูลลูกค้า") {
                        IRow("ชื่อ", o.cust("name"))
                        IRow("โทร", o.cust("phone"))
                        IRow("ที่อยู่", o.cust("address"))
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActBtn("โทร", C.Primary, Modifier.weight(1f)) { dial(o.cust("phone")) }
                            ActBtn("นำทาง", C.Primary, Modifier.weight(1f)) { navTo(o.custNum("lat"), o.custNum("lng")) }
                            ActBtn(if (chatUnread > 0) "แชท (" + chatUnread + ")" else "แชท", Color(0xFF16A34A), Modifier.weight(1f)) { onChat() }
                        }
                    }
                }
                Sec("สรุปคำสั่งซื้อ") {
                    for (i in o.items()) {
                        val qty: Int = (i["qty"] as? Number)?.toInt() ?: 0
                        val price: Double = (i["price"] as? Number)?.toDouble() ?: 0.0
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(i["name"]?.toString() ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Text)
                                val cat: String = i["category"]?.toString() ?: ""
                                if (cat.isNotEmpty()) Text("หมวด: " + cat, fontSize = 11.sp, color = C.Subtext)
                                for (op in com.nimit.delivery.data.asList(i["options"])) {
                                    val om: Map<String, Any?> = com.nimit.delivery.data.asMap(op)
                                    val nm: String = if (op is String) op else (om["name"]?.toString() ?: "")
                                    val pr: Double = (om["price"] as? Number)?.toDouble() ?: 0.0
                                    Text("- " + nm + (if (pr > 0.0) " +" + money(pr) else ""), fontSize = 12.sp, color = C.Subtext)
                                }
                                val note: String = i["note"]?.toString() ?: ""
                                if (note.isNotEmpty()) Text("📝 " + note, fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                            }
                            Text("x" + qty, fontSize = 13.sp, color = C.Text, modifier = Modifier.padding(horizontal = 10.dp))
                            Text(money(price * qty), fontSize = 13.sp, color = C.Text, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalLine()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("รวม", fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text(money(o.n("subtotal")), fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                }
                Sec("สรุปราคา") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("เรียกเก็บลูกค้า", fontSize = 13.sp, color = C.Subtext); Text(money(o.n("grandTotal")), fontSize = 13.sp, color = C.Text) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("จ่ายร้าน", fontSize = 13.sp, color = C.Subtext); Text(money(o.n("baseCost")), fontSize = 13.sp, color = C.Text) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("หักกระเป๋า", fontSize = 13.sp, color = C.Subtext); Text(money(o.n("walletCut")), fontSize = 13.sp, color = C.Text) }
                    HorizontalLine()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("รายได้รอบนี้", fontWeight = FontWeight.ExtraBold, color = C.Text); Text(money(o.n("riderEarn")), fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A)) }
                }
            }
            if (!fromHistory) {
                val isDone: Boolean = o.status == "done"
                Box(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
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
                                RiderApi.setStatus(cur.id, next)
                                scope.launch { RiderApi.notifyCustomer(cur, next) }
                                if (next == "done") RiderApi.finishWallet(cur, store.riderId, riderName)
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
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
