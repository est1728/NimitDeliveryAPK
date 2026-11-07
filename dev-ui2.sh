#!/bin/bash
# แจ้งเตือน: หมวดหมู่พับได้ + หน้าตั้งค่าแจ้งเตือนแสดง "ทำงานเมื่อ" ของแต่ละเหตุการณ์ (เขียนทับ 3 ไฟล์ รันซ้ำได้)
set -e
D=app/src/dev/java/com/nimit/delivery
[ -f "$D/ui/DevNotifyCat.kt" ] && [ -f "$D/data/DevNotifyConfig.kt" ] || { echo "หยุด: ต้องรัน dev-ncat.sh และ dev-ncfg.sh ก่อน และรันใน ~/nimit-customer"; exit 1; }
cat > "$D/ui/DevNotify.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevBroadcast
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevNotify
import com.nimit.delivery.data.DevNotifyCat
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun devRoleLabel(r: String): String = when (r) {
    "shop" -> "ร้านค้า"
    "admin" -> "แอดมิน"
    "rider" -> "ไรเดอร์"
    else -> "ลูกค้า"
}

private fun devScopeLabel(s: String): String = when (s) {
    "all" -> "ทั้งหมดทุกเขต"
    "zone" -> "ทั้งหมดในเขต"
    else -> "รายคน"
}

private fun devPlanLabel(p: String): String = when (p) {
    "now" -> "ส่งทันที"
    "once" -> "ตั้งเวลาครั้งเดียว"
    "daily" -> "ทุกวัน"
    else -> "บันทึกไว้กดส่งเอง"
}

private data class DevSendSpec(
    val role: String,
    val scope: String,
    val zoneId: String,
    val person: String,
    val title: String,
    val body: String,
    val real: Boolean,
    val savedId: String?
)

private data class DevConfirm(
    val kind: String,
    val tokens: List<String>,
    val spec: DevSendSpec,
    val plan: String,
    val runAt: Long,
    val timeOfDay: String
)

// หน้าแจ้งเตือน: ทดสอบ / ส่งจริง + กลุ่มผู้รับ + ขอบเขต + การส่ง (ทันที / ครั้งเดียว / ทุกวัน / บันทึกไว้กดส่งเอง)
@Composable
fun DevNotifyScreen() {
    var real by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("shop") }
    var sc by remember { mutableStateOf("all") }
    var plan by remember { mutableStateOf("now") }
    var zones by remember { mutableStateOf<List<DevFranchise>>(emptyList()) }
    var zoneId by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var onceDate by remember { mutableStateOf("") }
    var onceTime by remember { mutableStateOf("") }
    var dailyTime by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var resultOk by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<DevConfirm?>(null) }
    var blist by remember { mutableStateOf<List<DevBroadcast>?>(null) }
    var bTick by remember { mutableStateOf(0) }
    var delTarget by remember { mutableStateOf<DevBroadcast?>(null) }
    var category by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCatManage by remember { mutableStateOf(false) }
    var catSend by remember { mutableStateOf<String?>(null) }
    var moveCatTarget by remember { mutableStateOf<DevBroadcast?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val cs = rememberCoroutineScope()

    LaunchedEffect(sc) {
        if (sc == "zone" && zones.isEmpty()) {
            try {
                zones = DevRepo.franchises()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = "โหลดรายชื่อเขตไม่สำเร็จ"
                resultOk = false
            }
        }
    }
    LaunchedEffect(bTick) {
        try {
            blist = DevNotify.listBroadcasts()
            try {
                cats = DevNotifyCat.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            blist = emptyList()
        }
    }

    fun validate(): String? {
        if (title.isBlank()) return "กรุณาใส่หัวข้อ"
        if (body.isBlank()) return "กรุณาใส่ข้อความ"
        if (sc == "zone" && zoneId.isEmpty()) return "กรุณาเลือกเขต"
        if (sc == "one" && person.isBlank()) return "กรุณาใส่เบอร์โทรหรือรหัสของผู้รับ"
        if (real && plan == "once") {
            val ms = DevNotify.parseOnce(onceDate, onceTime)
                ?: return "วันที่/เวลาไม่ถูกต้อง (รูปแบบ วว/ดด/ปปปป และ ชช:นน)"
            if (ms < System.currentTimeMillis() + 60000L) return "เวลาที่ตั้งต้องอยู่ในอนาคตอย่างน้อย 1 นาที"
        }
        if (real && plan == "daily" && !DevNotify.validTime(dailyTime)) return "เวลาไม่ถูกต้อง (รูปแบบ ชช:นน เช่น 09:30)"
        return null
    }

    fun doSend(spec: DevSendSpec, tokens: List<String>) {
        cs.launch {
            busy = true
            result = "กำลังส่ง..."
            resultOk = true
            // ทดสอบร้านค้า: ส่งแบบ new_order ทดสอบ (test:"1", orderId ขึ้นต้น TEST-) ให้แอปร้านดังโดยไม่ต้องมีออเดอร์จริง
            val extra: Map<String, String> = if (!spec.real && spec.role == "shop") {
                mapOf("type" to "new_order", "test" to "1", "orderId" to "TEST-" + (System.currentTimeMillis() % 100000))
            } else emptyMap()
            val r = DevNotify.send(tokens, spec.title, spec.body, extra)
            resultOk = r.first
            var text = "ส่งถึง " + tokens.size + " เครื่อง\n" + r.second
            if (spec.real) {
                val logged = DevNotify.log(spec.role, spec.scope, spec.zoneId, spec.person, spec.title, tokens.size, r.first)
                if (!logged) text += "\n(บันทึกประวัติการส่งไม่สำเร็จ)"
            }
            val sid = spec.savedId
            if (sid != null) {
                DevNotify.markSent(sid, r.first, tokens.size)
                bTick++
            }
            result = text
            busy = false
        }
    }

    fun doSchedule(c: DevConfirm) {
        cs.launch {
            busy = true
            val zn = zones.firstOrNull { it.id == c.spec.zoneId }?.name ?: ""
            val err = DevNotify.saveBroadcast(
                c.spec.role, c.spec.scope, c.spec.zoneId, zn, c.spec.person,
                c.spec.title, c.spec.body, c.plan, c.runAt, c.timeOfDay, category
            )
            busy = false
            if (err == null) {
                result = "บันทึกแล้ว (" + devPlanLabel(c.plan) + ")"
                resultOk = true
                bTick++
            } else {
                result = err
                resultOk = false
            }
        }
    }

    fun onSend() {
        val err = validate()
        if (err != null) {
            result = err
            resultOk = false
            return
        }
        cs.launch {
            busy = true
            result = "กำลังหาผู้รับ..."
            resultOk = true
            val r = DevNotify.resolve(role, sc, zoneId, person)
            busy = false
            if (r.second != null) {
                result = r.second ?: ""
                resultOk = false
                return@launch
            }
            val spec = DevSendSpec(role, sc, zoneId, person, title.trim(), body.trim(), real, null)
            if (!real) {
                doSend(spec, r.first)
                return@launch
            }
            result = ""
            if (plan == "now") {
                confirm = DevConfirm("send", r.first, spec, plan, 0L, "")
            } else {
                val runAt = if (plan == "once") (DevNotify.parseOnce(onceDate, onceTime) ?: 0L)
                else if (plan == "daily") DevNotify.nextDaily(dailyTime) else 0L
                confirm = DevConfirm("schedule", r.first, spec, plan, runAt, DevNotify.normTime(dailyTime))
            }
        }
    }

    fun sendSaved(b: DevBroadcast) {
        cs.launch {
            busy = true
            result = "กำลังหาผู้รับ..."
            resultOk = true
            val r = DevNotify.resolve(b.role, b.scope, b.zoneId, b.person)
            busy = false
            if (r.second != null) {
                result = r.second ?: ""
                resultOk = false
                return@launch
            }
            result = ""
            val spec = DevSendSpec(b.role, b.scope, b.zoneId, b.person, b.title, b.body, true, b.id)
            confirm = DevConfirm("saved", r.first, spec, "manual", 0L, "")
        }
    }

    fun togglePause(b: DevBroadcast) {
        cs.launch {
            val ok = if (b.status == "active") DevNotify.setBroadcastStatus(b.id, "paused", null)
            else DevNotify.setBroadcastStatus(b.id, "active", DevNotify.nextDaily(b.timeOfDay))
            if (!ok) {
                result = "อัปเดตสถานะไม่สำเร็จ"
                resultOk = false
            }
            bTick++
        }
    }

    DevCard {
        DevNLabel("โหมด")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            DevChip("ทดสอบ", !real, true, DevC.P) {
                real = false
                plan = "now"
            }
            DevChip("ส่งจริง", real, true, DevC.R) { real = true }
        }
        DevNLabel("กลุ่มผู้รับ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            listOf("shop", "admin", "rider", "customer").forEach { r ->
                DevChip(devRoleLabel(r), role == r, true, DevC.P) {
                    role = r
                    if (r != "shop" && sc == "zone") sc = "all"
                }
            }
        }
        if (!real && role == "shop") {
            Text(
                "ทดสอบร้านค้า: แอปร้านจะดังเหมือนออเดอร์ใหม่ประมาณ 20 วินาที ส่วนโหมดส่งจริงจะไม่ดังแบบออเดอร์",
                fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        DevNLabel("ขอบเขต")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)
        ) {
            DevChip(devScopeLabel("all"), sc == "all", true, DevC.P) { sc = "all" }
            DevChip(devScopeLabel("zone"), sc == "zone", role == "shop", DevC.P) { sc = "zone" }
            DevChip(devScopeLabel("one"), sc == "one", true, DevC.P) { sc = "one" }
        }
        if (role != "shop") {
            Text(
                "ส่งตามเขตได้เฉพาะร้านค้า (กลุ่มอื่นยังไม่มีข้อมูลเขตในระบบ)",
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (sc == "zone") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 8.dp)
            ) {
                if (zones.isEmpty()) Text("กำลังโหลดเขต...", fontSize = 12.sp, color = DevC.S)
                zones.forEach { z -> DevChip(z.name, zoneId == z.id, true, DevC.Gn) { zoneId = z.id } }
            }
        }
        if (sc == "one") {
            Box(Modifier.padding(top = 6.dp))
            DevNLabel(
                if (role == "shop") "เบอร์โทรร้าน (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)"
                else if (role == "admin") "รหัส/อีเมลแอดมิน"
                else "เบอร์โทร (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)"
            )
            DevInput(person, { person = it }, "เช่น 0812345678", 1)
        }
        Box(Modifier.padding(top = 8.dp))
        DevNLabel("หัวข้อ")
        DevInput(title, { title = it }, "หัวข้อแจ้งเตือน", 1)
        DevNLabel("ข้อความ")
        DevInput(body, { body = it }, "ข้อความที่จะส่ง", 3)
        if (real) {
            DevNLabel("การส่ง")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)
            ) {
                listOf("now", "once", "daily", "manual").forEach { p ->
                    DevChip(devPlanLabel(p), plan == p, true, DevC.P) { plan = p }
                }
            }
            if (plan == "once") {
                DevNLabel("วันที่ (วว/ดด/ปปปป) และเวลา (ชช:นน) เวลาไทย")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { DevInput(onceDate, { onceDate = it }, "25/12/2026", 1) }
                    Box(Modifier.weight(1f)) { DevInput(onceTime, { onceTime = it }, "09:30", 1) }
                }
                Text("ส่งครั้งเดียวตามเวลาที่ตั้ง แล้วรายการจะเปลี่ยนเป็น \"ส่งแล้ว\"", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (plan == "daily") {
                DevNLabel("เวลาที่ส่งทุกวัน (ชช:นน) เวลาไทย")
                DevInput(dailyTime, { dailyTime = it }, "09:30", 1)
            }
            if (plan == "once" || plan == "daily") {
                Text(
                    "การตั้งเวลาทำงานผ่านเซิร์ฟเวอร์ (ปิดมือถือได้) ต้องตั้งตัวเรียก run-broadcasts ทุก 1 นาทีก่อน",
                    fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            if (plan == "manual") {
                Text(
                    "บันทึกข้อความไว้ในรายการด้านล่าง กด \"ส่งเลย\" เมื่อไรก็ได้ ส่งซ้ำได้ (กดหนึ่งครั้งส่งหนึ่งรอบ)",
                    fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
        if (real && plan != "now") {
            DevNLabel("หมวดหมู่ (ไม่บังคับ)")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)
            ) {
                DevChip("ไม่มีหมวด", category.isEmpty(), true, DevC.S) { category = "" }
                cats.forEach { c0 -> DevChip(c0, category == c0, true, DevC.P) { category = c0 } }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (busy) Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF94A3B8)))
                    else if (real) Brush.linearGradient(listOf(DevC.R, Color(0xFF991B1B)))
                    else Brush.linearGradient(listOf(DevC.P, DevC.PD))
                )
                .clickable(enabled = !busy) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (busy) "กำลังดำเนินการ..."
                else if (!real) "ส่งทดสอบ"
                else if (plan == "now") "ส่งจริง"
                else if (plan == "manual") "บันทึกข้อความ"
                else "ตั้งเวลาส่ง",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
            )
        }
    }
    if (result.isNotEmpty()) {
        DevCard {
            Text(
                result, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                color = if (resultOk) DevC.T else DevC.R
            )
        }
    }

    val bl = blist
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (bl == null) "ข้อความที่ตั้งไว้" else "ข้อความที่ตั้งไว้ (" + bl.size + ")",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T, modifier = Modifier.weight(1f)
        )
        Text(
            "จัดการหมวดหมู่", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showCatManage = true }.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
    if (bl == null) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
    } else if (bl.isEmpty()) {
        DevCard { Text("ยังไม่มีข้อความที่ตั้งเวลาหรือบันทึกไว้", fontSize = 13.sp, color = DevC.S) }
    } else {
        // แยกตามหมวด: หมวดที่สร้างไว้ก่อน แล้วค่อย "ไม่มีหมวด" (รวมข้อความที่หมวดถูกลบไปแล้ว)
        (cats + listOf("")).forEach { g ->
            val inGroup = bl.filter { if (g.isEmpty()) (it.category.isEmpty() || it.category !in cats) else it.category == g }
            if (g.isNotEmpty() || inGroup.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (g in collapsed) "▸ " else "▾ ") + (if (g.isEmpty()) "ไม่มีหมวด" else g) + " (" + inGroup.size + ")",
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { collapsed = if (g in collapsed) collapsed - g else collapsed + g }
                            .padding(vertical = 6.dp)
                    )
                    if (g.isNotEmpty() && inGroup.isNotEmpty()) {
                        Text(
                            "ส่งทั้งหมวด", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DevC.R)
                                .clickable(enabled = !busy) { catSend = g }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                if (g !in collapsed) {
                    if (inGroup.isEmpty()) DevCard { Text("หมวดนี้ยังไม่มีข้อความ", fontSize = 12.sp, color = DevC.S) }
                    inGroup.forEach { b ->
                        DevBroadcastCard(b, busy, { sendSaved(b) }, { togglePause(b) }, { delTarget = b }, { moveCatTarget = b })
                    }
                }
            }
        }
    }

    if (showCatManage) {
        DevCategoryManageDialog(cats, blist ?: emptyList(), { showCatManage = false }, { bTick++ })
    }
    val cSend = catSend
    if (cSend != null && bl != null) {
        DevCategorySendDialog(
            category = cSend,
            items = bl.filter { it.category == cSend },
            onDismiss = { catSend = null },
            onDone = { text ->
                catSend = null
                result = text
                resultOk = true
                bTick++
            }
        )
    }
    val mvc = moveCatTarget
    if (mvc != null) {
        DevMoveCategoryDialog(mvc, cats, { moveCatTarget = null }, {
            moveCatTarget = null
            bTick++
        })
    }

    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color.White,
            title = {
                Text(
                    if (c.kind == "schedule") "ยืนยันตั้งเวลา?" else if (c.kind == "saved") "ส่งข้อความที่บันทึกไว้?" else "ยืนยันส่งจริง?",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    (if (c.kind == "schedule") "ผู้รับตอนนี้ " else "จะส่งถึง ") + c.tokens.size + " เครื่อง\nกลุ่ม: " + devRoleLabel(c.spec.role) +
                        "\nขอบเขต: " + devScopeLabel(c.spec.scope) + "\nหัวข้อ: " + c.spec.title +
                        (if (c.kind == "schedule") "\nการส่ง: " + devPlanLabel(c.plan) +
                            (if (c.plan == "once") " " + DevNotify.fmt(c.runAt) else if (c.plan == "daily") " " + c.timeOfDay + " น." else "") else ""),
                    fontSize = 13.sp, color = DevC.T, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (c.kind == "schedule") doSchedule(c) else doSend(c.spec, c.tokens)
                }) { Text(if (c.kind == "schedule") "ตั้งเวลา" else "ส่งเลย", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
    }
    val dt = delTarget
    if (dt != null) {
        AlertDialog(
            onDismissRequest = { delTarget = null },
            containerColor = Color.White,
            title = { Text("ลบข้อความนี้?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = { Text(dt.title, fontSize = 13.sp, color = DevC.S) },
            confirmButton = {
                TextButton(onClick = {
                    delTarget = null
                    cs.launch {
                        if (!DevNotify.deleteBroadcast(dt.id)) {
                            result = "ลบไม่สำเร็จ"
                            resultOk = false
                        }
                        bTick++
                    }
                }) { Text("ลบ", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
    }
}

@Composable
private fun DevBroadcastCard(
    b: DevBroadcast,
    busy: Boolean,
    onSend: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveCat: () -> Unit
) {
    val who = devRoleLabel(b.role) + " · " + devScopeLabel(b.scope) +
        (if (b.scope == "zone" && b.zoneName.isNotEmpty()) " " + b.zoneName else "")
    val whenText = when (b.repeat) {
        "once" -> if (b.status == "done") "ครั้งเดียว (ส่งแล้ว)" else "ครั้งเดียว " + DevNotify.fmt(b.nextRunMillis)
        "daily" -> "ทุกวัน " + b.timeOfDay + " น." + (if (b.status == "active") " (ครั้งต่อไป " + DevNotify.fmt(b.nextRunMillis) + ")" else "")
        else -> "กดส่งเอง"
    }
    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(b.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1)
                Text(who, fontSize = 11.5.sp, color = DevC.S, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
            }
            when (b.status) {
                "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                "paused" -> DevBadge("หยุดชั่วคราว", DevC.OL, DevC.O)
                "done" -> DevBadge("ส่งแล้ว", Color(0xFFF1F5F9), DevC.S)
                else -> DevBadge(b.status, Color(0xFFF1F5F9), DevC.S)
            }
        }
        Text(whenText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DevC.T, modifier = Modifier.padding(top = 8.dp))
        if (b.lastRunMillis > 0L || b.lastResult.isNotEmpty()) {
            Text(
                "ล่าสุด " + DevNotify.fmt(b.lastRunMillis) + (if (b.lastResult.isNotEmpty()) " · " + b.lastResult else ""),
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (b.repeat == "manual") {
                Text(
                    "ส่งเลย", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onSend() }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (b.repeat == "daily" && b.status != "done") {
                Text(
                    if (b.status == "active") "หยุดชั่วคราว" else "เปิดใช้งาน", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.O,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onToggle() }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Text(
                "ย้ายหมวด", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.S,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onMoveCat() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Text(
                "ลบ", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onDelete() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun DevNLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DevChip(text: String, on: Boolean, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (!enabled) Color(0xFF94A3B8) else if (on) Color.White else accent,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (!enabled) Color(0xFFF1F5F9) else if (on) accent else accent.copy(alpha = 0.1f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun DevInput(value: String, onChange: (String) -> Unit, hint: String, lines: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
KT_EOF
cat > "$D/data/DevNotifyConfig.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// รายการเหตุการณ์แจ้งเตือนที่มีอยู่จริงในระบบ (จากโค้ดแอปและ rider.html เดิม) vars = ตัวแปรที่ผู้ส่งแนบได้
data class DevEventDef(val id: String, val group: String, val label: String, val vars: String, val whenText: String)

// title/body ว่าง = ใช้ข้อความเดิมของแอป/ผู้ส่ง
data class DevEventCfg(val enabled: Boolean, val title: String, val body: String)

/**
 * ตั้งค่าข้อความแจ้งเตือนจากแอป dev เก็บที่ appConfig/notifications:
 *   { events: { "<รหัสเหตุการณ์>": { enabled, title, body } }, updatedAt, updatedBy }
 * ตัวแปรในข้อความเขียนเป็น {orderId} {shopName} ฯลฯ ผู้อ่านค่า (เซิร์ฟเวอร์/แอป) เป็นคนแทนที่
 * ทุกครั้งที่บันทึกจะเขียนประวัติลง devConfigLog (เพิ่มอย่างเดียว) ในครั้งเดียวกัน
 */
object DevNotifyConfig {
    val groups = listOf(
        "rider" to "ไรเดอร์ (ในแอปไรเดอร์)",
        "status" to "ลูกค้า (สถานะจากไรเดอร์)",
        "order" to "ออเดอร์ใหม่",
        "shop" to "ร้านค้า",
        "chat" to "แชท"
    )

    val events = listOf(
        DevEventDef("rider_new_job", "rider", "งานใหม่มาแล้ว (แบนเนอร์+เสียงในแอปไรเดอร์)", "{orderId} {shopName}", "เมื่อมีออเดอร์ใหม่รอไรเดอร์รับ (สถานะ pending)"),
        DevEventDef("rider_accepted", "rider", "รับงานแล้ว", "{orderId}", "เมื่อไรเดอร์กดรับงานสำเร็จ"),
        DevEventDef("rider_assigned", "rider", "ถูกมอบหมายงาน", "{orderId}", "เมื่อแอดมินมอบหมายงานให้ไรเดอร์คนนี้"),
        DevEventDef("rider_reassigned", "rider", "งานถูกถอด/โอน (แบนเนอร์สีแดง)", "{orderId}", "เมื่องานถูกถอด/โอน (ผมยังไม่พบจุดที่เรียกใช้ในโค้ดเดิม)"),
        DevEventDef("status_accepted", "status", "ไรเดอร์รับงานแล้ว", "{orderId} {riderName} {riderPhone}", "เมื่อไรเดอร์กดรับงาน (ส่งถึงลูกค้า)"),
        DevEventDef("status_picking", "status", "ไรเดอร์กำลังไปร้าน", "{orderId} {riderName}", "เมื่อไรเดอร์เปลี่ยนสถานะเป็นกำลังไปร้าน (ส่งถึงลูกค้า)"),
        DevEventDef("status_arrived", "status", "ไรเดอร์ถึงร้านแล้ว", "{orderId} {riderName}", "เมื่อไรเดอร์เปลี่ยนสถานะเป็นถึงร้านแล้ว (ส่งถึงลูกค้า)"),
        DevEventDef("status_delivering", "status", "กำลังนำอาหารมาส่ง", "{orderId} {riderName}", "เมื่อไรเดอร์เปลี่ยนสถานะเป็นกำลังนำมาส่ง (ส่งถึงลูกค้า)"),
        DevEventDef("status_done", "status", "จัดส่งสำเร็จแล้ว", "{orderId}", "เมื่อไรเดอร์กดจัดส่งสำเร็จ (ส่งถึงลูกค้า)"),
        DevEventDef("order_new", "order", "มีออเดอร์ใหม่ (ถึงร้าน/แอดมิน/ไรเดอร์)", "{orderId} {shopName} {total}", "เมื่อลูกค้ากดสั่งซื้อสำเร็จ (แอปลูกค้าส่งหาร้าน/แอดมิน/ไรเดอร์)"),
        DevEventDef("shop_new_order", "shop", "ออเดอร์ใหม่ในแอปร้านค้า", "{orderId} {total}", "เมื่อออเดอร์ใหม่เข้าร้านและแอปร้านตรวจพบ"),
        DevEventDef("chat_message", "chat", "ข้อความแชทใหม่", "{orderId}", "เมื่อมีข้อความแชทใหม่ในออเดอร์")
    )

    private fun cfgOf(m: Map<String, Any?>): DevEventCfg = DevEventCfg(
        enabled = (m["enabled"] as? Boolean) ?: true,
        title = (m["title"] as? String) ?: "",
        body = (m["body"] as? String) ?: ""
    )

    suspend fun load(): Map<String, DevEventCfg> {
        val d = FirebaseFirestore.getInstance().collection("appConfig").document("notifications").get().await()
        val ev = d.get("events") as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, DevEventCfg>()
        for ((k, v) in ev) {
            val m = (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: continue
            out[k.toString()] = cfgOf(m)
        }
        return out
    }

    /** คืนข้อความผิดพลาด หรือ null ถ้าสำเร็จ */
    suspend fun save(def: DevEventDef, cfg: DevEventCfg, before: DevEventCfg?): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        return try {
            val db = FirebaseFirestore.getInstance()
            val b = db.batch()
            b.set(
                db.collection("appConfig").document("notifications"),
                mapOf(
                    "events" to mapOf(
                        def.id to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                    ),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to email
                ),
                SetOptions.merge()
            )
            b.set(
                db.collection("devConfigLog").document(),
                mapOf(
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "event" to def.id,
                    "label" to def.label,
                    "before" to mapOf(
                        "enabled" to (before?.enabled ?: true),
                        "title" to (before?.title ?: ""),
                        "body" to (before?.body ?: "")
                    ),
                    "after" to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }
}
KT_EOF
cat > "$D/ui/DevNotifyConfig.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevEventCfg
import com.nimit.delivery.data.DevEventDef
import com.nimit.delivery.data.DevNotifyConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ตั้งค่าข้อความแจ้งเตือนของทุกแอป (เปิด/ปิด หัวข้อ เนื้อหา) เว้นว่าง = ใช้ข้อความเดิมของแอป
@Composable
fun DevNotifyConfigScreen() {
    val cfgs = remember { mutableStateMapOf<String, DevEventCfg>() }
    var loaded by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf("rider") }

    LaunchedEffect(tick) {
        loaded = false
        err = false
        try {
            val m = DevNotifyConfig.load()
            cfgs.clear()
            cfgs.putAll(m)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
        loaded = true
    }

    DevCard {
        Text("ตั้งค่าข้อความแจ้งเตือน", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "เว้นหัวข้อ/เนื้อหาว่างไว้ = ใช้ข้อความเดิมของแอป ตัวแปรเช่น {orderId} จะถูกแทนที่โดยผู้ส่ง/เซิร์ฟเวอร์ " +
                "ค่าที่ตั้งจะมีผลเมื่อเซิร์ฟเวอร์หรือแอปนั้นถูกแก้ให้อ่านค่า (ยังไม่ได้เปิดใช้ในทุกแอป) " +
                "เวลาที่แจ้งเตือนทำงาน (\"ทำงานเมื่อ\") ถูกกำหนดในโค้ดของแต่ละแอป หน้านี้แสดงให้ดูอย่างเดียว",
            fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)
    ) {
        DevNotifyConfig.groups.forEach { (id, label) ->
            Text(
                label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (group == id) Color.White else DevC.P,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (group == id) DevC.P else DevC.PL)
                    .clickable { group = id }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดค่าที่ตั้งไว้ไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
    } else if (!loaded) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
    } else {
        DevNotifyConfig.events.filter { it.group == group }.forEach { def ->
            DevEventCard(def, cfgs[def.id]) { saved -> cfgs[def.id] = saved }
        }
    }
}

@Composable
private fun DevEventCard(def: DevEventDef, cfg: DevEventCfg?, onSaved: (DevEventCfg) -> Unit) {
    var enabled by remember(cfg) { mutableStateOf(cfg?.enabled ?: true) }
    var title by remember(cfg) { mutableStateOf(cfg?.title ?: "") }
    var body by remember(cfg) { mutableStateOf(cfg?.body ?: "") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = enabled != (cfg?.enabled ?: true) || title.trim() != (cfg?.title ?: "") || body.trim() != (cfg?.body ?: "")

    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(def.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                Text("ทำงานเมื่อ: " + def.whenText, fontSize = 11.sp, color = DevC.T, modifier = Modifier.padding(top = 2.dp))
                Text("ตัวแปร: " + def.vars, fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(
                checked = enabled, onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DevC.Gn)
            )
        }
        Text("หัวข้อ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        DevCfgInput(title, { title = it }, "เว้นว่าง = ใช้ข้อความเดิม", 1)
        Text("เนื้อหา", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
        DevCfgInput(body, { body = it }, "เว้นว่าง = ใช้ข้อความเดิม", 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (busy) "กำลังบันทึก..." else "บันทึก", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                color = if (dirty) Color.White else Color(0xFF94A3B8),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (dirty) DevC.P else Color(0xFFF1F5F9))
                    .clickable(enabled = dirty && !busy) {
                        busy = true
                        msg = ""
                        val next = DevEventCfg(enabled, title.trim(), body.trim())
                        scope.launch {
                            val r = DevNotifyConfig.save(def, next, cfg)
                            busy = false
                            if (r == null) {
                                msg = "บันทึกแล้ว และบันทึกประวัติแล้ว"
                                msgErr = false
                                onSaved(next)
                            } else {
                                msg = r
                                msgErr = true
                            }
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
            if (msg.isNotEmpty()) {
                Text(msg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (msgErr) DevC.R else DevC.Gn, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DevCfgInput(value: String, onChange: (String) -> Unit, hint: String, lines: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
KT_EOF
echo "== ตรวจผล =="
grep -c "collapsed" "$D/ui/DevNotify.kt" | sed "s/^/DevNotify: collapsed = /"
grep -c "whenText" "$D/data/DevNotifyConfig.kt" | sed "s/^/DevNotifyConfig: whenText = /"
git status --short
