package com.nimit.delivery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevAuditItem
import com.nimit.delivery.data.DevDetailRepo
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevOrderRow
import com.nimit.delivery.data.DevShopRow
import com.nimit.delivery.data.DevZoneDetail
import com.nimit.delivery.data.DevRepo
import com.nimit.delivery.data.DevZoneRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class DevCodeInfo(val zone: String, val phone: String, val code: String)

private val devGroupColors = listOf(
    Color(0xFF0C4AA6), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFF7C3AED), Color(0xFFDB2777)
)

private fun devGroupColor(g: String): Color = devGroupColors[Math.abs(g.hashCode() % devGroupColors.size)]

// หน้าแรก: การ์ดเขตแฟรนไชส์ (อ่านจาก franchises + shops) พร้อมปุ่มผูกเขต / เลิกผูก
@Composable
fun DevZonesScreen() {
    var list by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var showLink by remember { mutableStateOf(false) }
    var unlinkTarget by remember { mutableStateOf<DevFranchise?>(null) }
    var unlinkBusy by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var formTarget by remember { mutableStateOf<DevFranchise?>(null) }
    var codeInfo by remember { mutableStateOf<DevCodeInfo?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var linkInitial by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notice by remember { mutableStateOf("") }
    var noticeErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        list = null
        err = false
        try {
            list = DevRepo.franchises()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }
    val l = list
    val canLink = l != null && l.size >= 2
    val detailF = if (l != null) l.firstOrNull { it.id == detailId } else null
    if (detailF != null) BackHandler { detailId = null }

    if (detailF != null && l != null) {
        DevZoneDetailScreen(
            f = detailF, all = l, notice = notice, noticeErr = noticeErr,
            onBack = { detailId = null },
            onEdit = {
                formTarget = detailF
                showForm = true
            },
            onLink = {
                linkInitial = setOf(detailF.id)
                showLink = true
            },
            onUnlink = { unlinkTarget = detailF }
        )
    } else {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (l == null) "เขตแฟรนไชส์ทั้งหมด" else "เขตแฟรนไชส์ทั้งหมด (" + l.size + ")",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .padding(end = 8.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (canLink) DevC.PL else Color(0xFFF1F5F9))
                .clickable(enabled = canLink) {
                    linkInitial = emptySet()
                    showLink = true
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("ผูกเขต", color = if (canLink) DevC.P else DevC.S, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(20.dp)).background(DevC.P)
                .clickable {
                    formTarget = null
                    showForm = true
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ เพิ่มเขต", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    if (notice.isNotEmpty()) {
        DevCard {
            Text(notice, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (noticeErr) DevC.R else DevC.Gn)
        }
    }
    when {
        err -> DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        l == null -> DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        l.isEmpty() -> DevCard {
            Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S)
            Text(
                "เพิ่มเขตหลังคุยกับผู้สมัครทางไลน์ แล้วใช้ปุ่ม \"ผูกเขต\" เพื่อให้หลายเขตใช้หลังบ้านแอดมินเดียวกัน",
                fontSize = 11.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        else -> l.forEachIndexed { i, f ->
            val prevSame = f.groupId.isNotEmpty() && i > 0 && l[i - 1].groupId == f.groupId
            val nextSame = f.groupId.isNotEmpty() && i < l.size - 1 && l[i + 1].groupId == f.groupId
            DevZoneRow(
                f, l, prevSame, nextSame,
                { detailId = f.id },
                {
                    formTarget = f
                    showForm = true
                }
            ) { unlinkTarget = f }
        }
    }
    }

    if (showForm) {
        DevZoneFormDialog(
            target = formTarget,
            onDismiss = { showForm = false },
            onSaved = { info, msg ->
                showForm = false
                formTarget = null
                notice = msg
                noticeErr = false
                codeInfo = info
                tick++
            }
        )
    }
    val ci = codeInfo
    if (ci != null) {
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { codeInfo = null },
            containerColor = Color.White,
            title = { Text("รหัสเข้าแอปแอดมิน", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("เขต: " + ci.zone, fontSize = 13.sp, color = DevC.T)
                    Text("เบอร์: " + ci.phone, fontSize = 13.sp, color = DevC.T, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        ci.code, fontSize = 24.sp, fontWeight = FontWeight.Black, color = DevC.P,
                        modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                    )
                    Text(
                        "ส่งรหัสนี้ให้เจ้าของเขตตอนนี้ เมื่อปิดหน้าต่างนี้จะดูรหัสซ้ำไม่ได้ (ตั้งรหัสใหม่ได้ที่ปุ่มแก้ไข)",
                        fontSize = 12.sp, color = DevC.S, lineHeight = 18.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { codeInfo = null }) { Text("ปิด", color = DevC.P, fontWeight = FontWeight.Bold) } },
            dismissButton = {
                TextButton(onClick = { clip.setText(AnnotatedString(ci.code)) }) { Text("คัดลอกรหัส", color = DevC.S) }
            }
        )
    }
    if (showLink && l != null) {
        DevLinkDialog(
            all = l,
            initial = linkInitial,
            onDismiss = { showLink = false },
            onDone = {
                showLink = false
                notice = "ผูกเขตเรียบร้อย และบันทึกประวัติแล้ว"
                noticeErr = false
                tick++
            }
        )
    }
    val t = unlinkTarget
    if (t != null) {
        AlertDialog(
            onDismissRequest = { if (!unlinkBusy) unlinkTarget = null },
            containerColor = Color.White,
            title = { Text("เลิกผูก " + t.name + "?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "เขตนี้จะไม่ใช้หลังบ้านแอดมินร่วมกับเขตอื่นอีก ร้านและออเดอร์เดิมไม่ถูกลบ",
                    fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !unlinkBusy,
                    onClick = {
                        unlinkBusy = true
                        scope.launch {
                            val r = DevRepo.unlinkZone(t, l ?: emptyList())
                            unlinkBusy = false
                            unlinkTarget = null
                            if (r == null) {
                                notice = "เลิกผูก " + t.name + " แล้ว และบันทึกประวัติแล้ว"
                                noticeErr = false
                                tick++
                            } else {
                                notice = r
                                noticeErr = true
                            }
                        }
                    }
                ) { Text(if (unlinkBusy) "กำลังบันทึก..." else "เลิกผูก", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !unlinkBusy, onClick = { unlinkTarget = null }) { Text("ยกเลิก", color = DevC.S) }
            }
        )
    }
}

// แถวการ์ด + เส้นเชื่อมด้านหน้า (จุดที่การ์ดของเขตที่ผูกกัน เส้นต่อลงไปหาการ์ดถัดไปของกลุ่มเดียวกัน)
@Composable
private fun DevZoneRow(
    f: DevFranchise,
    all: List<DevFranchise>,
    prevSame: Boolean,
    nextSame: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onUnlink: () -> Unit
) {
    val col = if (f.groupId.isEmpty()) Color.Transparent else devGroupColor(f.groupId)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(18.dp)
                .fillMaxHeight()
                .drawBehind {
                    if (f.groupId.isNotEmpty()) {
                        val cx = 6.dp.toPx()
                        val cy = (size.height - 12.dp.toPx()) / 2f
                        val sw = 3.dp.toPx()
                        if (prevSame) drawLine(col, Offset(cx, 0f), Offset(cx, cy), strokeWidth = sw)
                        if (nextSame) drawLine(col, Offset(cx, cy), Offset(cx, size.height), strokeWidth = sw)
                        drawCircle(col, radius = 5.dp.toPx(), center = Offset(cx, cy))
                    }
                }
        )
        Column(Modifier.weight(1f)) { DevZoneCard(f, all, col, onOpen, onEdit, onUnlink) }
    }
}

@Composable
private fun DevZoneCard(f: DevFranchise, all: List<DevFranchise>, col: Color, onOpen: () -> Unit, onEdit: () -> Unit, onUnlink: () -> Unit) {
    val fee = if (f.monthlyFee > 0) " · ค่าแฟรนไชส์/เดือน ฿" + String.format("%,.0f", f.monthlyFee) else ""
    val sub = (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + (if (f.phone.isEmpty()) "ไม่มีเบอร์" else f.phone) + " · " + f.shopCount + " ร้าน" + fee
    val grouped = f.groupId.isNotEmpty()
    DevCard(Modifier.clickable { onOpen() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 11.5.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            when (f.status) {
                "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                "suspended" -> DevBadge("ระงับ", DevC.RL, DevC.R)
                else -> DevBadge(if (f.status.isEmpty()) "-" else f.status, Color(0xFFF1F5F9), DevC.S)
            }
        }
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (f.zonePoints >= 3) DevBadge("มีเขตแล้ว", DevC.GnL, DevC.Gn)
            else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
            if (!f.hasCode) DevBadge("ยังไม่ตั้งรหัส", DevC.OL, DevC.O)
            if (grouped) DevBadge("หลังบ้านร่วม", col.copy(alpha = 0.12f), col)
            Box(Modifier.weight(1f))
            Text(
                "แก้ไข", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DevC.P,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEdit() }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            if (grouped) {
                Text(
                    "เลิกผูก", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUnlink() }.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        if (grouped) {
            val others = all.filter { it.groupId == f.groupId && it.id != f.id }.joinToString(", ") { it.name }
            if (others.isNotEmpty()) {
                Text("ใช้หลังบ้านร่วมกับ: " + others, fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DevLinkDialog(all: List<DevFranchise>, initial: Set<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("ผูกเขต", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "เลือกเขตที่จะให้ใช้หลังบ้านแอดมินร่วมกัน (อย่างน้อย 2 เขต) ร้านและข้อมูลอื่นยังแยกตามเขตเหมือนเดิม ถ้าเลือกเขตที่ผูกอยู่แล้ว กลุ่มเดิมจะรวมเข้าด้วย",
                    fontSize = 12.sp, color = DevC.S, lineHeight = 18.sp
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
                    all.forEach { f ->
                        val on = f.id in sel
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { sel = if (on) sel - f.id else sel + f.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DevC.P))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                                if (f.groupId.isNotEmpty()) {
                                    Text("ผูกอยู่แล้ว", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = devGroupColor(f.groupId))
                                }
                            }
                        }
                    }
                }
                if (err.isNotEmpty()) {
                    Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && sel.size >= 2,
                onClick = {
                    busy = true
                    err = ""
                    scope.launch {
                        val r = DevRepo.linkZones(sel.toList(), all)
                        busy = false
                        if (r == null) onDone() else err = r
                    }
                }
            ) { Text(if (busy) "กำลังบันทึก..." else "ผูก (" + sel.size + ")", color = DevC.P, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก", color = DevC.S) }
        }
    )
}

private fun devOrderStatus(s: String): String = when (s) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "ร้านรับแล้ว"
    "picking" -> "กำลังจัดของ"
    "arrived" -> "ถึงจุดรับ"
    "delivering" -> "กำลังส่ง"
    "done" -> "สำเร็จ"
    "rejected" -> "ร้านปฏิเสธ"
    "cancelled" -> "ยกเลิก"
    else -> s
}

// หน้ารายละเอียดเขต (กดการ์ดเขต): ตั้งค่าเขต + หลังบ้านร่วม + ร้านในเขต + ออเดอร์ล่าสุด + ประวัติของเขตนี้
@Composable
private fun DevZoneDetailScreen(
    f: DevFranchise,
    all: List<DevFranchise>,
    notice: String,
    noticeErr: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    var detail by remember { mutableStateOf<DevZoneDetail?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(f, tick) {
        detail = null
        err = false
        try {
            detail = DevDetailRepo.load(f)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }
    val grouped = f.groupId.isNotEmpty()
    val col = if (grouped) devGroupColor(f.groupId) else DevC.S
    val others = all.filter { grouped && it.groupId == f.groupId && it.id != f.id }

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ กลับ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBack() }.padding(end = 10.dp, top = 6.dp, bottom = 6.dp)
        )
        Text(
            f.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        when (f.status) {
            "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
            "suspended" -> DevBadge("ระงับ", DevC.RL, DevC.R)
            else -> DevBadge(if (f.status.isEmpty()) "-" else f.status, Color(0xFFF1F5F9), DevC.S)
        }
    }
    if (notice.isNotEmpty()) {
        DevCard { Text(notice, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (noticeErr) DevC.R else DevC.Gn) }
    }

    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ข้อมูลเขต", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.weight(1f))
            Text(
                "แก้ไข", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEdit() }.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        DevInfoRow("ชื่อเขต (ลูกค้าเห็น)", f.name)
        DevInfoRow("พื้นที่", if (f.province.isEmpty()) "-" else f.province)
        DevInfoRow("เบอร์ (เข้าแอปแอดมิน)", if (f.phone.isEmpty()) "-" else f.phone)
        DevInfoRow("รหัสเข้าใช้", if (f.hasCode) "ตั้งแล้ว" else "ยังไม่ตั้ง")
        DevInfoRow("ค่าแฟรนไชส์/เดือน", if (f.monthlyFee > 0) "฿" + String.format("%,.0f", f.monthlyFee) else "-")
        DevInfoRow("เขตบนแผนที่", if (f.zonePoints >= 3) "มีเขตแล้ว (" + f.zonePoints + " จุด)" else "ยังไม่กำหนด")
    }

    DevCard {
        Text("หลังบ้านแอดมิน", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 8.dp))
        if (grouped) {
            DevBadge("ใช้หลังบ้านร่วมกับเขตอื่น", col.copy(alpha = 0.12f), col)
            Text(
                "ร่วมกับ: " + (if (others.isEmpty()) "-" else others.joinToString(", ") { it.name }),
                fontSize = 12.sp, color = DevC.T, modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "เลิกผูกเขตนี้", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onUnlink() }.padding(vertical = 4.dp, horizontal = 2.dp)
            )
        } else {
            Text("แยกหลังบ้านของตัวเอง", fontSize = 12.sp, color = DevC.S)
            if (all.size >= 2) {
                Text(
                    "ผูกกับเขตอื่น", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.P,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onLink() }.padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }

    val d = detail
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลหลังบ้านของเขตไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
    } else if (d == null) {
        DevCard { Text("กำลังโหลดข้อมูลหลังบ้าน...", fontSize = 13.sp, color = DevC.S) }
    } else {
        DevCard {
            Text("ร้านค้าในเขต (" + d.shops.size + ")", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
            if (d.shops.isEmpty()) Text("ยังไม่มีร้านในเขตนี้", fontSize = 12.sp, color = DevC.S)
            d.shops.forEach { s -> DevShopLine(s) }
        }
        DevCard {
            Text("ออเดอร์ล่าสุดของเขต", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
            val o = d.orders
            if (o == null) {
                Text("โหลดออเดอร์ไม่สำเร็จ", fontSize = 12.sp, color = DevC.R)
            } else if (o.isEmpty()) {
                Text("ไม่พบออเดอร์ของเขตนี้ใน 200 ออเดอร์ล่าสุดของระบบ", fontSize = 12.sp, color = DevC.S)
            } else {
                val doneList = o.filter { it.status == "done" }
                Text(
                    "จาก 200 ออเดอร์ล่าสุดของระบบ: " + o.size + " ของเขตนี้ · สำเร็จ " + doneList.size +
                        " · ยอดที่สำเร็จ ฿" + String.format("%,.0f", doneList.sumOf { it.total }),
                    fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp)
                )
                o.take(10).forEach { x -> DevOrderLine(x) }
            }
        }
        DevCard {
            Text("ประวัติของเขตนี้", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
            val au = d.audit
            if (au == null) {
                Text("โหลดประวัติไม่สำเร็จ", fontSize = 12.sp, color = DevC.R)
            } else if (au.isEmpty()) {
                Text("ยังไม่มีประวัติ", fontSize = 12.sp, color = DevC.S)
            } else {
                au.forEach { a -> DevAuditLine(a) }
            }
        }
    }
}

@Composable
private fun DevInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 12.sp, color = DevC.S, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DevShopLine(s: DevShopRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        when (s.isOpen) {
            true -> DevBadge("เปิด", DevC.GnL, DevC.Gn)
            false -> DevBadge("ปิด", Color(0xFFF1F5F9), DevC.S)
            null -> DevBadge("-", Color(0xFFF1F5F9), DevC.S)
        }
    }
}

@Composable
private fun DevOrderLine(o: DevOrderRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(o.orderId, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1)
            Text(o.shopName + " · " + devOrderStatus(o.status), fontSize = 11.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("฿" + String.format("%,.0f", o.total), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.T)
    }
}

@Composable
private fun DevAuditLine(a: DevAuditItem) {
    val label = when (a.action) {
        "link_zones" -> "ผูกเขต"
        "unlink_zone" -> "เลิกผูกเขต"
        "create_zone" -> "เพิ่มเขต"
        "edit_zone" -> "แก้ไขเขต"
        else -> a.action
    }
    val time = if (a.atMillis <= 0L) "กำลังบันทึก"
    else java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).format(java.util.Date(a.atMillis))
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label + " · " + time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.T)
        Text(if (a.by.isEmpty()) "ไม่ทราบผู้ทำ" else a.by, fontSize = 11.sp, color = DevC.S)
        a.details.forEach { line -> Text(line, fontSize = 11.sp, color = DevC.T) }
    }
}

// ฟอร์มเพิ่มเขต (target = null) / แก้ไขเขต: ชื่อเขต พื้นที่ เบอร์ (ใช้เข้าแอปแอดมิน) รหัส ค่าแฟรนไชส์ สถานะ
@Composable
private fun DevZoneFormDialog(target: DevFranchise?, onDismiss: () -> Unit, onSaved: (DevCodeInfo?, String) -> Unit) {
    val editing = target != null
    var name by remember { mutableStateOf(target?.name ?: "") }
    var province by remember { mutableStateOf(target?.province ?: "") }
    var phone by remember { mutableStateOf(target?.phone ?: "") }
    var fee by remember {
        mutableStateOf(if (target != null && target.monthlyFee > 0) String.format("%.0f", target.monthlyFee) else "")
    }
    var status by remember { mutableStateOf(if (target?.status == "suspended") "suspended" else "active") }
    var changeCode by remember { mutableStateOf(!editing) }
    var code by remember { mutableStateOf(DevZoneRepo.generateCode()) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        val feeNum = if (fee.isBlank()) 0.0 else (fee.trim().toDoubleOrNull() ?: -1.0)
        if (name.isBlank()) { err = "กรุณาใส่ชื่อเขต"; return }
        if (!DevZoneRepo.validPhone(phone)) { err = "เบอร์โทรไม่ถูกต้อง (ตัวเลข 9-10 หลัก ขึ้นต้นด้วย 0)"; return }
        if (feeNum < 0.0) { err = "ค่าแฟรนไชส์ต้องเป็นตัวเลข"; return }
        if (changeCode && code.trim().length < 6) { err = "รหัสต้องยาวอย่างน้อย 6 ตัวอักษร"; return }
        err = ""
        busy = true
        scope.launch {
            val phoneClean = DevZoneRepo.cleanPhone(phone)
            val codeTrim = code.trim()
            val r = if (target == null) {
                DevZoneRepo.createZone(name, province, phone, feeNum, codeTrim)
            } else {
                DevZoneRepo.updateZone(target, name, province, phone, feeNum, status, if (changeCode) codeTrim else null)
            }
            busy = false
            if (r == null) {
                val info = if (changeCode) DevCodeInfo(name.trim(), phoneClean, codeTrim) else null
                onSaved(info, if (target == null) "เพิ่มเขตเรียบร้อย และบันทึกประวัติแล้ว" else "แก้ไขเขตเรียบร้อย และบันทึกประวัติแล้ว")
            } else {
                err = r
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text(if (editing) "แก้ไขเขต" else "เพิ่มเขต", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                DevFormInput(name, { name = it }, "ชื่อเขต (ลูกค้าจะเห็นชื่อนี้)", "เช่น แม่จัน", KeyboardType.Text)
                DevFormInput(province, { province = it }, "พื้นที่ / จังหวัด (ไม่บังคับ)", "เช่น เชียงราย", KeyboardType.Text)
                DevFormInput(phone, { phone = it }, "เบอร์โทร (ใช้เข้าแอปแอดมิน)", "0812345678", KeyboardType.Phone)
                DevFormInput(fee, { fee = it }, "ค่าแฟรนไชส์ต่อเดือน (ไม่บังคับ)", "เช่น 3000", KeyboardType.Number)
                if (editing) {
                    Text("สถานะ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        DevFormChip("ใช้งาน", status == "active", DevC.Gn) { status = "active" }
                        DevFormChip("ระงับ", status == "suspended", DevC.R) { status = "suspended" }
                    }
                    Row(Modifier.padding(bottom = 10.dp)) {
                        DevFormChip(if (changeCode) "ยกเลิกการตั้งรหัสใหม่" else "ตั้งรหัสใหม่", changeCode, DevC.P) { changeCode = !changeCode }
                    }
                }
                if (changeCode) {
                    DevFormInput(code, { code = it }, "รหัสเข้าแอปแอดมิน (อย่างน้อย 6 ตัว)", "", KeyboardType.Text)
                    Text(
                        "สุ่มรหัสใหม่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.P,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { code = DevZoneRepo.generateCode() }.padding(vertical = 4.dp)
                    )
                    Text(
                        "รหัสไม่ถูกเก็บเป็นข้อความ (เก็บแบบเข้ารหัสทางเดียว) จะเห็นรหัสได้ครั้งเดียวหลังบันทึก",
                        fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (editing && target != null) {
                    Text(if (target.hasCode) "รหัสเดิมยังใช้ต่อ (ไม่เปลี่ยน)" else "เขตนี้ยังไม่มีรหัส ควรตั้งรหัสใหม่", fontSize = 11.sp, color = DevC.S)
                }
                if (err.isNotEmpty()) {
                    Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { save() }) {
                Text(if (busy) "กำลังบันทึก..." else "บันทึก", color = DevC.P, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก", color = DevC.S) } }
    )
}

@Composable
private fun DevFormChip(text: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (on) Color.White else accent,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) accent else accent.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun DevFormInput(value: String, onChange: (String) -> Unit, label: String, hint: String, type: KeyboardType) {
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}

@Composable
fun DevBadge(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
