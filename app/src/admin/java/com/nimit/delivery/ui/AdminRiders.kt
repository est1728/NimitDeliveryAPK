package com.nimit.delivery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
private val RRedL = Color(0xFFFEE2E2)
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
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ไรเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
            Text(onlineCount.toString() + " คนออนไลน์", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RGreen)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(20.dp)).background(C.Primary)
                    .clickable { editing = null; fName = ""; fPhone = ""; fPlate = ""; fSlug = ""; showForm = true }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("+ เพิ่ม", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (r.active) Color.White else Color(0xFFF1F5F9))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Primary, Color(0xFF1565C0)))),
                contentAlignment = Alignment.Center
            ) { Text(r.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (online) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(RGreen))
                    }
                }
                if (phoneLine.isNotBlank()) Text(phoneLine, fontSize = 12.sp, color = C.Subtext)
                if (shiftIn.isNotEmpty() || shiftOut.isNotEmpty()) {
                    Text("เข้า " + shiftIn.ifEmpty { "-" } + " · ออก " + shiftOut.ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rMoney(r.wallet), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (r.wallet < 0.0) RRed else C.Primary)
                Text("กระเป๋า", fontSize = 10.sp, color = C.Subtext)
            }
        }
        HorizontalDivider(color = Color(0xFFF5F6F8))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = r.active, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = RGreen, checkedThumbColor = Color.White)
            )
            Text(if (r.active) "เปิด" else "ปิด", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            RBtn("ลิงก์", C.PrimaryLight, C.Primary) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (e: Exception) { rToast(ctx, "เปิดลิงก์ไม่ได้") }
            }
            Spacer(Modifier.width(6.dp))
            RBtn("เติมเงิน", RGreenL, RGreen) { onTopup() }
            Spacer(Modifier.width(6.dp))
            RBtn("แก้ไข", C.PrimaryLight, C.Primary) { onEdit() }
            Spacer(Modifier.width(6.dp))
            RBtn("ลบ", RRedL, RRed) { onDelete() }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(link, fontSize = 10.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "คัดลอก", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                modifier = Modifier.clickable {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("rider-link", link))
                        rToast(ctx, "คัดลอกลิงก์แล้ว")
                    } catch (e: Exception) { rToast(ctx, "คัดลอกไม่ได้") }
                }.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun RBtn(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.height(30.dp).clip(RoundedCornerShape(8.dp)).background(bg).clickable { onClick() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
