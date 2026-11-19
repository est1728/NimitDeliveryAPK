package com.nimit.delivery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.SHOP_ADMIN_PHONE
import com.nimit.delivery.data.SHOP_STATUS_TH
import com.nimit.delivery.data.ShopOrd
import com.nimit.delivery.data.ShopStore
import com.nimit.delivery.data.shopDateTime
import com.nimit.delivery.data.shopNum

private fun shopDial(ctx: Context, phone: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) { }
}

private fun shopStatusColor(s: String): Color = when (s) {
    "pending" -> ShopOrange
    "accepted", "picking" -> C.Primary
    "arrived" -> Color(0xFFD97706)
    "delivering", "done" -> ShopGreen
    "rejected" -> ShopRed
    else -> C.Text
}

/** รายละเอียดออเดอร์ (ดูอย่างเดียว ตามหน้า HTML ร้านไม่มีปุ่มเปลี่ยนสถานะ) */
@Composable
fun ShopOrderDetail(store: ShopStore, docId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val o: ShopOrd? = store.orders.value.firstOrNull { it.id == docId }
    // เปิดหน้านี้ = รับทราบออเดอร์ใหม่ (เสียงเตือนหยุด) / ออกจากหน้านี้ก็รับทราบออเดอร์ที่เข้ามาระหว่างนั้น
    DisposableEffect(docId) { ShopAlert.detailOpened(); onDispose { ShopAlert.detailClosed() } }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(Modifier.fillMaxWidth().shadow(2.dp).background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(o?.orderId ?: "รายละเอียดออเดอร์", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
            if (o != null) ShopPill(o.status)
        }
        if (o == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("กำลังโหลด...", fontSize = 14.sp, color = C.Subtext) }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
            val riderName: String = o.s("riderName")
            val riderPhone: String = o.s("riderPhone")
            val items: List<Map<String, Any?>> = o.items()
            var itemsTotal = 0.0
            for (i in items) itemsTotal += ((i["price"] as? Number)?.toDouble() ?: 0.0) * ((i["qty"] as? Number)?.toInt() ?: 0)

            // การ์ดหัว: รหัส เวลา และชิปสรุป
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(18.dp)
            ) {
                Text("ออเดอร์", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
                Text(o.orderId, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                Text(shopDateTime(o.created), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(SHOP_STATUS_TH[o.status] ?: o.status)
                    HeroChip(o.qty().toString() + " รายการ")
                    HeroChip(if (o.s("paymentMethod") == "cash") "เงินสด" else "โอนเงิน")
                }
            }

            ShopSection("รายละเอียด") {
                ShopRow("สถานะออเดอร์", SHOP_STATUS_TH[o.status] ?: o.status, shopStatusColor(o.status), true)
                ShopRow("จำนวน", o.qty().toString() + " รายการ")
                ShopRow("ชำระเงิน", if (o.s("paymentMethod") == "cash") "เงินสด" else "โอนเงิน")
                ShopRow("เวลา", shopDateTime(o.created), last = true)
            }

            ShopSection("ติดต่อ") {
                ContactRow("แอดมิน Nimit Delivery", shopFmtPhone(SHOP_ADMIN_PHONE), "โทร") { shopDial(ctx, SHOP_ADMIN_PHONE) }
                if (riderName.isNotEmpty()) {
                    HorizontalDivider(color = Color(0xFFF5F6F8), modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("ไรเดอร์ · $riderName", if (riderPhone.isNotEmpty()) shopFmtPhone(riderPhone) else "ยังไม่มีเบอร์", "โทร") { if (riderPhone.isNotEmpty()) shopDial(ctx, riderPhone) }
                }
            }

            // ตารางรายการสินค้า
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
                Text("รายการสินค้า", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth().background(C.PrimaryLight).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("รายการ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.weight(1f))
                    Text("จำนวน", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                    Text("ราคา", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                }
                for ((idx, i) in items.withIndex()) {
                    val qty: Int = (i["qty"] as? Number)?.toInt() ?: 0
                    val price: Double = (i["price"] as? Number)?.toDouble() ?: 0.0
                    val cat: String = i["category"]?.toString() ?: ""
                    val note: String = i["note"]?.toString() ?: ""
                    val opts: List<Map<String, Any?>> = com.nimit.delivery.data.shopAsList(i["options"]).map { com.nimit.delivery.data.shopAsMap(it) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(i["name"]?.toString() ?: "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                            if (cat.isNotEmpty()) Text("หมวด: $cat", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            for (op in opts) {
                                val p: Double = (op["price"] as? Number)?.toDouble() ?: 0.0
                                Text("• " + (op["name"]?.toString() ?: "-") + (if (p != 0.0) "  +฿" + shopNum(p) else ""), fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (note.isNotEmpty()) Text("📝 $note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text("x$qty", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                        Text("฿" + shopNum(price * qty), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                    }
                    HorizontalDivider(color = Color(0xFFF1F3F6))
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFFFAFBFC)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("รวมสินค้า " + items.sumOf { (it["qty"] as? Number)?.toInt() ?: 0 } + " ชิ้น", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.weight(1f))
                    Text("฿" + shopNum(itemsTotal), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                }
            }

            // สรุปรายได้ร้าน
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(ShopGreenLight).padding(16.dp)
            ) {
                Text("สรุปรายได้", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ShopGreen, modifier = Modifier.padding(bottom = 10.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("ราคาสินค้ารวม", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.weight(1f))
                    Text("฿" + shopNum(itemsTotal), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                }
                HorizontalDivider(color = Color(0x3316A34A), modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ร้านค้ารับเงิน", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ShopGreen, modifier = Modifier.weight(1f))
                    Text("฿" + shopNum(o.n("baseCost")), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ShopGreen)
                }
            }
        }
    }
}

private fun shopFmtPhone(p: String): String {
    val d: String = p.filter { it.isDigit() }
    return if (d.length == 10) d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6) else p
}

@Composable
private fun HeroChip(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 5.dp))
}

@Composable
private fun ContactRow(title: String, phone: String, btn: String, onCall: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(C.PrimaryLight), contentAlignment = Alignment.Center) { PathIcon(ShopP.PHONE, C.Primary, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
            Text(phone, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Primary).clickable { onCall() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PathIcon(ShopP.PHONE, Color.White, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(btn, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
private fun ShopSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(bottom = 10.dp))
        HorizontalDivider(color = C.Gray, thickness = 2.dp, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
private fun ShopRow(label: String, value: String, color: Color = C.Text, extra: Boolean = false, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, fontSize = 14.sp, color = C.Subtext, modifier = Modifier.width(110.dp))
        Text(":", fontSize = 14.sp, color = C.Subtext, modifier = Modifier.padding(end = 8.dp))
        Text(value, fontSize = 14.sp, fontWeight = if (extra) FontWeight.ExtraBold else FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
    }
    if (!last) HorizontalDivider(color = Color(0xFFF5F6F8))
}
