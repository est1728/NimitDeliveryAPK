package com.nimit.delivery.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nimit.delivery.R
import com.nimit.delivery.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private val FILTERS = listOf(
    Triple("rec", "แนะนำ", P.THUMB), Triple("fav", "ร้านโปรด", P.HEART), Triple("near", "ใกล้ฉัน", P.PIN),
    Triple("rating", "คะแนนสูง", P.STAR), Triple("open", "เปิดอยู่", P.CLOCK)
)
internal val BADGE_LABEL = mapOf("pop" to "ยอดนิยม", "top" to "คะแนนสูงสุด", "new" to "เปิดใหม่", "fast" to "ส่งไว", "promo" to "โปรโมชัน")
private val BADGE_COLOR = mapOf("pop" to N.Amber600, "top" to N.B700, "new" to N.Green600, "fast" to Color(0xFF1291A6), "promo" to N.Red500)

private object ShopsCache {
    var owner: String = ""
    var shops: List<Shop> = emptyList()
    var ads: List<Pair<String, Doc>> = emptyList()
    var cats: List<Pair<String, Doc>> = emptyList()
    var flash: List<Pair<String, Doc>> = emptyList()
    var coupons: List<HomeCoupon> = emptyList()
    var notif: Int = 0
    var order: Pair<String, Doc>? = null
    var bnr: Doc? = null
    var svc: Doc? = null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ShopsScreen(session: Session, onOpenShop: (String, String) -> Unit, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val warm = remember { ShopsCache.owner == session.customerPhone.orEmpty() && ShopsCache.shops.isNotEmpty() }
    var shops by remember { mutableStateOf<List<Shop>>(if (warm) ShopsCache.shops else emptyList()) }
    var ads by remember { mutableStateOf<List<Pair<String, Doc>>>(if (warm) ShopsCache.ads else emptyList()) }
    var cats by remember { mutableStateOf<List<Pair<String, Doc>>>(if (warm) ShopsCache.cats else emptyList()) }
    var flash by remember { mutableStateOf<List<Pair<String, Doc>>>(if (warm) ShopsCache.flash else emptyList()) }
    var coupons by remember { mutableStateOf<List<HomeCoupon>>(if (warm) ShopsCache.coupons else emptyList()) }
    var prefer by remember { mutableStateOf(session.preferCoupon) }
    var notif by remember { mutableStateOf(if (warm) ShopsCache.notif else 0) }
    var order by remember { mutableStateOf<Pair<String, Doc>?>(if (warm) ShopsCache.order else null) }
    var bnr by remember { mutableStateOf<Doc?>(if (warm) ShopsCache.bnr else null) }
    var svc by remember { mutableStateOf<Doc?>(if (warm) ShopsCache.svc else null) }
    var loading by remember { mutableStateOf(!warm) }
    androidx.compose.runtime.SideEffect {
        if (shops.isNotEmpty()) {
            ShopsCache.owner = session.customerPhone.orEmpty()
            ShopsCache.shops = shops; ShopsCache.ads = ads; ShopsCache.cats = cats; ShopsCache.flash = flash
            ShopsCache.coupons = coupons; ShopsCache.notif = notif; ShopsCache.order = order
            ShopsCache.bnr = bnr; ShopsCache.svc = svc
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    var zoneOut by remember { mutableStateOf(false) }
    var sidebar by remember { mutableStateOf(false) }
    val ringMode = session.cardStyle == "ring"
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    NotifPermissionOnce()
    if (showAccounts) AccountDialog(session, onDismiss = { showAccounts = false }, onAdd = { showAccounts = false; session.loginReturnRoute = "shops"; onNavigate("login") }, onSwitched = { showAccounts = false; onNavigate("__reload") }, onLogout = { showAccounts = false; onLogout() })
    var filters by remember { mutableStateOf(setOf("rec")) }
    var favs by remember {
        mutableStateOf(try { JSONArray(session.favoriteShops ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } } catch (_: Exception) { emptySet<String>() })
    }
    var minuteTick by remember { mutableIntStateOf(0) }
    val cartCount = remember(shops) { cartCount(session) }

    val customer = remember { try { JSONObject(session.customerData ?: "{}") } catch (_: Exception) { JSONObject() } }
    val phone = customer.optString("phone").ifEmpty { session.customerPhone.orEmpty() }
    val loginText = if (customer.optString("name").isNotEmpty() && phone.isNotEmpty()) "${customer.optString("name")} · $phone" else phone.ifEmpty { "เข้าสู่ระบบ" }

    suspend fun loadAll() = coroutineScope {
        launch { ads = ShopsRepo.ads() }
        launch { cats = ShopsRepo.categoryButtons() }
        launch { flash = ShopsRepo.flashDeals() }
        launch { coupons = ShopsRepo.homeCoupons(phone) }
        launch { notif = ShopsRepo.unreadNotifs(session.notifLastRead?.toLongOrNull() ?: 0L) }
        launch { order = ShopsRepo.activeOrder(phone) }
        launch { bnr = ShopsRepo.appBanner() }
        launch { svc = ShopsRepo.homeButtons() }
        launch {
            ShopsRepo.refreshCustomer(session)
            val (list, zone) = ShopsRepo.shops(session)
            shops = list; loading = false
            if (zone != null && !zone.inside) zoneOut = true
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val loc = withTimeoutOrNull(6000) {
                        LocationServices.getFusedLocationProviderClient(ctx).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    }
                    if (loc != null) {
                        list.forEach { s ->
                            val la = s.d.dbl("lat"); val lo = s.d.dbl("lng")
                            if (la != null && lo != null) s.dist = ShopsRepo.distKm(loc.latitude, loc.longitude, la, lo)
                        }
                        shops = list.toList()
                    }
                } catch (_: Exception) {}
            }
        }
    }
    LaunchedEffect(Unit) { loadAll() }
    LaunchedEffect(Unit) { while (true) { delay(60_000); minuteTick++ } }

    fun pick(id: String, name: String) { onOpenShop(id, name) }
    fun openUrl(u: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {} }
    // ลิงก์ที่ชี้หน้าของเราเอง (เว็บเดิม) ให้เข้าหน้า native; เปิดเบราว์เซอร์เฉพาะเว็บภายนอกจริงๆ
    fun openLink(u: String, id: String) {
        val low = u.lowercase()
        val internal = !low.startsWith("http") || "nimitdelivery" in low || "vercel.app" in low
        if (!internal) { openUrl(u); return }
        val file = low.substringBefore("?").substringBefore("#").substringAfterLast("/")
        val qid = try { Uri.parse(u).getQueryParameter("id") } catch (_: Exception) { null } ?: ""
        val cid = id.ifEmpty { qid }
        val route = when (file) {
            "category.html" -> if (cid.isNotEmpty()) "category/$cid" else null
            "coupon.html" -> "coupon"
            "cart.html" -> "cart"
            "checkout.html" -> "checkout"
            "search.html" -> "search"
            "address.html" -> "address"
            "notifications.html" -> "notifications"
            "reorder.html" -> "reorder"
            else -> null
        }
        if (route != null) onNavigate(route)
        else if (file.isNotEmpty() && file != "shops.html") Toast.makeText(ctx, "หน้านี้กำลังแปลงเป็นแอป", Toast.LENGTH_SHORT).show()
    }
    fun toggleFav(id: String) {
        favs = if (id in favs) favs - id else favs + id
        session.favoriteShops = JSONArray(favs.toList()).toString()
    }

    fun couponAct(hc: HomeCoupon) {
        if (hc.claimId == null) {
            if (phone.isEmpty()) { Toast.makeText(ctx, "กรุณาเข้าสู่ระบบก่อนเก็บคูปอง", Toast.LENGTH_SHORT).show(); session.loginReturnRoute = "shops"; onNavigate("login"); return }
            scope.launch {
                val cid = ShopsRepo.claimCoupon(phone, hc.id)
                if (cid != null) { coupons = coupons.map { if (it.id == hc.id) HomeCoupon(it.id, it.d, cid) else it }; Toast.makeText(ctx, "เก็บคูปองแล้ว", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(ctx, "เก็บคูปองไม่สำเร็จ", Toast.LENGTH_SHORT).show()
            }
        } else if (prefer != hc.claimId) {
            session.preferCoupon = hc.claimId; prefer = hc.claimId
            Toast.makeText(ctx, "จะใช้คูปองนี้ก่อนตอนชำระเงิน", Toast.LENGTH_SHORT).show()
        } else {
            session.preferCoupon = null; prefer = null
            Toast.makeText(ctx, "นำคูปองออกแล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    val showSticky by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }

    val open = remember(shops, filters, favs, minuteTick) {
        var out = shops.toList()
        if ("fav" in filters) out = out.filter { it.id in favs }
        if ("open" in filters) out = out.filter { it.openNow() }
        out = when {
            "rating" in filters -> out.sortedByDescending { it.rating }
            "near" in filters -> out.sortedBy { it.dist ?: 999.0 }
            else -> out.sortedByDescending { it.score }
        }
        out
    }
    val (openList, closedList) = remember(open, minuteTick) { open.partition { it.openNow() } }
    val popular = remember(shops, minuteTick) { shops.filter { it.openNow() }.sortedByDescending { it.score }.take(5) }
    val liveFlash = remember(flash, minuteTick) { flash.filter { (it.second.lng("endAt") ?: 0L) > System.currentTimeMillis() } }

    val bnrItems = remember(bnr) { bannerItems(bnr) }
    val svcList = remember(svc) { serviceButtons(svc) }
    // ตำแหน่งหัวข้อ "ร้านทั้งหมด" ใน LazyColumn (ไว้ให้ปุ่มสั่งอาหารเลื่อนไป)
    val shopsHdIdx = 2 + (if (order != null) 1 else 0) + (if (ads.isNotEmpty()) 1 else 0) + (if (svcList.isNotEmpty()) 1 else 0) +
        (if (cats.isNotEmpty()) 1 else 0) + (if (coupons.isNotEmpty()) 1 else 0) + (if (liveFlash.isNotEmpty()) 1 else 0) +
        (if (popular.isNotEmpty()) 1 else 0) + (if (bnrItems.isNotEmpty()) 1 else 0)

    Box(Modifier.fillMaxSize().background(N.Bg)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; loadAll(); refreshing = false } },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                // TOP BAR
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(Brush.linearGradient(listOf(N.B900, N.B700)))
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Logo(40, float = true)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Nimit Delivery", fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Row(
                                        Modifier.padding(top = 5.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.14f))
                                            .clickable { showAccounts = true }
                                            .padding(horizontal = 9.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(loginText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        PathIcon(P.TRI, Color.White.copy(alpha = 0.75f), 11.dp)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircleBtn(P.BELL, notif) { notif = 0; onNavigate("notifications") }
                                CircleBtn(P.MENU, 0) { sidebar = true }
                            }
                        }
                        SearchBox { onNavigate("search") }
                    }
                }
                // ACTIVE ORDER
                order?.let { (id, o) ->
                    item {
                        val idx = ShopsRepo.activeStatuses.indexOf(o.str("status"))
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(N.B700, N.B900)))
                                .clickable { onNavigate("track/$id") }.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LiveDot()
                                    Text("#" + (o.str("orderId").ifEmpty { id.takeLast(6).uppercase() }), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                Text("กำลังดำเนินการ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 4.dp))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(o.str("shopName"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                                Text(ShopsRepo.statusLabel[o.str("status")] ?: o.str("status"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            OrderProgress(idx, ShopsRepo.activeStatuses.size)
                        }
                    }
                }
                // ADS
                if (ads.isNotEmpty()) item { AdsPager(ads, ::pick) { openLink(it, "") } }
                // SERVICE BUTTONS (settings/homeButtons)
                if (svcList.isNotEmpty()) item {
                    ServiceButtons(svcList, svc,
                        onFood = { scope.launch { listState.animateScrollToItem(shopsHdIdx) } },
                        onSoon = { Toast.makeText(ctx, "บริการนี้เร็วๆ นี้ — รอติดตามนะ", Toast.LENGTH_SHORT).show() })
                }
                // CATEGORIES
                if (cats.isNotEmpty()) item {
                    SectionTitle("หมวดหมู่สินค้า", top = 18)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        items(cats) { (id, b) ->
                            Column(Modifier.width(60.dp).clickable {
                                openLink(b.str("destPage").ifEmpty { "category.html" }, id)
                            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White).border(1.dp, N.Line, CircleShape), contentAlignment = Alignment.Center) {
                                    if (b.str("iconType") == "custom" && b.str("iconUrl").isNotEmpty()) AsyncImage(b.str("iconUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else PathIcon(P.presets[b.str("iconPreset")] ?: P.GRID, N.B700, 24.dp)
                                }
                                Text(b.str("name"), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = N.Ink700, textAlign = TextAlign.Center, lineHeight = 13.sp)
                            }
                        }
                    }
                }
                // COUPON BOX (การ์ดคูปองแบบตั๋ว)
                if (coupons.isNotEmpty()) item {
                    CouponBox(coupons, prefer, onAll = { onNavigate("coupon") }, onAct = { hc -> couponAct(hc) })
                }
                // FLASH
                if (liveFlash.isNotEmpty()) item {
                    Column(Modifier.padding(top = 18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(N.Red500))
                                Text("ดีลลับ ลดแรง", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = N.Red500)
                            }
                            FlashCountdown(liveFlash.first().second.lng("endAt") ?: 0L)
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            items(liveFlash) { (_, f) ->
                                val deal = f.dbl("dealPrice") ?: 0.0; val orig = f.dbl("originalPrice") ?: 0.0
                                val off = if (orig > 0) Math.round((1 - deal / orig) * 100) else 0
                                Column(
                                    Modifier.width(116.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, N.Line, RoundedCornerShape(16.dp))
                                        .clickable(enabled = f.str("shopId").isNotEmpty()) { pick(f.str("shopId"), f.str("shopName")) }
                                ) {
                                    Box(Modifier.fillMaxWidth().height(78.dp).background(N.B50)) {
                                        if (f.str("imgUrl").isNotEmpty()) AsyncImage(f.str("imgUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        Text("-$off%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(N.Red500).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Column(Modifier.padding(9.dp)) {
                                        Text(f.str("itemName"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("฿${fmt(deal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = N.Red500)
                                            Text("฿${fmt(orig)}", fontSize = 10.5.sp, color = N.Ink300, textDecoration = TextDecoration.LineThrough)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // POPULAR
                if (popular.isNotEmpty()) item {
                    SectionTitle("ร้านแนะนำ", top = 18)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(popular) { s ->
                            Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, N.Line, RoundedCornerShape(14.dp)).clickable { pick(s.id, s.name) }) {
                                Box(Modifier.fillMaxWidth().height(78.dp).background(N.B50), contentAlignment = Alignment.Center) {
                                    if (s.bannerUrl.isNotEmpty()) AsyncImage(s.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else PathIcon(P.RESTAURANT, N.B500.copy(alpha = 0.35f), 30.dp)
                                }
                                Column(Modifier.padding(9.dp)) {
                                    Text(s.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        PathIcon(P.STAR, Color(0xFFF2A93C), 12.dp)
                                        Text("%.1f · %s นาที%s".format(s.rating, s.deliveryTime, distTxt(s)), fontSize = 10.sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                // APP BANNER (settings/appBanner)
                if (bnrItems.isNotEmpty()) item { BannerCarousel(bnrItems, (bnr?.dbl("sec") ?: 4.0).toInt()) { openLink(it, "") } }
                // FILTERS
                item {
                    LazyRow(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTERS) { (key, label, icon) ->
                            val on = key in filters
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) N.B700 else Color.White)
                                    .border(1.dp, if (on) N.B700 else N.Line, RoundedCornerShape(20.dp))
                                    .clickable { filters = if (on && key != "rec") setOf("rec") else setOf(key) }.padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                PathIcon(icon, if (on) Color.White else N.Ink700, 14.dp, stroke = key == "fav" || key == "near")
                                Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else N.Ink700)
                            }
                        }
                    }
                }
                // SHOPS
                item { SectionTitle(if (filters.size == 1 && "rec" in filters) "ร้านทั้งหมด" else "ร้านทั้งหมด (${open.size})", top = 16) }
                if (loading) items(3) { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)).background(N.B100)) }
                items(openList, key = { it.id }) { s ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp)) {
                        if (ringMode) RingShopCard(
                            s, true, expandedId == s.id, s.id in favs, session,
                            onToggle = { expandedId = if (expandedId == s.id) null else s.id }, onFav = { toggleFav(s.id) },
                            onReviews = { onNavigate("reviews/shop/${s.id}") }, onGoMenu = { expandedId = s.id }, onCheckout = { onNavigate("checkout") },
                            onLogin = { session.loginReturnRoute = "shops"; onNavigate("login") }
                        ) else ShopCard(s, true, s.id in favs, { pick(s.id, s.name) }, { toggleFav(s.id) }) { onNavigate("reviews/shop/${s.id}") }
                    }
                }
                if (closedList.isNotEmpty()) {
                    item { Text("ร้านที่ปิดอยู่ตอนนี้", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = N.Ink300, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 9.dp)) }
                    items(closedList, key = { it.id }) { s ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp)) {
                            if (ringMode) RingShopCard(s, false, false, s.id in favs, session, onToggle = {}, onFav = { toggleFav(s.id) }, onReviews = { onNavigate("reviews/shop/${s.id}") }, onGoMenu = {}, onCheckout = {}, onLogin = {})
                            else ShopCard(s, false, s.id in favs, {}, { toggleFav(s.id) }) {}
                        }
                    }
                }
            }
        }

        // STICKY BAR
        AnimatedVisibility(showSticky, Modifier.align(Alignment.TopCenter), enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
            Row(Modifier.fillMaxWidth().shadow(8.dp).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(34)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { SearchBox(light = true) { onNavigate("search") } }
                Spacer(Modifier.width(10.dp))
                Box {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(N.B50).clickable { onNavigate("cart") }, contentAlignment = Alignment.Center) {
                        PathIcon(P.CART, N.B700, 20.dp)
                    }
                    if (cartCount > 0) Badge(if (cartCount > 99) "99+" else cartCount.toString(), Modifier.align(Alignment.TopEnd))
                }
            }
        }

        // BOTTOM NAV
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            NavItem(P.HOME, "หน้าหลัก", true, Modifier.weight(1f)) {}
            NavItem(P.HISTORY, "ประวัติ", false, Modifier.weight(1f)) { onNavigate("reorder") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(y = (-16).dp).size(50.dp)) {
                    Box(
                        Modifier.fillMaxSize().shadow(10.dp, CircleShape).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(N.B500, N.B700))).border(4.dp, Color.White, CircleShape).clickable { onNavigate("cart") },
                        contentAlignment = Alignment.Center
                    ) {
                        PathIcon(P.CART, Color.White, 22.dp)
                    }
                    if (cartCount > 0) Badge(if (cartCount > 99) "99+" else cartCount.toString(), Modifier.align(Alignment.TopEnd))
                }
            }
            NavItem(P.TICKET, "คูปอง", false, Modifier.weight(1f)) { onNavigate("coupon") }
            NavItem(P.SOCIAL, "โซเชียล", false, Modifier.weight(1f).alpha(0.45f)) { Toast.makeText(ctx, "ระบบโซเชียลเร็วๆ นี้ครับ", Toast.LENGTH_SHORT).show() }
        }

        // SIDEBAR
        AnimatedVisibility(sidebar, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Box(Modifier.fillMaxSize().background(N.B900.copy(alpha = 0.45f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sidebar = false })
        }
        AnimatedVisibility(sidebar, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            Column(Modifier.width(284.dp).fillMaxHeight().shadow(16.dp).background(Color.White)) {
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(N.B800, N.B900))).statusBarsPadding().padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.Top) {
                    Logo(38)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nimit Delivery", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("เมนูของฉัน", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable { sidebar = false }, contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White, fontSize = 14.sp)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    SidebarNotifRow()
                    SbItem(SbI.PIN, "ที่อยู่จัดส่ง", customer.optString("address").ifEmpty { "เพิ่มที่อยู่จัดส่ง" }) { sidebar = false; onNavigate("address") }
                    SbItem(SbI.GEAR, "ตั้งค่า", "รูปแบบการ์ดร้านค้า") { sidebar = false; onNavigate("settings") }
                    SbItem(SbI.PEOPLE, "สลับบัญชี", loginText) { sidebar = false; showAccounts = true }
                    SbItem(SbI.LOGOUT, "ออกจากระบบ", "เข้าสู่ระบบด้วยเบอร์อื่น", last = true) { sidebar = false; onLogout() }
                }
            }
        }

        // ZONE OUT
        if (zoneOut) Dialog(onDismissRequest = { zoneOut = false }) {
            Column(Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(N.Red100), contentAlignment = Alignment.Center) { PathIcon(P.PIN, N.Red500, 30.dp, stroke = true) }
                Text("คุณอยู่นอกพื้นที่ให้บริการ", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = N.Ink900, modifier = Modifier.padding(top = 14.dp))
                Text("ที่อยู่ปัจจุบันของคุณอยู่นอกเขตที่เราให้บริการในขณะนี้ ยังสามารถเดินดูร้านค้าต่อได้ แต่อาจสั่งซื้อไม่ได้จนกว่าจะเปลี่ยนที่อยู่ หรือรอทีมงานขยายพื้นที่เพิ่มเติม",
                    fontSize = 13.sp, color = N.Ink500, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(N.B600, N.B700))).clickable { zoneOut = false; onNavigate("address") }, contentAlignment = Alignment.Center) {
                    Text("เปลี่ยนที่อยู่", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text("เดินดูร้านค้าต่อ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = N.Ink500, modifier = Modifier.padding(top = 10.dp).clickable { zoneOut = false }.padding(12.dp))
            }
        }
    }
}

private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
private fun distTxt(s: Shop): String = s.dist?.let { " · " + if (it < 1) "${Math.round(it * 1000)}m" else "%.2fkm".format(it) } ?: ""

private fun cartCount(session: Session): Int = try {
    val o = JSONObject(session.cart ?: "{}"); var n = 0
    o.keys().forEach { k -> val items = o.getJSONObject(k).optJSONArray("items"); if (items != null) for (i in 0 until items.length()) n += items.getJSONObject(i).optInt("qty", 1) }
    n
} catch (_: Exception) { 0 }

@Composable private fun Logo(size: Int, float: Boolean = false) {
    val y: State<Float>? = if (float) rememberInfiniteTransition(label = "logo").animateFloat(
        0f, -3f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "logoY"
    ) else null
    Box(Modifier.graphicsLayer { translationY = (y?.value ?: 0f) * density }.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(N.B800)
        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape((size / 4).dp))) {
        androidx.compose.foundation.Image(painterResource(R.drawable.logo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable private fun Badge(t: String, modifier: Modifier) {
    Box(modifier.offset(x = 3.dp, y = (-3).dp).defaultMinSize(minWidth = 16.dp).height(16.dp).clip(RoundedCornerShape(20.dp)).background(N.Red500).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable private fun CircleBtn(icon: String, badge: Int, onClick: () -> Unit) {
    Box {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
            PathIcon(icon, Color.White, 20.dp)
        }
        if (badge > 0) Badge(if (badge > 99) "99+" else badge.toString(), Modifier.align(Alignment.TopEnd))
    }
}

@Composable private fun SearchBox(light: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (light) 38.dp else 46.dp).clip(RoundedCornerShape(if (light) 19.dp else 14.dp))
            .background(if (light) N.B50 else Color.White).clickable { onClick() }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PathIcon(P.SEARCH, N.Ink300, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text("ค้นหาร้านหรือเมนู...", fontSize = 13.5.sp, color = N.Ink300)
    }
}

@Composable private fun SectionTitle(t: String, top: Int) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = top.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(N.B600))
        Text(t, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900)
    }
}

@Composable private fun NavItem(icon: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }.padding(top = 9.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PathIcon(icon, if (active) N.B700 else N.Ink300, 22.dp)
        Text(label, fontSize = 10.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) N.B700 else N.Ink300)
    }
}

private object SbI {
    const val PIN = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5S10.62 6.5 12 6.5s2.5 1.12 2.5 2.5S13.38 11.5 12 11.5z"
    const val LOGOUT = "M16 17v-3H9v-4h7V7l5 5-5 5M14 2a2 2 0 0 1 2 2v2h-2V4H5v16h9v-2h2v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9z"
    const val GEAR = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
    const val PEOPLE = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
}

@Composable private fun SbItem(icon: String, title: String, sub: String, last: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PathIcon(icon, N.Ink500, 19.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900)
                Text(sub, fontSize = 11.sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            PathIcon(P.CHEVRON, N.Ink300, 14.dp)
        }
        if (!last) HorizontalDivider(color = N.Line)
    }
}

@Composable
private fun AdsPager(ads: List<Pair<String, Doc>>, pick: (String, String) -> Unit, openUrl: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { ads.size })
    LaunchedEffect(ads.size) { while (ads.size > 1) { delay(4500); pager.animateScrollToPage((pager.currentPage + 1) % ads.size) } }
    Column(Modifier.padding(top = 14.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth()) { i ->
            val a = ads[i].second
            Box(Modifier.fillMaxWidth().aspectRatio(2.1f).background(N.B50).clickable {
                if (a.str("shopId").isNotEmpty()) pick(a.str("shopId"), a.str("shopName")) else if (a.str("linkUrl").isNotEmpty()) openUrl(a.str("linkUrl"))
            }) {
                val img = adImg(a)
                if (img.isNotEmpty()) AdImage(img, Modifier.fillMaxSize())
                else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(N.B700, N.B900))))
                    Text("ไม่พบรูป · " + a.keys.joinToString(","), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                if (a.str("caption").isNotEmpty()) Text(a.str("caption"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, N.B900.copy(alpha = 0.75f)))).padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 14.dp))
            }
        }
        if (ads.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            ads.indices.forEach { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == pager.currentPage) N.B700 else N.B100))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopCard(s: Shop, open: Boolean, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit, onReviews: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val badge = s.badge()
    Column(
        Modifier.fillMaxWidth().alpha(if (open) 1f else 0.6f).clip(shape).background(N.B50).border(1.dp, N.Line, shape).clickable(enabled = open) { onClick() }
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(N.B50)) {
            if (s.bannerUrl.isNotEmpty()) AsyncImage(s.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PathIcon(P.RESTAURANT, N.B500.copy(alpha = 0.3f), 44.dp) }
            if (badge != null) Text(BADGE_LABEL[badge] ?: "", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp).clip(RoundedCornerShape(8.dp)).background(BADGE_COLOR[badge] ?: N.B700).padding(horizontal = 9.dp, vertical = 4.dp))
            Text(if (open) "เปิด" else "ปิด", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (open) N.Green600 else N.Red500,
                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp).clip(RoundedCornerShape(8.dp)).background(if (open) N.Green100 else N.Red100).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.offset(y = (-28).dp).size(64.dp).clip(RoundedCornerShape(16.dp)).background(N.B50).border(3.dp, Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    if (s.avatarUrl.isNotEmpty()) AsyncImage(s.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else PathIcon("M20 4H4v2l8 5 8-5V4zm0 4.236l-8 5-8-5V20h16V8.236z", N.B700, 28.dp)
                }
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.desc, fontSize = 11.5.sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { onFav() }, contentAlignment = Alignment.Center) {
                        PathIcon(P.HEART, if (fav) N.Red500 else N.Ink300, 20.dp, stroke = !fav)
                    }
                }
            }
            Box(Modifier.offset(y = (-14).dp)) {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Meta(P.CLOCK, "${s.deliveryTime} นาที${distTxt(s)}", N.Ink500)
                        Row(Modifier.clickable { onReviews() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PathIcon(P.STAR, Color(0xFFF2A93C), 14.dp)
                            Text("%.1f".format(s.rating) + (if (s.ratingCount > 0) " (${s.ratingCount.toLong()})" else ""), fontSize = 11.5.sp, color = N.Ink500)
                        }
                        if (truthy(s.d["loyaltyEnabled"])) Meta(P.TAG, "สะสมแต้ม", N.B700)
                    }
                    if (!open && s.d.str("closeReason").isNotEmpty()) Text("🔒 ${s.d.str("closeReason")}", fontSize = 11.5.sp, color = N.Red500, modifier = Modifier.padding(top = 4.dp))
                    if (truthy(s.d["autoSchedule"]) && s.d.str("scheduleOpen").isNotEmpty() && s.d.str("scheduleClose").isNotEmpty())
                        Meta(P.CLOCK, "เปิด ${s.d.str("scheduleOpen")}-${s.d.str("scheduleClose")} น.", N.Ink500, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable private fun Meta(icon: String, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PathIcon(icon, color, 14.dp); Text(text, fontSize = 11.5.sp, color = color)
    }
}

private fun adImg(a: Doc): String {
    for (k in listOf("imgUrl", "imageUrl", "image", "img", "photo", "url", "banner")) { val v = a.str(k); if (v.isNotEmpty()) return v }
    return ""
}

@Composable
private fun AdImage(src: String, modifier: Modifier = Modifier) {
    if (src.startsWith("data:image")) {
        val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, src) {
            value = withContext(Dispatchers.Default) {
                try {
                    val b = android.util.Base64.decode(src.substringAfter("base64,"), android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
        bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = modifier) }
    } else AsyncImage(src, null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
private fun FlashCountdown(endAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val left = (endAt - now).coerceAtLeast(0) / 1000
    val pulse = rememberInfiniteTransition(label = "dot").animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dotA"
    )
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(N.Ink900).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(N.Red500))
        Text("%02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60), style = TextStyle(fontFeatureSettings = "tnum"),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun LiveDot() {
    val p = rememberInfiniteTransition(label = "live").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)), label = "liveP"
    )
    Box(Modifier.size(7.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF4ADE80)))
        Box(
            Modifier.requiredSize(15.dp)
                .graphicsLayer { val k = 0.6f + 1.5f * p.value; scaleX = k; scaleY = k; alpha = 0.8f * (1f - p.value) }
                .border(1.5.dp, Color(0xFF4ADE80), CircleShape)
        )
    }
}

@Composable
private fun OrderProgress(idx: Int, n: Int) {
    val cur = idx.coerceAtLeast(0)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val bounce = androidx.compose.animation.core.CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val pos = androidx.compose.animation.core.animateFloatAsState(if (go) (cur + 1).toFloat() / n else 0f, tween(900, easing = bounce), label = "riderPos")
    val bob = rememberInfiniteTransition(label = "ride").animateFloat(
        0f, 3f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rideBob"
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        val riderW = 28.dp
        Column {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(n) { i ->
                    val f = androidx.compose.animation.core.animateFloatAsState(if (go && i <= cur) 1f else 0f, tween(900, easing = bounce), label = "seg")
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(f.value.coerceIn(0f, 1f)).fillMaxHeight().background(if (i < cur) Color(0xFFF59E0B) else Color.White))
                    }
                }
            }
        }
        // ตัวรถหันขวา วิ่งไปตามสถานะล่าสุด
        Box(
            Modifier.offset {
                val w = maxWidth.toPx()
                val rw = riderW.toPx()
                val x = (w * pos.value - rw / 2f).coerceIn(0f, w - rw) + bob.value.dp.toPx()
                androidx.compose.ui.unit.IntOffset(x.toInt(), 0)
            }.size(riderW, 24.dp).graphicsLayer { scaleX = -1f },
            contentAlignment = Alignment.Center
        ) { Text("\uD83D\uDEF5", fontSize = 20.sp) }
    }
}

// ---------- หน้าแรก v2: ปุ่มบริการ + แบนเนอร์หลายรูป (ตั้งค่าจากแอดมิน/ผู้พัฒนา) ----------
private class SvcDef(val k: String, val title: String, val sub: String, val path: String, val bg: Color, val fg: Color, val def: String)
private const val SVC_CAR = "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z"
private const val SVC_TRUCK = "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"
private val SVC_DEF = listOf(
    SvcDef("food", "สั่งอาหาร", "ร้านใกล้คุณ", P.RESTAURANT, Color(0xFFFFF1E0), Color(0xFFE8772E), "on"),
    SvcDef("ride", "เรียกรถ", "รับส่งคน", SVC_CAR, Color(0xFFE3F0FF), N.B600, "soon"),
    SvcDef("send", "ฝากซื้อ/ส่งของ", "ให้ไรเดอร์ช่วย", SVC_TRUCK, Color(0xFFE6F7EC), Color(0xFF15803D), "soon")
)

// สถานะปุ่ม: on เปิด / soon เร็วๆ นี้ / off ซ่อน (ค่าเริ่มต้นเหมือนเว็บ)
private fun svcState(cfg: Doc?, b: SvcDef): String = cfg?.str(b.k)?.takeIf { it == "on" || it == "soon" || it == "off" } ?: b.def
private fun serviceButtons(cfg: Doc?): List<SvcDef> = SVC_DEF.filter { svcState(cfg, it) != "off" }

@Composable
private fun ServiceButtons(list: List<SvcDef>, cfg: Doc?, onFood: () -> Unit, onSoon: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEach { b ->
            val soon = svcState(cfg, b) == "soon"
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().alpha(if (soon) 0.55f else 1f).shadow(2.dp, shape).clip(shape).background(Color.White)
                        .border(1.dp, N.Line, shape).clickable { if (soon) onSoon() else onFood() }
                        .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(b.bg), contentAlignment = Alignment.Center) { PathIcon(b.path, b.fg, 26.dp) }
                    Text(b.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = N.B900, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    Text(b.sub, fontSize = 10.5.sp, color = Color(0xFF64748B), maxLines = 1)
                }
                if (soon) Text("เร็วๆ นี้", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = (-7).dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF59E0B)).padding(horizontal = 7.dp, vertical = 1.dp))
            }
        }
    }
}

// รายการแบนเนอร์จาก settings/appBanner {on, sec, items[{imgUrl,title,sub,linkUrl,imgOnly}]} (รองรับรูปแบบเก่าที่เป็นรูปเดียว)
private fun bannerItems(b: Doc?): List<Doc> {
    if (b == null || !truthy(b["on"])) return emptyList()
    val raw = b["items"] as? List<*>
    val l: List<Doc> = if (raw != null) raw.mapNotNull { x -> (x as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }
    else if (b.str("imgUrl").isNotEmpty() || b.str("title").isNotEmpty()) listOf(b) else emptyList()
    return l.filter { it.str("imgUrl").isNotEmpty() || it.str("title").isNotEmpty() }
}

@Composable
private fun BannerCarousel(items: List<Doc>, sec: Int, onClick: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(items.size, sec) { while (items.size > 1) { delay(sec.coerceIn(2, 15) * 1000L); pager.animateScrollToPage((pager.currentPage + 1) % items.size) } }
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().aspectRatio(1080f / 393f).shadow(6.dp, shape).clip(shape)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val b = items[i]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(N.B700, N.B500))).clickable(enabled = b.str("linkUrl").isNotEmpty()) { onClick(b.str("linkUrl")) }) {
                if (b.str("imgUrl").isNotEmpty()) AsyncImage(b.str("imgUrl"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (!truthy(b["imgOnly"])) {
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to N.B900.copy(alpha = 0.65f), 0.7f to Color.Transparent, 1f to Color.Transparent)))
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.62f).padding(start = 18.dp)) {
                        if (b.str("title").isNotEmpty()) Text(b.str("title"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (b.str("sub").isNotEmpty()) Text(b.str("sub"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
                    }
                }
            }
        }
        if (items.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.indices.forEach { i ->
                Box(Modifier.size(if (i == pager.currentPage) 14.dp else 5.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.55f)))
            }
        }
    }
}

// ---------- การ์ดคูปองแบบตั๋ว (เหมือนเว็บ) ----------
private class TicketShape(val notchX: Float, val r: Float, val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val base = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner))) }
        val holes = Path().apply {
            addOval(Rect(Offset(notchX - r, -r), Size(r * 2, r * 2)))
            addOval(Rect(Offset(notchX - r, size.height - r), Size(r * 2, r * 2)))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, base, holes))
    }
}

private fun noEmoji(t: String): String = t.filter { ch -> !Character.isSurrogate(ch) && Character.getType(ch) != Character.OTHER_SYMBOL.toInt() }.trim()

@Composable
private fun CouponBox(list: List<HomeCoupon>, prefer: String?, onAll: () -> Unit, onAct: (HomeCoupon) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFE7EFFC), Color(0xFFF4F8FE), Color.White)))
            .border(1.dp, Color(0xFFD3E1FA), shape).padding(top = 12.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(N.B700), contentAlignment = Alignment.Center) { PathIcon(P.TICKET, Color.White, 15.dp) }
                Text("คูปองของคุณ (${list.size})", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = N.B900)
            }
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { onAll() }.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("ดูทั้งหมด", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = N.B700)
                PathIcon(P.CHEVRON, N.B700, 13.dp)
            }
        }
        LazyRow(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { hc -> CouponTicket(hc, picked = hc.claimId != null && hc.claimId == prefer, onAct = { onAct(hc) }) }
        }
    }
}

@Composable
private fun CouponTicket(hc: HomeCoupon, picked: Boolean, onAct: () -> Unit) {
    val c = hc.d
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val shape = remember(dens) { TicketShape(with(dens) { 80.dp.toPx() }, with(dens) { 6.dp.toPx() }, with(dens) { 12.dp.toPx() }) }
    val type = c.str("type")
    val big = when (type) { "percent" -> "${fmt(c.dbl("value") ?: 0.0)}%"; "fixed" -> "฿${fmt(c.dbl("value") ?: 0.0)}"; else -> "ส่งฟรี" }
    val sub = if (type == "freeship") "FREE SHIP" else "DISCOUNT"
    val end = remember(c) { java.text.SimpleDateFormat("d MMM yy", java.util.Locale("th", "TH")).format(java.util.Date(c.lng("endAt") ?: 0L)) }
    val min = c.dbl("minOrderAmount") ?: 0.0
    val meta = (if (min > 0) "ขั้นต่ำ ฿${fmt(min)} · " else "") + (if (c.str("shopId").isNotEmpty()) c.str("shopName").ifEmpty { "ร้านเฉพาะ" } else "ใช้ได้ทุกร้าน")
    val btn = when { hc.claimId == null -> "เก็บ"; picked -> "นำออก"; else -> "ใช้" }
    val dash = Color(0xFFC7D6F3)
    Row(
        Modifier.width(252.dp).height(66.dp).clip(shape).background(Color.White)
            .then(if (picked) Modifier.border(2.dp, N.B600, shape) else Modifier.border(1.dp, N.Line, shape)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.width(80.dp).fillMaxHeight().drawBehind {
                drawLine(dash, Offset(size.width, 8.dp.toPx()), Offset(size.width, size.height - 8.dp.toPx()), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(big, fontSize = 21.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, color = N.B700, maxLines = 1)
            Text(sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(top = 3.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Text(noEmoji(c.str("title")).ifEmpty { big }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = N.B900, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("หมดเขต $end", fontSize = 10.sp, color = Color(0xFFC2410C), maxLines = 1)
        }
        val pill = when (btn) { "เก็บ" -> N.B700 to Color.White; "ใช้" -> Color.White to N.B700; else -> Color(0xFFEEF2F9) to Color(0xFF64748B) }
        Box(
            Modifier.padding(start = 2.dp, end = 8.dp).height(24.dp).clip(RoundedCornerShape(14.dp)).background(pill.first)
                .border(1.5.dp, if (btn == "นำออก") Color(0xFFEEF2F9) else N.B700, RoundedCornerShape(14.dp))
                .clickable { onAct() }.padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) { Text(btn, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = pill.second, maxLines = 1) }
    }
}
