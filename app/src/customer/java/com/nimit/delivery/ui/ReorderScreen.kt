package com.nimit.delivery.ui

import android.widget.Toast
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val RP = Color(0xFF0C4AA6)
private val RPD = Color(0xFF083570)
private val RPL = Color(0xFFE8F0FE)
private val RBG = Color(0xFFF5F6F8)
private val RRED = Color(0xFFDC2626)
private val MON = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

private class RO(val id: String, val d: Doc) {
    val items = asMapList(d["items"])
    val ms = ((d["createdAt"] as? Timestamp)?.toDate()?.time) ?: 0L
    val food = items.sumOf { (num(it["price"]) ?: 0.0) * (num(it["qty"]) ?: 1.0) }
}

private fun dateText(ms: Long): String {
    if (ms <= 0) return ""
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%d %s %d · %02d:%02d น.".format(c.get(Calendar.DAY_OF_MONTH), MON[c.get(Calendar.MONTH)], (c.get(Calendar.YEAR) + 543) % 100, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

@Composable
fun ReorderScreen(session: Session, onBack: () -> Unit, onHome: () -> Unit, onCoupon: () -> Unit, onLogin: () -> Unit, onCart: () -> Unit) {
    val ctx = LocalContext.current
    val phone = remember { try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() } }
    var orders by remember { mutableStateOf<List<RO>>(emptyList()) }
    var shops by remember { mutableStateOf<Map<String, Doc>>(emptyMap()) }
    var msg by remember { mutableStateOf<Pair<String, String>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var qty by remember { mutableStateOf<Map<String, Map<Int, Int>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        if (phone.isEmpty()) { onLogin(); return@LaunchedEffect }
        try {
            val db = Firebase.firestore
            val docs = db.collection("orders").whereEqualTo("customer.phone", phone).whereEqualTo("status", "done").get().await().documents
            val list = docs.map { RO(it.id, it.data ?: emptyMap()) }.filter { it.items.isNotEmpty() }.sortedByDescending { it.ms }.take(10)
            if (list.isEmpty()) msg = "ยังไม่มีประวัติการสั่งซื้อ" to "สั่งอาหารครั้งแรกแล้วค่อยกลับมาสั่งซ้ำได้ที่นี่"
            else {
                orders = list
                shops = coroutineScope {
                    list.map { it.d.str("shopId") }.filter { it.isNotEmpty() }.distinct().map { sid -> async { sid to (try { db.collection("shops").document(sid).get().await().data } catch (_: Exception) { null }) } }.map { it.await() }
                }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
            }
        } catch (e: Exception) { msg = "โหลดไม่สำเร็จ" to (e.message ?: "เกิดข้อผิดพลาด ลองเข้าหน้านี้ใหม่") }
        loading = false
    }

    fun addToCart(o: RO, idxs: List<Int>, q: Map<Int, Int>) {
        val shopId = o.d.str("shopId")
        val cart = CartStore.load(session)
        val shop = cart.optJSONObject(shopId) ?: JSONObject().put("shopName", o.d.str("shopName")).put("items", JSONArray()).also { cart.put(shopId, it) }
        val arr = shop.getJSONArray("items")
        idxs.forEach { idx ->
            val it = o.items[idx]; val n = q[idx] ?: (num(it["qty"]) ?: 1.0).toInt().coerceAtLeast(1)
            var merged = false
            for (i in 0 until arr.length()) { val ex = arr.getJSONObject(i); if (ex.optString("id") == it.str("id")) { ex.put("qty", ex.optInt("qty") + n); merged = true; break } }
            if (!merged) arr.put((deepJson(it) as JSONObject).put("qty", n))
        }
        CartStore.save(session, cart)
        onCart()
    }

    Box(Modifier.fillMaxSize().background(RBG)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(RP, RPD))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true) }
                Spacer(Modifier.width(12.dp))
                Text("สั่งซ้ำ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = RP, strokeWidth = 4.dp, modifier = Modifier.size(34.dp)); Text("กำลังโหลด...", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 14.dp))
                    }
                    msg != null -> Column(Modifier.fillMaxWidth().padding(top = 70.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { PathIcon(P.HISTORY, Color(0xFFCBD5E1), 30.dp) }
                        Text(msg!!.first, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                        Text(msg!!.second, fontSize = 13.sp, color = C.Subtext, textAlign = TextAlign.Center, lineHeight = 20.8.sp, modifier = Modifier.padding(bottom = 18.dp))
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(RP).clickable { onHome() }.padding(horizontal = 26.dp), contentAlignment = Alignment.Center) { Text("เลือกร้าน", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(orders.withIndex().toList(), key = { it.value.id }) { (n, o) ->
                            val shop = shops[o.d.str("shopId")]
                            val open = shop?.let { computeShopOpenNow(it) } ?: true
                            val isOpen = expanded == o.id
                            val sel = selected[o.id] ?: emptySet()
                            val q = qty[o.id] ?: emptyMap()
                            Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
                                // หัวการ์ด: ร้าน + วันที่
                                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(RPL, Color.White))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                        val av = shop?.str("avatarUrl").orEmpty()
                                        if (av.isNotEmpty()) AsyncImage(av, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else PathIcon("M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z", RP, 22.dp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(o.d.str("shopName"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            PathIcon("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z", Color(0xFF16A34A), 13.dp)
                                            Spacer(Modifier.width(4.dp))
                                            Text("ส่งสำเร็จ · ${dateText(o.ms)}", fontSize = 11.5.sp, color = C.Subtext, maxLines = 1)
                                        }
                                    }
                                    if (n == 0) Text("ล่าสุด", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFFEF3C7)).padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                                if (!open) Text("ร้านปิดอยู่ตอนนี้ — สั่งซ้ำได้เมื่อร้านเปิด", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RRED, modifier = Modifier.fillMaxWidth().background(Color(0xFFFEE2E2)).padding(horizontal = 14.dp, vertical = 8.dp))
                                // สรุปรายการ
                                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    o.items.take(3).forEach { it ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${(num(it["qty"]) ?: 1.0).toInt()}×", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = RP, modifier = Modifier.width(26.dp))
                                            Text(it.str("name"), fontSize = 13.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text("฿${fmtNum((num(it["price"]) ?: 0.0) * (num(it["qty"]) ?: 1.0))}", fontSize = 12.5.sp, color = C.Subtext)
                                        }
                                    }
                                    if (o.items.size > 3) Text("และอีก ${o.items.size - 3} รายการ", fontSize = 11.5.sp, color = C.Subtext)
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
                                // ท้ายการ์ด: ยอด + ปุ่ม
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("ค่าอาหาร", fontSize = 11.sp, color = C.Subtext)
                                        Text("฿${fmtNum(o.food)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.Text)
                                    }
                                    Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, RP, RoundedCornerShape(12.dp)).clickable {
                                        expanded = if (isOpen) null else o.id
                                        if (!isOpen && o.id !in selected) { selected = selected + (o.id to emptySet()); qty = qty + (o.id to o.items.indices.associateWith { i -> (num(o.items[i]["qty"]) ?: 1.0).toInt().coerceAtLeast(1) }) }
                                    }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(if (isOpen) "ย่อ" else "เลือกรายการ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RP) }
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.height(40.dp).alpha(if (open) 1f else 0.45f).shadow(if (open) 6.dp else 0.dp, RoundedCornerShape(12.dp), ambientColor = RP.copy(alpha = 0.3f), spotColor = RP.copy(alpha = 0.3f)).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(RP, RPD)))
                                        .clickable(enabled = open) { addToCart(o, o.items.indices.toList(), emptyMap()) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("สั่งซ้ำทั้งหมด", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                                }
                                // โหมดเลือกรายการ
                                if (isOpen) Column(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
                                    o.items.forEachIndexed { idx, it ->
                                        val on = idx in sel
                                        val opts = asMapList(it["options"]).map { op -> op.str("name") }.filter { s -> s.isNotEmpty() }.joinToString(", ")
                                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(if (on) RPL else Color.White).border(1.5.dp, if (on) RP else Color(0xFFE8EBF0), RoundedCornerShape(14.dp))
                                            .clickable { selected = selected + (o.id to if (on) sel - idx else sel + idx) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) RP else Color.White).border(2.dp, if (on) RP else Color(0xFFCBD5E1), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { if (on) PathIcon("M20 6L9 17l-5-5", Color.White, 14.dp, stroke = true) }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(it.str("name"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                                if (opts.isNotEmpty()) Text(opts, fontSize = 11.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                                                Text("฿${fmtNum(num(it["price"]) ?: 0.0)}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = RP, modifier = Modifier.padding(top = 3.dp))
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEE2E2)).clickable { qty = qty + (o.id to (q + (idx to maxOf(1, (q[idx] ?: 1) - 1)))) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = RRED) }
                                                Text("${q[idx] ?: 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.defaultMinSize(minWidth = 16.dp), textAlign = TextAlign.Center)
                                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(RP).clickable { qty = qty + (o.id to (q + (idx to (q[idx] ?: 1) + 1))) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                                            }
                                        }
                                    }
                                    val can = open && sel.isNotEmpty()
                                    val sum = sel.sumOf { (num(o.items[it]["price"]) ?: 0.0) * (q[it] ?: 1) }
                                    Box(Modifier.fillMaxWidth().height(48.dp).alpha(if (can) 1f else 0.5f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(RP, RPD))).clickable(enabled = can) { addToCart(o, sel.sorted(), q) }, contentAlignment = Alignment.Center) {
                                        Text(if (sel.isEmpty()) "เลือกรายการที่ต้องการ" else "เพิ่ม ${sel.size} รายการลงตะกร้า · ฿${fmtNum(sum)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(8.dp).background(Color.White).navigationBarsPadding()) {
            RNav(P.HOME, "หน้าหลัก", false, Modifier.weight(1f)) { onHome() }
            RNav(P.HISTORY, "ประวัติ", true, Modifier.weight(1f)) {}
            RNav(P.TAG, "คูปอง", false, Modifier.weight(1f)) { onCoupon() }
            RNav(P.SOCIAL, "โซเชียล", false, Modifier.weight(1f).alpha(0.55f)) { Toast.makeText(ctx, "ระบบโซเชียลเร็วๆ นี้ครับ", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable private fun RNav(icon: String, label: String, active: Boolean, m: Modifier, onClick: () -> Unit) {
    Column(m.clickable { onClick() }.padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PathIcon(icon, if (active) RP else C.Subtext, 22.dp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) RP else C.Subtext)
    }
}
