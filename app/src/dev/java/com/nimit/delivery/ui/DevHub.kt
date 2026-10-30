package com.nimit.delivery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevAuditItem
import com.nimit.delivery.data.DevAuditRepo
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun devHubLabel(a: String): String = when (a) {
    "link_zones" -> "ผูกเขต"
    "unlink_zone" -> "เลิกผูกเขต"
    "create_zone" -> "เพิ่มเขต"
    "edit_zone" -> "แก้ไขเขต"
    "move_shop" -> "ย้ายร้าน"
    "copy_shop" -> "คัดลอกร้าน"
    "set_zone" -> "กำหนดเขตบนแผนที่"
    else -> a
}

private fun devHubTime(ms: Long): String =
    if (ms <= 0L) "กำลังบันทึก" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

// ศูนย์ข้อมูลเขต: ภาพรวมทุกเขต + กดดูประวัติของแต่ละเขต (อ่านอย่างเดียว) รายละเอียด/แก้ไขอยู่ที่เมนู "เขตแฟรนไชส์"
@Composable
fun DevHubScreen() {
    var zones by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var audit by remember { mutableStateOf<List<DevAuditItem>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) {
        zones = null
        audit = null
        err = false
        try {
            zones = DevRepo.franchises()
            audit = try {
                DevAuditRepo.recent(200)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }

    val z = zones
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลเขตไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    if (z == null) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        return
    }
    val active = z.count { it.status == "active" }
    val suspended = z.count { it.status == "suspended" }
    val groups = z.map { it.groupId }.filter { it.isNotEmpty() }.toSet().size
    val withMap = z.count { it.zonePoints >= 3 }

    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DevStat("เขตทั้งหมด", z.size.toString(), DevC.P, Modifier.weight(1f))
        DevStat("ใช้งาน", active.toString(), DevC.Gn, Modifier.weight(1f))
        DevStat("ระงับ", suspended.toString(), DevC.R, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DevStat("มีรูปเขต", withMap.toString(), DevC.P, Modifier.weight(1f))
        DevStat("กลุ่มหลังบ้านร่วม", groups.toString(), DevC.O, Modifier.weight(1f))
        DevStat("รายการประวัติ", (audit?.size ?: 0).toString(), DevC.S, Modifier.weight(1f))
    }
    if (z.isEmpty()) {
        DevCard { Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S) }
    }
    val items = audit
    z.forEach { f ->
        val mine = items?.filter { f.id in it.zoneIds } ?: emptyList()
        val last = mine.firstOrNull()
        val isOpen = open == f.id
        DevCard(Modifier.clickable { open = if (isOpen) null else f.id }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + f.shopCount + " ร้าน",
                        fontSize = 11.5.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                when (f.status) {
                    "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                    "suspended" -> DevBadge("ระงับ", DevC.RL, DevC.R)
                    else -> DevBadge(if (f.status.isEmpty()) "-" else f.status, Color(0xFFF1F5F9), DevC.S)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.zonePoints >= 3) DevBadge("มีรูปเขต", DevC.GnL, DevC.Gn) else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
                if (f.groupId.isNotEmpty()) DevBadge("หลังบ้านร่วม", DevC.OL, DevC.O)
                if (!f.hasCode) DevBadge("ยังไม่ตั้งรหัส", DevC.OL, DevC.O)
            }
            Text(
                if (last == null) "ยังไม่มีประวัติ" else "ล่าสุด: " + devHubLabel(last.action) + " · " + devHubTime(last.atMillis),
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp)
            )
            if (isOpen) {
                Text("ประวัติของเขตนี้ (" + mine.size + ")", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                if (items == null) {
                    Text("โหลดประวัติไม่สำเร็จ", fontSize = 12.sp, color = DevC.R)
                } else if (mine.isEmpty()) {
                    Text("ยังไม่มีประวัติ", fontSize = 12.sp, color = DevC.S)
                } else {
                    mine.take(10).forEach { a ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(devHubLabel(a.action) + " · " + devHubTime(a.atMillis), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                            Text(if (a.by.isEmpty()) "ไม่ทราบผู้ทำ" else a.by, fontSize = 11.sp, color = DevC.S)
                            a.details.forEach { line -> Text(line, fontSize = 11.sp, color = DevC.T) }
                        }
                    }
                }
                Text("รายละเอียด/แก้ไขเขต: เมนู เขตแฟรนไชส์", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DevStat(label: String, value: String, color: Color, modifier: Modifier) {
    DevCard(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
