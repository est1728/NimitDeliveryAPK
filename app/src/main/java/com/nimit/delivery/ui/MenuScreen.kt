package com.nimit.delivery.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

private val PRIMARY = Color(0xFF0C4AA6)
private val PRIMARY_DARK = Color(0xFF083570)
private val PRIMARY_LIGHT = Color(0xFFE8F0FE)
private val BG = Color(0xFFF5F6F8)
private const val CART_PATH = "M7 18c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zM17 18c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zM7.17 14l.94-2h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1 1 0 0 0 19.97 3H5.21l-.94-2H1v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45z"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(session: Session, shopIdArg: String, onBack: () -> Unit, onCart: () -> Unit, onCheckout: () -> Unit, onLogin: () -> Unit) {
    val ctx = LocalContext.current
    var shopId by remember { mutableStateOf(shopIdArg) }
    var shop by remember { mutableStateOf<Doc?>(null) }
    var shopName by remember { mutableStateOf(session.selectedShopName.orEmpty()) }
    var pricing by remember { mutableStateOf(Pricing()) }
    var menus by remember { mutableStateOf<List<Pair<String, Doc>>>(emptyList()) }
    var status by remember { mutableStateOf("loading") } // loading | ok | error | notfound
    var resolved by remember { mutableStateOf<String?>(null) }
    var cart by remember { mutableStateOf(CartStore.load(session)) }
    var cartTick by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("ทั้งหมด") }
    var popup by remember { mutableStateOf<PopupState?>(null) }
    var profile by remember { mutableStateOf(false) }
    var bigImage by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }

    LaunchedEffect(shopIdArg) {
        if (shopIdArg.isEmpty()) { status = "notfound"; return@LaunchedEffect }
        try {
            val db = Firebase.firestore
            var id = shopIdArg
            var snap = db.collection("shops").document(id).get().await()
            if (!snap.exists()) {
                val q = db.collection("shops").whereEqualTo("slug", id).get().await()
                if (!q.isEmpty) { snap = q.documents[0]; id = snap.id; session.selectedShopId = id }
            }
            val d = if (snap.exists()) snap.data else null
            if (d != null) { shop = d; shopName = d.str("name").ifEmpty { "ร้านค้า" }; session.selectedShopName = d.str("name") }
            val s = try { db.collection("settings").document("pricing").get().await().data } catch (_: Exception) { null }
            pricing = Pricing.from(s).withShopType(d?.str("shopType")?.ifEmpty { "normal" } ?: "normal")
            shopId = id; resolved = id
        } catch (_: Exception) { status = "error" }
    }

    DisposableEffect(resolved) {
        val id = resolved
        val reg = if (id == null) null else Firebase.firestore.collection("shops").document(id).collection("menus").addSnapshotListener { snap, err ->
            if (err != null) { status = "error"; return@addSnapshotListener }
            menus = snap?.documents?.map { it.id to (it.data ?: emptyMap()) } ?: emptyList()
            status = "ok"
        }
        onDispose { reg?.remove() }
    }

    val open = remember(shop, tick) { shop?.let { computeShopOpenNow(it) } ?: true }
    val shopCats = remember(shop) { asMapList(shop?.get("categories")).map { it.str("name") } }
    val cats = remember(menus, shopCats) { listOf("ทั้งหมด") + if (shopCats.isNotEmpty()) shopCats else menus.map { it.second.str("category") }.filter { it.isNotEmpty() }.distinct() }
    val filtered = remember(menus, cat, search) {
        menus.filter { (_, m) -> (cat == "ทั้งหมด" || m.str("category") == cat) && (search.isBlank() || m.str("name").lowercase().contains(search.lowercase())) }
    }
    val groups = remember(filtered, cat, shopCats) {
        val names = if (cat != "ทั้งหมด") listOf(cat) else if (shopCats.isNotEmpty()) shopCats else filtered.map { it.second.str("category") }.filter { it.isNotEmpty() }.distinct()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Pair<String, List<Pair<String, Doc>>>>()
        names.forEach { n ->
            val items = filtered.filter { it.second.str("category") == n }.sortedBy { it.second.dbl("order") ?: 0.0 }
            if (items.isNotEmpty()) { seen += n; out += n to items }
        }
        val rest = filtered.filter { it.second.str("category") !in seen }.sortedBy { it.second.dbl("order") ?: 0.0 }
        if (rest.isNotEmpty()) out += "เมนูอื่นๆ" to rest
        out
    }
    val (cartQty, _) = remember(cartTick, cart) { CartStore.totals(cart) }
    val (shopQty, shopTotal) = remember(cartTick, cart, shopId) { shopTotals(cart, shopId) }

    fun persist() { CartStore.save(session, cart); cartTick++ }
    fun goCheckout() {
        // checkout อ่าน checkoutShops (รายการ id ร้านที่เลือก) — รูปแบบเดียวกับที่หน้าตะกร้าส่งให้
        ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE).edit()
            .putString("checkoutShops", org.json.JSONArray().put(shopId).toString()).apply()
        onCheckout()
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(110.dp).background(PRIMARY_LIGHT)) {
                    val banner = shop?.str("bannerUrl").orEmpty()
                    if (banner.isNotEmpty()) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable { bigImage = banner })
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.size(38.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", C.Text, 20.dp, stroke = true) }
                        Box(Modifier.size(38.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { onCart() }, contentAlignment = Alignment.Center) {
                            PathIcon(CART_PATH, PRIMARY, 20.dp)
                            if (cartQty > 0) Text("$cartQty", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).defaultMinSize(minWidth = 16.dp).height(16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE5484D)).padding(horizontal = 3.dp))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Box(Modifier.offset(y = (-30).dp).size(72.dp).shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).border(3.dp, Color.White, RoundedCornerShape(18.dp)).clickable { profile = true }, contentAlignment = Alignment.Center) {
                        val av = shop?.str("avatarUrl").orEmpty()
                        if (av.isNotEmpty()) AsyncImage(av, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else PathIcon("M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z", Color(0xFF94A3B8), 30.dp)
                    }
                    Column(Modifier.offset(y = (-18).dp).clickable { profile = true }) {
                        Text(shopName.ifEmpty { "ร้านค้า" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        val desc = shop?.str("desc").orEmpty()
                        if (desc.isNotEmpty()) Text(desc, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (!open) {
                        val reason = shop?.str("closeReason").orEmpty()
                        Text(if (reason.isNotEmpty()) "ร้านปิดอยู่ เหตุผล: $reason" else "ร้านนี้ปิดอยู่ในขณะนี้", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFEE2E2)).padding(12.dp))
                    }
                    Text("เมนูอาหาร", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(top = 0.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth().height(46.dp).shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PathIcon(P.SEARCH, Color(0xFF9CA3AF), 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (search.isEmpty()) Text("ค้นหาเมนู...", fontSize = 15.sp, color = Color(0xFF9CA3AF))
                            BasicTextField(search, { search = it }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = C.Text), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cats) { c ->
                            val on = c == cat
                            Text(c, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext,
                                modifier = Modifier.shadow(if (on) 0.dp else 2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) PRIMARY else Color.White).clickable { cat = c }.padding(horizontal = 18.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            if (menus.isEmpty() || groups.isEmpty()) item {
                Text(
                    when { status == "loading" -> "กำลังโหลดเมนู..."; status == "error" -> "โหลดไม่ได้"; status == "notfound" -> "ไม่พบร้านค้า กรุณากลับไปเลือกร้านใหม่อีกครั้ง"; else -> "ไม่มีเมนู" },
                    fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)
                )
            }
            groups.forEach { (name, list) ->
                item(key = "h-$name") {
                    Box(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 10.dp).fillMaxWidth()) {
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().drawBehindBorders().padding(vertical = 12.dp))
                    }
                }
                items(list, key = { it.first }) { (id, m) ->
                    val dp = pricing.calcPrice(num(m["price"]) ?: 0.0, num(m["gp"]) ?: 0.0)
                    val qty = remember(cartTick, cart) { CartStore.qtyInCart(cart, shopId, id) }
                    val soldOut = m["available"] == false || !isMenuWithinSchedule(m)
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().alpha(if (soldOut) 0.55f else 1f).shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable(enabled = !soldOut) { popup = PopupState(id, m) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(BG), contentAlignment = Alignment.Center) {
                            if (m.str("imgUrl").isNotEmpty()) AsyncImage(m.str("imgUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else PathIcon(P.RESTAURANT, Color(0xFFCBD5E1), 32.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.str("name"), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 4.dp))
                            if (m.str("desc").isNotEmpty()) Text(m.str("desc"), fontSize = 13.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("฿$dp", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                                if (soldOut) Text("สินค้าหมด", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFFEE2E2)).padding(horizontal = 12.dp, vertical = 5.dp))
                                else if (qty > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(PRIMARY_LIGHT).clickable { CartStore.removeOne(cart, shopId, id); persist() }, contentAlignment = Alignment.Center) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY) }
                                    Text("$qty", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(PRIMARY).clickable { popup = PopupState(id, m) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                                } else Box(Modifier.size(42.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(PRIMARY).clickable { popup = PopupState(id, m) }, contentAlignment = Alignment.Center) { PathIcon(CART_PATH, Color.White, 20.dp) }
                            }
                        }
                    }
                }
            }
        }

        // แถบสรุปคำสั่งซื้อ (ลอย ไม่มีพื้นขาว) — กดแล้วไป checkout ของร้านนี้ทันที
        if (shopQty > 0) {
            val barShape = RoundedCornerShape(16.dp)
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .shadow(14.dp, barShape, ambientColor = PRIMARY.copy(alpha = 0.45f), spotColor = PRIMARY.copy(alpha = 0.45f))
                    .clip(barShape).background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK)))
                    .clickable { goCheckout() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { PathIcon(CART_PATH, Color.White, 20.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$shopQty รายการ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    Text("฿${fmtNum(shopTotal)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text("สรุปคำสั่งซื้อ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                PathIcon(P.CHEVRON, Color.White, 24.dp)
            }
        }
    }

    // POPUP ตัวเลือกเมนู
    popup?.let { st ->
        ModalBottomSheet(
            onDismissRequest = { popup = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White, dragHandle = null, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            var note by remember(st) { mutableStateOf("") }
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(BG), contentAlignment = Alignment.Center) {
                        val img = st.m.str("imgUrl")
                        if (img.isNotEmpty()) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else PathIcon(P.RESTAURANT, Color(0xFFCBD5E1), 56.dp)
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(st.m.str("name"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text("฿${pricing.calcPrice(num(st.m["price"]) ?: 0.0, st.menuGp)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        PopupBody(st, pricing)
                        Text("คำแนะนำเพิ่มเติม", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 6.dp))
                        NimitInput(note, { note = it }, "เช่น ไม่ใส่ผัก ไม่เผ็ด", height = 80.dp, singleLine = false, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                HorizontalDividerLine()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("จำนวน", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, Color(0xFFE2E8F0), CircleShape).clickable { st.qty = maxOf(1, st.qty - 1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY) }
                        Text("${st.qty}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(PRIMARY).clickable { st.qty += 1 }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F5F9)).clickable { popup = null }, contentAlignment = Alignment.Center) { Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Subtext) }
                    Box(Modifier.weight(2f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(PRIMARY, PRIMARY_DARK))).clickable {
                        val phone = try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() }
                        if (phone.isEmpty()) {
                            Toast.makeText(ctx, "กรุณาเข้าสู่ระบบก่อนสั่งซื้อครับ", Toast.LENGTH_SHORT).show()
                            session.loginReturnRoute = "menu/$shopId"; popup = null; onLogin(); return@clickable
                        }
                        val miss = st.missing()
                        if (miss.isNotEmpty()) { st.errors = miss; return@clickable }
                        val item = JSONObject().put("id", st.id).put("name", st.m.str("name")).put("basePrice", num(st.m["price"]) ?: 0.0)
                            .put("price", st.unitPrice(pricing)).put("qty", st.qty).put("imgUrl", st.m.str("imgUrl"))
                            .put("options", st.selectedOptions()).put("optionGroups", deepJson(st.m["optionGroups"] ?: emptyList<Any>()))
                            .put("note", note).put("gp", st.menuGp).put("category", st.m.str("category"))
                        CartStore.add(cart, shopId, shopName, item); persist(); popup = null
                    }, contentAlignment = Alignment.Center) {
                        Text("เพิ่มลงตะกร้า ฿${st.total(pricing)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }

    // โปรไฟล์ร้าน
    if (profile) Dialog(onDismissRequest = { profile = false }) {
        val d = shop ?: emptyMap()
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ข้อมูลร้านค้า", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("✕", fontSize = 16.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1F5F9)).clickable { profile = false }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Row(Modifier.padding(top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(PRIMARY_LIGHT).clickable(enabled = d.str("avatarUrl").isNotEmpty()) { bigImage = d.str("avatarUrl") }, contentAlignment = Alignment.Center) {
                    if (d.str("avatarUrl").isNotEmpty()) AsyncImage(d.str("avatarUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else PathIcon("M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z", Color(0xFF94A3B8), 32.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(shopName.ifEmpty { "ร้านค้า" }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    Text(if (open) "เปิดอยู่ตอนนี้" else "ปิดอยู่ในขณะนี้", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (open) Color(0xFF16A34A) else Color(0xFFDC2626), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(d.str("desc").ifEmpty { "ยังไม่มีคำอธิบายร้าน" }, fontSize = 14.sp, color = Color(0xFF334155), lineHeight = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
            if (truthy(d["autoSchedule"]) && d.str("scheduleOpen").isNotEmpty() && d.str("scheduleClose").isNotEmpty())
                Text("เวลาเปิด-ปิด: ${d.str("scheduleOpen")} - ${d.str("scheduleClose")}", fontSize = 13.sp, color = C.Subtext)
        }
    }

    // รูปเต็มจอ
    bigImage?.let { url ->
        Dialog(onDismissRequest = { bigImage = null }) {
            AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { bigImage = null })
        }
    }
}

@Composable private fun HorizontalDividerLine() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5))) }

private fun Modifier.drawBehindBorders(): Modifier = this.then(
    Modifier.drawBehind {
        val w = 1.5.dp.toPx()
        drawLine(PRIMARY, androidx.compose.ui.geometry.Offset(0f, w / 2), androidx.compose.ui.geometry.Offset(size.width, w / 2), w)
        drawLine(PRIMARY, androidx.compose.ui.geometry.Offset(0f, size.height - w / 2), androidx.compose.ui.geometry.Offset(size.width, size.height - w / 2), w)
    }
)

private fun shopTotals(c: JSONObject, id: String): Pair<Int, Double> {
    val items = c.optJSONObject(id)?.optJSONArray("items") ?: return 0 to 0.0
    var q = 0; var t = 0.0
    for (i in 0 until items.length()) { val it = items.getJSONObject(i); q += it.optInt("qty"); t += it.optDouble("price") * it.optInt("qty") }
    return q to t
}
