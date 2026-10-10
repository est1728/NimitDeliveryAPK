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
            ShopSection("รายละเอียด") {
                ShopRow("รหัส", o.orderId)
                ShopRow("สถานะออเดอร์", SHOP_STATUS_TH[o.status] ?: o.status, shopStatusColor(o.status), true)
                ShopRow("จำนวน", o.qty().toString() + " รายการ")
                ShopRow("ชำระเงิน", if (o.s("paymentMethod") == "cash") "เงินสด" else "โอนเงิน")
                ShopRow("เวลา", shopDateTime(o.created))
                if (riderName.isNotEmpty()) ShopRow("ไรเดอร์", riderName + (if (riderPhone.isNotEmpty()) " · $riderPhone" else ""), last = true)
            }
            ShopSection("ติดต่อ") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShopCallBtn("โทรแอดมิน", Modifier.weight(1f)) { shopDial(ctx, SHOP_ADMIN_PHONE) }
                    if (riderName.isNotEmpty()) ShopCallBtn("โทรไรเดอร์", Modifier.weight(1f)) { if (riderPhone.isNotEmpty()) shopDial(ctx, riderPhone) }
                }
            }
            ShopSection("รายการสินค้า") {
                val items = o.items()
                for ((idx, i) in items.withIndex()) {
                    val qty: Int = (i["qty"] as? Number)?.toInt() ?: 0
                    val price: Double = (i["price"] as? Number)?.toDouble() ?: 0.0
                    val cat: String = i["category"]?.toString() ?: ""
                    val note: String = i["note"]?.toString() ?: ""
                    val opts: List<Map<String, Any?>> = com.nimit.delivery.data.shopAsList(i["options"]).map { com.nimit.delivery.data.shopAsMap(it) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(i["name"]?.toString() ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text)
                            if (cat.isNotEmpty()) Text("หมวด: $cat", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            for (op in opts) {
                                val p: Double = (op["price"] as? Number)?.toDouble() ?: 0.0
                                Text("• " + (op["name"]?.toString() ?: "-") + (if (p != 0.0) "  +฿" + shopNum(p) else ""), fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (note.isNotEmpty()) Text("📝 $note", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ShopRed, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text("x$qty", fontSize = 15.sp, color = C.Text, modifier = Modifier.padding(horizontal = 12.dp))
                        Text("฿" + shopNum(price * qty), fontSize = 15.sp, color = C.Text)
                    }
                    if (idx < items.size - 1) HorizontalDivider(color = C.Gray)
                }
                HorizontalDivider(color = C.Gray, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("ร้านค้ารับเงิน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ShopGreen, modifier = Modifier.weight(1f))
                    Text("฿" + shopNum(o.n("baseCost")), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ShopGreen)
                }
            }
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

@Composable
private fun ShopCallBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.PrimaryLight).clickable { onClick() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PathIcon(ShopP.PHONE, C.Primary, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary)
    }
}
