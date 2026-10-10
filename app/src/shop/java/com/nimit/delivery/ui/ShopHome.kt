package com.nimit.delivery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.nimit.delivery.data.SHOP_ACTIVE_STATUSES
import com.nimit.delivery.data.ShopApi
import com.nimit.delivery.data.ShopOrd
import com.nimit.delivery.data.ShopStore
import com.nimit.delivery.data.shopNextOpenTime
import com.nimit.delivery.data.shopTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SHOP_GRAD = Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))

@Composable
fun ShopHome(store: ShopStore, onOpenOrder: (String) -> Unit, onReviews: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shop: Map<String, Any> = store.shop.value
    var tab by remember { mutableIntStateOf(0) }
    var sidebar by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    val isOpen: Boolean = shop["isOpen"] == true
    val reason: String = shop["closeReason"]?.toString() ?: ""
    val closeType: String = shop["closeType"]?.toString() ?: ""
    val loyaltyOn: Boolean = shop["loyaltyEnabled"] == true
    val name: String = shop["name"]?.toString().takeIf { !it.isNullOrEmpty() } ?: "ร้านค้า"
    val avatar: String = shop["avatarUrl"]?.toString() ?: ""

    BackHandler(sidebar) { sidebar = false }

    val active: List<ShopOrd> = store.orders.value.filter { SHOP_ACTIVE_STATUSES.contains(it.status) }
    val history: List<ShopOrd> = store.orders.value.filter { it.status == "done" || it.status == "rejected" }

    Box(Modifier.fillMaxSize().background(C.Gray)) {
        Column(Modifier.fillMaxSize()) {
            // หัวร้าน
            Row(Modifier.fillMaxWidth().background(SHOP_GRAD).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    if (avatar.isNotEmpty()) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else PathIcon(ShopP.SHOP, Color.White, 22.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("พอร์ทัลจัดการร้านค้า", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).clickable { onReviews() }.padding(horizontal = 10.dp, vertical = 2.dp)) {
                            Text("⭐ รีวิว", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { sidebar = true }, contentAlignment = Alignment.Center) {
                    PathIcon(P.MENU, Color.White, 18.dp)
                }
            }
            // สวิตช์เปิด/ปิดร้าน (ใช้เอกสาร shops/{id} ร่วมกับแอดมิน)
            Row(Modifier.fillMaxWidth().background(SHOP_GRAD).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isOpen) "ร้านเปิดอยู่" else "ร้านปิดอยู่", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                ShopSwitch(isOpen, Color(0xFF22C55E), Color.White.copy(alpha = 0.25f)) {
                    if (!store.shopLoaded.value) return@ShopSwitch
                    if (isOpen) showClose = true
                    else scope.launch {
                        try { ShopApi.openShop(store.shopId); shopToast(ctx, "เปิดร้านแล้ว") } catch (e: Exception) { shopToast(ctx, "เปิดร้านไม่สำเร็จ") }
                    }
                }
            }
            // แท็บ
            Row(Modifier.fillMaxWidth().background(Color.White)) {
                ShopTab("ออเดอร์", tab == 0, Modifier.weight(1f)) { tab = 0 }
                ShopTab("ประวัติ", tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
            HorizontalDivider(color = Color(0xFFF0F2F5))
            // รายการ
            val list: List<ShopOrd> = if (tab == 0) active else history
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list.isEmpty()) ShopEmpty(if (tab == 0) "ยังไม่มีออเดอร์" else "ยังไม่มีประวัติ", if (tab == 0) "ออเดอร์ใหม่จะแสดงที่นี่" else "")
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                    items(list, key = { it.id }) { o ->
                        if (tab == 0) ShopOrderCard(o) { onOpenOrder(o.id) } else ShopHistoryCard(o) { onOpenOrder(o.id) }
                    }
                }
            }
            // แถบเหตุผลที่ปิดร้าน
            if (!isOpen && reason.isNotEmpty()) {
                val note = if (closeType == "temp") " (จะเปิดเองอัตโนมัติ)" else if (closeType == "long") " (จะเปิดเองตามรอบเวลาถัดไป)" else ""
                Text(
                    "ร้านปิดอยู่ เหตุผล: $reason$note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(ShopRedLight).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            // เมนูล่าง
            Row(Modifier.fillMaxWidth().shadow(6.dp).background(Color.White).navigationBarsPadding()) {
                ShopNavItem(P.HOME, "หน้าหลัก", true, Modifier.weight(1f)) { }
                ShopNavItem(ShopP.DOLLAR, "รายได้", false, Modifier.weight(1f)) { shopToast(ctx, "หน้ารายได้กำลังจะมาในอัปเดตถัดไป") }
                if (loyaltyOn) ShopNavItem(P.TAG, "สะสมแต้ม", false, Modifier.weight(1f)) { shopToast(ctx, "หน้าสะสมแต้มกำลังจะมาในอัปเดตถัดไป") }
                ShopNavItem(ShopP.MORE, "เพิ่มเติม", false, Modifier.weight(1f)) { sidebar = true }
            }
        }

        // แบนเนอร์ออเดอร์ใหม่ (ลอยด้านบน)
        val banner: String? = store.banner.value
        LaunchedEffect(banner) { if (banner != null) { delay(4000); if (store.banner.value == banner) store.banner.value = null } }
        AnimatedVisibility(
            banner != null, Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)), exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))
        ) {
            Box(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(C.Primary).clickable { store.banner.value = null }.padding(14.dp)) {
                Text(banner ?: "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        ShopSidebar(
            visible = sidebar, name = name, sub = shop["desc"]?.toString().takeIf { !it.isNullOrEmpty() } ?: "จัดการข้อมูลร้าน", avatar = avatar, loyaltyOn = loyaltyOn,
            onClose = { sidebar = false },
            onSoon = { shopToast(ctx, "ฟีเจอร์นี้กำลังจะมาในอัปเดตถัดไป") },
            onShare = {
                val slug: String = shop["slug"]?.toString().takeIf { !it.isNullOrEmpty() } ?: store.shopId
                val link = "https://nimitdelivery.vercel.app/menu.html?id=" + java.net.URLEncoder.encode(slug, "UTF-8") + "&openExternalBrowser=1"
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("menu", link))
                shopToast(ctx, "คัดลอกลิงก์แล้ว")
                sidebar = false
            },
            onReviews = { sidebar = false; onReviews() },
            onLoyalty = {
                val target: Int = (shop["loyaltyTarget"] as? Number)?.toInt() ?: 10
                scope.launch { try { ShopApi.setLoyalty(store.shopId, !loyaltyOn, target) } catch (e: Exception) { shopToast(ctx, "บันทึกไม่สำเร็จ") } }
            },
            onLogout = { sidebar = false; onLogout() }
        )

        if (showClose) ShopCloseDialog(
            onDismiss = { showClose = false },
            onConfirm = { type: String, reasonText: String, minutes: Int ->
                showClose = false
                scope.launch {
                    try {
                        val until: Long? = if (type == "temp") System.currentTimeMillis() + minutes * 60_000L else shopNextOpenTime(shop)
                        ShopApi.closeShop(store.shopId, reasonText, type, until)
                        shopToast(ctx, "ปิดร้านแล้ว")
                    } catch (e: Exception) { shopToast(ctx, "ปิดร้านไม่สำเร็จ") }
                }
            }
        )
    }
}

@Composable
private fun ShopTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clickable { onClick() }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) C.Primary else C.Subtext)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(top = 30.dp).fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(C.Primary))
    }
}

@Composable
private fun ShopNavItem(path: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (active) C.Primary else C.Subtext
    Column(modifier.clickable { onClick() }.padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PathIcon(path, c, 22.dp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
private fun ShopEmpty(title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(76.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            PathIcon(P.BAG, Color(0xFFCBD5E1), 34.dp)
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = C.Subtext)
    }
}

@Composable
private fun ShopOrderCard(o: ShopOrd, onClick: () -> Unit) {
    val items = o.items()
    val summary: String = items.joinToString(", ") { (it["name"]?.toString() ?: "") + " x" + ((it["qty"] as? Number)?.toInt() ?: 0) }
    val rider: String = o.s("riderName")
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.PrimaryLight, Color(0xFFF0F6FF)))).padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(o.orderId, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
            ShopPill(o.status)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                PathIcon(P.CART, C.Subtext, 14.dp, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Text(summary, fontSize = 13.sp, color = C.Text)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PathIcon(ShopP.PERSON, C.Subtext, 14.dp)
                Spacer(Modifier.width(10.dp))
                Text(o.cust("name").ifEmpty { o.cust("phone").ifEmpty { "-" } }, fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
                Text(shopTime(o.created), fontSize = 11.sp, color = C.Subtext)
            }
        }
        HorizontalDivider(color = Color(0xFFF1F3F6))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(o.qty().toString() + " รายการ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext)
            Spacer(Modifier.weight(1f))
            if (rider.isNotEmpty()) {
                Box(Modifier.widthIn38().clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(rider, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(ShopOrangeLight).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("รอแอดมินจัดไรเดอร์", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ShopOrange, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("฿" + com.nimit.delivery.data.shopNum(o.n("grandTotal")), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        }
    }
}

private fun Modifier.widthIn38(): Modifier = this.width(120.dp)

@Composable
private fun ShopHistoryCard(o: ShopOrd, onClick: () -> Unit) {
    val summary: String = o.items().joinToString(", ") { it["name"]?.toString() ?: "" }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(o.orderId, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
            ShopPill(o.status)
        }
        Text(summary, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("฿" + com.nimit.delivery.data.shopNum(o.n("grandTotal")), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text(com.nimit.delivery.data.shopDateTime(o.created), fontSize = 11.sp, color = C.Subtext)
        }
    }
}

@Composable
private fun ShopSidebar(
    visible: Boolean, name: String, sub: String, avatar: String, loyaltyOn: Boolean,
    onClose: () -> Unit, onSoon: () -> Unit, onShare: () -> Unit, onReviews: () -> Unit, onLoyalty: () -> Unit, onLogout: () -> Unit
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() })
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(200)) { it }, exit = slideOutHorizontally(tween(200)) { it }
        ) {
            Column(Modifier.width(280.dp).fillMaxHeight().shadow(16.dp).background(Color.White)) {
                Column(Modifier.fillMaxWidth().background(SHOP_GRAD).statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        if (avatar.isNotEmpty()) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else PathIcon(ShopP.SHOP, Color.White, 28.dp)
                    }
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                    Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
                }
                ShopSideItem(ShopP.EDIT, "แก้ไขข้อมูลร้าน", "ชื่อ เบอร์ เวลา ที่อยู่", null) { onSoon() }
                ShopSideItem(ShopP.SHARE, "แชร์ลิงก์เมนู", "คัดลอกลิงก์ให้ลูกค้าสั่งได้ตรงร้านนี้เลย", null) { onShare() }
                ShopSideItem(ShopP.IMAGE, "โฆษณา", "เพิ่มและจัดการโฆษณา", null) { onSoon() }
                ShopSideItem(P.STAR, "รีวิวร้านของฉัน", "คะแนนดาวและความคิดเห็น", null) { onReviews() }
                Row(Modifier.fillMaxWidth().clickable { onLoyalty() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PathIcon(P.TAG, C.Subtext, 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("สะสมแต้ม", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                        Text(if (loyaltyOn) "เปิดอยู่" else "ปิดอยู่", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                    }
                    ShopSwitch(loyaltyOn, C.Primary, Color(0xFFCBD5E1), 40.dp, 22.dp) { onLoyalty() }
                }
                HorizontalDivider(color = C.Gray)
                ShopSideItem(ShopP.LOGOUT, "ออกจากระบบ", "", ShopRed) { onLogout() }
            }
        }
    }
}

@Composable
private fun ShopSideItem(path: String, title: String, sub: String, tint: Color?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PathIcon(path, tint ?: C.Subtext, 20.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint ?: C.Text)
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
            }
        }
        HorizontalDivider(color = C.Gray)
    }
}

/** ป๊อบอัพปิดร้าน: เลือกปิดยาว/ปิดชั่วคราว + เหตุผล (บังคับ) */
@Composable
private fun ShopCloseDialog(onDismiss: () -> Unit, onConfirm: (String, String, Int) -> Unit) {
    var type by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("0") }
    var minute by remember { mutableStateOf("30") }
    var err by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ปิดร้าน", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (type.isEmpty()) {
                Text("เลือกรูปแบบการปิดร้าน", fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ShopChoice("ปิดยาว", "ปิดจนกว่าจะถึงรอบเปิดร้านถัดไปตามเวลาที่ตั้งไว้ (ถ้าไม่ได้เปิด \"เวลาเปิด-ปิดอัตโนมัติ\" ไว้ จะปิดค้างจนกว่าจะกดเปิดเอง)") { type = "long" }
                ShopChoice("ปิดชั่วคราว", "ตั้งเวลานับถอยหลัง ครบเวลาแล้วร้านเปิดเองอัตโนมัติ") { type = "temp" }
                Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp))
            } else {
                Text("‹ กลับ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.clickable { type = ""; err = "" })
                if (type == "temp") {
                    Text("ปิดนานเท่าไหร่", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(70.dp)) { NimitInput(hour, { hour = it.filter { c -> c.isDigit() }.take(2) }, "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                        Text("ชม.", fontSize = 13.sp, color = C.Subtext)
                        Box(Modifier.width(70.dp)) { NimitInput(minute, { minute = it.filter { c -> c.isDigit() }.take(2) }, "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                        Text("นาที", fontSize = 13.sp, color = C.Subtext)
                    }
                }
                Text("กรุณาระบุเหตุผลที่ปิดร้าน (บังคับ)", fontSize = 14.sp, color = C.Subtext)
                NimitInput(reason, { reason = it; err = "" }, "เช่น ของหมด, พักร้าน, ปิดปรับปรุง...", height = 70.dp, singleLine = false, fontSize = 14)
                if (err.isNotEmpty()) Text(err, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    }
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(ShopRed).clickable {
                        val mins: Int = (hour.toIntOrNull() ?: 0) * 60 + (minute.toIntOrNull() ?: 0)
                        if (reason.trim().isEmpty()) err = "กรุณาระบุเหตุผลที่ปิดร้าน"
                        else if (type == "temp" && mins <= 0) err = "กรุณาตั้งเวลาปิดชั่วคราวอย่างน้อย 1 นาที"
                        else onConfirm(type, reason.trim(), mins)
                    }, contentAlignment = Alignment.Center) {
                        Text("ยืนยันปิดร้าน", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopChoice(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(2.dp, Color(0xFFEEF0F4), RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(sub, fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp), lineHeight = 17.sp)
    }
}
