package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException

// หน้าแรก: การ์ดเขตแฟรนไชส์ (อ่านข้อมูลเดิมจาก franchises + shops, ยังเป็นอ่านอย่างเดียว)
@Composable
fun DevZonesScreen() {
    var list by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
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
    val n = list?.size
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (n == null) "เขตแฟรนไชส์ทั้งหมด" else "เขตแฟรนไชส์ทั้งหมด ($n)",
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T, modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.height(38.dp).clip(RoundedCornerShape(20.dp)).background(DevC.P).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ เพิ่มเขต", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    val l = list
    when {
        err -> DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        l == null -> DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        l.isEmpty() -> DevCard {
            Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S)
            Text(
                "เพิ่มเขตหลังคุยกับผู้สมัครทางไลน์ แล้วใช้ปุ่ม \"เชื่อมหลังบ้าน\" เพื่อผูกหลายเขตเข้าหลังบ้านเดียวกัน",
                fontSize = 11.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        else -> l.forEach { f -> DevZoneCard(f) }
    }
}

@Composable
private fun DevZoneCard(f: DevFranchise) {
    val fee = if (f.monthlyFee > 0) " · ค่าแฟรนไชส์/เดือน ฿" + String.format("%,.0f", f.monthlyFee) else ""
    val sub = (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + f.shopCount + " ร้าน" + fee
    DevCard {
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
        Row(Modifier.padding(top = 8.dp)) {
            if (f.zonePoints >= 3) DevBadge("มีเขตแล้ว", DevC.GnL, DevC.Gn)
            else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
        }
    }
}

@Composable
fun DevBadge(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
