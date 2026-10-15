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
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevNotify
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

// หน้าแจ้งเตือน: โหมดทดสอบ / ส่งจริง, กลุ่มผู้รับ, ขอบเขต (ส่งทันที; ตั้งเวลารอฝั่งเซิร์ฟเวอร์)
@Composable
fun DevNotifyScreen() {
    var real by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("shop") }
    var sc by remember { mutableStateOf("all") }
    var zones by remember { mutableStateOf<List<DevFranchise>>(emptyList()) }
    var zoneId by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var resultOk by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
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

    fun validate(): String? {
        if (title.isBlank()) return "กรุณาใส่หัวข้อ"
        if (body.isBlank()) return "กรุณาใส่ข้อความ"
        if (sc == "zone" && zoneId.isEmpty()) return "กรุณาเลือกเขต"
        if (sc == "one" && person.isBlank()) return "กรุณาใส่เบอร์โทรหรือรหัสของผู้รับ"
        return null
    }

    fun sendNow(tokens: List<String>) {
        cs.launch {
            busy = true
            result = "กำลังส่ง..."
            resultOk = true
            // ทดสอบร้านค้า: ส่งแบบ new_order ทดสอบ (test:"1", orderId ขึ้นต้น TEST-) ให้แอปร้านดังโดยไม่ต้องมีออเดอร์จริง
            val extra: Map<String, String> = if (!real && role == "shop") {
                mapOf("type" to "new_order", "test" to "1", "orderId" to "TEST-" + (System.currentTimeMillis() % 100000))
            } else emptyMap()
            val r = DevNotify.send(tokens, title.trim(), body.trim(), extra)
            resultOk = r.first
            var text = "ส่งถึง " + tokens.size + " เครื่อง\n" + r.second
            if (real) {
                val logged = DevNotify.log(role, sc, zoneId, person, title.trim(), tokens.size, r.first)
                if (!logged) text += "\n(บันทึกประวัติการส่งไม่สำเร็จ)"
            }
            result = text
            busy = false
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
            } else if (real) {
                result = ""
                confirm = r.first
            } else {
                sendNow(r.first)
            }
        }
    }

    DevCard {
        DevNLabel("โหมด")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            DevChip("ทดสอบ", !real, true, DevC.P) { real = false }
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
            DevNLabel(if (role == "shop") "เบอร์โทรร้าน (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)" else if (role == "admin") "รหัส/อีเมลแอดมิน" else "เบอร์โทร (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)")
            DevInput(person, { person = it }, "เช่น 0812345678", 1)
        }
        Box(Modifier.padding(top = 8.dp))
        DevNLabel("หัวข้อ")
        DevInput(title, { title = it }, "หัวข้อแจ้งเตือน", 1)
        DevNLabel("ข้อความ")
        DevInput(body, { body = it }, "ข้อความที่จะส่ง", 3)
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
                if (busy) "กำลังดำเนินการ..." else if (real) "ส่งจริง" else "ส่งทดสอบ",
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
    val tk = confirm
    if (tk != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color.White,
            title = { Text("ยืนยันส่งจริง?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "จะส่งถึง " + tk.size + " เครื่อง\nกลุ่ม: " + devRoleLabel(role) + "\nขอบเขต: " + devScopeLabel(sc) +
                        "\nหัวข้อ: " + title.trim(),
                    fontSize = 13.sp, color = DevC.T, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    sendNow(tk)
                }) { Text("ส่งเลย", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
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
