package com.nimit.delivery.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

private val P1 = Color(0xFF0C4AA6)
private val P2 = Color(0xFF083570)
private val BG1 = Color(0xFFF5F6F8)
private val CARD = Color(0xFFF1F5F9)

private class CatState(val title: String? = null, val msgTitle: String? = null, val msgSub: String? = null,
                       val shops: List<Pair<String, Doc>> = emptyList(), val items: List<Triple<String, Doc, String>> = emptyList(),
                       val prices: Map<String, Int> = emptyMap(), val shopNames: Map<String, String> = emptyMap(), val loading: Boolean = false,
                       val shopDocs: Map<String, Doc> = emptyMap(), val pricing: Pricing? = null)

@Composable
fun CategoryScreen(session: Session, buttonId: String, onBack: () -> Unit, onOpenShop: (String) -> Unit, onCheckout: () -> Unit, onLogin: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("หมวดหมู่") }
    var st by remember { mutableStateOf(CatState(loading = true)) }
    var cart by remember { mutableStateOf(CartStore.load(session)) }
    var tick by remember { mutableIntStateOf(0) }
    var popup by remember { mutableStateOf<PopupState?>(null) }
    var popupShop by remember { mutableStateOf("") }
    val tagged = remember(tick, cart, buttonId) { taggedTotals(cart, buttonId) }

    LaunchedEffect(buttonId) {
        fun msg(t: String, s: String) { st = CatState(msgTitle = t, msgSub = s) }
        if (buttonId.isEmpty()) { msg("ไม่พบหมวดหมู่ที่ต้องการ", "ลองกลับไปหน้าร้านค้าแล้วกดใหม่อีกครั้ง"); return@LaunchedEffect }
        try {
            val db = Firebase.firestore
            val snap = db.collection("categoryButtons").document(buttonId).get().await()
            if (!snap.exists()) { msg("ไม่พบหมวดหมู่นี้", "อาจถูกลบไปแล้ว ลองกลับไปหน้าร้านค้า"); return@LaunchedEffect }
            val d = snap.data ?: emptyMap()
            d.str("name").takeIf { it.isNotEmpty() }?.let { title = it }
            if (d.str("mode").ifEmpty { "items" } == "shops") {
                val ids = (d["shopIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
                if (ids.isEmpty()) { msg("ยังไม่มีร้านค้าในหมวดหมู่นี้", "ลองดูหมวดหมู่อื่น หรือกลับมาเช็คใหม่ภายหลัง"); return@LaunchedEffect }
                val shops = coroutineScope {
                    ids.map { id -> async { try { db.collection("shops").document(id).get().await().let { s -> if (s.exists()) id to (s.data ?: emptyMap()) else null } } catch (_: Exception) { null } } }.awaitAll()
                }.filterNotNull()
                if (shops.isEmpty()) msg("ยังไม่มีร้านค้าในหมวดหมู่นี้", "ลองดูหมวดหมู่อื่น หรือกลับมาเช็คใหม่ภายหลัง") else st = CatState(shops = shops)
            } else {
                val refs = asMapList(d["itemRefs"])
                if (refs.isEmpty()) { msg("ยังไม่มีเมนูในหมวดหมู่นี้", "ลองดูหมวดหมู่อื่น หรือกลับมาเช็คใหม่ภายหลัง"); return@LaunchedEffect }
                val items: List<Triple<String, Doc, String>> = coroutineScope {
                    refs.map { r -> async { try { db.collection("shops").document(r.str("shopId")).collection("menus").document(r.str("menuId")).get().await().let { s -> if (s.exists()) Triple<String, Doc, String>(s.id, s.data ?: emptyMap(), r.str("shopId")) else null } } catch (_: Exception) { null } } }.awaitAll()
                }.filterNotNull()
                if (items.isEmpty()) { msg("ยังไม่มีเมนูในหมวดหมู่นี้", "ลองดูหมวดหมู่อื่น หรือกลับมาเช็คใหม่ภายหลัง"); return@LaunchedEffect }
                val shopDocs: Map<String, Doc> = coroutineScope {
                    items.map { it.third }.distinct().map { sid -> async { val sd: Doc = try { db.collection("shops").document(sid).get().await().data ?: emptyMap() } catch (_: Exception) { emptyMap() }; sid to sd } }.awaitAll()
                }.toMap()
                // ราคาที่แสดงใช้สูตร GP เดียวกับหน้าเมนู (เว็บเดิมโชว์ราคาดิบ ทำให้ไม่ตรงกับหน้าเมนู)
                val pr = Pricing.from(try { db.collection("settings").document("pricing").get().await().data } catch (_: Exception) { null })
                val prices = items.associate { (id, m, sid) -> "$sid/$id" to pr.withShopType(shopDocs[sid]?.str("shopType")?.ifEmpty { "normal" } ?: "normal").calcPrice(num(m["price"]) ?: 0.0, num(m["gp"]) ?: 0.0) }
                st = CatState(items = items.map { Triple(it.first, it.second, it.third) }, prices = prices, shopNames = shopDocs.mapValues { it.value.str("name") }, shopDocs = shopDocs, pricing = pr)
            }
        } catch (e: Exception) { msg("โหลดไม่สำเร็จ", e.message ?: "เกิดข้อผิดพลาด ลองเข้าหน้านี้ใหม่") }
    }

    Column(Modifier.fillMaxSize().background(BG1)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(P1, P2))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true) }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            st.loading -> Column(Modifier.fillMaxSize().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = P1, strokeWidth = 4.dp, modifier = Modifier.size(34.dp))
                Text("กำลังโหลด...", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 14.dp))
            }
            st.msgTitle != null -> Column(Modifier.fillMaxSize().padding(top = 70.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { PathIcon(P.GRID, Color(0xFFCBD5E1), 30.dp) }
                Text(st.msgTitle!!, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                Text(st.msgSub.orEmpty(), fontSize = 13.sp, color = C.Subtext, textAlign = TextAlign.Center, lineHeight = 20.8.sp)
            }
            st.shops.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(st.shops, key = { it.first }) { (id, s) ->
                    Column(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { session.selectedShopId = id; session.selectedShopName = s.str("name"); onOpenShop(id) }) {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(CARD)) { if (s.str("bannerUrl").isNotEmpty()) AsyncImage(s.str("bannerUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.offset(y = (-30).dp).size(52.dp).shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FE)).border(3.dp, Color.White, RoundedCornerShape(14.dp))) {
                                if (s.str("avatarUrl").isNotEmpty()) AsyncImage(s.str("avatarUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.str("name"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                                Text("⭐${"%.2f".format(s.dbl("rating") ?: 0.0)} · ${s["deliveryTime"]?.toString()?.takeIf { it.isNotBlank() } ?: "20-30"} นาที", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(st.items, key = { it.third + "/" + it.first }) { (id, m, sid) ->
                    val shopOpen = st.shopDocs[sid]?.let { computeShopOpenNow(it) } ?: true
                    val unavailable = m["available"] == false || !isMenuWithinSchedule(m) || !shopOpen
                    Column(Modifier.fillMaxWidth().alpha(if (unavailable) 0.55f else 1f).shadow(1.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(enabled = !unavailable) {
                        popup = PopupState(id, m); popupShop = sid
                    }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(CARD), contentAlignment = Alignment.Center) {
                            if (m.str("imgUrl").isNotEmpty()) AsyncImage(m.str("imgUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else PathIcon(P.RESTAURANT, Color(0xFFCBD5E1), 34.dp)
                            if (unavailable) Text(if (!shopOpen) "ร้านปิด" else "สินค้าหมด", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFEE2E2)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                            Text(m.str("name"), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.5.sp, modifier = Modifier.heightIn(min = 35.dp))
                            Text(st.shopNames[sid].orEmpty(), fontSize = 11.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text("฿${st.prices["$sid/$id"] ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = P1, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        }
        // แถบสรุปคำสั่งซื้อ (ลอย) — นับเฉพาะรายการที่เพิ่มจากหมวดนี้ กดแล้วไป checkout ของร้านที่เกี่ยวข้อง
        if (tagged.first > 0) {
            val barShape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 6.dp)
                    .shadow(14.dp, barShape, ambientColor = P1.copy(alpha = 0.45f), spotColor = P1.copy(alpha = 0.45f))
                    .clip(barShape).background(Brush.linearGradient(listOf(P1, P2)))
                    .clickable {
                        ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE).edit()
                            .putString("checkoutShops", org.json.JSONArray(tagged.third).toString()).apply()
                        onCheckout()
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${tagged.first} รายการ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    Text("฿${fmtNum(tagged.second)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text("สรุปคำสั่งซื้อ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                PathIcon(P.CHEVRON, Color.White, 24.dp)
            }
        }
    }

    popup?.let { p ->
        val shop = st.shopDocs[popupShop] ?: emptyMap()
        val pr = (st.pricing ?: Pricing()).withShopType(shop.str("shopType").ifEmpty { "normal" })
        ItemSheet(p, pr, onDismiss = { popup = null }) { note ->
            val phone = try { org.json.JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() }
            if (phone.isEmpty()) {
                android.widget.Toast.makeText(ctx, "กรุณาเข้าสู่ระบบก่อนสั่งซื้อครับ", android.widget.Toast.LENGTH_SHORT).show()
                session.loginReturnRoute = "category/$buttonId"; popup = null; onLogin(); return@ItemSheet
            }
            val miss = p.missing()
            if (miss.isNotEmpty()) { p.errors = miss; return@ItemSheet }
            // ยังเก็บลงกลุ่มของร้านนั้น (ออเดอร์/ค่าส่ง/ไรเดอร์แยกตามร้าน) แต่ติดแท็กหมวดไว้ให้แสดงในตะกร้า
            val item = org.json.JSONObject().put("id", p.id).put("name", p.m.str("name")).put("basePrice", num(p.m["price"]) ?: 0.0)
                .put("price", p.unitPrice(pr)).put("qty", p.qty).put("imgUrl", p.m.str("imgUrl"))
                .put("options", p.selectedOptions()).put("optionGroups", deepJson(p.m["optionGroups"] ?: emptyList<Any>()))
                .put("note", note).put("gp", p.menuGp).put("category", p.m.str("category"))
                .put("fromCategoryId", buttonId).put("fromCategoryName", title)
            CartStore.add(cart, popupShop, st.shopNames[popupShop].orEmpty(), item); CartStore.save(session, cart); tick++; popup = null
        }
    }
}

/** (จำนวน, ยอดรวม, รายการ shopId) ของรายการที่เพิ่มจากหมวดนี้ */
private fun taggedTotals(c: org.json.JSONObject, catId: String): Triple<Int, Double, List<String>> {
    var q = 0; var t = 0.0; val shops = linkedSetOf<String>()
    c.keys().forEach { sid ->
        val a = c.optJSONObject(sid)?.optJSONArray("items") ?: return@forEach
        for (i in 0 until a.length()) { val it = a.getJSONObject(i); if (it.optString("fromCategoryId") == catId) { q += it.optInt("qty"); t += it.optDouble("price") * it.optInt("qty"); shops += sid } }
    }
    return Triple(q, t, shops.toList())
}
