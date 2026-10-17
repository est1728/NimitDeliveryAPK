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
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun devAuditLabel(a: String): String = when (a) {
    "link_zones" -> "ผูกเขต"
    "unlink_zone" -> "เลิกผูกเขต"
    "create_zone" -> "เพิ่มเขต"
    "edit_zone" -> "แก้ไขเขต"
    "move_shop" -> "ย้ายร้าน"
    "copy_shop" -> "คัดลอกร้าน"
    else -> a
}

private fun devAuditTime(ms: Long): String =
    if (ms <= 0L) "กำลังบันทึก" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

// หน้าประวัติการเปลี่ยนแปลง: ใครทำอะไรกับเขต เมื่อไร (อ่านอย่างเดียว ลบ/แก้ไม่ได้จากแอป)
@Composable
fun DevAuditScreen() {
    var list by remember { mutableStateOf<List<DevAuditItem>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        list = null
        err = false
        try {
            list = DevAuditRepo.recent()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }
    val l = list
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (l == null) "ประวัติการเปลี่ยนแปลง" else "ประวัติการเปลี่ยนแปลง (" + l.size + ")",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Text(
            "รีเฟรช", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.P,
            modifier = Modifier.clickable { tick++ }.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
    when {
        err -> DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดประวัติไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        l == null -> DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        l.isEmpty() -> DevCard {
            Text("ยังไม่มีประวัติ", fontSize = 13.sp, color = DevC.S)
            Text("ทุกครั้งที่ผูกเขตหรือเลิกผูกเขต ระบบจะบันทึกไว้ที่นี่", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
        }
        else -> l.forEach { a -> DevAuditCard(a) }
    }
}

@Composable
private fun DevAuditCard(a: DevAuditItem) {
    val unlink = a.action == "unlink_zone"
    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (a.names.isEmpty()) "-" else a.names.joinToString(" + "),
                    fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    (if (a.by.isEmpty()) "ไม่ทราบผู้ทำ" else a.by) + " · " + devAuditTime(a.atMillis),
                    fontSize = 11.5.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (unlink) DevBadge(devAuditLabel(a.action), DevC.RL, DevC.R)
            else if (a.action == "link_zones") DevBadge(devAuditLabel(a.action), DevC.PL, DevC.P)
            else if (a.action == "create_zone") DevBadge(devAuditLabel(a.action), DevC.GnL, DevC.Gn)
            else if (a.action == "edit_zone") DevBadge(devAuditLabel(a.action), DevC.OL, DevC.O)
            else if (a.action == "move_shop" || a.action == "copy_shop") DevBadge(devAuditLabel(a.action), DevC.PL, DevC.P)
            else DevBadge(devAuditLabel(a.action), Color(0xFFF1F5F9), DevC.S)
        }
        a.details.forEach { line ->
            Text(line, fontSize = 11.5.sp, color = DevC.T, modifier = Modifier.padding(top = 4.dp))
        }
        if (a.groupId.isNotEmpty()) {
            Text("รหัสกลุ่มหลังบ้าน: " + a.groupId, fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
