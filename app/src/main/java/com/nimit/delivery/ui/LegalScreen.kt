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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
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
const val ADMIN_LINE_URL: String = "https://lin.ee/6rWI6rH"

@Composable
fun LegalScreen(title: String, version: String, sections: List<LegalSection>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        val hs = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
        Box(
            Modifier.fillMaxWidth().clip(hs)
                .background(Brush.linearGradient(listOf(Color(0xFF1560C8), C.Primary, C.PrimaryDark)))
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.07f), radius = 110.dp.toPx(), center = Offset(size.width * 0.95f, 0f))
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = 80.dp.toPx(), center = Offset(size.width * 0.02f, size.height * 0.9f))
                }
                .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // ชื่อแบรนด์ตัวหนาเอียง: Nimit ใหญ่ + Delivery เล็กกว่า (ฟอนต์เดียวกัน)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1).sp)) { append("Nimit") }
                        withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-0.5).sp)) { append(" Delivery") }
                    },
                    color = Color.White
                )
                Spacer(Modifier.height(14.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ฉบับที่ $version", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp)) {
            val shape = RoundedCornerShape(18.dp)
            sections.forEach { sec ->
                val m: MatchResult? = Regex("^(\\d+)\\.\\s*(.*)$").find(sec.title)
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).shadow(2.dp, shape).clip(shape).background(Color.White).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m != null) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(C.PrimaryLight), contentAlignment = Alignment.Center) {
                                Text(m.groupValues[1], fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (m != null) m.groupValues[2] else sec.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    sec.lines.forEach { l ->
                        Row(Modifier.padding(bottom = 6.dp)) {
                            Box(Modifier.padding(top = 9.dp, end = 10.dp).size(6.dp).clip(CircleShape).background(C.Primary.copy(alpha = 0.55f)))
                            Text(l, fontSize = 14.5.sp, lineHeight = 22.sp, color = C.Text.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().shadow(2.dp, shape).clip(shape).background(Color.White).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("มีข้อสงสัยเกี่ยวกับเงื่อนไขหรือข้อมูลของคุณ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Spacer(Modifier.height(10.dp))
                ContactAdmin()
            }
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
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0xFF06C755), RoundedCornerShape(16.dp)).clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADMIN_LINE_URL))) } catch (e: Exception) { }
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("ทักไลน์แอดมิน", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF06A64A)) }
        }
    }
}
