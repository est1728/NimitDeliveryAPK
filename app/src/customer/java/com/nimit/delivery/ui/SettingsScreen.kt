package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.Session

/** หน้าตั้งค่า (ลูกค้า): เลือกรูปแบบการ์ดร้านค้า — คลาสสิกเป็นค่าเริ่มต้น */
@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit) {
    var style by remember { mutableStateOf(session.cardStyle) }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("ตั้งค่า", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("รูปแบบการ์ดร้านค้า", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text("เลือกการแสดงรายการร้านในหน้าหลัก ร้านแนะนำยังเปิดหน้าเมนูเหมือนเดิม", fontSize = 12.sp, color = C.Subtext)
            StyleOption("การ์ดคลาสสิก", "แตะการ์ดร้านเพื่อเข้าหน้าเมนู (แบบเดิม)", style == "classic") { style = "classic"; session.cardStyle = "classic" }
            StyleOption("การ์ดแบบใหม่", "การ์ดขยายได้ เลือกหัวข้อเมนูจากการ์ดโดยตรง (เร็วๆ นี้)", style == "ring") { style = "ring"; session.cardStyle = "ring" }
        }
    }
}

@Composable
private fun StyleOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) C.Primary else Color(0xFFE2E8F0), shape)
            .clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text(sub, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) C.Primary else Color(0xFFCBD5E1), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(C.Primary)) }
    }
}
