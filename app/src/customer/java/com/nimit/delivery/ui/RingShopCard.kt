package com.nimit.delivery.ui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// การ์ดร้านแบบใหม่ ("Ring Menu Card") — แตะแล้วการ์ดขยายในที่เดิม มีวงแหวนเมนู 3D ลากหมุนได้ แตะเมนูหน้าสุดเพื่อเลือกตัวเลือกแล้วใส่ตะกร้า
// ตรรกะราคา/ตัวเลือก/ตะกร้าใช้ของเดิม (PopupState, ItemSheet, CartStore) เหมือนหน้าเมนู

private const val RING_STEP = 30f
private const val RING_RAD = 240f
private const val RING_PX = 64f
/** ค่าตามสูตร CSS ต้นฉบับ: perspective(700px) translate3d(sin*RAD, -(1-sp)*50, (c-1)*RAD) rotateY(deg) scale(sc) */
private fun ringScale(c: Float): Float = 1f + 0.03f * max(0f, (c - 0.87f) / 0.13f) - 0.06f * min(1f, max(0f, (0.87f - c) / 0.37f))
private fun ringCos(d: Float): Float = if (abs(d) > 6f) -1f else cos(Math.toRadians((d * RING_STEP).toDouble())).toFloat()

/** ขอบซ้าย/ขวาของการ์ดที่ตำแหน่ง d หลังฉายภาพ (หน่วย dp จากกึ่งกลางวง) ใช้หาว่าแตะโดนใบไหน */
private fun ringExtent(d: Float): FloatArray {
    val a = Math.toRadians((d * RING_STEP).toDouble()); val c = ringCos(d); val sc = ringScale(c)
    val tx = sin(a).toFloat() * RING_RAD; val tz = (c - 1f) * RING_RAD
    var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
    for (sx in floatArrayOf(-60f, 60f)) {
        val x = sx * sc
        val xx = x * cos(a).toFloat() + tx; val zz = -x * sin(a).toFloat() + tz
        val v = xx * 700f / (700f - zz)
        lo = min(lo, v); hi = max(hi, v)
    }
    return floatArrayOf(lo, hi)
}

/** เมทริกซ์ฉายภาพเหมือน CSS (หน่วยพิกเซล) รอบกึ่งกลางการ์ด */
private fun ringMatrix(d: Float, s: Float, density: Float): Matrix {
    val a = Math.toRadians((d * RING_STEP * s).toDouble()); val th = Math.toRadians((d * RING_STEP).toDouble())
    val c = ringCos(d); val sc = ringScale(c)
    val tx = sin(th).toFloat() * RING_RAD * s * density
    val ty = -(1f - s) * 50f * density
    val tz = (c - 1f) * RING_RAD * s * density
    val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
    val per = 700f * density
    val v = FloatArray(16)
    v[0] = sc * cs; v[3] = sc * sn / per
    v[5] = sc
    v[10] = 1f
    v[12] = tx; v[13] = ty; v[15] = 1f - tz / per
    return Matrix(v)
}
private val RING_BACK = Easing { k -> val q = k - 1f; 1f + 2.1f * q * q * q + 1.1f * q * q }
private val RING_PAL = listOf(0xFFB45309, 0xFFCA8A04, 0xFFC2410C, 0xFF15803D, 0xFFB91C1C, 0xFFDC2626, 0xFFEA580C, 0xFF9F1239, 0xFF0EA5E9, 0xFF7C2D12, 0xFFA16207, 0xFFBE185D)
private val RING_SUB = Color(0xFF64748B)

/** เงา 3 ชั้นของการ์ดต้นแบบ: วงแหวนฟ้าจาง 3px + แสงเรืองฟ้า 14px + เงาตกพื้นนุ่ม (0 6px 20px) */
private fun Modifier.cardGlow(radius: androidx.compose.ui.unit.Dp): Modifier = this.drawBehind {
    val r = radius.toPx(); val sp = 3.dp.toPx()
    drawRoundRect(Color(0x1F2E6FE0), Offset(-sp, -sp), Size(size.width + 2 * sp, size.height + 2 * sp), CornerRadius(r + sp))
    drawIntoCanvas { c ->
        val fp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        fp.color = android.graphics.Color.WHITE
        fp.setShadowLayer(12.dp.toPx(), 0f, 0f, Color(0x592E6FE0).toArgb())
        c.nativeCanvas.drawRoundRect(android.graphics.RectF(0f, 0f, size.width, size.height), r, r, fp)
        fp.setShadowLayer(18.dp.toPx(), 0f, 6.dp.toPx(), Color(0x1F0B2C63).toArgb())
        c.nativeCanvas.drawRoundRect(android.graphics.RectF(0f, 0f, size.width, size.height), r, r, fp)
    }
}

/** ปุ่ม "ปิดเมนู": โผล่/หายแบบ fade + เด้งขยาย (0.4↔1, หน่วง .12s, cubic-bezier(.34,1.3,.5,1)) กดแล้วหด .95 ตามต้นแบบ */
@Composable
private fun CloseMenuButton(a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, sc: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "press")
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .graphicsLayer { alpha = a.value; scaleX = sc.value * press; scaleY = sc.value * press }
            .shadow(6.dp, shape, ambientColor = N.B500.copy(alpha = 0.4f), spotColor = N.B500.copy(alpha = 0.4f))
            .clip(shape).background(Brush.linearGradient(listOf(N.B500, N.B700)))
            .clickable(interactionSource = src, indication = null) { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text("✕ ปิดเมนู", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

private object RingPricingCache { var base: Pricing? = null }

@Composable
fun RingShopCard(
    s: Shop, open: Boolean, expanded: Boolean, fav: Boolean, session: Session,
    onToggle: () -> Unit, onFav: () -> Unit, onReviews: () -> Unit, onGoMenu: () -> Unit, onCheckout: () -> Unit, onLogin: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menus by remember { mutableStateOf<List<Pair<String, Doc>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf(Pricing()) }
    var cat by remember { mutableStateOf("ทั้งหมด") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<PopupState?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf(false) }
    var cartList by remember { mutableStateOf(false) }
    var searchShown by remember { mutableStateOf(false) }
    val sx = remember { Animatable(0.12f) }
    val sy = remember { Animatable(0.8f) }
    val sAlpha = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val btnA = remember { Animatable(0f) }
    val btnS = remember { Animatable(0.4f) }
    LaunchedEffect(expanded) {
        delay(120)
        launch { btnA.animateTo(if (expanded) 1f else 0f, tween(500, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) }
        btnS.animateTo(if (expanded) 1f else 0.4f, tween(600, easing = CubicBezierEasing(0.34f, 1.3f, 0.5f, 1f)))
    }
    val favA by animateFloatAsState(if (expanded) 0f else 1f, tween(300), label = "fav")

    fun openSearch() {
        searchShown = true; searching = true
        scope.launch { sx.snapTo(0.12f); sx.animateTo(1f, keyframes { durationMillis = 800; 1.04f at 240; 0.985f at 384; 1.012f at 528; 0.998f at 656; 1f at 800 }) }
        scope.launch { sy.snapTo(0.8f); sy.animateTo(1f, keyframes { durationMillis = 800; 1.12f at 240; 0.92f at 384; 1.04f at 528; 0.985f at 656; 1f at 800 }) }
        scope.launch { sAlpha.snapTo(0f); delay(250); sAlpha.animateTo(1f, tween(200)); try { focus.requestFocus() } catch (_: Exception) { } }
    }
    fun closeSearch() {
        scope.launch {
            sAlpha.snapTo(0f)
            launch { sx.animateTo(0.12f, keyframes { durationMillis = 320; 0.55f at 128; 0.12f at 320 }) }
            launch { sy.animateTo(0.8f, tween(320)) }
            delay(330)
            query = ""; searching = false; searchShown = false
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            failed = false
            val t0 = System.currentTimeMillis()
            try {
                val db = Firebase.firestore
                val base = RingPricingCache.base ?: Pricing.from(try { db.collection("settings").document("pricing").get().await().data } catch (_: Exception) { null })
                RingPricingCache.base = base
                pricing = base.withShopType(s.d.str("shopType").ifEmpty { "normal" })
                val snap = db.collection("shops").document(s.id).collection("menus").get().await()
                val loaded = canonMenuCats(snap.documents.map { it.id to (it.data ?: emptyMap()) }, s.d["categories"])
                // รอให้กรอบร้านขยายเสร็จก่อนค่อยสร้างวงแหวน (เหมือนต้นฉบับ) ไม่งั้นงานสร้างการ์ดชุดใหญ่ไปแย่งเฟรมกับอนิเมชั่นขยายจนกระตุก
                val wait = 380L - (System.currentTimeMillis() - t0)
                if (wait > 0) delay(wait)
                menus = loaded
            } catch (_: Exception) { failed = true }
        } else {
            delay(420)
            menus = null; cat = "ทั้งหมด"; query = ""; searching = false; searchShown = false
        }
    }

    val shopCats = remember(s) { asMapList(s.d["categories"]).map { it.str("name") }.filter { it.isNotEmpty() } }
    val cats = remember(menus, shopCats) {
        listOf("ทั้งหมด") + (if (shopCats.isNotEmpty()) shopCats else (menus ?: emptyList()).map { it.second.str("category") }.filter { it.isNotEmpty() }.distinct())
    }
    val filtered = remember(menus, cat, query, cats) {
        (menus ?: emptyList()).filter { (_, m) ->
            (cat == "ทั้งหมด" || m.str("category") == cat) && (query.isBlank() || m.str("name").lowercase().contains(query.trim().lowercase()))
        }
            // เรียงตามลำดับหมวดของร้าน จบทีละหมวดก่อนไปหมวดถัดไป (ในหมวดเรียงตามลำดับเมนูที่ร้านตั้งไว้) หมวดที่ไม่รู้จักอยู่ท้าย
            .sortedWith(compareBy<Pair<String, Doc>>({ val i = cats.indexOf(it.second.str("category")); if (i < 0) Int.MAX_VALUE else i }, { it.second.dbl("order") ?: 0.0 }))
    }
    // ชิปหมวดล็อกตามเมนูตรงกลางวงแหวน (ไม่มีการกรองหมวดแล้ว) แตะชิป = หมุนไปเมนูแรกของหมวดนั้น
    val chipCats = remember(cats) { cats.drop(1) }
    var frontCat by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val chipState = rememberLazyListState()
    val chipDensity = LocalDensity.current.density
    var chipDragging by remember { mutableStateOf(false) }
    LaunchedEffect(chipState) { chipState.interactionSource.interactions.collect { chipDragging = it is DragInteraction.Start } }
    LaunchedEffect(frontCat) {
        if (frontCat.isEmpty() || chipDragging) return@LaunchedEffect
        val i = chipCats.indexOf(frontCat)
        if (i >= 0) { try { chipState.animateScrollToItem(i, -(60 * chipDensity).toInt()) } catch (_: Exception) { } }
    }
    val inCart = remember(tick, expanded) {
        val a = CartStore.load(session).optJSONObject(s.id)?.optJSONArray("items")
        var n = 0
        if (a != null) for (i in 0 until a.length()) n += a.getJSONObject(i).optInt("qty", 1)
        n
    }

    val shape = RoundedCornerShape(20.dp)
    val thumb by animateDpAsState(if (expanded) 44.dp else 80.dp, tween(350), label = "thumb")
    val badge = s.badge()
    Column(
        Modifier.fillMaxWidth().alpha(if (open) 1f else 0.6f)
            .cardGlow(20.dp)
            .clip(shape).background(Color.White).border(2.dp, N.B500, shape)
            .animateContentSize(tween(350))
            .clickable(enabled = !expanded) { if (open) onToggle() else Toast.makeText(ctx, "ร้านปิดอยู่ตอนนี้", Toast.LENGTH_SHORT).show() }
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = if (expanded) 12.dp else 16.dp, bottom = if (expanded) 0.dp else 16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(thumb).clip(RoundedCornerShape(if (expanded) 10.dp else 14.dp)).background(Brush.linearGradient(listOf(N.B500, N.B800))).clickable { profile = true },
                contentAlignment = Alignment.Center
            ) {
                if (s.avatarUrl.isNotEmpty()) AsyncImage(s.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else PathIcon(P.RESTAURANT, Color.White, thumb * 0.5f)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).heightIn(min = if (expanded) 44.dp else 80.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = N.B800, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) Text(
                        BADGE_LABEL[badge] ?: "", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = N.B600,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(N.B100).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (!expanded) {
                    val cap = if (open) s.desc else s.d.str("closeReason").let { if (it.isNotEmpty()) "🔒 $it" else s.desc.ifEmpty { "ร้านปิดอยู่ตอนนี้" } }
                    if (cap.isNotEmpty()) Text(cap, fontSize = 12.5.sp, color = RING_SUB, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PathIcon(P.STAR, Color(0xFFF5B301), 13.dp)
                        val dt = s.deliveryTime.let { if (it.contains("นาที")) it else "$it นาที" }
                        val dist = s.dist?.let { " • " + if (it < 1) "${Math.round(it * 1000)}m" else "%.2fkm".format(it) } ?: ""
                        val loy = if (truthy(s.d["loyaltyEnabled"])) " • สะสมแต้ม" else ""
                        Text(" " + "%.1f".format(s.rating) + " • " + dt + dist + loy, fontSize = 13.sp, color = RING_SUB, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.padding(start = 8.dp), contentAlignment = Alignment.TopEnd) {
                Box(Modifier.alpha(favA).size(30.dp).clip(CircleShape).background(N.B50).clickable(enabled = !expanded) { onFav() }, contentAlignment = Alignment.Center) {
                    PathIcon(P.HEART, if (fav) N.Red500 else Color(0xFFB6C2D6), 15.dp)
                }
                if (expanded || btnA.value > 0.01f) CloseMenuButton(btnA, btnS, onToggle)
            }
        }

        if (expanded) {
            // แถบหมวด + ค้นหา (ช่องค้นหาขยายแบบหยดน้ำออกจากปุ่มแว่นขยาย)
            Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp).height(36.dp)) {
                if (!searchShown) {
                    LazyRow(Modifier.padding(start = 44.dp).fillMaxHeight(), state = chipState, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(chipCats) { c ->
                            val on = c == frontCat
                            Text(
                                c, fontSize = 12.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) N.B700 else RING_SUB,
                                // ชิปของหมวดที่กำลังอยู่: ขอบสีน้ำเงิน (ไม่ใช่สีน้ำเงินทึบทั้งช่อง)
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                                    .border(if (on) 2.dp else 1.dp, if (on) N.B500 else Color(0xFFD5DEEE), RoundedCornerShape(50))
                                    .clickable { jump = c to ((jump?.second ?: 0) + 1) }.padding(horizontal = if (on) 10.dp else 11.dp, vertical = if (on) 4.dp else 5.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { scaleX = sx.value; scaleY = sy.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                            .clip(RoundedCornerShape(18.dp)).background(N.B50).border(1.dp, Color(0xFFBFD0EE), RoundedCornerShape(18.dp))
                    )
                    Row(Modifier.fillMaxSize().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { PathIcon(P.SEARCH, N.B600, 17.dp) }
                        Box(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(sAlpha.value)) {
                            if (query.isEmpty()) Text("ค้นหาเมนูในร้านนี้", fontSize = 15.sp, color = Color(0xFF94A3B8))
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = N.B800), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                        }
                        Box(Modifier.alpha(sAlpha.value).size(28.dp).clip(CircleShape).background(N.B100).clickable { closeSearch() }, contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 12.sp, color = N.B600)
                        }
                    }
                }
                if (!searchShown) Box(
                    Modifier.size(36.dp).clip(CircleShape).background(N.B50).border(1.dp, Color(0xFFBFD0EE), CircleShape).clickable { openSearch() },
                    contentAlignment = Alignment.Center
                ) { PathIcon(P.SEARCH, N.B600, 17.dp) }
            }

            // วงแหวนเมนู
            Box(Modifier.fillMaxWidth().height(188.dp).padding(horizontal = 6.dp).clipToBounds().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                val list = menus
                when {
                    failed -> Text("โหลดเมนูไม่ได้ ลองใหม่อีกครั้ง", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    list == null -> Text("กำลังโหลดเมนู...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    filtered.isEmpty() -> Text(if (list.isEmpty()) "ร้านนี้ยังไม่มีเมนู" else "ไม่พบเมนูที่ค้นหา", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    else -> RingCarousel(
                        items = filtered, pricing = pricing, resetKey = "${filtered.size}|$query",
                        showCat = true, jump = jump, debug = session.ringDebug,
                        onFront = { c -> frontCat = c },
                        onSoldOut = { Toast.makeText(ctx, "เมนูนี้หมดชั่วคราว", Toast.LENGTH_SHORT).show() },
                        onPick = { (id, m) -> popup = PopupState(id, m) }
                    )
                }
            }

            // สรุปการสั่งซื้อ
            if (inCart > 0) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(N.B500, N.B700)))
                            .clickable {
                                ctx.getSharedPreferences("nimit", android.content.Context.MODE_PRIVATE).edit()
                                    .putString("checkoutShops", JSONArray().put(s.id).toString()).apply()
                                session.selectedShopId = s.id; session.selectedShopName = s.name
                                onCheckout()
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("สรุปการสั่งซื้อ ($inCart)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(N.B100).clickable { cartList = true }, contentAlignment = Alignment.Center) {
                        PathIcon("M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z", N.B600, 22.dp)
                    }
                }
            } else Spacer(Modifier.height(12.dp))
        }
    }

    popup?.let { p ->
        RingWizard(p, pricing, onDismiss = { popup = null }) { note ->
            val phone = try { JSONObject(session.customerData ?: "{}").optString("phone") } catch (_: Exception) { "" }.ifEmpty { session.customerPhone.orEmpty() }
            if (phone.isEmpty()) {
                Toast.makeText(ctx, "กรุณาเข้าสู่ระบบก่อนสั่งซื้อครับ", Toast.LENGTH_SHORT).show()
                popup = null; onLogin(); return@RingWizard
            }
            val miss = p.missing()
            if (miss.isNotEmpty()) { p.errors = miss; return@RingWizard }
            val cart = CartStore.load(session)
            val item = JSONObject().put("id", p.id).put("name", p.m.str("name")).put("basePrice", num(p.m["price"]) ?: 0.0)
                .put("price", p.unitPrice(pricing)).put("qty", p.qty).put("imgUrl", p.m.str("imgUrl"))
                .put("options", p.selectedOptions()).put("optionGroups", deepJson(p.m["optionGroups"] ?: emptyList<Any>()))
                .put("note", note).put("gp", p.menuGp).put("category", p.m.str("category"))
            CartStore.add(cart, s.id, s.name, item); CartStore.save(session, cart); tick++; popup = null
            Toast.makeText(ctx, "เพิ่มลงตะกร้าแล้ว", Toast.LENGTH_SHORT).show()
        }
    }
    if (cartList) RingCartSheet(session, s.id, onChanged = { tick++ }, onDismiss = { cartList = false })
    if (profile) RingProfileSheet(s, open, onReviews = onReviews, onGoMenu = onGoMenu, onDismiss = { profile = false })
}

@Composable
private fun RingCarousel(
    items: List<Pair<String, Doc>>, pricing: Pricing, resetKey: String,
    onSoldOut: () -> Unit, onPick: (Pair<String, Doc>) -> Unit,
    showCat: Boolean = false, jump: Pair<String, Int>? = null, onFront: (String) -> Unit = {}, debug: Boolean = false
) {
    val b = items.size
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val pos = remember(resetKey) { Animatable(0f) }
    val sp = remember(resetKey) { Animatable(0f) }
    val firstRun = remember { BooleanArray(1) { true } }
    // อนิเมชั่นการ์ดตกลงมาแล้วกางเป็นวง (easeOutCubic เหมือนต้นฉบับ)
    // นับเวลาเองทีละเฟรมและจำกัดเฟรมละไม่เกิน 24ms: ถ้าเครื่องค้างยาวช่วงต้น (สร้างการ์ดชุดใหญ่) อนิเมชั่นจะไม่ข้ามไปจบทันที
    suspend fun fan(dur: Int) {
        sp.snapTo(0f)
        // รอให้เฟรมแรก (ที่สร้างการ์ดหนักๆ) วาดเสร็จก่อน แล้วค่อยเริ่มนับเวลาอนิเมชั่น
        withFrameNanos { }; withFrameNanos { }
        val ease = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
        var last = withFrameNanos { it }
        var elapsed = 0f
        while (elapsed < dur) {
            val now = withFrameNanos { it }
            elapsed += ((now - last) / 1_000_000f).coerceIn(0f, 24f)
            last = now
            sp.snapTo(ease.transform((elapsed / dur).coerceIn(0f, 1f)))
        }
        sp.snapTo(1f)
    }
    LaunchedEffect(resetKey) {
        val dur = if (firstRun[0]) 550 else 420
        firstRun[0] = false
        fan(dur)
    }
    val center by remember(resetKey) { derivedStateOf { Math.round(pos.value) } }
    // แจ้งหมวดของการ์ดตรงกลางทุกครั้งที่ใบหน้าเปลี่ยน (รวมค่าแรกตอนเปิด)
    LaunchedEffect(resetKey) {
        snapshotFlow { Math.floorMod(Math.round(pos.value), b) }.collect { i -> onFront(items.getOrNull(i)?.second?.str("category").orEmpty()) }
    }
    // แตะชิปหมวด: หมุนไปเมนูแรกของหมวดนั้นทางที่ใกล้ที่สุด
    val firstJumpNonce = remember { jump?.second ?: 0 }
    LaunchedEffect(jump) {
        val j = jump ?: return@LaunchedEffect
        val t = items.indexOfFirst { it.second.str("category") == j.first }
        if (t < 0) return@LaunchedEffect
        if (j.second == firstJumpNonce) return@LaunchedEffect   // ค่าเก่าที่ค้างจากก่อนสร้างวงใหม่ ไม่ใช่การแตะใหม่
        // ข้ามไปหมวดนั้นเลย ไม่ไล่ผ่านหมวดระหว่างทาง: ย้ายตำแหน่งทันทีแล้วกางวงใหม่
        pos.stop()
        pos.snapTo(t.toFloat())
        fan(420)
    }

    Box(
        Modifier.fillMaxWidth().height(180.dp)
            .pointerInput(items) {
                detectTapGestures { off ->
                    if (sp.value < 0.5f) return@detectTapGestures
                    val x = (off.x - size.width / 2f) / density
                    var bestJ = Int.MIN_VALUE; var bestD = Float.MAX_VALUE
                    for (j in center - 4..center + 4) {
                        val d = j - pos.value
                        if (ringCos(d) <= 0.4f) continue
                        val ex = ringExtent(d)
                        if (x >= ex[0] && x <= ex[1] && abs(d) < bestD) { bestD = abs(d); bestJ = j }
                    }
                    if (bestJ == Int.MIN_VALUE) return@detectTapGestures
                    val (mid, m) = items[Math.floorMod(bestJ, b)]
                    if (bestD < 0.5f) {
                        val sold = m["available"] == false || !isMenuWithinSchedule(m)
                        if (sold) onSoldOut() else onPick(mid to m)
                    } else scope.launch { pos.animateTo(bestJ.toFloat(), tween(520, easing = RING_BACK)) }
                }
            }
            .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta -> scope.launch { pos.snapTo(pos.value - delta / (RING_PX * density)) } },
            onDragStarted = { scope.launch { pos.stop() } },
            onDragStopped = { vel ->
                val v = (vel / density / 1000f).coerceIn(-1.5f, 1.5f)
                val target = Math.round(pos.value + (-v * 140f / RING_PX).coerceIn(-3f, 3f)).toFloat()
                scope.launch { pos.animateTo(target, tween(520, easing = RING_BACK)) }
            }
        )
    ) {
        if (debug) {
            // โหมดตรวจ: แสดงค่า sp และความยาวเฟรมช่วง 2 วินาทีแรกหลังเปิด (ใช้ดูว่าเครื่องค้างตรงไหน)
            var info by remember(resetKey) { mutableStateOf("รอ...") }
            LaunchedEffect(resetKey) {
                var last = withFrameNanos { it }
                val t0 = last
                var maxDt = 0f; var frames = 0; var lastShow = last; var slow = 0
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = (now - last) / 1_000_000f
                    last = now; frames++
                    val el = (now - t0) / 1_000_000f
                    if (el < 2000f) { if (dt > maxDt) maxDt = dt; if (dt > 40f) slow++ }
                    if (now - lastShow > 90_000_000L) {
                        lastShow = now
                        info = "t=%.0fms sp=%.2f dt=%.0f max=%.0f ช้า>40ms=%d f=%d".format(el, sp.value, dt, maxDt, slow, frames)
                    }
                }
            }
            Text(info, fontSize = 10.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).zIndex(100f).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
        // 9 ช่องคงที่ (สลับใช้ซ้ำ): ตอนหมุนข้ามไปทีละใบ มีแค่ช่องเดียวที่เปลี่ยนเมนู (ซึ่งอยู่ที่ขอบ มองไม่เห็น)
        // ไม่สร้าง/ทิ้งการ์ดใหม่ทุกครั้ง และโหลดรูปไว้ล่วงหน้าตั้งแต่ก่อนการ์ดเข้ามาในวง
        for (s in 0 until RING_SLOTS) {
            val j = center + Math.floorMod(s - center + 4, RING_SLOTS) - 4
            key(s) {
                val idx = Math.floorMod(j, b)
                val m = items[idx].second
                val sold = m["available"] == false || !isMenuWithinSchedule(m)
                val priceText = if (sold) "หมด" else "฿${pricing.calcPrice(num(m["price"]) ?: 0.0, num(m["gp"]) ?: 0.0)}"
                RingCardSlot(j, center, pos, sp, density, idx, m, priceText, sold, showCat)
            }
        }
    }
}


private const val RING_SLOTS = 9

/** การ์ดหนึ่งช่องของวง: เนื้อหาถูกบันทึกไว้ใน GraphicsLayer ครั้งเดียว แล้วทุกเฟรมแค่วาดเลเยอร์นั้นผ่านเมทริกซ์ฉายภาพ
 *  (ไม่ต้องวาดข้อความ/รูป/ขอบใหม่ทุกเฟรม) — เนื้อหาเปลี่ยน (รูปโหลดเสร็จ, ขอบหน้าสุด) เมื่อไหร่ค่อยบันทึกใหม่เอง */
@Composable
private fun BoxScope.RingCardSlot(
    j: Int, center: Int, pos: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    sp: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, density: Float,
    idx: Int, m: Doc, priceText: String, sold: Boolean, showCat: Boolean
) {
    val layer = rememberGraphicsLayer()
    val front by remember(j) { derivedStateOf { ringCos(j - pos.value) > 0.9f } }
    val shape = RoundedCornerShape(16.dp)
    SideEffect { layer.compositingStrategy = CompositingStrategy.ModulateAlpha }
    Box(
        Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(width = 120.dp, height = 158.dp)
            .zIndex(30f - min(25f, abs(j - center).toFloat()))
            .graphicsLayer { }   // ขอบเขตเลเยอร์: การวาดใหม่ทุกเฟรมไม่ลามไปทั้งหน้าจอ
            .drawWithContent {
                drawContent()   // ลูกบันทึกเนื้อหาลง layer (ตัวลูกเองไม่วาดอะไร)
                val d = j - pos.value
                val c = ringCos(d)
                val fade = ((sp.value - 0.12f) / 0.88f).coerceIn(0f, 1f)
                layer.alpha = fade * ((c - 0.1f) / 0.35f).coerceIn(0f, 1f)
                val m4 = ringMatrix(d, sp.value, density)
                val cx = size.width / 2f; val cy = size.height / 2f
                withTransform({ translate(cx, cy); transform(m4); translate(-cx, -cy) }) {
                    if (layer.alpha > 0.003f) {
                        drawLayer(layer)
                        val dim = (0.35f * (1f - max(c, 0f))).coerceIn(0f, 1f) * layer.alpha
                        if (dim > 0.003f) drawRoundRect(N.B800, cornerRadius = CornerRadius(16.dp.toPx()), alpha = dim)
                    }
                }
            }
    ) {
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { }
                .drawWithContent {
                    layer.record(this, layoutDirection, androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) {
                        this@drawWithContent.drawContent()
                    }
                }
                .clip(shape).background(Color.White)
                .border(2.dp, if (front) N.B700 else N.B500, shape)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(11.dp)).background(Color(RING_PAL[idx % 12]).copy(alpha = 0.2f)).alpha(if (sold) 0.6f else 1f),
                contentAlignment = Alignment.Center
            ) {
                val img = m.str("imgUrl")
                if (img.isNotEmpty()) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else PathIcon(P.RESTAURANT, N.B600.copy(alpha = 0.55f), 40.dp)
                val catName = m.str("category")
                if (showCat && catName.isNotEmpty()) Box(
                    Modifier.align(Alignment.TopStart).padding(3.dp).widthIn(max = 78.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0B2C63).copy(alpha = 0.62f)).padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text(catName, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(m.str("name"), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = N.B800, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(priceText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = N.B600)
        }
    }
}
