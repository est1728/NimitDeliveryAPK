#!/bin/bash
# หน้า "ร้านค้า" และ "ไรเดอร์" ให้หน้าตาเหมือนเว็บ: หัวไล่สี + ปุ่ม "+", การ์ดซ้อนในกรอบขาว, รูปร้าน, สวิตช์สีน้ำเงิน
# ปุ่มอื่นนอกจากสวิตช์รวมเป็นปุ่มแก้ไขปุ่มเดียว กดแล้วมีป๊อปอัปเมนู (ร้าน: เปิดหน้าร้าน/จัดการเมนู/แก้ไขข้อมูล/ลบ  ไรเดอร์: ดูลิงก์/เติมเงิน/แก้ไข/ลบ)
# ตัวกรองเขตแสดงเฉพาะเมื่อเขตผูกหลังบ้านร่วมกับเขตอื่น  เพิ่ม/แก้ไข/ลบร้านได้ (ร้านใหม่เริ่มต้นอยู่ในเขตของคุณ)  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
N=$J/ui/AdminNav.kt
IC=$J/ui/AdminIcons.kt
SD=$J/data/AdminShops.kt
for f in $N $IC $SD $J/ui/AdminShops.kt $J/ui/AdminRiders.kt $J/data/AdminRiders.kt; do [ -f $f ] || die "ไม่เจอ $f (ต้องรัน admin-shoplist-v1.sh และ admin-riders-v1.sh ก่อน)"; done
grep -q 'AdminShopsPage(store' $N || die "AdminNav.kt ไม่มี AdminShopsPage"
grep -q 'AdminRidersPage()' $N || die "AdminNav.kt ไม่มี AdminRidersPage"
grep -q 'private fun adminVec' $IC || die "AdminIcons.kt ไม่ใช่เวอร์ชันที่คาด"
[ "$(tail -n 1 $SD)" = "}" ] || die "AdminShops.kt (data): บรรทัดสุดท้ายไม่ใช่ }"

# ---------- ไอคอน (พาธเดียวกับที่เว็บใช้) ----------
if grep -q 'AdminIcEye' $IC; then
  echo "ไอคอน: มีแล้ว ข้าม"
else
  cat >> $IC <<'KT_EOF'

val AdminIcEye: ImageVector = adminVec("a_eye", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z")
val AdminIcDoc: ImageVector = adminVec("a_doc", "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z")
val AdminIcEdit: ImageVector = adminVec("a_edit", "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z")
val AdminIcTrash: ImageVector = adminVec("a_trash", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z")
val AdminIcWallet: ImageVector = adminVec("a_wallet", "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z")
val AdminIcPlus: ImageVector = adminVec("a_plus", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
val AdminIcChevron: ImageVector = adminVec("a_chev", "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z")
KT_EOF
  echo "ไอคอน: เพิ่มแล้ว"
fi

# ---------- ข้อมูล: เพิ่ม/แก้ไข/ลบร้าน ----------
if grep -q 'suspend fun saveShop' $SD; then
  echo "data(shops): แก้แล้ว ข้าม"
else
  grep -q '^import com.google.firebase.firestore.FieldValue$' $SD || die "data(shops): ไม่เจอ import FieldValue"
  head -n -1 $SD > .admin-lists.tmp
  cat >> .admin-lists.tmp <<'KT_EOF'

    /** เพิ่ม (id = null) หรือแก้ไขร้าน: ชื่อ slug คำอธิบาย เบอร์ และเขตที่ผูก (franchiseIds) เหมือนเว็บ */
    suspend fun saveShop(id: String?, name: String, slug: String, desc: String, phone: String, zoneIds: List<String>) {
        val m = HashMap<String, Any?>()
        m["name"] = name
        m["slug"] = slug
        m["desc"] = desc
        m["phone"] = phone
        m["updatedAt"] = FieldValue.serverTimestamp()
        if (id != null) {
            // แก้ไขร้านเดิม: ไม่แตะสถานะเปิด/ปิด  เลือกเขตไว้ = เขียนทับ franchiseIds และลบฟิลด์เก่า franchiseId  ไม่เลือกเลย = ลบทั้งสองฟิลด์
            if (zoneIds.isNotEmpty()) {
                m["franchiseIds"] = zoneIds
                m["franchiseId"] = FieldValue.delete()
            } else {
                m["franchiseIds"] = FieldValue.delete()
                m["franchiseId"] = FieldValue.delete()
            }
            db.collection("shops").document(id).update(m).await()
        } else {
            m["isOpen"] = true
            if (zoneIds.isNotEmpty()) m["franchiseIds"] = zoneIds
            m["createdAt"] = FieldValue.serverTimestamp()
            db.collection("shops").add(m).await()
        }
    }

    suspend fun deleteShop(id: String) {
        db.collection("shops").document(id).delete().await()
    }
}
KT_EOF
  mv .admin-lists.tmp $SD
  grep -q 'suspend fun saveShop' $SD || die "data(shops): เพิ่มไม่สำเร็จ"
  echo "data(shops): แก้แล้ว"
fi

# ---------- หน้าร้านค้า ----------
cat > $J/ui/AdminShops.kt <<'KT_EOF'
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
KT_EOF
echo "AdminShops.kt (ui): เขียนใหม่แล้ว"

# ---------- หน้าไรเดอร์ ----------
cat > $J/ui/AdminRiders.kt <<'KT_EOF'
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminRiderFull
import com.nimit.delivery.data.AdminRidersApi
import com.nimit.delivery.data.adminErrorText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RGreen = Color(0xFF16A34A)
private val RGreenL = Color(0xFFDCFCE7)
private val RRed = Color(0xFFDC2626)
private val RBorder = Color(0xFFEEF1F6)
private const val RWEB_BASE = "https://nimitdelivery.vercel.app"

private fun rToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun rMoney(d: Double): String {
    val whole: Boolean = d % 1.0 == 0.0
    return "฿" + (if (whole) java.text.NumberFormat.getIntegerInstance().format(d.toLong()) else d.toString())
}

@Composable
fun AdminRidersPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var riders by remember { mutableStateOf<List<AdminRiderFull>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<AdminRiderFull?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var fName by remember { mutableStateOf("") }
    var fPhone by remember { mutableStateOf("") }
    var fPlate by remember { mutableStateOf("") }
    var fSlug by remember { mutableStateOf("") }
    var topupFor by remember { mutableStateOf<AdminRiderFull?>(null) }
    var topupAmt by remember { mutableStateOf("") }
    var deleteFor by remember { mutableStateOf<AdminRiderFull?>(null) }

    DisposableEffect(Unit) {
        val reg = AdminRidersApi.listen({ riders = it }, { error = it })
        onDispose { reg.remove() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val list: List<AdminRiderFull>? = riders
    val onlineCount: Int = list?.count { it.online(now) } ?: 0

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ไรเดอร์", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(onlineCount.toString() + " คนออนไลน์", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { editing = null; fName = ""; fPhone = ""; fPlate = ""; fSlug = ""; showForm = true },
                contentAlignment = Alignment.Center
            ) { Icon(AdminIcPlus, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list == null || list.isEmpty()) {
                item {
                    val msg: String = when {
                        error != null -> "โหลดไรเดอร์ไม่ได้: " + error
                        list == null -> "กำลังโหลด..."
                        else -> "ยังไม่มีไรเดอร์"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            if (list != null) {
                items(list.sortedBy { it.name }, key = { it.id }) { r ->
                    RiderCard(
                        r, r.online(now),
                        onToggle = {
                            scope.launch {
                                try { AdminRidersApi.setActive(r.id, !r.active); rToast(ctx, if (!r.active) "เปิดใช้งานไรเดอร์แล้ว" else "ปิดใช้งานไรเดอร์แล้ว") }
                                catch (e: Exception) { rToast(ctx, "ไม่สำเร็จ") }
                            }
                        },
                        onTopup = { topupFor = r; topupAmt = "" },
                        onEdit = { editing = r; fName = r.name; fPhone = r.s("phone"); fPlate = r.s("plate"); fSlug = r.s("slug"); showForm = true },
                        onDelete = { deleteFor = r }
                    )
                }
            }
        }
    }

    // ===== เพิ่ม/แก้ไข =====
    if (showForm) {
        val ed = editing
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (ed == null) "เพิ่มไรเดอร์" else "แก้ไขไรเดอร์") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fName, onValueChange = { fName = it }, label = { Text("ชื่อ-นามสกุล *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = fPhone, onValueChange = { fPhone = it.filter { c -> c.isDigit() } }, label = { Text("เบอร์โทร") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = fPlate, onValueChange = { fPlate = it }, label = { Text("ทะเบียนรถ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fSlug, onValueChange = { fSlug = it }, label = { Text("slug (ลิงก์)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fName.trim().isEmpty()) {
                        rToast(ctx, "กรุณากรอกชื่อ")
                    } else {
                        val id: String? = ed?.id
                        val n = fName.trim()
                        val ph = fPhone.trim()
                        val pl = fPlate.trim()
                        val sl = fSlug.trim()
                        showForm = false
                        scope.launch {
                            try { AdminRidersApi.save(id, n, ph, pl, sl); rToast(ctx, "บันทึกสำเร็จ") }
                            catch (e: Exception) { rToast(ctx, "บันทึกไม่สำเร็จ: " + adminErrorText(e)) }
                        }
                    }
                }) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("ปิด") } }
        )
    }

    // ===== เติมเงิน =====
    val tf = topupFor
    if (tf != null) {
        AlertDialog(
            onDismissRequest = { topupFor = null },
            title = { Text("เติมเงิน " + tf.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ยอดปัจจุบัน: " + rMoney(tf.wallet), fontSize = 13.sp, color = C.Subtext)
                    OutlinedTextField(
                        value = topupAmt, onValueChange = { topupAmt = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("จำนวนเงิน (บาท)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt: Double = topupAmt.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        rToast(ctx, "กรุณากรอกจำนวนเงิน")
                    } else {
                        val id = tf.id
                        topupFor = null
                        scope.launch {
                            try { AdminRidersApi.topup(id, amt); rToast(ctx, "เติมเงิน " + rMoney(amt) + " สำเร็จ") }
                            catch (e: Exception) { rToast(ctx, "เติมเงินไม่สำเร็จ") }
                        }
                    }
                }) { Text("เติมเงิน") }
            },
            dismissButton = { TextButton(onClick = { topupFor = null }) { Text("ปิด") } }
        )
    }

    // ===== ลบ =====
    val df = deleteFor
    if (df != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("ลบไรเดอร์?") },
            text = { Text(df.name + " จะถูกลบออกจากระบบ") },
            confirmButton = {
                TextButton(onClick = {
                    val id = df.id
                    deleteFor = null
                    scope.launch {
                        try { AdminRidersApi.delete(id); rToast(ctx, "ลบแล้ว") }
                        catch (e: Exception) { rToast(ctx, "ลบไม่สำเร็จ") }
                    }
                }) { Text("ลบ", color = RRed) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun RiderCard(
    r: AdminRiderFull, online: Boolean,
    onToggle: () -> Unit, onTopup: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val link: String = RWEB_BASE + "/rider.html?id=" + r.slug
    val plate: String = r.s("plate")
    val phoneLine: String = r.s("phone") + (if (plate.isNotEmpty()) " · " + plate else "")
    val shiftIn: String = r.s("shiftIn")
    val shiftOut: String = r.s("shiftOut")
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().alpha(if (r.active) 1f else 0.55f).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .border(BorderStroke(1.5.dp, RBorder), RoundedCornerShape(22.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Primary, Color(0xFF1565C0)))),
                contentAlignment = Alignment.Center
            ) { Icon(AdminIcRiders, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (online) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(9.dp).clip(CircleShape).background(RGreen))
                    }
                }
                if (phoneLine.isNotBlank()) Text(phoneLine, fontSize = 14.sp, color = C.Subtext)
                if (shiftIn.isNotEmpty() || shiftOut.isNotEmpty()) {
                    Text("เข้า " + shiftIn.ifEmpty { "-" } + " · ออก " + shiftOut.ifEmpty { "-" }, fontSize = 14.sp, color = C.Subtext)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rMoney(r.wallet), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (r.wallet < 0.0) RRed else C.Primary)
                Text("กระเป๋า", fontSize = 12.sp, color = C.Subtext)
            }
        }
        HorizontalDivider(color = RBorder)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = r.active, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
            )
            Text(if (r.active) "เปิด" else "ปิด", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            // ปุ่มแก้ไขปุ่มเดียว: กดแล้วมีป๊อปอัปเมนู
            Box {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(RGreenL).clickable { menu = true },
                    contentAlignment = Alignment.Center
                ) { Icon(AdminIcEdit, null, tint = RGreen, modifier = Modifier.size(24.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(0.dp, (-260).dp)) {
                    DropdownMenuItem(
                        text = { Text("ดูลิงก์ไรเดอร์ (เว็บ)") }, leadingIcon = { Icon(AdminIcEye, null, tint = C.Primary) },
                        onClick = {
                            menu = false
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (e: Exception) { rToast(ctx, "เปิดลิงก์ไม่ได้") }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("เติมเงิน") }, leadingIcon = { Icon(AdminIcWallet, null, tint = RGreen) },
                        onClick = { menu = false; onTopup() }
                    )
                    DropdownMenuItem(
                        text = { Text("แก้ไขข้อมูล") }, leadingIcon = { Icon(AdminIcEdit, null, tint = C.Primary) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("ลบไรเดอร์", color = RRed) }, leadingIcon = { Icon(AdminIcTrash, null, tint = RRed) },
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
                        cm.setPrimaryClip(ClipData.newPlainText("rider-link", link))
                        rToast(ctx, "คัดลอกลิงก์แล้ว")
                    } catch (e: Exception) { rToast(ctx, "คัดลอกไม่ได้") }
                }.padding(start = 10.dp)
            )
        }
    }
}
KT_EOF
echo "AdminRiders.kt (ui): เขียนใหม่แล้ว"

# ---------- นำทาง: ส่งรหัสเขตของตัวเองให้หน้าร้านค้า ----------
if grep -q 'AdminShopsPage(store, s.franchiseId)' $N; then
  echo "AdminNav: แก้แล้ว ข้าม"
else
  grep -q 'AdminShopsPage(store)' $N || die "AdminNav: ไม่เจอจุดยึด AdminShopsPage(store)"
  sed -i 's|AdminShopsPage(store)|AdminShopsPage(store, s.franchiseId)|' $N
  grep -q 'AdminShopsPage(store, s.franchiseId)' $N || die "แก้ AdminNav ไม่สำเร็จ"
  echo "AdminNav: แก้แล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $IC $SD $J/ui/AdminShops.kt $J/ui/AdminRiders.kt $N; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -c 'coil.compose.AsyncImage' $J/ui/AdminShops.kt | sed 's/^/ใช้ Coil โหลดรูปร้าน: /'
echo "เสร็จ"
