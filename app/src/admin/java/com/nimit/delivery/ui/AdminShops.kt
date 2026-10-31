package com.nimit.delivery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminShop
import com.nimit.delivery.data.AdminShopsApi
import com.nimit.delivery.data.adminErrorText
import kotlinx.coroutines.launch

private val SGreen = Color(0xFF16A34A)
private val SGreenL = Color(0xFFDCFCE7)
private val SRed = Color(0xFFDC2626)
private val SRedL = Color(0xFFFEE2E2)
private val SBorder = Color(0xFFEEF1F6)
private const val WEB_BASE = "https://nimitdelivery.vercel.app"

private fun sToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun sOpenUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { sToast(ctx, "เปิดลิงก์ไม่ได้") }
}

@Composable
fun AdminShopsPage(store: AdminOrdersStore, ownZoneId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shops by remember { mutableStateOf<List<AdminShop>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoneFilter by remember { mutableStateOf("") }
    var zoneMenu by remember { mutableStateOf(false) }
    var closeFor by remember { mutableStateOf<AdminShop?>(null) }
    var typeFor by remember { mutableStateOf<AdminShop?>(null) }
    var deleteFor by remember { mutableStateOf<AdminShop?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminShop?>(null) }
    var stage by remember { mutableStateOf(0) }
    var closeType by remember { mutableStateOf("long") }
    var reason by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }
    var fName by remember { mutableStateOf("") }
    var fSlug by remember { mutableStateOf("") }
    var fDesc by remember { mutableStateOf("") }
    var fPhone by remember { mutableStateOf("") }
    val fZones = remember { mutableStateListOf<String>() }

    DisposableEffect(Unit) {
        val reg = AdminShopsApi.listen({ shops = it }, { error = it })
        onDispose { reg.remove() }
    }

    val scopeIds: Set<String> = store.shopIds.value
    val zoneIds: List<String> = store.zoneIds.value
    val list: List<AdminShop>? = shops
        ?.filter { scopeIds.contains(it.id) && (zoneFilter.isEmpty() || it.zones().contains(zoneFilter)) }
        ?.sortedBy { it.name }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        // หัวไล่สี + ปุ่ม +
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ร้านค้า", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)).clickable {
                    editing = null; fName = ""; fSlug = ""; fDesc = ""; fPhone = ""
                    fZones.clear(); fZones.add(ownZoneId)
                    showForm = true
                },
                contentAlignment = Alignment.Center
            ) { Icon(AdminIcPlus, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        // เลือกเขต: แสดงเฉพาะเมื่อผูกหลังบ้านร่วมกับเขตอื่น
        if (zoneIds.size > 1) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0F1F3))
                        .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                        .clickable { zoneMenu = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (zoneFilter.isEmpty()) "ร้านค้าทั้งหมด" else (store.zoneNames.value[zoneFilter] ?: zoneFilter), fontSize = 15.sp, color = C.Text, modifier = Modifier.weight(1f))
                    Icon(AdminIcChevron, null, tint = C.Subtext, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                    DropdownMenuItem(text = { Text("ร้านค้าทั้งหมด") }, onClick = { zoneFilter = ""; zoneMenu = false })
                    for (z in zoneIds) {
                        DropdownMenuItem(text = { Text(store.zoneNames.value[z] ?: z) }, onClick = { zoneFilter = z; zoneMenu = false })
                    }
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list == null || list.isEmpty()) {
                item {
                    val msg: String = when {
                        error != null -> "โหลดร้านค้าไม่ได้: " + error
                        list == null -> "กำลังโหลด..."
                        else -> "ไม่พบร้านค้า"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            if (list != null) {
                items(list, key = { it.id }) { s ->
                    ShopCard(
                        s, store,
                        onToggleOpen = {
                            if (s.isOpen) {
                                closeFor = s; stage = 0; closeType = "long"; reason = ""; hours = "0"; minutes = "30"
                            } else {
                                scope.launch {
                                    try { AdminShopsApi.open(s.id); sToast(ctx, "เปิดร้านแล้ว") } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ") }
                                }
                            }
                        },
                        onToggleType = { typeFor = s },
                        onEdit = {
                            editing = s; fName = s.name; fSlug = s.s("slug"); fDesc = s.s("desc"); fPhone = s.s("phone")
                            fZones.clear(); fZones.addAll(s.zones())
                            showForm = true
                        },
                        onDelete = { deleteFor = s }
                    )
                }
            }
        }
    }

    // ===== เพิ่ม/แก้ไขร้าน =====
    if (showForm) {
        val ed = editing
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (ed == null) "เพิ่มร้านค้า" else "แก้ไขข้อมูลร้าน") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fName, onValueChange = { fName = it }, label = { Text("ชื่อร้าน *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fSlug, onValueChange = { fSlug = it }, label = { Text("slug (ลิงก์)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fDesc, onValueChange = { fDesc = it }, label = { Text("คำอธิบาย") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = fPhone, onValueChange = { fPhone = it.filter { c -> c.isDigit() } }, label = { Text("เบอร์โทร") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                    )
                    Text("เขตที่ร้านนี้อยู่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(top = 4.dp))
                    for (z in zoneIds) {
                        Row(
                            Modifier.fillMaxWidth().clickable { if (fZones.contains(z)) fZones.remove(z) else fZones.add(z) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = fZones.contains(z), onCheckedChange = { on -> if (on) { if (!fZones.contains(z)) fZones.add(z) } else fZones.remove(z) })
                            Text(store.zoneNames.value[z] ?: z, fontSize = 14.sp, color = C.Text)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fName.trim().isEmpty()) {
                        sToast(ctx, "กรุณากรอกชื่อร้าน")
                    } else {
                        val id: String? = ed?.id
                        // เขตนอกกลุ่มที่เห็นอยู่ ต้องคงไว้ตามเดิม ไม่ให้หายตอนบันทึก
                        val keep: List<String> = ed?.zones()?.filter { !zoneIds.contains(it) } ?: emptyList()
                        val picked: List<String> = fZones.filter { zoneIds.contains(it) } + keep
                        val n = fName.trim()
                        val sl = fSlug.trim()
                        val de = fDesc.trim()
                        val ph = fPhone.trim()
                        showForm = false
                        scope.launch {
                            try { AdminShopsApi.saveShop(id, n, sl, de, ph, picked); sToast(ctx, "บันทึกสำเร็จ") }
                            catch (e: Exception) { sToast(ctx, "บันทึกไม่สำเร็จ: " + adminErrorText(e)) }
                        }
                    }
                }) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("ปิด") } }
        )
    }

    // ===== ลบร้าน =====
    val df = deleteFor
    if (df != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("ลบร้านนี้?") },
            text = { Text(df.name + " จะถูกลบออกจากระบบ") },
            confirmButton = {
                TextButton(onClick = {
                    val id = df.id
                    deleteFor = null
                    scope.launch {
                        try { AdminShopsApi.deleteShop(id); sToast(ctx, "ลบแล้ว") } catch (e: Exception) { sToast(ctx, "ลบไม่สำเร็จ") }
                    }
                }) { Text("ลบ", color = SRed) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("ยกเลิก") } }
        )
    }

    // ===== ปิดร้าน: เลือกรูปแบบ แล้วใส่เหตุผล =====
    val cf = closeFor
    if (cf != null) {
        AlertDialog(
            onDismissRequest = { closeFor = null },
            title = { Text("ปิดร้าน") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stage == 0) {
                        Text("เลือกรูปแบบการปิดร้าน", fontSize = 13.sp, color = C.Subtext)
                        SChoice("ปิดยาว", "ปิดจนกว่าจะถึงรอบเปิดร้านถัดไปตามเวลาที่ตั้งไว้ (ถ้าร้านไม่ได้ตั้งเวลาเปิด-ปิดอัตโนมัติ จะปิดค้างจนกว่าจะกดเปิดเอง)") {
                            closeType = "long"; stage = 1
                        }
                        SChoice("ปิดชั่วคราว", "ตั้งเวลานับถอยหลัง ครบเวลาแล้วร้านเปิดเองอัตโนมัติ") {
                            closeType = "temp"; stage = 1
                        }
                    } else {
                        Text("‹ กลับ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.clickable { stage = 0 })
                        if (closeType == "temp") {
                            Text("ปิดนานเท่าไหร่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)
                                )
                                Text("ชม.", fontSize = 13.sp, color = C.Subtext)
                                OutlinedTextField(
                                    value = minutes, onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)
                                )
                                Text("นาที", fontSize = 13.sp, color = C.Subtext)
                            }
                        }
                        Text("กรุณาระบุเหตุผลที่ปิดร้าน (บังคับ)", fontSize = 12.sp, color = C.Subtext)
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (stage == 1) {
                    TextButton(onClick = {
                        val r = reason.trim()
                        if (r.isEmpty()) {
                            sToast(ctx, "กรุณาใส่เหตุผล")
                        } else {
                            var until: Long? = null
                            var ok = true
                            if (closeType == "temp") {
                                val h = hours.toIntOrNull() ?: 0
                                val m = minutes.toIntOrNull() ?: 0
                                if (h <= 0 && m <= 0) {
                                    sToast(ctx, "กรุณาตั้งเวลาปิดชั่วคราวอย่างน้อย 1 นาที")
                                    ok = false
                                } else {
                                    until = System.currentTimeMillis() + (h * 3600L + m * 60L) * 1000L
                                }
                            } else {
                                until = AdminShopsApi.nextOpenTime(cf)
                            }
                            if (ok) {
                                val type = closeType
                                val sid = cf.id
                                closeFor = null
                                scope.launch {
                                    try { AdminShopsApi.close(sid, r, type, until); sToast(ctx, "ปิดร้านแล้ว") } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ") }
                                }
                            }
                        }
                    }) { Text("ปิดร้าน", color = SRed) }
                }
            },
            dismissButton = { TextButton(onClick = { closeFor = null }) { Text("ยกเลิก") } }
        )
    }

    // ===== สลับร้านฝากซื้อ/ร้านปกติ =====
    val tf = typeFor
    if (tf != null) {
        val toProxy: Boolean = !tf.isProxy
        AlertDialog(
            onDismissRequest = { typeFor = null },
            title = { Text(if (toProxy) "ตั้งเป็นร้านฝากซื้อ" else "เปลี่ยนกลับเป็นร้านปกติ") },
            text = {
                Text(
                    if (toProxy) "ตั้งร้านนี้เป็น \"ร้านฝากซื้อ\" ใช่หรือไม่?\nราคาสินค้าจะถือเป็นราคาสุทธิ ไม่บวก GP"
                    else "เปลี่ยนร้านนี้กลับเป็น \"ร้านปกติ\" ใช่หรือไม่?\nราคาสินค้าจะกลับไปคิด GP ตามปกติ"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = tf.id
                    typeFor = null
                    scope.launch {
                        try {
                            AdminShopsApi.setType(id, if (toProxy) "proxy" else "normal")
                            sToast(ctx, if (toProxy) "ตั้งเป็นร้านฝากซื้อแล้ว" else "ตั้งเป็นร้านปกติแล้ว")
                        } catch (e: Exception) { sToast(ctx, "ไม่สำเร็จ: " + adminErrorText(e)) }
                    }
                }) { Text("ยืนยัน") }
            },
            dismissButton = { TextButton(onClick = { typeFor = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun SChoice(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(BorderStroke(1.dp, SBorder), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(sub, fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ShopCard(
    s: AdminShop, store: AdminOrdersStore,
    onToggleOpen: () -> Unit, onToggleType: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val zones: List<String> = s.zones()
    val link: String = WEB_BASE + "/shop-admin.html?id=" + s.slug
    var menu by remember { mutableStateOf(false) }
    val img: String = s.s("avatarUrl").ifEmpty { s.s("imgUrl") }
    Column(
        Modifier.fillMaxWidth().alpha(if (s.isOpen) 1f else 0.6f).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .border(BorderStroke(1.5.dp, SBorder), RoundedCornerShape(22.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Primary, Color(0xFF1565C0)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(AdminIcShops, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(26.dp))
                if (img.isNotEmpty()) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.s("phone").isNotEmpty()) Text(s.s("phone"), fontSize = 14.sp, color = C.Subtext)
                val addr: String = s.s("address").ifEmpty { s.s("desc") }
                if (addr.isNotEmpty()) Text(addr, fontSize = 14.sp, color = C.Subtext, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (zones.isEmpty()) {
                    SBadge("ร้านกลาง (ไม่มีเขต)", Color(0xFFF1F5F9), C.Subtext)
                } else {
                    for (z in zones) SBadge("เขต: " + (store.zoneNames.value[z] ?: z), Color(0xFFE8F0FE), C.Primary)
                }
                if (!s.isOpen && s.s("closeReason").isNotEmpty()) {
                    Text("เหตุผลที่ปิด: " + s.s("closeReason"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SRed, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (s.isOpen) "เปิด" else "ปิด", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (s.isOpen) SGreen else SRed)
                Text("สถานะ", fontSize = 12.sp, color = C.Subtext)
            }
        }
        HorizontalDivider(color = SBorder)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = s.isOpen, onCheckedChange = { onToggleOpen() },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
            )
            Text(if (s.isOpen) "เปิด" else "ปิด", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = s.isProxy, onCheckedChange = { onToggleType() },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
            )
            Text("ฝากซื้อ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (s.isProxy) C.Primary else C.Subtext, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            // ปุ่มแก้ไขปุ่มเดียว: กดแล้วมีป๊อปอัปเมนู
            Box {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(SGreenL).clickable { menu = true },
                    contentAlignment = Alignment.Center
                ) { Icon(AdminIcEdit, null, tint = SGreen, modifier = Modifier.size(24.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(0.dp, (-260).dp)) {
                    DropdownMenuItem(
                        text = { Text("เปิดหน้าร้าน (เว็บ)") }, leadingIcon = { Icon(AdminIcEye, null, tint = C.Primary) },
                        onClick = { menu = false; sOpenUrl(ctx, link) }
                    )
                    DropdownMenuItem(
                        text = { Text("จัดการเมนู (เว็บ)") }, leadingIcon = { Icon(AdminIcDoc, null, tint = SGreen) },
                        onClick = { menu = false; sOpenUrl(ctx, WEB_BASE + "/shop-manage.html?id=" + s.slug) }
                    )
                    DropdownMenuItem(
                        text = { Text("แก้ไขข้อมูลร้าน") }, leadingIcon = { Icon(AdminIcEdit, null, tint = C.Primary) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("ลบร้าน", color = SRed) }, leadingIcon = { Icon(AdminIcTrash, null, tint = SRed) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(link, fontSize = 13.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "คัดลอก", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary,
                modifier = Modifier.clickable {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("shop-link", link))
                        sToast(ctx, "คัดลอกลิงก์แล้ว")
                    } catch (e: Exception) { sToast(ctx, "คัดลอกไม่ได้") }
                }.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun SBadge(text: String, bg: Color, fg: Color) {
    Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}
