package com.nimit.delivery.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt

private val SP = Color(0xFF0C4AA6)
private val SGRAY = Color(0xFFF1F5F9)
private val HL = Color(0xFFFFF9C4)

private data class MenuHit(val id: String, val shopId: String, val d: Doc)

/** จับคำผิดเล็กน้อย (พอร์ตจาก fuzzyMatch ของเว็บ) */
private fun fuzzy(text: String, query: String): Boolean {
    if (text.isEmpty() || query.isEmpty()) return false
    val t = text.lowercase(); val q = query.lowercase()
    if (t.contains(q)) return true
    if (abs(t.length - q.length) > 4) return false
    fun bigrams(s: String) = (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()
    val tb = bigrams(t); val qb = bigrams(q)
    val common = qb.count { it in tb }
    if (qb.isNotEmpty() && common.toDouble() / qb.size >= 0.5) return true
    var ti = 0; var qi = 0; var matched = 0
    while (ti < t.length && qi < q.length) { if (t[ti] == q[qi]) { matched++; qi++ }; ti++ }
    return matched.toDouble() / q.length >= 0.75
}

private fun highlight(text: String, q: String): AnnotatedString = buildAnnotatedString {
    if (q.isEmpty()) { append(text); return@buildAnnotatedString }
    val lt = text.lowercase(); val lq = q.lowercase(); var i = 0
    while (i < text.length) {
        val at = lt.indexOf(lq, i)
        if (at < 0) { append(text.substring(i)); break }
        append(text.substring(i, at))
        withStyle(SpanStyle(background = HL)) { append(text.substring(at, at + lq.length)) }
        i = at + lq.length
    }
}

@Composable
fun SearchScreen(session: Session, onBack: () -> Unit, onOpenShop: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE) }
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var shops by remember { mutableStateOf<List<Pair<String, Doc>>>(emptyList()) }
    var menus by remember { mutableStateOf<List<MenuHit>>(emptyList()) }
    var pricing by remember { mutableStateOf(Pricing()) }
    var ready by remember { mutableStateOf(false) }
    var popular by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf(readHistory(prefs)) }

    LaunchedEffect(Unit) { requester.requestFocus() }
    LaunchedEffect(text) { delay(250); query = text.trim() }   // debounce 250ms เหมือนเว็บ

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        launch {
            try {
                popular = db.collection("searchLogs").get().await().documents
                    .map { it.id to ((it.getLong("count") ?: 0L)) }.sortedByDescending { it.second }.take(8).map { it.first }
            } catch (_: Exception) {}
        }
        try { pricing = Pricing.from(db.collection("settings").document("pricing").get().await().data) } catch (_: Exception) {}
        val s = try { db.collection("shops").get().await().documents.map { it.id to (it.data ?: emptyMap()) } } catch (_: Exception) { emptyList() }
        shops = s
        menus = coroutineScope {
            s.map { (sid, _) -> async { try { db.collection("shops").document(sid).collection("menus").get().await().documents.map { MenuHit(it.id, sid, it.data ?: emptyMap()) } } catch (_: Exception) { emptyList() } } }.awaitAll()
        }.flatten()
        ready = true
    }

    fun saveHistory(q: String) {
        val h = readHistory(prefs).filter { it != q }.toMutableList(); h.add(0, q)
        prefs.edit().putString("searchHistory", JSONArray(h.take(10)).toString()).apply()
        history = readHistory(prefs)
        if (q.length >= 2) scope.launch { try { Firebase.firestore.collection("searchLogs").document(q).set(mapOf("count" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await() } catch (_: Exception) {} }
    }
    // กดผลค้นหา: ร้าน -> เข้าร้านนั้น / เมนู -> เข้าร้านแล้วเด้งป๊อบอัพเมนูนั้น (หน้าเมนูอ่านค่า openItem แล้วเปิดป๊อบอัพเอง)
    fun pick(shopId: String, shopName: String, itemId: String? = null) {
        if (shopId.isEmpty()) { Toast.makeText(ctx, "ไม่พบร้านของรายการนี้", Toast.LENGTH_SHORT).show(); return }
        saveHistory(query.ifEmpty { shopName })
        session.selectedShopId = shopId; session.selectedShopName = shopName
        prefs.edit().apply { if (itemId != null) putString("openItem", "$shopId|$itemId|${System.currentTimeMillis()}") else remove("openItem") }.apply()
        onOpenShop(shopId)
    }

    val shopRes = remember(query, shops) { if (query.isEmpty()) emptyList() else shops.filter { (_, d) -> fuzzy(d.str("name"), query) || fuzzy(d.str("desc"), query) || fuzzy(d.str("name") + d.str("desc"), query) } }
    val menuRes = remember(query, menus) { if (query.isEmpty()) emptyList() else menus.filter { fuzzy(it.d.str("name"), query) || fuzzy(it.d.str("desc"), query) } }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(SGRAY).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", C.Text, 18.dp, stroke = true) }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(SGRAY).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PathIcon(P.SEARCH, Color(0xFF9CA3AF), 20.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("ค้นหาร้านหรือเมนู...", fontSize = 15.sp, color = Color(0xFF9CA3AF))
                    BasicTextField(
                        text, { text = it }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = C.Text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { query = text.trim(); focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(requester)
                    )
                }
                if (text.isNotEmpty()) Text("✕", fontSize = 14.sp, color = C.Subtext, modifier = Modifier.clickable { text = ""; query = "" }.padding(start = 8.dp))
            }
        }
        if (query.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
                if (history.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🕐 ค้นหาล่าสุด", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                        Text("ล้างทั้งหมด", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SP, modifier = Modifier.clickable { prefs.edit().remove("searchHistory").apply(); history = emptyList() })
                    }
                    ChipFlow(history, Color(0xFF475569), SGRAY) { text = it; query = it }
                }
                if (popular.isNotEmpty()) {
                    Text("🔥 ค้นหายอดนิยม", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    ChipFlow(popular, Color(0xFFEA580C), Color(0xFFFFF7ED)) { text = it; query = it }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (shopRes.isNotEmpty()) {
                item { Divider("ร้านค้า (${shopRes.size})") }
                items(shopRes.take(5), key = { "s" + it.first }) { (id, s) ->
                    Row(Modifier.fillMaxWidth().clickable { pick(id, s.str("name")) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FE)), contentAlignment = Alignment.Center) {
                            if (s.str("avatarUrl").isNotEmpty()) AsyncImage(s.str("avatarUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else PathIcon("M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z", SP, 22.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(highlight(s.str("name"), query), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                            Text("⭐${"%.2f".format(s.dbl("rating") ?: 0.0)} · ${s["deliveryTime"]?.toString()?.takeIf { it.isNotBlank() } ?: "20-30"} นาที", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (computeShopOpenNow(s)) Text("เปิด", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }
            if (!ready) item { Text("⏳ กำลังโหลดเมนู...", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(16.dp)) }
            if (menuRes.isNotEmpty()) {
                item { Divider("เมนู (${menuRes.size})") }
                items(menuRes.take(10), key = { "m" + it.shopId + it.id }) { m ->
                    val shop = shops.firstOrNull { it.first == m.shopId }?.second
                    val price = pricing.withShopType(shop?.str("shopType")?.ifEmpty { "normal" } ?: "normal").calcPrice(num(m.d["price"]) ?: 0.0, num(m.d["gp"]) ?: 0.0)
                    val img = m.d.str("imgUrl").ifEmpty { m.d.str("imageUrl") }
                    Row(Modifier.fillMaxWidth().clickable { pick(m.shopId, shop?.str("name").orEmpty(), m.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(SGRAY), contentAlignment = Alignment.Center) {
                            if (img.isNotEmpty()) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("🍽", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(highlight(m.d.str("name"), query), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("📍 ${shop?.str("name")?.ifEmpty { null } ?: "ไม่ทราบร้าน"}", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text("฿$price", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = SP)
                    }
                }
            }
            if (shopRes.isEmpty() && menuRes.isEmpty() && ready) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 52.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PathIcon(P.SEARCH, Color(0xFFCBD5E1), 48.dp)
                    Text("ไม่พบ \"$query\"", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Text("ลองค้นหาด้วยคำอื่น", fontSize = 13.sp, color = C.Subtext)
                }
            }
        }
    }
}

private fun readHistory(p: android.content.SharedPreferences): List<String> = try {
    JSONArray(p.getString("searchHistory", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
} catch (_: Exception) { emptyList() }

@Composable private fun Divider(t: String) {
    Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 16.dp, vertical = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ChipFlow(items: List<String>, fg: Color, bg: Color, onClick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { q -> Text(q, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).clickable { onClick(q) }.padding(horizontal = 14.dp, vertical = 7.dp)) }
    }
}
