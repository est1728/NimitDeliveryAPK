package com.nimit.delivery.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminCoupon
import com.nimit.delivery.data.AdminCouponsApi
import com.nimit.delivery.data.AdminCreateApi
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminShopRef
import com.nimit.delivery.data.adminErrorText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private val KRed = Color(0xFFDC2626)
private val KRedL = Color(0xFFFEE2E2)

private fun kToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun kNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private fun kDesc(c: AdminCoupon): String = when (c.type) {
    "percent" -> "ลด " + kNum(c.n("value")) + "%" + (if (c.n("maxDiscount") > 0.0) " (สูงสุด ฿" + kNum(c.n("maxDiscount")) + ")" else "")
    "fixed" -> "ลด ฿" + kNum(c.n("value"))
    else -> "ส่งฟรี"
}

private fun kDate(ms: Long): String = SimpleDateFormat("d MMM yy HH:mm", Locale("th", "TH")).format(java.util.Date(ms))

@Composable
fun AdminCouponsPage(store: AdminOrdersStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }
    var coupons by remember { mutableStateOf<List<AdminCoupon>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<AdminCoupon?>(null) }
    var shops by remember { mutableStateOf<List<AdminShopRef>?>(null) }
    var shopMenu by remember { mutableStateOf(false) }
    var cTitle by remember { mutableStateOf("") }
    var cType by remember { mutableStateOf("percent") }
    var cValue by remember { mutableStateOf("") }
    var cMax by remember { mutableStateOf("") }
    var cMin by remember { mutableStateOf("") }
    var cScope by remember { mutableStateOf("all") }
    var cShop by remember { mutableStateOf<AdminShopRef?>(null) }
    var cEnd by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val reg = AdminCouponsApi.listen({ coupons = it }, { error = it })
        onDispose { reg.remove() }
    }
    LaunchedEffect(showForm) {
        if (showForm && shops == null) {
            try { shops = AdminCreateApi.shops() } catch (e: Exception) { shops = emptyList() }
        }
    }
    val scopeIds: Set<String> = store.shopIds.value
    val list: List<AdminCoupon>? = coupons
        ?.filter { it.s("shopId").isEmpty() || scopeIds.contains(it.s("shopId")) }
        ?.sortedByDescending { it.createdSec }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("จัดการคูปอง", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)).clickable {
                    cTitle = ""; cType = "percent"; cValue = ""; cMax = ""; cMin = ""; cScope = "all"; cShop = null; cEnd = 0L
                    showForm = true
                },
                contentAlignment = Alignment.Center
            ) { Icon(AdminIcPlus, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list == null || list.isEmpty()) {
                item {
                    val msg: String = when {
                        error != null -> "โหลดคูปองไม่ได้: " + error
                        list == null -> "กำลังโหลด..."
                        else -> "ยังไม่มีคูปอง"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            if (list != null) {
                items(list, key = { it.id }) { c ->
                    val expired: Boolean = c.endAt < System.currentTimeMillis()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.s("title"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                kDesc(c) + (if (c.n("minOrderAmount") > 0.0) " · ขั้นต่ำ ฿" + kNum(c.n("minOrderAmount")) else "") +
                                    " · " + (if (c.s("shopId").isNotEmpty()) c.s("shopName").ifEmpty { c.s("shopId") } else "ทุกร้าน"),
                                fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                "หมดเขต " + kDate(c.endAt) + (if (expired) " (หมดอายุแล้ว)" else ""),
                                fontSize = 12.sp, color = if (expired) KRed else C.Subtext
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(KRedL).clickable { deleteFor = c }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("ลบ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KRed)
                        }
                    }
                }
            }
        }
    }

    // ===== เพิ่มคูปอง =====
    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("เพิ่มคูปอง") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cTitle, onValueChange = { cTitle = it }, label = { Text("ชื่อคูปอง (ลูกค้าจะเห็นข้อความนี้)") },
                        placeholder = { Text("เช่น ลด 10% สูงสุด 50 บาท") }, modifier = Modifier.fillMaxWidth()
                    )
                    Text("ประเภทส่วนลด", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    KChoice("ลดเป็นเปอร์เซ็นต์ (%)", cType == "percent") { cType = "percent" }
                    KChoice("ลดเป็นจำนวนเงินตายตัว (บาท)", cType == "fixed") { cType = "fixed" }
                    KChoice("ส่งฟรี", cType == "freeship") { cType = "freeship" }
                    if (cType != "freeship") {
                        OutlinedTextField(
                            value = cValue, onValueChange = { cValue = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (cType == "percent") "ลด (%)" else "ลด (บาท)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (cType != "fixed") {
                        OutlinedTextField(
                            value = cMax, onValueChange = { cMax = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("ลดสูงสุด (บาท)") }, placeholder = { Text("เว้นว่าง = ไม่จำกัด") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = cMin, onValueChange = { cMin = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("ยอดสั่งซื้อขั้นต่ำ (บาท)") }, placeholder = { Text("เว้นว่าง = ไม่มีขั้นต่ำ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                    Text("ใช้ได้ที่ร้านไหน", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    KChoice("ใช้ได้ทุกร้าน", cScope == "all") { cScope = "all" }
                    KChoice("ผูกกับร้านเดียว", cScope == "shop") { cScope = "shop" }
                    if (cScope == "shop") {
                        Box {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)).clickable { shopMenu = true }.padding(horizontal = 14.dp, vertical = 14.dp)
                            ) { Text(cShop?.name ?: "เลือกร้านค้า", fontSize = 14.sp, color = if (cShop == null) C.Subtext else C.Text) }
                            DropdownMenu(expanded = shopMenu, onDismissRequest = { shopMenu = false }) {
                                val ls: List<AdminShopRef> = (shops ?: emptyList()).filter { scopeIds.contains(it.id) }.sortedBy { it.name }
                                if (ls.isEmpty()) DropdownMenuItem(text = { Text("ไม่พบร้านค้า") }, onClick = { shopMenu = false })
                                for (sh in ls) DropdownMenuItem(text = { Text(sh.name) }, onClick = { cShop = sh; shopMenu = false })
                            }
                        }
                    }
                    Text("คูปองหมดเขตเมื่อไหร่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)).clickable {
                            val now = Calendar.getInstance()
                            DatePickerDialog(ctx, { _, y, m, d ->
                                TimePickerDialog(ctx, { _, hh, mm ->
                                    val c = Calendar.getInstance()
                                    c.set(y, m, d, hh, mm, 0)
                                    c.set(Calendar.MILLISECOND, 0)
                                    cEnd = c.timeInMillis
                                }, 23, 59, true).show()
                            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                        }.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) { Text(if (cEnd > 0L) kDate(cEnd) else "เลือกวันและเวลา", fontSize = 14.sp, color = if (cEnd > 0L) C.Text else C.Subtext) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val value: Double = cValue.toDoubleOrNull() ?: 0.0
                    val maxD: Double = cMax.toDoubleOrNull() ?: 0.0
                    val minO: Double = cMin.toDoubleOrNull() ?: 0.0
                    val sh: AdminShopRef? = cShop
                    if (cTitle.trim().isEmpty()) kToast(ctx, "กรุณาใส่ชื่อคูปอง")
                    else if (cType != "freeship" && value <= 0.0) kToast(ctx, "กรุณาใส่มูลค่าส่วนลด")
                    else if (cScope == "shop" && sh == null) kToast(ctx, "กรุณาเลือกร้านที่ผูกคูปองนี้")
                    else if (cEnd <= 0L) kToast(ctx, "กรุณาเลือกเวลาหมดเขต")
                    else if (cEnd <= System.currentTimeMillis()) kToast(ctx, "เวลาหมดเขตต้องเป็นอนาคต")
                    else {
                        val title = cTitle.trim()
                        val type = cType
                        val end = cEnd
                        val sid: String = if (cScope == "shop" && sh != null) sh.id else ""
                        val sname: String = if (cScope == "shop" && sh != null) sh.name else ""
                        busy = true
                        scope.launch {
                            try {
                                AdminCouponsApi.add(title, type, if (type == "freeship") 0.0 else value, if (type == "fixed") 0.0 else maxD, minO, sid, sname, end)
                                kToast(ctx, "เพิ่มคูปองเรียบร้อย")
                                showForm = false
                            } catch (e: Exception) {
                                kToast(ctx, "ผิดพลาด: " + adminErrorText(e))
                            }
                            busy = false
                        }
                    }
                }) { Text(if (busy) "กำลังบันทึก..." else "เพิ่มคูปอง") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("ปิด") } }
        )
    }

    // ===== ลบ =====
    val df = deleteFor
    if (df != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("ลบคูปองนี้?") },
            text = { Text(df.s("title")) },
            confirmButton = {
                TextButton(onClick = {
                    val id = df.id
                    deleteFor = null
                    scope.launch {
                        try { AdminCouponsApi.delete(id); kToast(ctx, "ลบแล้ว") } catch (e: Exception) { kToast(ctx, "ลบไม่สำเร็จ") }
                    }
                }) { Text("ลบ", color = KRed) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun KChoice(text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) C.PrimaryLight else Color(0xFFF8FAFC)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (on) "●" else "○", fontSize = 14.sp, color = if (on) C.Primary else Color(0xFF94A3B8))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
    }
}
