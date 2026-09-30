package com.nimit.delivery.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

private val PRIMARY = Color(0xFF0C4AA6)
private val PRIMARY_DARK = Color(0xFF083570)
private val BG = Color(0xFFF0F2F7)
private val RED = Color(0xFFDC2626)
private val STATUS = mapOf(
    "pending" to "รอรับ", "accepted" to "รับแล้ว", "picking" to "ไรเดอร์มา", "arrived" to "ถึงร้าน",
    "delivering" to "กำลังส่ง", "done" to "เสร็จสิ้น", "rejected" to "ปฏิเสธ", "cancelled" to "ยกเลิก"
)

@Composable
fun CartScreen(session: Session, onBack: () -> Unit, onShop: (String) -> Unit, onCheckout: () -> Unit, onTrack: (String) -> Unit, onShops: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cart by remember { mutableStateOf(CartStore.load(session)) }
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf("food") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var orders by remember { mutableStateOf<List<Pair<String, Doc>>?>(null) }
    val phone = remember { try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() } }

    fun clean() {
        val dead = cart.keys().asSequence().filter { (cart.optJSONObject(it)?.optJSONArray("items")?.length() ?: 0) == 0 }.toList()
        dead.forEach { cart.remove(it) }
    }
    fun persist() { clean(); CartStore.save(session, cart); tick++ }
    LaunchedEffect(Unit) { clean() }

    @Suppress("UNUSED_VARIABLE") val t = tick
    val shopIds = cart.keys().asSequence().toList()
    val effSel = selected.filter { it in shopIds }.toSet().ifEmpty { shopIds.toSet() }
    val totalQty = CartStore.totals(cart).first
    val selTotal = effSel.sumOf { id ->
        val items = cart.optJSONObject(id)?.optJSONArray("items")
        var s = 0.0; if (items != null) for (i in 0 until items.length()) s += items.getJSONObject(i).optDouble("price") * items.getJSONObject(i).optInt("qty"); s
    }

    LaunchedEffect(tab) {
        if (tab == "history" && orders == null) {
            orders = try {
                Firebase.firestore.collection("orders").whereEqualTo("customer.phone", phone.ifEmpty { "_" }).get().await().documents
                    .map { it.id to (it.data ?: emptyMap()) }
                    .sortedByDescending { ((it.second["createdAt"] as? Timestamp)?.seconds ?: 0L) }
            } catch (_: Exception) { emptyList() }
        }
    }

    fun changeQty(shopId: String, idx: Int, delta: Int) {
        val items = cart.optJSONObject(shopId)?.optJSONArray("items") ?: return
        val it = items.getJSONObject(idx)
        it.put("qty", it.optInt("qty") + delta)
        if (it.optInt("qty") <= 0) items.remove(idx)
        persist()
    }
    fun goCheckout() {
        if (effSel.isEmpty()) return
        ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE).edit()
            .putString("checkoutShops", JSONArray(effSel.toList()).toString()).apply()
        onCheckout()
    }
    fun reorder(docId: String) {
        scope.launch {
            try {
                val o = Firebase.firestore.collection("orders").document(docId).get().await().data ?: return@launch
                val sid = o.str("shopId").ifEmpty { return@launch }
                val shop = cart.optJSONObject(sid) ?: JSONObject().put("shopName", o.str("shopName")).put("items", JSONArray()).also { cart.put(sid, it) }
                val arr = shop.getJSONArray("items")
                asMapList(o["items"]).forEach { item ->
                    var merged = false
                    for (i in 0 until arr.length()) {
                        val ex = arr.getJSONObject(i)
                        if (ex.optString("id") == item.str("id")) { ex.put("qty", ex.optInt("qty") + (num(item["qty"])?.toInt() ?: 1)); merged = true; break }
                    }
                    if (!merged) arr.put(deepJson(item) as JSONObject)
                }
                persist(); tab = "food"
            } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().background(BG)) {
        // หัว
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 22.dp, stroke = true)
            }
            Spacer(Modifier.width(14.dp))
            Text("รายการรถเข็น", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        // แท็บ
        Row(Modifier.fillMaxWidth().background(Color.White)) {
            TabItem(Modifier.weight(1f), "อาหาร", if (totalQty > 0) totalQty else null, tab == "food") { tab = "food" }
            TabItem(Modifier.weight(1f), "ประวัติการสั่งซื้อ", null, tab == "history") { tab = "history" }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == "food") {
                if (shopIds.isEmpty()) Empty("ตะกร้าว่างเปล่า", "เลือกร้าน", onShops)
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(shopIds, key = { it }) { shopId ->
                        val grp = cart.getJSONObject(shopId); val arr = grp.getJSONArray("items")
                        val on = shopId in effSel
                        Column(Modifier.fillMaxWidth().alpha(if (on) 1f else 0.55f).shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (on) PRIMARY else Color.White).border(2.dp, if (on) PRIMARY else Color(0xFFCBD5E1), RoundedCornerShape(9.dp))
                                    .clickable { selected = if (on) effSel - shopId else effSel + shopId }, contentAlignment = Alignment.Center) {
                                    if (on) PathIcon("M20 6L9 17l-5-5", Color.White, 18.dp, stroke = true)
                                }
                                Spacer(Modifier.width(14.dp))
                                Row(Modifier.clickable { session.selectedShopId = shopId; session.selectedShopName = grp.optString("shopName"); onShop(shopId) }, verticalAlignment = Alignment.CenterVertically) {
                                    Text(grp.optString("shopName"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                                    Spacer(Modifier.width(6.dp))
                                    PathIcon(P.CHEVRON, C.Subtext, 18.dp)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
                            for (idx in 0 until arr.length()) {
                                val item = arr.getJSONObject(idx)
                                val opts = item.optJSONArray("options")
                                val names = buildList { if (opts != null) for (i in 0 until opts.length()) opts.getJSONObject(i).optString("name").takeIf { it.isNotEmpty() }?.let { add(it) } }
                                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)).background(BG), contentAlignment = Alignment.Center) {
                                        val img = item.optString("imgUrl")
                                        if (img.isNotEmpty()) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else PathIcon(P.RESTAURANT, Color(0xFFCBD5E1), 32.dp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.optString("name"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                                        if (names.isNotEmpty()) Row(
                                            Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF1F2)).border(1.5.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PathIcon("M20 6L9 17l-5-5", RED, 16.dp, stroke = true)
                                            Spacer(Modifier.width(8.dp))
                                            Text(names.joinToString(", "), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RED, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("฿${fmtNum(item.optDouble("price"))}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(Modifier.size(width = 40.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFEE2E2)).clickable { changeQty(shopId, idx, -1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = RED) }
                                                Text("${item.optInt("qty")}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.defaultMinSize(minWidth = 32.dp).border(1.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp), textAlign = TextAlign.Center)
                                                Box(Modifier.size(width = 40.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(PRIMARY).clickable { changeQty(shopId, idx, 1) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val list = orders
                if (list == null) Text("กำลังโหลด...", color = C.Subtext, modifier = Modifier.align(Alignment.Center))
                else if (list.isEmpty()) Empty("ยังไม่มีประวัติการสั่งซื้อ", null, {})
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.first }) { (id, o) ->
                        val st = o.str("status")
                        val done = st == "done"
                        val names = asMapList(o["items"]).joinToString(", ") { it.str("name") }
                        val date = (o["createdAt"] as? Timestamp)?.toDate()?.let { SimpleDateFormat("d MMM yyyy", Locale("th", "TH")).format(it) }.orEmpty()
                        Column(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onTrack(id) }.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("#${o.str("orderId")}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                                val c = when { done -> Color(0xFF16A34A); st == "rejected" || st == "cancelled" -> RED; else -> PRIMARY }
                                Text(STATUS[st] ?: st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            Text(o.str("shopName"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(top = 6.dp))
                            Text(names, fontSize = 13.sp, color = C.Subtext, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("฿${fmtNum(o.dbl("grandTotal") ?: 0.0)} · $date", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PRIMARY)
                                if (done) Text("สั่งซ้ำ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PRIMARY).clickable { reorder(id) }.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
        }

        // แถบล่าง
        if (tab == "food" && shopIds.isNotEmpty()) Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ยอดรวม", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                Text("฿${fmtNum(selTotal)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            }
            val enabled = effSel.isNotEmpty()
            Box(
                Modifier.fillMaxWidth().height(56.dp).then(if (enabled) Modifier.shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = PRIMARY.copy(alpha = 0.4f), spotColor = PRIMARY.copy(alpha = 0.4f)) else Modifier)
                    .clip(RoundedCornerShape(18.dp)).background(if (enabled) Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK)) else Brush.linearGradient(listOf(Color(0xFFC5CFE8), Color(0xFFC5CFE8))))
                    .clickable(enabled = enabled) { goCheckout() }, contentAlignment = Alignment.Center
            ) { Text("สั่งซื้อที่เลือก (${effSel.size} ร้าน)", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}

@Composable private fun TabItem(m: Modifier, label: String, badge: Int?, active: Boolean, onClick: () -> Unit) {
    Column(m.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (active) PRIMARY else C.Subtext)
            if (badge != null) Text("$badge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.clip(CircleShape).background(PRIMARY).defaultMinSize(minWidth = 26.dp, minHeight = 26.dp).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) PRIMARY else Color.Transparent))
    }
}

@Composable private fun Empty(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
        if (action != null) Text(action, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(14.dp)).background(PRIMARY).clickable { onAction() }.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}
