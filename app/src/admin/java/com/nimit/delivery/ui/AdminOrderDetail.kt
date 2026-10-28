package com.nimit.delivery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ADMIN_STATUS_TH
import com.nimit.delivery.data.AdminOrd
import com.nimit.delivery.data.AdminOrderApi
import com.nimit.delivery.data.AdminOrderEdit
import com.nimit.delivery.data.AdminRider
import com.nimit.delivery.data.adminErrorText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val DGreen = Color(0xFF16A34A)
private val DGreenL = Color(0xFFDCFCE7)
private val DRed = Color(0xFFDC2626)
private val DRedL = Color(0xFFFEE2E2)
private val DLine = Color(0xFFF0F2F5)
private val DRowLine = Color(0xFFF5F6F8)

private val DNext: Map<String, String> = mapOf(
    "pending" to "accepted", "accepted" to "picking", "picking" to "arrived", "arrived" to "delivering", "delivering" to "done"
)
private val DPrev: Map<String, String> = mapOf(
    "accepted" to "pending", "picking" to "accepted", "arrived" to "picking", "delivering" to "arrived"
)
private val DNextLabel: Map<String, String> = mapOf(
    "pending" to "รับงาน", "accepted" to "เริ่มงาน", "picking" to "ถึงร้านแล้ว", "arrived" to "ขับไปหาลูกค้า", "delivering" to "ส่งสำเร็จ"
)

private class DR(val label: String, val value: String, val color: Color = C.Text, val labelColor: Color = C.Subtext, val small: Boolean = false)

private fun dPill(st: String): Pair<Color, Color> = when (st) {
    "pending" -> Pair(Color(0xFFFFF7ED), Color(0xFFEA580C))
    "accepted", "picking" -> Pair(C.PrimaryLight, C.Primary)
    "arrived" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
    "delivering" -> Pair(DGreenL, DGreen)
    "rejected" -> Pair(DRedL, DRed)
    else -> Pair(Color(0xFFF1F5F9), C.Subtext)
}

private fun dToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun dDial(ctx: Context, phone: String) {
    if (phone.isEmpty()) { dToast(ctx, "ไม่มีเบอร์โทร"); return }
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))) } catch (e: Exception) { dToast(ctx, "โทรไม่ได้") }
}

private fun dMap(ctx: Context, lat: Double?, lng: Double?, addr: String) {
    val q: String = if (lat != null && lng != null) lat.toString() + "," + lng.toString() else Uri.encode(addr)
    if (q.isEmpty()) { dToast(ctx, "ไม่มีพิกัด"); return }
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + q))) } catch (e: Exception) { dToast(ctx, "เปิดแผนที่ไม่ได้") }
}

private fun dMoney(n: Double): String = "฿" + Math.round(n)

private fun dNum(m: Map<*, *>?, k: String): Double? = (m?.get(k) as? Number)?.toDouble()

private fun dItems(o: AdminOrd): List<Map<*, *>> = (o.d["items"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

private fun dOpts(m: Map<*, *>): List<String> {
    val l = m["options"] as? List<*> ?: return emptyList()
    return l.mapNotNull { op ->
        when (op) {
            is String -> op
            is Map<*, *> -> {
                val n = op["name"]?.toString() ?: ""
                val p = (op["price"] as? Number)?.toDouble() ?: 0.0
                if (p > 0.0) n + " +฿" + Math.round(p) else n
            }
            else -> null
        }
    }
}

@Composable
fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: () -> Unit, onEditMenu: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<AdminOrd?>(null) }
    var chatUnread by remember { mutableStateOf(0) }
    var riderReadAt by remember { mutableStateOf<Date?>(null) }
    var stats by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirm by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf("") }
    var showAssign by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var proxyText by remember { mutableStateOf("") }

    DisposableEffect(orderId) {
        val db = FirebaseFirestore.getInstance()
        val r1 = db.collection("orders").document(orderId).addSnapshotListener { s, _ ->
            if (s != null && s.exists()) order = AdminOrd(s.id, s.data ?: emptyMap())
        }
        val r2 = db.collection("chats").document(orderId).addSnapshotListener { s, _ ->
            if (s != null && s.exists()) {
                chatUnread = (s.getLong("unread_admin") ?: 0L).toInt()
                riderReadAt = s.getTimestamp("riderReadAt")?.toDate()
            }
        }
        onDispose {
            r1.remove()
            r2.remove()
        }
    }

    val loadedId: String? = order?.id
    LaunchedEffect(loadedId) {
        val oo = order
        if (oo != null) proxyText = Math.round(oo.n("grandTotal")).toString()
    }

    val o: AdminOrd? = order
    val phone: String = o?.cust("phone") ?: ""
    LaunchedEffect(phone) {
        stats = null
        if (phone.isNotEmpty()) stats = AdminOrderApi.customerStats(phone)
    }

    if (o == null) {
        Box(Modifier.fillMaxSize().background(C.Gray), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = C.Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        return
    }

    val st: String = o.status
    val finished: Boolean = st == "done" || st == "rejected"
    val pill = dPill(st)
    val isProxy: Boolean = o.s("shopType") == "proxy"
    val cm = o.d["customer"] as? Map<*, *>

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        // แถบบน (.dtb)
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("#" + o.orderId, fontSize = 22.sp, fontWeight = FontWeight.Black, color = C.Primary, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pill.first).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(ADMIN_STATUS_TH[st] ?: st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pill.second)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(20.dp)).background(DRedL).clickable { showCancel = true }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("ยกเลิก", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DRed) }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // รายละเอียดออเดอร์
            DSection("รายละเอียดออเดอร์", null) {
                val rows = ArrayList<DR>()
                rows.add(DR("รหัส", o.orderId))
                rows.add(DR("UUID", o.id, C.Subtext, C.Subtext, true))
                rows.add(DR("จำนวน", o.qty().toString() + " รายการ"))
                rows.add(DR("จากร้าน", o.s("shopName").ifEmpty { "-" }))
                val pm = o.s("paymentMethod")
                rows.add(DR("การจ่ายเงิน", if (pm.isEmpty() || pm == "cash") "เงินสด" else pm))
                rows.add(DR("เวลา", o.created?.let { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")).format(it) } ?: "-"))
                rows.add(DR("สถานะ", ADMIN_STATUS_TH[st] ?: st))
                if (st == "rejected") {
                    val faultTxt = if (o.s("cancelFault") == "shop") "ไม่หักเครดิตลูกค้า" else "หักเครดิตลูกค้า"
                    rows.add(DR("เหตุยกเลิก", o.s("cancelNote").ifEmpty { "-" } + " (" + faultTxt + ")", if (o.s("cancelFault") == "shop") DGreen else DRed))
                }
                rows.add(DR("ไรเดอร์", o.s("riderName").ifEmpty { "ยังไม่มี" }))
                val rr = riderReadAt
                rows.add(DR("อ่านออเดอร์", if (rr != null) "อ่านแล้ว เวลา " + SimpleDateFormat("HH:mm", Locale("th", "TH")).format(rr) else "ยังไม่อ่าน", if (rr != null) DGreen else C.Subtext))
                DRows(rows, true)
            }

            // ข้อมูลร้านค้า (ชื่อ : ข้อมูล ชิดซ้าย)
            DSectionEdit("ข้อมูลร้านค้า", "แก้ไข", { editKind = "shop" }) {
                val rows = ArrayList<DR>()
                rows.add(DR("ชื่อ", o.s("shopName").ifEmpty { "-" }))
                rows.add(DR("โทร", o.s("shopPhone").ifEmpty { "-" }))
                rows.add(DR("ที่อยู่", o.s("shopAddress").ifEmpty { "-" }, C.Subtext))
                DRows(rows, true)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAct("โทรหาร้าน", Modifier.weight(1f), false) { dDial(ctx, o.s("shopPhone")) }
                    DAct("นำทาง", Modifier.weight(1f), false) {
                        val lat = (o.d["shopLat"] as? Number)?.toDouble()
                        val lng = (o.d["shopLng"] as? Number)?.toDouble()
                        if (lat == null || lng == null) dToast(ctx, "ไม่มีพิกัดร้าน") else dMap(ctx, lat, lng, "")
                    }
                }
            }

            // ข้อมูลลูกค้า (ชื่อ : ข้อมูล ชิดซ้าย)
            DSectionEdit("ข้อมูลลูกค้า", "แก้ไข", { editKind = "cus" }) {
                val rows = ArrayList<DR>()
                rows.add(DR("ชื่อ", o.cust("name").ifEmpty { "-" }))
                rows.add(DR("โทร", o.cust("phone").ifEmpty { "-" }))
                rows.add(DR("ที่จัดส่ง", o.cust("address").ifEmpty { "-" }))
                rows.add(DR("สั่งสำเร็จ", stats?.let { it.first.toString() + " ครั้ง" } ?: "...", DGreen, DGreen))
                rows.add(DR("ยกเลิก", stats?.let { it.second.toString() + " ครั้ง" } ?: "...", DRed, DRed))
                DRows(rows, true)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAct("โทร", Modifier.weight(1f), false) { dDial(ctx, o.cust("phone")) }
                    DAct(if (chatUnread > 0) "แชท (" + chatUnread + ")" else "แชท", Modifier.weight(1f), true) { onChat() }
                    DAct("นำทาง", Modifier.weight(1f), false) { dMap(ctx, dNum(cm, "lat"), dNum(cm, "lng"), o.cust("address")) }
                }
            }

            // สรุปคำสั่งซื้อ
            DSectionEdit("สรุปคำสั่งซื้อ", "แก้ไขออเดอร์", { editKind = "order" }) {
                Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                    Text("สินค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.weight(1f))
                    Text("จำนวน", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.width(48.dp))
                    Text("ราคา/หน่วย", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.width(76.dp))
                    Text("รวม", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                }
                HorizontalDivider(thickness = 2.dp, color = C.Gray)
                val items = dItems(o)
                for (idx in items.indices) {
                    val it = items[idx]
                    val qty = (it["qty"] as? Number)?.toInt() ?: 0
                    val unit = dNum(it, "price") ?: dNum(it, "basePrice") ?: 0.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f).padding(end = 6.dp)) {
                            Text(it["name"]?.toString() ?: "-", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
                            val cat = it["category"]?.toString() ?: ""
                            if (cat.isNotEmpty()) Text("หมวด: " + cat, fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 1.dp))
                            for (op in dOpts(it)) Text("- " + op, fontSize = 12.sp, color = C.Subtext)
                            val note = it["note"]?.toString() ?: ""
                            if (note.isNotEmpty()) Text("หมายเหตุ: " + note, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DRed, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text(qty.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.width(48.dp))
                        Text(dMoney(unit), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.width(76.dp))
                        Text(dMoney(unit * qty), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Text, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                    }
                    if (idx < items.size - 1) HorizontalDivider(color = DRowLine)
                }
            }

            // สรุปราคา
            DSection("สรุปราคา", null) {
                DPriceRow("เรียกเก็บลูกค้า", dMoney(o.n("grandTotal")), false)
                DPriceRow("จ่ายร้าน (ต้นทุน)", dMoney(o.n("baseCost")), false)
                DPriceRow("หักจากกระเป๋า", dMoney(o.n("walletCut")), false)
                DPriceRow("ค่าส่ง", dMoney(o.n("deliveryFee")), false)
                HorizontalDivider(thickness = 2.dp, color = DGreenL, modifier = Modifier.padding(top = 4.dp))
                DPriceRow("รายได้รอบนี้", dMoney(o.n("riderEarn")), true)
            }

            // แก้ไขราคา (เฉพาะร้านฝากซื้อและยังไม่จบงาน)
            if (isProxy && !finished) {
                DSection("แก้ไขราคา (ร้านฝากซื้อ)", null) {
                    Text(
                        "ร้านฝากซื้อ: ราคาที่ตั้งใหม่ = ยอดเรียกเก็บลูกค้าทั้งหมด ไม่มีค่าส่งแยก",
                        fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = proxyText, onValueChange = { proxyText = it.filter { c -> c.isDigit() } },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (busy) Color(0xFFC5CFE8) else C.Primary)
                                .clickable(enabled = !busy) {
                                    val v = proxyText.toLongOrNull()
                                    if (v == null || v <= 0L) {
                                        dToast(ctx, "กรุณาใส่ราคาที่ถูกต้อง")
                                    } else {
                                        busy = true
                                        scope.launch {
                                            try {
                                                AdminOrderEdit.proxyPrice(orderId, v)
                                                dToast(ctx, "บันทึกราคาใหม่แล้ว ฿" + v)
                                            } catch (e: Exception) {
                                                dToast(ctx, adminErrorText(e))
                                            }
                                            busy = false
                                        }
                                    }
                                }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("บันทึกราคาใหม่", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                    val pe = o.date("priceEditedAt")
                    Text(
                        "ราคาปัจจุบัน ฿" + Math.round(o.n("grandTotal")) + " · แก้ไขล่าสุด: " +
                            (if (pe != null) SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")).format(pe) else "ยังไม่เคยแก้ไข"),
                        fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // แถบปุ่มล่าง
        HorizontalDivider(color = DLine)
        Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (DPrev.containsKey(st)) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Gray)
                        .clickable(enabled = !busy) { confirm = "prev" },
                    contentAlignment = Alignment.Center
                ) { Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(20.dp)) }
            }
            val nextOn = !finished && !busy
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (!nextOn) Color(0xFFC5CFE8) else if (st == "delivering") DGreen else C.Primary)
                    .clickable(enabled = nextOn) { confirm = "next" },
                contentAlignment = Alignment.Center
            ) { Text(if (finished) "เสร็จสิ้น" else (DNextLabel[st] ?: "-"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            if (!finished) {
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(DGreenL)
                        .clickable(enabled = !busy) { showAssign = true }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) { Text("โยนงาน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DGreen) }
            }
        }
    }

    // ยืนยันเปลี่ยน/ย้อนสถานะ
    if (confirm.isNotEmpty()) {
        val isNext = confirm == "next"
        val target: String = (if (isNext) DNext[st] else DPrev[st]) ?: ""
        val label: String = if (isNext) (DNextLabel[st] ?: "-") else "ย้อนกลับ"
        AlertDialog(
            onDismissRequest = { confirm = "" },
            title = { Text(if (isNext) "เปลี่ยนสถานะ" else "ย้อนกลับสถานะ") },
            text = { Text(if (isNext) label + " ยืนยันหรือไม่" else "ย้อนกลับเป็น \"" + (ADMIN_STATUS_TH[target] ?: target) + "\" ยืนยันหรือไม่") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = ""
                    if (target.isNotEmpty()) {
                        busy = true
                        scope.launch {
                            try {
                                if (isNext && target == "done") {
                                    AdminOrderApi.completeDelivery(orderId, o.s("riderId"), o.s("riderName"))
                                    dToast(ctx, "ออเดอร์เสร็จสิ้น")
                                    onBack()
                                } else {
                                    AdminOrderApi.advance(orderId, st, target)
                                }
                            } catch (e: Exception) {
                                dToast(ctx, adminErrorText(e))
                            }
                            busy = false
                        }
                    }
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { confirm = "" }) { Text("ยกเลิก") } }
        )
    }

    if (showAssign) {
        AdminAssignDialog(
            orderLabel = "ออเดอร์: #" + o.orderId + " · " + o.s("shopName"),
            currentRiderId = o.s("riderId"),
            onDismiss = { showAssign = false },
            onConfirm = { r: AdminRider? ->
                showAssign = false
                busy = true
                scope.launch {
                    try {
                        if (r == null) {
                            AdminOrderApi.unassign(orderId)
                            dToast(ctx, "ถอดไรเดอร์แล้ว")
                        } else {
                            AdminOrderApi.assign(orderId, r)
                            dToast(ctx, "โยนงานให้ " + r.name + " แล้ว")
                        }
                    } catch (e: Exception) {
                        dToast(ctx, adminErrorText(e))
                    }
                    busy = false
                }
            }
        )
    }

    // หน้าต่างแก้ไข: ร้าน / ลูกค้า / ออเดอร์
    if (editKind == "shop") {
        DEditDialog(
            "แก้ไขข้อมูลร้านค้า", listOf("ชื่อร้าน", "เบอร์โทร", "ที่อยู่"),
            listOf(o.s("shopName"), o.s("shopPhone"), o.s("shopAddress")), listOf(KeyboardType.Text, KeyboardType.Phone, KeyboardType.Text),
            onDismiss = { editKind = "" },
            onSave = { v ->
                editKind = ""
                scope.launch {
                    try {
                        AdminOrderEdit.shopInfo(orderId, v[0].trim(), v[1].trim(), v[2].trim())
                        dToast(ctx, "บันทึกสำเร็จ")
                    } catch (e: Exception) { dToast(ctx, "บันทึกไม่สำเร็จ") }
                }
            }
        )
    }
    if (editKind == "cus") {
        DEditDialog(
            "แก้ไขข้อมูลลูกค้า", listOf("ชื่อ", "เบอร์โทร", "ที่อยู่จัดส่ง"),
            listOf(o.cust("name"), o.cust("phone"), o.cust("address")), listOf(KeyboardType.Text, KeyboardType.Phone, KeyboardType.Text),
            onDismiss = { editKind = "" },
            onSave = { v ->
                editKind = ""
                scope.launch {
                    try {
                        AdminOrderEdit.customer(orderId, v[0].trim(), v[1].trim(), v[2].trim())
                        dToast(ctx, "บันทึกข้อมูลลูกค้าสำเร็จ")
                    } catch (e: Exception) { dToast(ctx, "บันทึกไม่สำเร็จ") }
                }
            }
        )
    }
    if (editKind == "order") {
        DEditDialog(
            "แก้ไขออเดอร์", listOf("ค่าส่ง", "หมายเหตุแอดมิน"),
            listOf(Math.round(o.n("deliveryFee")).toString(), o.s("adminNote")), listOf(KeyboardType.Number, KeyboardType.Text),
            extra = {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight)
                        .clickable { editKind = ""; onEditMenu() },
                    contentAlignment = Alignment.Center
                ) { Text("แก้ไขรายการเมนูในออเดอร์ ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
            },
            onDismiss = { editKind = "" },
            onSave = { v ->
                editKind = ""
                scope.launch {
                    try {
                        AdminOrderEdit.orderExtra(orderId, v[0].trim().toDoubleOrNull() ?: 0.0, v[1].trim())
                        dToast(ctx, "บันทึกสำเร็จ")
                    } catch (e: Exception) { dToast(ctx, "บันทึกไม่สำเร็จ") }
                }
            }
        )
    }

    if (showCancel) {
        var fault by remember { mutableStateOf("customer") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("ยกเลิกออเดอร์") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DChip("ลูกค้า (หักเครดิต)", fault == "customer", Modifier.weight(1f)) { fault = "customer" }
                        DChip("ร้าน (ไม่หัก)", fault == "shop", Modifier.weight(1f)) { fault = "shop" }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        placeholder = { Text("เหตุผลที่ยกเลิก") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (note.trim().isEmpty()) {
                        dToast(ctx, "กรุณาใส่เหตุผล")
                    } else {
                        showCancel = false
                        busy = true
                        val n = note.trim()
                        val f = fault
                        scope.launch {
                            try {
                                AdminOrderApi.cancel(orderId, n, f)
                                dToast(ctx, if (f == "shop") "ยกเลิกแล้ว (ไม่หักเครดิตลูกค้า)" else "ยกเลิกออเดอร์แล้ว")
                                onBack()
                            } catch (e: Exception) {
                                dToast(ctx, adminErrorText(e))
                            }
                            busy = false
                        }
                    }
                }) { Text("ยืนยันยกเลิก", color = DRed) }
            },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun DEditDialog(
    title: String,
    labels: List<String>,
    initial: List<String>,
    types: List<KeyboardType>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    val values = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (extra != null) extra()
                for (i in labels.indices) {
                    OutlinedTextField(
                        value = values[i], onValueChange = { values[i] = it },
                        label = { Text(labels[i]) },
                        keyboardOptions = KeyboardOptions(keyboardType = types[i]),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values.toList()) }) { Text("บันทึก") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun AdminAssignDialog(
    orderLabel: String,
    currentRiderId: String,
    onDismiss: () -> Unit,
    onConfirm: (AdminRider?) -> Unit
) {
    var riders by remember { mutableStateOf<List<AdminRider>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try { riders = AdminOrderApi.riders() } catch (e: Exception) { failed = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("โยนงานให้ไรเดอร์") },
        text = {
            Column {
                Text(orderLabel, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 8.dp))
                val list = riders
                if (failed) {
                    Text("โหลดไรเดอร์ไม่ได้", color = DRed)
                } else if (list == null) {
                    CircularProgressIndicator(color = C.Primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (currentRiderId.isNotEmpty()) {
                            DPick("ถอดไรเดอร์ออกจากงาน", "", sel == "__unassign__", true) { sel = "__unassign__" }
                        }
                        for (r in list) {
                            DPick(r.name + if (r.id == currentRiderId) " (คนปัจจุบัน)" else "", r.phone, sel == r.id, false) { sel = r.id }
                        }
                        if (list.isEmpty()) Text("ยังไม่มีไรเดอร์", color = C.Subtext, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sel.isNotEmpty(), onClick = {
                if (sel == "__unassign__") onConfirm(null) else onConfirm(riders?.firstOrNull { it.id == sel })
            }) { Text("ยืนยัน") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun DPick(title: String, sub: String, on: Boolean, danger: Boolean, onClick: () -> Unit) {
    val bg = if (on) (if (danger) DRedL else C.PrimaryLight) else Color(0xFFF8FAFC)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }.padding(12.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (danger) DRed else C.Text)
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Subtext)
    }
}

@Composable
private fun DChip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.Primary else C.Gray).clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext) }
}

// หัวส่วน (.dsh) มีเส้นล่างหนา และปุ่มแก้ไข (.deb) ถ้ามี
@Composable
private fun DSectionHead(title: String, editLabel: String?, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.weight(1f))
        if (editLabel != null) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.PrimaryLight).clickable { onEdit() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(editLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(thickness = 2.dp, color = C.Gray)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DSection(title: String, editLabel: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        DSectionHead(title, editLabel) { }
        content()
    }
}

@Composable
private fun DSectionEdit(title: String, editLabel: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        DSectionHead(title, editLabel, onEdit)
        content()
    }
}

// แถวข้อมูลแบบ "ชื่อ : ข้อมูล" ต่อกันชิดซ้าย (ไม่เว้นคอลัมน์ให้ตรงกัน)
@Composable
private fun DRows(rows: List<DR>, colon: Boolean) {
    for (i in rows.indices) {
        val r = rows[i]
        val txt = buildAnnotatedString {
            withStyle(SpanStyle(color = r.labelColor, fontWeight = FontWeight.Normal)) { append(r.label + "  :  ") }
            withStyle(SpanStyle(color = r.color, fontWeight = FontWeight.Bold)) { append(r.value) }
        }
        Text(
            txt, fontSize = if (r.small) 11.sp else 13.sp, textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
        )
        if (i < rows.size - 1) HorizontalDivider(color = DRowLine)
    }
}

@Composable
private fun DPriceRow(label: String, value: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = if (last) 12.dp else 7.dp, bottom = 7.dp)) {
        Text(label, fontSize = if (last) 16.sp else 14.sp, fontWeight = if (last) FontWeight.ExtraBold else FontWeight.Normal, color = if (last) DGreen else C.Subtext, modifier = Modifier.weight(1f))
        Text(value, fontSize = if (last) 16.sp else 14.sp, fontWeight = if (last) FontWeight.ExtraBold else FontWeight.Normal, color = if (last) DGreen else C.Text)
    }
    if (!last) HorizontalDivider(color = DRowLine)
}

@Composable
private fun DAct(text: String, modifier: Modifier, green: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (green) DGreenL else C.PrimaryLight).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (green) DGreen else C.Primary) }
}
