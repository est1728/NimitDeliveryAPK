package com.nimit.delivery.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * หน้า "เงื่อนไขการใช้บริการ" / "นโยบายข้อมูลส่วนบุคคล" + ข้อความท้ายหน้าล็อกอิน ใช้ร่วมกันทุกแอป (อยู่ใน main)
 * แต่ละแอปส่งเนื้อหาของตัวเองเข้ามาเป็น List<LegalSection> (ไฟล์ <App>Legal.kt ในโฟลเดอร์ของแฟลเวอร์)
 */

/** หัวข้อหนึ่งข้อ เช่น "1. ข้อมูลที่เราเก็บ" พร้อมรายการบรรทัดข้างใต้ (แต่ละบรรทัดขึ้นต้นด้วย "- " ในหน้าจอ) */
data class LegalSection(val title: String, val lines: List<String>)

/** ลิงก์ LINE ของผู้ดูแลระบบ (ใช้ร่วมทุกแอป) ถ้าเว้นว่างจะซ่อนปุ่มทักไลน์ */
const val ADMIN_LINE_URL: String = ""

@Composable
fun LegalScreen(title: String, version: String, sections: List<LegalSection>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            // ชื่อแบรนด์: Nimit ตัวใหญ่ + delivery ตัวเล็ก (ฟอนต์/น้ำหนักเดียวกัน)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)) { append("Nimit") }
                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)) { append(" delivery") }
                },
                color = Color.White
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text("ฉบับที่ $version", fontSize = 14.sp, color = C.Subtext, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Color(0xFFE5E7EB), shape).padding(20.dp)) {
                sections.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(22.dp))
                    Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    Spacer(Modifier.height(8.dp))
                    s.lines.forEach { l ->
                        Text("- $l", fontSize = 15.sp, lineHeight = 24.sp, color = C.Text.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            ContactAdmin()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** ข้อความเล็กใต้ช่องกรอกเบอร์: ยอมรับเงื่อนไข/นโยบาย (กดได้) + ติดต่อผู้ดูแลระบบ + ปุ่มทักไลน์ */
@Composable
fun LegalFooter(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val link = SpanStyle(color = C.Primary, fontWeight = FontWeight.Bold)
    val text: AnnotatedString = buildAnnotatedString {
        append("การใช้งานถือว่าคุณยอมรับ ")
        pushStringAnnotation("legal", "terms")
        withStyle(link) { append("เงื่อนไขการใช้บริการ") }
        pop()
        append(" และ ")
        pushStringAnnotation("legal", "privacy")
        withStyle(link) { append("นโยบายข้อมูลส่วนบุคคล") }
        pop()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ClickableText(
            text = text,
            style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = C.Subtext, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
            onClick = { off: Int ->
                val a = text.getStringAnnotations("legal", off, off).firstOrNull()
                if (a != null) { if (a.item == "terms") onTerms() else onPrivacy() }
            }
        )
        Spacer(Modifier.height(14.dp))
        ContactAdmin()
    }
}

@Composable
private fun ContactAdmin() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ติดต่อผู้ดูแลระบบ", fontSize = 12.sp, color = C.Subtext)
        if (ADMIN_LINE_URL.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF06C755)).clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADMIN_LINE_URL))) } catch (e: Exception) { }
                },
                contentAlignment = Alignment.Center
            ) { Text("ทักไลน์แอดมิน", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}
