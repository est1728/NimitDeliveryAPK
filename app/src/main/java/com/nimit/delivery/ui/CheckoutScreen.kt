package com.nimit.delivery.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

private val PRIMARY = Color(0xFF0C4AA6)
private val PRIMARY_DARK = Color(0xFF083570)
private val PRIMARY_LIGHT = Color(0xFFE8F0FE)
private val GRAY = Color(0xFFF0F2F7)
private val GREEN = Color(0xFF16A34A)
private const val WEB_BASE = "https://nimitdelivery.vercel.app" // ใช้ส่งแจ้งเตือนออเดอร์ใหม่ผ่าน API เดิมของเว็บ
private const val PIN_FILL = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"

private fun unwrap(v: Any?): Any? = when (v) {
    JSONObject.NULL -> null
    is JSONObject -> v.toMapDeep()
    is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
    else -> v
}
private fun JSONObject.toMapDeep(): Map<String, Any?> { val m = mutableMapOf<String, Any?>(); keys().forEach { k -> m[k] = unwrap(get(k)) }; return m }
private fun norm(d: Double): Any = if (d == floor(d) && abs(d) < 1e15) d.toLong() else d
private fun Modifier.dashed(color: Color, w: Dp, r: Dp) = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(r.toPx()), style = Stroke(w.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
}

private fun inPoly(lat: Double, lng: Double, poly: List<Pair<Double, Double>>): Boolean {
    var inside = false; var j = poly.size - 1
    for (i in poly.indices) {
        val (yi, xi) = poly[i]; val (yj, xj) = poly[j]
        if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** true/false = อยู่ในเขตหรือไม่, null = ระบบยังไม่มีเขตเลย (ปล่อยผ่านเหมือนเว็บ) */
private suspend fun insideAnyZone(lat: Double, lng: Double): Boolean? {
    val snap = Firebase.firestore.collection("franchiseZones").get().await()
    var any = false; var inside = false
    snap.documents.forEach { d ->
        val poly = asMapList(d.get("zone")).mapNotNull { p -> val la = num(p["lat"]); val lo = num(p["lng"]); if (la != null && lo != null) la to lo else null }
        if (poly.size < 3) return@forEach
        any = true
        if (inPoly(lat, lng, poly)) inside = true
    }
    return if (any) inside else null
}

private fun notifyNewOrder(shopId: String, shopName: String, orderId: String) {
    try {
        val db = Firebase.firestore
        val tokens = mutableListOf<String>()
        for (role in listOf("admin", "rider")) {
            val s = com.google.android.gms.tasks.Tasks.await(db.collection("fcmTokens").whereEqualTo("role", role).get())
            s.documents.forEach { d -> d.getString("token")?.takeIf { it.isNotEmpty() }?.let { tokens += it } }
        }
        val sh = com.google.android.gms.tasks.Tasks.await(db.collection("fcmTokens").document(shopId + "_shop").get())
        sh.getString("token")?.takeIf { it.isNotEmpty() }?.let { tokens += it }
        if (tokens.isEmpty()) return
        val body = JSONObject().put("tokens", JSONArray(tokens)).put("title", "มีออเดอร์ใหม่เข้ามา 🔔")
            .put("body", "$orderId จาก ${shopName.ifEmpty { "ร้านค้า" }}").put("orderId", orderId).put("url", "/admin.html")
        val c = URL("$WEB_BASE/api/send-order-notification").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        c.responseCode; c.disconnect()
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(session: Session, onBack: () -> Unit, onAddress: () -> Unit, onPlaced: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE) }
    val shopIds = remember { try { JSONArray(prefs.getString("checkoutShops", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (_: Exception) { emptyList() } }
    val cart = remember { CartStore.load(session) }
    val addresses = remember { try { JSONArray(session.addresses ?: "[]").let { a -> (0 until a.length()).map { a.getJSONObject(it) } } } catch (_: Exception) { emptyList() } }
    val phone = remember { try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() } }

    var selIdx by remember { mutableIntStateOf(session.defaultAddress?.toIntOrNull() ?: 0) }
    var tempIdx by remember { mutableIntStateOf(0) }
    var walletPct by remember { mutableDoubleStateOf(30.0) }
    var riderPct by remember { mutableDoubleStateOf(70.0) }
    var baseFee by remember { mutableDoubleStateOf(9.0) }
    var tiers by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var meta by remember { mutableStateOf<Map<String, Doc>>(emptyMap()) }
    var cards by remember { mutableStateOf<Map<String, Doc>>(emptyMap()) }
    var coupons by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var picked by remember { mutableStateOf<Doc?>(null) }
    var loading by remember { mutableStateOf(true) }
    var placing by remember { mutableStateOf(false) }
    var zoneOut by remember { mutableStateOf(false) }
    var addrSheet by remember { mutableStateOf(false) }
    var couponSheet by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        try {
            db.collection("settings").document("pricing").get().await().data?.let { d ->
                walletPct = num(d["walletPercent"]) ?: 30.0; riderPct = num(d["riderPercent"]) ?: 70.0
                baseFee = num(d["deliveryFee"]) ?: 9.0; tiers = asMapList(d["deliveryTiers"])
            }
        } catch (_: Exception) {}
        val m = mutableMapOf<String, Doc>()
        shopIds.forEach { id -> try { db.collection("shops").document(id).get().await().data?.let { m[id] = it } } catch (_: Exception) {} }
        meta = m
        if (phone.isNotEmpty()) {
            val c = mutableMapOf<String, Doc>()
            shopIds.forEach { id ->
                if (m[id]?.get("loyaltyEnabled") == true) c[id] = try { db.collection("loyaltyCards").document(id + "_" + phone).get().await().data ?: mapOf("points" to 0) } catch (_: Exception) { mapOf("points" to 0) }
            }
            cards = c
            try {
                val claims = db.collection("claimedCoupons").whereEqualTo("phone", phone).whereEqualTo("used", false).get().await().documents
                val now = System.currentTimeMillis()
                coupons = claims.mapNotNull { cl ->
                    val cid = cl.getString("couponId") ?: return@mapNotNull null
                    val cs = try { db.collection("coupons").document(cid).get().await() } catch (_: Exception) { null }
                    val d = cs?.data
                    if (d != null && cs.exists() && (num(d["endAt"]) ?: 0.0) > now) d + mapOf("claimId" to cl.id, "id" to cs.id) else null
                }
            } catch (_: Exception) {}
        }
        loading = false
    }

    val addr = addresses.getOrNull(selIdx) ?: addresses.firstOrNull()
    fun tierFee(km: Double): Double {
        val s = tiers.sortedBy { num(it["min"]) ?: 0.0 }
        s.forEachIndexed { i, t ->
            val mn = num(t["min"]) ?: 0.0; val mx = num(t["max"]) ?: Double.POSITIVE_INFINITY
            val ok = if (i == 0) km >= mn && km <= mx else km > mn && km <= mx
            if (ok) return num(t["fee"]) ?: 0.0
        }
        return baseFee
    }
    fun deliveryFee(id: String): Double {
        val mt = meta[id] ?: return 0.0
        val la = addr?.optDouble("lat", Double.NaN) ?: Double.NaN; val lo = addr?.optDouble("lng", Double.NaN) ?: Double.NaN
        val ml = num(mt["lat"]); val mo = num(mt["lng"])
        return if (!la.isNaN() && !lo.isNaN() && ml != null && mo != null) tierFee(ShopsRepo.distKm(ml, mo, la, lo)) else 0.0
    }
    fun shopFood(id: String): Double { val a = cart.optJSONObject(id)?.optJSONArray("items") ?: return 0.0; var s = 0.0; for (i in 0 until a.length()) s += a.getJSONObject(i).optDouble("price") * a.getJSONObject(i).optInt("qty"); return s }
    val foodTotal = shopIds.sumOf { shopFood(it) }
    val deliveryTotal = shopIds.sumOf { deliveryFee(it) }

    fun eligible(c: Doc): String? {
        val sid = c.str("shopId")
        if (sid.isNotEmpty() && sid !in shopIds) return "ใช้ไม่ได้กับร้านที่เลือกอยู่ตอนนี้"
        val mn = num(c["minOrderAmount"]) ?: 0.0
        if (mn > 0 && foodTotal < mn) return "ต้องซื้อขั้นต่ำ ฿${fmtNum(mn)}"
        return null
    }
    fun discountOf(c: Doc): Double {
        val v = num(c["value"]) ?: 0.0
        var d = when (c.str("type")) {
            "percent" -> { var x = Math.round(foodTotal * v / 100).toDouble(); num(c["maxDiscount"])?.takeIf { it > 0 }?.let { x = min(x, it) }; x }
            "fixed" -> v
            "freeship" -> deliveryTotal
            else -> 0.0
        }
        return min(d, foodTotal + deliveryTotal)
    }
    val activeCoupon = picked?.takeIf { eligible(it) == null }
    val discount = activeCoupon?.let { discountOf(it) } ?: 0.0
    val grand = max(0.0, foodTotal + deliveryTotal - discount)

    fun place() {
        val a = addr ?: return
        placing = true; error = null
        scope.launch {
            try {
                val la = a.optDouble("lat", Double.NaN); val lo = a.optDouble("lng", Double.NaN)
                if (!la.isNaN() && !lo.isNaN()) {
                    val ins = try { insideAnyZone(la, lo) } catch (_: Exception) { null }
                    if (ins == false) { zoneOut = true; placing = false; return@launch }
                }
                val db = Firebase.firestore
                val groupId = "GRP-" + System.currentTimeMillis().toString(36) + (1..4).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
                val customer = mapOf("name" to a.optString("name"), "phone" to a.optString("phone"), "address" to a.optString("address"),
                    "lat" to (if (la.isNaN()) null else la), "lng" to (if (lo.isNaN()) null else lo))
                if (a.optString("phone").isNotEmpty()) try {
                    db.collection("customers").document(a.optString("phone")).set(customer + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                } catch (_: Exception) {}
                var couponRemaining = discount
                val docs = mutableListOf<Triple<String, String, String>>()
                for (shopId in shopIds) {
                    val grp = cart.optJSONObject(shopId) ?: continue
                    val mt = meta[shopId] ?: emptyMap()
                    val arr = grp.getJSONArray("items")
                    val items = (0 until arr.length()).map { i ->
                        val o = JSONObject(arr.getJSONObject(i).toString()); o.remove("imgUrl")
                        o.optJSONArray("options")?.let { oa -> for (k in 0 until oa.length()) { val op = oa.getJSONObject(k); if (op.optString("name").isEmpty()) op.put("name", "") } }
                        o
                    }
                    val subtotal = items.sumOf { it.optDouble("price") * it.optInt("qty") }
                    val proxy = mt.str("shopType") == "proxy"
                    val baseCost = if (proxy) 0.0 else items.sumOf { it2 ->
                        var ob = 0.0; it2.optJSONArray("options")?.let { oa -> for (k in 0 until oa.length()) ob += oa.getJSONObject(k).optDouble("price", 0.0) }
                        (it2.optDouble("basePrice") + ob) * it2.optInt("qty")
                    }
                    val fee = if (proxy) 0.0 else deliveryFee(shopId)
                    var cd = 0.0
                    if (couponRemaining > 0) { cd = min(couponRemaining, subtotal + fee); couponRemaining -= cd }
                    val grandShop = subtotal + fee - cd
                    val totalDiff = if (proxy) subtotal + fee else (subtotal - baseCost) + fee
                    val walletCut = Math.round(totalDiff * walletPct / 100).toDouble()
                    val riderEarn = Math.round(totalDiff * riderPct / 100).toDouble()
                    val orderId = "NM-" + (1000 + (Math.random() * 9000).toInt())
                    val payload = mutableMapOf<String, Any?>(
                        "orderId" to orderId, "groupId" to groupId, "shopId" to shopId, "shopName" to grp.optString("shopName"),
                        "shopType" to mt.str("shopType").ifEmpty { "normal" }, "shopPhone" to mt.str("phone"), "shopAddress" to mt.str("address"),
                        "shopLat" to num(mt["lat"]), "shopLng" to num(mt["lng"]), "customer" to customer,
                        "items" to items.map { it.toMapDeep() },
                        "subtotal" to norm(subtotal), "baseCost" to norm(baseCost), "deliveryFee" to norm(fee), "grandTotal" to norm(grandShop),
                        "totalDiff" to norm(totalDiff), "walletCut" to norm(walletCut), "riderEarn" to norm(riderEarn),
                        "paymentMethod" to "cash", "status" to "pending", "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (cd > 0 && activeCoupon != null) { payload["couponId"] = activeCoupon.str("id"); payload["couponTitle"] = activeCoupon.str("title"); payload["couponDiscount"] = norm(cd) }
                    val ref = db.collection("orders").add(payload).await()
                    docs += Triple(ref.id, orderId, grp.optString("shopName"))
                    launch(Dispatchers.IO) { notifyNewOrder(shopId, grp.optString("shopName"), orderId) }
                    if (mt["loyaltyEnabled"] == true && phone.isNotEmpty()) try {
                        val cardRef = db.collection("loyaltyCards").document(shopId + "_" + phone)
                        if (cardRef.get().await().exists()) cardRef.update(mapOf("points" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())).await()
                        else cardRef.set(mapOf("shopId" to shopId, "shopName" to grp.optString("shopName"), "customerPhone" to phone, "customerName" to a.optString("name"), "points" to 1, "updatedAt" to FieldValue.serverTimestamp())).await()
                    } catch (_: Exception) {}
                }
                if (activeCoupon != null) try { db.collection("claimedCoupons").document(activeCoupon.str("claimId")).update(mapOf("used" to true, "usedAt" to FieldValue.serverTimestamp())).await() } catch (_: Exception) {}
                val remaining = CartStore.load(session); shopIds.forEach { remaining.remove(it) }; CartStore.save(session, remaining)
                prefs.edit().putString("currentGroupId", groupId)
                    .putString("currentOrderDocs", JSONArray(docs.map { JSONObject().put("docId", it.first).put("orderId", it.second).put("shopName", it.third) }).toString()).apply()
                placing = false
                onPlaced(docs.first().first)
            } catch (e: Exception) { placing = false; error = "เกิดข้อผิดพลาด: ${e.message}" }
        }
    }

    Column(Modifier.fillMaxSize().background(GRAY)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 20.dp, stroke = true) }
            Spacer(Modifier.width(12.dp))
            Text("สรุปการสั่งซื้อ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            // ที่อยู่
            Section("ที่อยู่จัดส่ง", action = if (addresses.isNotEmpty()) "เปลี่ยน" else null, onAction = { tempIdx = selIdx; addrSheet = true }) {
                if (addr == null) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("ยังไม่มีที่อยู่จัดส่ง", fontSize = 14.sp, color = C.Subtext)
                    Text("เพิ่มที่อยู่", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(PRIMARY).clickable { onAddress() }.padding(horizontal = 22.dp, vertical = 10.dp))
                } else Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PRIMARY_LIGHT), contentAlignment = Alignment.Center) { PathIcon(PIN_FILL, PRIMARY, 20.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(addr.optString("name"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text(addr.optString("phone"), fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                        Text(addr.optString("address"), fontSize = 13.sp, color = C.Text, lineHeight = 19.5.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            // รายการ
            Section("รายการสินค้า") {
                shopIds.forEachIndexed { si, id ->
                    val grp = cart.optJSONObject(id) ?: return@forEachIndexed
                    val mt = meta[id] ?: emptyMap()
                    Column(Modifier.padding(bottom = if (si < shopIds.lastIndex) 14.dp else 0.dp)) {
                        Text(grp.optString("shopName"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 10.dp))
                        if (mt["loyaltyEnabled"] == true) {
                            val pts = (num(cards[id]?.get("points")) ?: 0.0).toInt(); val target = (num(mt["loyaltyTarget"]) ?: 10.0).toInt()
                            if (pts >= target) Text("🎁 แต้มเต็มแล้ว! การแลกเมนูฟรีจะเปิดใช้ในอัปเดตถัดไป แต้มยังสะสมต่อได้", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412),
                                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7ED)).border(1.5.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp))
                            else Text("🎫 สั่งครั้งนี้ได้ 1 แต้ม (สะสม $pts/$target)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C), modifier = Modifier.padding(vertical = 6.dp))
                        }
                        val arr = grp.getJSONArray("items")
                        for (i in 0 until arr.length()) {
                            val it = arr.getJSONObject(i)
                            val opts = it.optJSONArray("options")
                            val optText = buildList { if (opts != null) for (k in 0 until opts.length()) opts.getJSONObject(k).optString("name").takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }.joinToString(", ")
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(GRAY)) { if (it.optString("imgUrl").isNotEmpty()) AsyncImage(it.optString("imgUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(it.optString("name"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                    if (optText.isNotEmpty()) Text(optText, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                                    if (it.optString("note").isNotEmpty()) Text("📝 ${it.optString("note")}", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("฿${fmtNum(it.optDouble("price") * it.optInt("qty"))}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                                    Text("x${it.optInt("qty")}", fontSize = 12.sp, color = C.Subtext)
                                }
                            }
                        }
                    }
                }
            }
            // ชำระเงิน
            Section("วิธีชำระเงิน") {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0FDF4)), contentAlignment = Alignment.Center) { PathIcon("M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z", GREEN, 20.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("เก็บเงินปลายทาง", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text("ชำระเป็นเงินสดให้กับไรเดอร์", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("เงินสด", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GREEN, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            // คูปอง
            Section("คูปองส่วนลด") {
                Row(Modifier.fillMaxWidth().dashed(PRIMARY, 1.5.dp, 12.dp).clip(RoundedCornerShape(12.dp)).clickable { couponSheet = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(activeCoupon?.str("title") ?: "เลือกคูปองที่เก็บไว้", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (activeCoupon != null) C.Text else PRIMARY, modifier = Modifier.weight(1f))
                    PathIcon(P.CHEVRON, C.Subtext, 16.dp)
                }
            }
            // สรุปราคา
            Section("สรุปราคา") {
                SumRow("ราคาอาหาร", "฿${fmtNum(foodTotal)}")
                SumRow("ค่าส่ง", "฿${fmtNum(deliveryTotal)}")
                if (discount > 0) SumRow("ส่วนลดคูปอง", "-฿${fmtNum(discount)}", GREEN)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).drawBehind { drawLine(Color(0xFFF0F2F7), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ยอดรวมทั้งหมด", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                    Text("฿${fmtNum(grand)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                }
            }
            error?.let { Text(it, fontSize = 13.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        val canOrder = !loading && !placing && addr != null
        Box(Modifier.fillMaxWidth().shadow(8.dp).background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).then(if (canOrder) Modifier.shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = PRIMARY.copy(alpha = 0.4f), spotColor = PRIMARY.copy(alpha = 0.4f)) else Modifier)
                    .clip(RoundedCornerShape(14.dp)).background(if (canOrder) Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK)) else Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1))))
                    .clickable(enabled = canOrder) { place() }, contentAlignment = Alignment.Center
            ) { Text(if (loading) "กำลังโหลด..." else if (placing) "กำลังสั่ง..." else "ยืนยันสั่งซื้อ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }

    if (addrSheet) ModalBottomSheet(onDismissRequest = { addrSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("เลือกที่อยู่จัดส่ง", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            addresses.forEachIndexed { i, a ->
                val on = i == tempIdx
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(if (on) PRIMARY_LIGHT else Color.White).border(2.dp, if (on) PRIMARY else Color(0xFFF0F2F5), RoundedCornerShape(14.dp)).clickable { tempIdx = i }.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(if (on) PRIMARY else Color.White).border(2.dp, if (on) PRIMARY else Color(0xFFCBD5E1), CircleShape), contentAlignment = Alignment.Center) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("${a.optString("name")} · ${a.optString("phone")}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text); Text(a.optString("address"), fontSize = 13.sp, color = C.Subtext, lineHeight = 19.5.sp, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().dashed(Color(0xFFD1D9E8), 2.dp, 14.dp).clip(RoundedCornerShape(14.dp)).clickable { addrSheet = false; onAddress() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PathIcon(P.PLUS, PRIMARY, 20.dp); Spacer(Modifier.width(12.dp)); Text("เพิ่มที่อยู่ใหม่", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PRIMARY)
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp).height(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK))).clickable {
                selIdx = tempIdx; session.defaultAddress = tempIdx.toString(); addrSheet = false
            }, contentAlignment = Alignment.Center) { Text("ยืนยันที่อยู่นี้", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }

    if (couponSheet) ModalBottomSheet(onDismissRequest = { couponSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Text("เลือกคูปอง", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            if (coupons.isEmpty()) Text("ยังไม่มีคูปองที่เก็บไว้\nไปเก็บคูปองได้ที่หน้าคูปอง", fontSize = 13.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp))
            else {
                CouponRow("–", "ไม่ใช้คูปอง", null, picked == null, true, Color(0xFF94A3B8)) { picked = null; couponSheet = false }
                coupons.forEach { c ->
                    val why = eligible(c); val big = when (c.str("type")) { "percent" -> "${fmtNum(num(c["value"]) ?: 0.0)}%"; "fixed" -> "฿${fmtNum(num(c["value"]) ?: 0.0)}"; else -> "ส่งฟรี" }
                    val mn = num(c["minOrderAmount"]) ?: 0.0
                    CouponRow(big, c.str("title"), why ?: if (mn > 0) "ขั้นต่ำ ฿${fmtNum(mn)}" else "ใช้ได้เลย", picked?.str("claimId") == c.str("claimId"), why == null, PRIMARY) { picked = c; couponSheet = false }
                }
            }
        }
    }

    if (zoneOut) Dialog(onDismissRequest = { zoneOut = false }) {
        Column(Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFFFF7ED)), contentAlignment = Alignment.Center) { PathIcon(P.PIN, Color(0xFFEA580C), 30.dp, stroke = true) }
            Text("คุณอยู่นอกพื้นที่ให้บริการ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Text("ที่อยู่ที่เลือกอยู่นอกเขตที่เราให้บริการในขณะนี้ ลองเลือกที่อยู่อื่น หรือรอทีมงานขยายพื้นที่เพิ่มเติม", fontSize = 13.sp, color = C.Subtext, textAlign = TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 22.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK))).clickable { zoneOut = false; tempIdx = selIdx; addrSheet = true }, contentAlignment = Alignment.Center) { Text("เปลี่ยนที่อยู่จัดส่ง", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(GRAY).clickable { zoneOut = false }, contentAlignment = Alignment.Center) { Text("ปิดหน้าต่างนี้", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = C.Subtext) }
        }
    }
}

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().drawBehind { drawLine(GRAY, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 2.dp.toPx()) }.padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY, letterSpacing = 0.4.sp)
            if (action != null) Text(action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PRIMARY, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PRIMARY_LIGHT).clickable { onAction() }.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable private fun SumRow(l: String, v: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, fontSize = 14.sp, color = color ?: C.Subtext); Text(v, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color ?: C.Text)
    }
}

@Composable private fun CouponRow(badge: String, title: String, meta: String?, active: Boolean, enabled: Boolean, badgeColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).then(if (enabled) Modifier else Modifier.alpha(0.45f)).clip(RoundedCornerShape(12.dp)).background(if (active) PRIMARY_LIGHT else Color.White).border(1.5.dp, if (active) PRIMARY else Color(0xFFEEF0F4), RoundedCornerShape(12.dp)).clickable(enabled = enabled) { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(if (badgeColor == PRIMARY) Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK)) else Brush.linearGradient(listOf(badgeColor, badgeColor))), contentAlignment = Alignment.Center) { Text(badge, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = C.Text); if (meta != null) Text(meta, fontSize = 11.5.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp)) }
    }
}
