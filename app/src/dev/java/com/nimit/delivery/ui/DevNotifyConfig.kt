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
                "ค่าที่ตั้งจะมีผลเมื่อเซิร์ฟเวอร์หรือแอปนั้นถูกแก้ให้อ่านค่า (ยังไม่ได้เปิดใช้ในทุกแอป)",
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
