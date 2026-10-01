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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CP = Color(0xFF0C4AA6)
private val CPD = Color(0xFF083570)
private val CPL = Color(0xFFE8F0FE)
private val CBG = Color(0xFFF5F6F8)

// ขนาดคงที่ทุกใบ — การ์ดสูงเท่ากัน ปุ่ม/ป้ายซ้ายกว้างเท่ากัน ไม่ว่าข้อความจะยาวสั้นแค่ไหน
private val CARD_H = 104.dp
private val STUB_W = 92.dp
private val BTN_W = 88.dp

private class Cp(val id: String, val d: Doc)
private class Loy(val shopName: String, val points: Int, val target: Int, val avatar: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CouponScreen(session: Session, onBack: () -> Unit, onHome: () -> Unit, onHistory: () -> Unit, onLogin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val phone = remember { try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() } }
    var tab by remember { mutableStateOf("coupon") }
    var coupons by remember { mutableStateOf<List<Cp>?>(null) }
    var claimed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var couponErr by remember { mutableStateOf<String?>(null) }
    var claiming by remember { mutableStateOf<String?>(null) }
    var loyalty by remember { mutableStateOf<List<Loy>?>(null) }
    var loyaltyErr by remember { mutableStateOf<String?>(null) }

    suspend fun loadCoupons() {
        try {
            val db = Firebase.firestore
            val now = System.currentTimeMillis()
            val list = db.collection("coupons").get().await().documents.map { Cp(it.id, it.data ?: emptyMap()) }
                .filter { it.d["active"] != false && (num(it.d["endAt"]) ?: 0.0) > now }.sortedBy { num(it.d["endAt"]) ?: 0.0 }
            if (phone.isNotEmpty()) claimed = db.collection("claimedCoupons").whereEqualTo("phone", phone).get().await().documents.mapNotNull { it.getString("couponId") }.toSet()
            coupons = list
        } catch (e: Exception) { couponErr = e.message ?: "เกิดข้อผิดพลาด" }
    }
    LaunchedEffect(Unit) { loadCoupons() }
    LaunchedEffect(tab) {
        if (tab == "loyalty" && loyalty == null && loyaltyErr == null) {
            if (phone.isEmpty()) { loyalty = emptyList(); return@LaunchedEffect }
            try {
                val db = Firebase.firestore
                val cards = withTimeout(10_000) { db.collection("loyaltyCards").whereEqualTo("customerPhone", phone).get().await().documents.map { it.data ?: emptyMap() } }
                val info = coroutineScope {
                    cards.map { it.str("shopId") }.distinct().map { sid -> async { sid to (try { db.collection("shops").document(sid).get().await().data } catch (_: Exception) { null }) } }.map { it.await() }
                }.toMap()
                loyalty = cards.map { c -> val i = info[c.str("shopId")]
                    Loy(c.str("shopName"), (num(c["points"]) ?: 0.0).toInt(), (num(i?.get("loyaltyTarget")) ?: 10.0).toInt().coerceAtLeast(1), i?.str("avatarUrl").orEmpty()) }
            } catch (e: Exception) { loyaltyErr = e.message ?: "หมดเวลารอ ลองใหม่อีกครั้ง" }
        }
    }

    fun claim(id: String) {
        if (phone.isEmpty()) { Toast.makeText(ctx, "กรุณาเข้าสู่ระบบก่อนเก็บคูปองครับ", Toast.LENGTH_SHORT).show(); session.loginReturnRoute = "coupon"; onLogin(); return }
        claiming = id
        scope.launch {
            try {
                Firebase.firestore.collection("claimedCoupons").add(mapOf("phone" to phone, "couponId" to id, "claimedAt" to FieldValue.serverTimestamp(), "used" to false)).await()
                claimed = claimed + id
            } catch (e: Exception) { Toast.makeText(ctx, "เก็บคูปองไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
            claiming = null
        }
    }

    Box(Modifier.fillMaxSize().background(CBG)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CP, CPD))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true) }
                Spacer(Modifier.width(12.dp))
                Text("คูปอง & แต้มสะสม", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            // แบนเนอร์ตั๋ว
            Row(
                Modifier.padding(16.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = CP.copy(alpha = 0.32f), spotColor = CP.copy(alpha = 0.32f)).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(CP, CPD)))
                    .drawWithContent {
                        drawContent()
                        val x = 84.dp.toPx()
                        drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 14.dp.toPx()), Offset(x, size.height - 14.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        drawCircle(CBG, 7.dp.toPx(), Offset(x, 0f)); drawCircle(CBG, 7.dp.toPx(), Offset(x, size.height))
                    }
            ) {
                Box(Modifier.width(84.dp).height(88.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { PathIcon(P.TAG, Color.White, 26.dp) }
                }
                Column(Modifier.weight(1f).padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.Center) {
                    Text("คูปองส่วนลด", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("รวมคูปองส่วนลดและสิทธิพิเศษของคุณไว้ที่นี่", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // แท็บ
            Row(Modifier.fillMaxWidth().background(Color.White)) {
                listOf("coupon" to "คูปอง", "loyalty" to "สะสมแต้ม").forEach { (k, label) ->
                    Box(Modifier.weight(1f).height(46.dp).clickable { tab = k }, contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (tab == k) CP else C.Subtext)
                        if (tab == k) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.6f).height(2.dp).clip(RoundedCornerShape(2.dp)).background(CP))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == "coupon") {
                    val list = coupons
                    when {
                        couponErr != null -> Msg("โหลดไม่สำเร็จ: $couponErr")
                        list == null -> Loading()
                        list.isEmpty() -> Msg("ยังไม่มีคูปองในขณะนี้\nรอติดตามโปรโมชันเร็วๆ นี้ครับ")
                        else -> {
                            // ใบที่ยังไม่เก็บขึ้นก่อน ใบที่เก็บแล้วไปอยู่ท้ายรายการ (เรียงตามวันหมดเขตภายในกลุ่ม)
                            val ordered = list.filter { it.id !in claimed } + list.filter { it.id in claimed }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(ordered, key = { it.id }) { c ->
                                    val got = c.id in claimed
                                    val big = when (c.d.str("type")) { "percent" -> "${fmtNum(num(c.d["value"]) ?: 0.0)}%"; "fixed" -> "฿${fmtNum(num(c.d["value"]) ?: 0.0)}"; else -> "ส่งฟรี" }
                                    val end = (num(c.d["endAt"]) ?: 0.0).toLong()
                                    val mn = num(c.d["minOrderAmount"]) ?: 0.0
                                    val scope2 = if (c.d.str("shopId").isNotEmpty()) c.d.str("shopName").ifEmpty { "ร้านเฉพาะ" } else "ใช้ได้ทุกร้าน"
                                    Row(
                                        Modifier.fillMaxWidth().height(CARD_H).alpha(if (got) 0.75f else 1f).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                                            .drawWithContent { drawContent(); val x = STUB_W.toPx(); drawCircle(CBG, 7.dp.toPx(), Offset(x, 0f)); drawCircle(CBG, 7.dp.toPx(), Offset(x, size.height)) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.width(STUB_W).fillMaxHeight().background(Brush.linearGradient(listOf(CP, CPD))).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(big, fontSize = if (big.length > 5) 16.sp else 20.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                                            Text(if (c.d.str("type") == "freeship") "FREE SHIP" else "DISCOUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                                            Text(c.d.str("title"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                                            Text((if (mn > 0) "ขั้นต่ำ ฿${fmtNum(mn)} · " else "") + scope2, fontSize = 11.5.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("หมดเขต " + SimpleDateFormat("d MMM yy", Locale("th", "TH")).format(Date(end)), fontSize = 10.5.sp, color = Color(0xFFC2410C), maxLines = 1)
                                        }
                                        Box(
                                            Modifier.padding(end = 14.dp).width(BTN_W).height(34.dp).clip(RoundedCornerShape(20.dp)).background(if (got) Color(0xFFEEF0F4) else CP)
                                                .clickable(enabled = !got && claiming == null) { claim(c.id) }, contentAlignment = Alignment.Center
                                        ) { Text(if (got) "เก็บแล้ว" else if (claiming == c.id) "กำลังเก็บ" else "เก็บคูปอง", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (got) C.Subtext else Color.White, maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val list = loyalty
                    when {
                        loyaltyErr != null -> Msg("โหลดไม่สำเร็จ: $loyaltyErr")
                        phone.isEmpty() -> Msg("กรุณาเข้าสู่ระบบก่อนครับ")
                        list == null -> Loading()
                        list.isEmpty() -> Msg("ยังไม่มีบัตรสะสมแต้มเลยครับ\nลองสั่งร้านที่มีป้าย 🎫 สะสมแต้ม ดูได้เลย")
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(list) { c ->
                                val full = c.points / c.target; val partial = c.points % c.target
                                Column {
                                    if (full > 0) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                            repeat(full) { Text("🎫 ครบแล้ว", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A), modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0FDF4)).border(1.5.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) }
                                        }
                                        Text("มี $full ใบที่ใช้สิทธิ์รับฟรีได้ตอนชำระเงินร้านนี้ (ใช้ได้ครั้งละ 1 ใบต่อออเดอร์)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 10.dp))
                                    }
                                    Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
                                        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CPL, Color.White))).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(CP, CPD))), contentAlignment = Alignment.Center) {
                                                if (c.avatar.isNotEmpty()) AsyncImage(c.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                else Text(c.shopName.trim().take(1).uppercase().ifEmpty { "?" }, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(c.shopName, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text("กำลังสะสม $partial/${c.target}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC2410C), maxLines = 1, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF7ED)).border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
                                        }
                                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                                            FlowRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8FAFC)).padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                repeat(c.target) { i -> PathIcon(P.STAR, if (i < partial) Color(0xFFF59E0B) else Color(0xFFDFE4EC), 24.dp) }
                                            }
                                            Text(if (c.target - partial > 0) "อีก ${c.target - partial} แต้ม ครบ 1 ดวง รับสิทธิ์ฟรี 🎫" else "ครบแล้ว! ใช้สิทธิ์ได้ตอนชำระเงินครั้งถัดไป", fontSize = 11.5.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // แถบล่าง (เหมือนหน้าเว็บ: หน้าหลัก / ประวัติ / คูปอง / โซเชียล)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(8.dp).background(Color.White).navigationBarsPadding()) {
            BNav(P.HOME, "หน้าหลัก", false, Modifier.weight(1f)) { onHome() }
            BNav(P.HISTORY, "ประวัติ", false, Modifier.weight(1f)) { onHistory() }
            BNav(P.TAG, "คูปอง", true, Modifier.weight(1f)) {}
            BNav(P.SOCIAL, "โซเชียล", false, Modifier.weight(1f).alpha(0.55f)) { Toast.makeText(ctx, "ระบบโซเชียลเร็วๆ นี้ครับ", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable private fun BNav(icon: String, label: String, active: Boolean, m: Modifier, onClick: () -> Unit) {
    Column(m.clickable { onClick() }.padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PathIcon(icon, if (active) CP else C.Subtext, 22.dp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) CP else C.Subtext)
    }
}

@Composable private fun Loading() {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = CP, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        Text("กำลังโหลด...", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable private fun Msg(t: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { PathIcon(P.TAG, Color(0xFFCBD5E1), 30.dp) }
        Text(t, fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, lineHeight = 24.sp, modifier = Modifier.padding(top = 16.dp))
    }
}
