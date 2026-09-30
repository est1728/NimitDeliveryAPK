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
private val BADGE_LABEL = mapOf("pop" to "ยอดนิยม", "top" to "คะแนนสูงสุด", "new" to "เปิดใหม่", "fast" to "ส่งไว", "promo" to "โปรโมชัน")
private val BADGE_COLOR = mapOf("pop" to N.Amber600, "top" to N.B700, "new" to N.Green600, "fast" to Color(0xFF1291A6), "promo" to N.Red500)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ShopsScreen(session: Session, onOpenShop: (String, String) -> Unit, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var shops by remember { mutableStateOf<List<Shop>>(emptyList()) }
    var ads by remember { mutableStateOf<List<Pair<String, Doc>>>(emptyList()) }
    var cats by remember { mutableStateOf<List<Pair<String, Doc>>>(emptyList()) }
    var flash by remember { mutableStateOf<List<Pair<String, Doc>>>(emptyList()) }
    var coupons by remember { mutableStateOf(0) }
    var notif by remember { mutableStateOf(0) }
    var order by remember { mutableStateOf<Pair<String, Doc>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var zoneOut by remember { mutableStateOf(false) }
    var sidebar by remember { mutableStateOf(false) }
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
        launch { coupons = ShopsRepo.couponCount() }
        launch { notif = ShopsRepo.unreadNotifs(session.notifLastRead?.toLongOrNull() ?: 0L) }
        launch { order = ShopsRepo.activeOrder(phone) }
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
    fun toggleFav(id: String) {
        favs = if (id in favs) favs - id else favs + id
        session.favoriteShops = JSONArray(favs.toList()).toString()
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
                                    Text(
                                        loginText, fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 5.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.14f))
                                            .clickable { onNavigate(if (phone.isNotEmpty()) "address" else "login") }
                                            .padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircleBtn(P.BELL, notif) { session.notifLastRead = System.currentTimeMillis().toString(); notif = 0; onNavigate("notifications") }
                                CircleBtn(P.MENU, 0) { sidebar = true }
                            }
                        }
                        SearchBox { onNavigate("search") }
                    }
                }
                // ADS
                if (ads.isNotEmpty()) item { AdsPager(ads, ::pick, ::openUrl) }
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
                                Text("● #" + (o.str("orderId").ifEmpty { id.takeLast(6).uppercase() }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("กำลังดำเนินการ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 4.dp))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(o.str("shopName"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                                Text(ShopsRepo.statusLabel[o.str("status")] ?: o.str("status"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ShopsRepo.activeStatuses.indices.forEach { i ->
                                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(
                                        when { i < idx -> Color.White; i == idx -> Color.White.copy(alpha = 0.6f); else -> Color.White.copy(alpha = 0.2f) }))
                                }
                            }
                        }
                    }
                }
                // COUPON PROMO
                if (coupons > 0) item {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(N.B700, N.B900))).clickable { onNavigate("coupon") }
                            .padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { PathIcon(P.TAG, Color.White, 20.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (coupons > 1) "มีคูปองส่วนลด $coupons ใบรอให้เก็บ!" else "มีคูปองส่วนลดรอให้เก็บ!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("แตะเพื่อดูคูปองทั้งหมด", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        PathIcon(P.CHEVRON, Color.White, 20.dp)
                    }
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
                            Column(Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, N.Line, RoundedCornerShape(14.dp)).clickable { pick(s.id, s.name) }) {
                                Box(Modifier.fillMaxWidth().height(96.dp).background(N.B50)) {
                                    if (s.bannerUrl.isNotEmpty()) AsyncImage(s.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Column(Modifier.padding(10.dp)) {
                                    Text(s.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("⭐ %.1f · %s นาที%s".format(s.rating, s.deliveryTime, distTxt(s)), fontSize = 10.5.sp, color = N.Ink500, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                }
                // CATEGORIES
                if (cats.isNotEmpty()) item {
                    SectionTitle("หมวดหมู่สินค้า", top = 18)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        items(cats) { (id, b) ->
                            Column(Modifier.width(60.dp).clickable {
                                val dp = b.str("destPage").ifEmpty { "category.html" }
                                if (dp.startsWith("http", true)) openUrl(dp) else onNavigate("category/$id")
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
                // FILTERS
                item {
                    LazyRow(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTERS) { (key, label, icon) ->
                            val on = key in filters
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) N.B700 else Color.White)
                                    .border(1.dp, if (on) N.B700 else N.Line, RoundedCornerShape(20.dp))
                                    .clickable { filters = if (on) filters - key else filters + key }.padding(horizontal = 13.dp, vertical = 7.dp),
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
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp)) { ShopCard(s, true, s.id in favs, { pick(s.id, s.name) }, { toggleFav(s.id) }) { onNavigate("reviews/shop/${s.id}") } }
                }
                if (closedList.isNotEmpty()) {
                    item { Text("ร้านที่ปิดอยู่ตอนนี้", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = N.Ink300, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 9.dp)) }
                    items(closedList, key = { it.id }) { s ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp)) { ShopCard(s, false, s.id in favs, {}, { toggleFav(s.id) }) {} }
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
                Box(Modifier.size(38.dp).clip(CircleShape).background(N.B50).clickable { onNavigate("cart") }, contentAlignment = Alignment.Center) {
                    PathIcon(P.CART, N.B700, 20.dp)
                    if (cartCount > 0) Badge(cartCount.toString(), Modifier.align(Alignment.TopEnd))
                }
            }
        }

        // BOTTOM NAV
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            NavItem(P.HOME, "หน้าหลัก", true, Modifier.weight(1f)) {}
            NavItem(P.HISTORY, "ประวัติ", false, Modifier.weight(1f)) { onNavigate("reorder") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.offset(y = (-16).dp).size(50.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(N.B500, N.B700))).border(4.dp, Color.White, CircleShape).clickable { onNavigate("cart") },
                    contentAlignment = Alignment.Center
                ) {
                    PathIcon(P.CART, Color.White, 22.dp)
                    if (cartCount > 0) Badge(cartCount.toString(), Modifier.align(Alignment.TopEnd))
                }
            }
            NavItem(P.TAG, "คูปอง", false, Modifier.weight(1f)) { onNavigate("coupon") }
            NavItem(P.SOCIAL, "โซเชียล", false, Modifier.weight(1f).alpha(0.45f)) { Toast.makeText(ctx, "ระบบโซเชียลเร็วๆ นี้ครับ", Toast.LENGTH_SHORT).show() }
        }

        // SIDEBAR
        AnimatedVisibility(sidebar, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Box(Modifier.fillMaxSize().background(N.B900.copy(alpha = 0.45f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sidebar = false })
        }
        AnimatedVisibility(sidebar, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            Column(Modifier.width(284.dp).fillMaxHeight().shadow(16.dp).background(Color.White)) {
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(N.B900, N.B700))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nimit Delivery", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("เมนูของฉัน", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { sidebar = false }.padding(8.dp))
                }
                SbItem("ที่อยู่จัดส่ง", customer.optString("address").ifEmpty { "เพิ่มที่อยู่จัดส่ง" }) { sidebar = false; onNavigate("address") }
                SbItem("ออกจากระบบ", "เข้าสู่ระบบด้วยเบอร์อื่น") { sidebar = false; onLogout() }
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
    Text(t, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
        modifier = modifier.offset(x = 3.dp, y = (-3).dp).defaultMinSize(minWidth = 16.dp).height(16.dp).clip(RoundedCornerShape(20.dp)).background(N.Red500).padding(horizontal = 3.dp))
}

@Composable private fun CircleBtn(icon: String, badge: Int, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        PathIcon(icon, Color.White, 20.dp)
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

@Composable private fun SbItem(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = N.Ink900)
                Text(sub, fontSize = 11.5.sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PathIcon(P.CHEVRON, N.Ink300, 20.dp)
        }
        HorizontalDivider(color = N.Line)
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
