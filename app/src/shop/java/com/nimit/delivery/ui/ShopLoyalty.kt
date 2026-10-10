package com.nimit.delivery.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ShopApi
import com.nimit.delivery.data.ShopStore
import kotlinx.coroutines.launch

private class LoyaltyMember(val phone: String, val name: String, val points: Long)

/** ตั้งค่าสะสมแต้ม: จำนวนแต้ม, รายชื่อสมาชิก (+1 แต้ม), เพิ่มลูกค้าใหม่ด้วยเบอร์ */
@Composable
fun ShopLoyaltyScreen(store: ShopStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shop: Map<String, Any> = store.shop.value
    val shopName: String = shop["name"]?.toString() ?: ""
    val target: Int = maxOf(1, (shop["loyaltyTarget"] as? Number)?.toInt() ?: 10)
    var targetText by remember { mutableStateOf(target.toString()) }
    var members by remember { mutableStateOf<List<LoyaltyMember>>(emptyList()) }
    var addPhone by remember { mutableStateOf("") }
    var addPts by remember { mutableStateOf("1") }

    DisposableEffect(store.shopId) {
        val reg = FirebaseFirestore.getInstance().collection("loyaltyCards").whereEqualTo("shopId", store.shopId).addSnapshotListener { snap, _ ->
            if (snap != null) {
                members = snap.documents.map {
                    LoyaltyMember(it.getString("customerPhone") ?: "", it.getString("customerName") ?: "", it.getLong("points") ?: 0L)
                }.sortedByDescending { it.points }
            }
        }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(Modifier.fillMaxWidth().shadow(2.dp).background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("ตั้งค่าสะสมแต้ม", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
            LoyCard("จำนวนแต้มที่ต้องสะสม") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { NimitInput(targetText, { targetText = it.filter { c -> c.isDigit() } }, "เช่น 10", height = 44.dp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                    Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Primary).clickable {
                        val v: Int = targetText.toIntOrNull() ?: 0
                        if (v < 1) shopToast(ctx, "กรุณาใส่จำนวนแต้มให้ถูกต้อง")
                        else scope.launch {
                            try { ShopApi.saveLoyaltyTarget(store.shopId, v); shopToast(ctx, "บันทึกแล้ว") } catch (e: Exception) { shopToast(ctx, "บันทึกไม่สำเร็จ") }
                        }
                    }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("บันทึก", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
                Text("สะสมครบตามจำนวนนี้ ลูกค้าจะได้เมนูฟรี 1 อย่างตอนสั่งครั้งถัดไป", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 8.dp))
            }
            LoyCard("สมาชิกสะสมแต้ม") {
                Text("กดปุ่ม +1 แต้ม ที่รายชื่อลูกค้าได้เลย ไม่ต้องพิมพ์เบอร์โทรเอง", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 10.dp))
                if (members.isEmpty()) {
                    Text("ยังไม่มีสมาชิกสะสมแต้ม", fontSize = 12.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                } else {
                    for (m in members) {
                        val full: Long = m.points / target
                        val partial: Long = m.points % target
                        val txt: String = if (full > 0) "$full ใบเต็ม" + (if (partial > 0) " + $partial/$target" else "") else "$partial/$target"
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name.ifEmpty { m.phone.ifEmpty { "-" } }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                Text(m.phone, fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            Text(txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (full > 0) ShopGreen else Color(0xFFC2410C))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.height(30.dp).clip(RoundedCornerShape(16.dp)).background(C.PrimaryLight).clickable {
                                if (m.phone.isNotEmpty()) scope.launch {
                                    try { ShopApi.addPoints(store.shopId, shopName, m.phone, m.name, 1L, false) } catch (e: Exception) { shopToast(ctx, "เพิ่มแต้มไม่สำเร็จ") }
                                }
                            }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text("+1 แต้ม", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                            }
                        }
                        HorizontalDivider(color = C.Gray)
                    }
                }
            }
            LoyCard("เพิ่มลูกค้าใหม่ (ยังไม่เคยสั่งร้านนี้)") {
                Text("ใช้เฉพาะกรณีลูกค้ายังไม่เคยสั่งร้านนี้เลยจึงยังไม่มีชื่อในรายชื่อด้านบน ต้องรู้เบอร์โทรลูกค้าก่อน", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 8.dp))
                NimitInput(addPhone, { addPhone = it.filter { c -> c.isDigit() } }, "เบอร์โทรลูกค้า", height = 44.dp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { NimitInput(addPts, { addPts = it.filter { c -> c.isDigit() } }, "1", height = 44.dp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                    Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Primary).clickable {
                        val phone: String = addPhone.trim()
                        val pts: Long = addPts.toLongOrNull()?.takeIf { it > 0 } ?: 1L
                        if (phone.isEmpty()) shopToast(ctx, "กรุณาใส่เบอร์โทรลูกค้า")
                        else scope.launch {
                            try {
                                ShopApi.addPoints(store.shopId, shopName, phone, "", pts, true)
                                addPhone = ""; addPts = "1"
                                shopToast(ctx, "เพิ่มแต้มสำเร็จ")
                            } catch (e: Exception) { shopToast(ctx, "เพิ่มแต้มไม่สำเร็จ") }
                        }
                    }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("เพิ่มแต้ม", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoyCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(bottom = 10.dp))
        HorizontalDivider(color = C.Gray, thickness = 2.dp, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}
