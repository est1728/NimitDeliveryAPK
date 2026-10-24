package com.nimit.delivery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.Session
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** หน้าตั้งค่า (ลูกค้า): เลือกรูปแบบการ์ดร้านค้า — มีภาพตัวอย่างเคลื่อนไหวของแต่ละแบบ (ค่าเริ่มต้น = แบบใหม่) */
@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit) {
    var style by remember { mutableStateOf(session.cardStyle) }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("ตั้งค่า", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("รูปแบบการ์ดร้านค้า", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text("เลือกการแสดงรายการร้านในหน้าหลัก ร้านแนะนำยังเปิดหน้าเมนูเหมือนเดิม", fontSize = 12.sp, color = C.Subtext)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StyleTile("คลาสสิก", style == "classic", Modifier.weight(1f)) { style = "classic"; session.cardStyle = "classic" }
                StyleTile("แบบใหม่", style == "ring", Modifier.weight(1f)) { style = "ring"; session.cardStyle = "ring" }
            }
            Text(
                if (style == "ring") "แบบใหม่: แตะการ์ดร้านเพื่อขยาย เลือกเมนูจากวงแหวนในการ์ดได้เลย" else "คลาสสิก: แตะการ์ดร้านเพื่อเข้าหน้าเมนู (แบบเดิม)",
                fontSize = 12.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StyleTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        val outer = RoundedCornerShape(22.dp)
        Box(
            Modifier.width(132.dp).height(224.dp).clip(outer).border(if (selected) 3.dp else 1.dp, if (selected) N.B500 else Color(0xFFD5DEEE), outer).padding(4.dp)
        ) {
            val inner = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxSize().clip(inner).background(Color.White)) {
                if (label == "คลาสสิก") ClassicPreview() else RingPreview()
            }
        }
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) N.B500 else C.Text, modifier = Modifier.padding(top = 10.dp))
    }
}

private val PV_GREY = Color(0xFFE3E8F2)
private val PV_BANNER = Color(0xFFDCE8FB)

@Composable
private fun PvHeader() {
    Box(Modifier.fillMaxWidth().height(22.dp).background(Brush.linearGradient(listOf(N.B800, N.B700)))) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.8f)))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(46.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.35f)))
    }
}

/** ตัวอย่างแบบคลาสสิก: รายการการ์ดร้าน → แตะการ์ด → เด้งเข้าหน้าเมนู → เมนูเลื่อนลง → กลับ (วนซ้ำ) */
@Composable
private fun ClassicPreview() {
    val tr = rememberInfiniteTransition(label = "classic")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "cl")
    val listScroll = smooth(seg(t, 0.02f, 0.22f)) - smooth(seg(t, 0.92f, 0.99f))
    val press = smooth(seg(t, 0.26f, 0.31f)) - smooth(seg(t, 0.35f, 0.39f))
    val open = smooth(seg(t, 0.37f, 0.50f)) - smooth(seg(t, 0.90f, 0.98f))
    val menuScroll = smooth(seg(t, 0.55f, 0.88f))
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PvHeader()
            Box(Modifier.fillMaxSize().clipToBounds()) {
                val cardH = 62f; val gap = 8f
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).graphicsLayer { translationY = -listScroll * 30f * density }, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                    Spacer(Modifier.height(2.dp))
                    for (i in 0 until 5) MiniClassicCard(cardH, i, if (i == 1) press else 0f)
                }
            }
        }
        if (open > 0.01f) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.44f)
                val sc = 0.5f + 0.5f * open
                scaleX = sc; scaleY = sc; alpha = open
            }.background(Color.White)) { MiniMenuPage(menuScroll) }
        }
    }
}

/** หน้าเมนูจำลอง: แถบหัว + แบนเนอร์ร้าน + รายการเมนูที่เลื่อนขึ้น */
@Composable
private fun MiniMenuPage(scroll: Float) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(22.dp).background(Brush.linearGradient(listOf(N.B800, N.B700)))) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
            Box(Modifier.align(Alignment.Center).width(40.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.6f)))
        }
        Box(Modifier.fillMaxWidth().height(36.dp).background(PV_BANNER))
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).graphicsLayer { translationY = -scroll * 120f * density }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 9) {
                    val sh = RoundedCornerShape(8.dp)
                    Row(Modifier.fillMaxWidth().height(32.dp).clip(sh).border(1.dp, Color(0xFFE2E8F2), sh).background(Color.White).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(RING_PV[i % RING_PV.size]))
                        Column(Modifier.padding(start = 6.dp).weight(1f)) {
                            Box(Modifier.width(if (i % 2 == 0) 44.dp else 36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF9AA2B8)))
                            Box(Modifier.padding(top = 3.dp).width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(PV_GREY))
                        }
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(N.B500).padding(horizontal = 5.dp, vertical = 3.dp)) { Box(Modifier.size(width = 10.dp, height = 3.dp).background(Color.White.copy(alpha = 0.9f))) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniClassicCard(h: Float, i: Int, press: Float = 0f) {
    val shape = RoundedCornerShape(9.dp)
    Column(Modifier.fillMaxWidth().height(h.dp).graphicsLayer { val k = 1f - 0.05f * press; scaleX = k; scaleY = k }.clip(shape).background(Color(0xFFF4F8FE)).border(if (press > 0.05f) 2.dp else 1.dp, if (press > 0.05f) N.B500 else Color(0xFFE2E8F2), shape)) {
        Box(Modifier.fillMaxWidth().height(26.dp).background(if (i % 2 == 0) PV_BANNER else Color(0xFFE6EEF9))) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Box(Modifier.size(width = 10.dp, height = 3.dp).background(Color(0xFF16A34A)))
            }
        }
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.offset(y = (-9).dp).size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.5.dp, Color.White, RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(4.dp)).background(N.B500.copy(alpha = 0.55f)))
            }
            Column(Modifier.padding(start = 6.dp, top = 3.dp)) {
                Box(Modifier.width(if (i % 2 == 0) 52.dp else 44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF9AA2B8)))
                Box(Modifier.padding(top = 3.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(PV_GREY))
            }
        }
    }
}

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float) = x * x * (3 - 2 * x)

/** ตัวอย่างแบบใหม่: การ์ดแถวเตี้ย → ขยายในที่ → วงแหวนเมนูไหลซ้าย-ขวา → หุบกลับ วนซ้ำ */
@Composable
private fun RingPreview() {
    val tr = rememberInfiniteTransition(label = "ring")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "rg")
    val expand = smooth(seg(t, 0.15f, 0.32f)) - smooth(seg(t, 0.86f, 0.98f))
    val pos = 5f * seg(t, 0.30f, 0.86f)   // หมุนต่อเนื่องไปทางเดียว ครบ 5 ใบ แล้วหุบ
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxSize()) {
        PvHeader()
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(shape).border(1.5.dp, N.B500, shape).background(Color.White).padding(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size((26 - 6 * expand).dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(N.B500, N.B800))))
                    Column(Modifier.padding(start = 6.dp).weight(1f)) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF6B7A99)))
                        if (expand < 0.5f) Box(Modifier.alpha(1f - expand * 2f).padding(top = 3.dp).width(54.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(PV_GREY))
                    }
                    if (expand > 0.3f) Box(Modifier.alpha(min(1f, (expand - 0.3f) * 3f)).clip(RoundedCornerShape(5.dp)).background(N.B500).padding(horizontal = 6.dp, vertical = 3.dp)) {
                        Box(Modifier.size(width = 14.dp, height = 3.dp).background(Color.White.copy(alpha = 0.9f)))
                    }
                }
                if (expand > 0.02f) {
                    Row(Modifier.padding(top = 5.dp).alpha(expand), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (k in 0 until 3) Box(Modifier.width(if (k == 0) 20.dp else 28.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (k == 0) N.B600 else PV_GREY))
                    }
                    Box(Modifier.fillMaxWidth().height((74 * expand).dp).padding(top = 6.dp).clipToBounds().alpha(expand)) {
                        // วงแหวนจริง: การ์ดวางตามมุม (30°/ใบ) หมุนต่อเนื่อง ใบข้างเอียงออกและเล็กลงตามระยะ
                        val base = Math.floor(pos.toDouble()).toInt()
                        for (j in base - 3..base + 3) {
                            val d = j - pos
                            val th = Math.toRadians((d * 30f).toDouble())
                            val c = Math.cos(th).toFloat()
                            Box(
                                Modifier.align(Alignment.TopCenter).size(width = 28.dp, height = 52.dp).zIndex(c)
                                    .graphicsLayer {
                                        translationX = Math.sin(th).toFloat() * 74f * density
                                        rotationY = d * 30f
                                        cameraDistance = 12f * density
                                        val sc = 0.72f + 0.28f * c.coerceAtLeast(0f)
                                        scaleX = sc; scaleY = sc
                                        alpha = ((c - 0.1f) / 0.4f).coerceIn(0f, 1f)
                                    }
                                    .clip(RoundedCornerShape(6.dp)).border(1.dp, if (abs(d) < 0.5f) N.B700 else N.B500, RoundedCornerShape(6.dp)).background(Color.White).padding(3.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(4.dp)).background(RING_PV[Math.floorMod(j, RING_PV.size)]))
                                    Box(Modifier.padding(top = 4.dp).width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF9AA2B8)))
                                    Box(Modifier.padding(top = 3.dp).width(11.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(N.B600))
                                }
                            }
                        }
                    }
                }
            }
            // การ์ดถัดไป (ถูกดันลงตอนขยาย)
            Row(Modifier.fillMaxWidth().clip(shape).border(1.5.dp, N.B500.copy(alpha = 0.6f), shape).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFB9CDEE)))
                Column(Modifier.padding(start = 6.dp)) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF9AA2B8)))
                    Box(Modifier.padding(top = 3.dp).width(50.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(PV_GREY))
                }
            }
        }
    }
}

private val RING_PV = listOf(Color(0xFFF3D5D5), Color(0xFFF5E3C8), Color(0xFFE6EBC9), Color(0xFFCFE8DC), Color(0xFFD4DFF5), Color(0xFFE5D2EE))
