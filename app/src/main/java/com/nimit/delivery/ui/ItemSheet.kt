package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nimit.delivery.data.*

private val IP = Color(0xFF0C4AA6)
private val IPD = Color(0xFF083570)

/** ป๊อบอัพเลือกตัวเลือกเมนู ใช้ซ้ำนอกหน้าเมนูร้าน (เช่นหน้าหมวดหมู่) หน้าตา/ตรรกะเดียวกับหน้าเมนู */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(st: PopupState, pricing: Pricing, onDismiss: () -> Unit, onAdd: (note: String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, dragHandle = null, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        var note by remember(st) { mutableStateOf("") }
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.94f).dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFF5F6F8)), contentAlignment = Alignment.Center) {
                    val img = st.m.str("imgUrl")
                    if (img.isNotEmpty()) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else PathIcon(P.RESTAURANT, Color(0xFFCBD5E1), 56.dp)
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                    Text(st.m.str("name"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    Text("฿${pricing.calcPrice(num(st.m["price"]) ?: 0.0, st.menuGp)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IP, modifier = Modifier.padding(top = 4.dp))
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PopupBody(st, pricing)
                    Text("คำแนะนำเพิ่มเติม", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    NimitInput(note, { note = it }, "เช่น ไม่ใส่ผัก, หวานน้อย", height = 100.dp, singleLine = false, fontSize = 14, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("จำนวน", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, Color(0xFFE2E8F0), CircleShape).clickable { st.qty = maxOf(1, st.qty - 1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = IP) }
                    Text("${st.qty}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(IP).clickable { st.qty += 1 }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text) }
                Box(Modifier.weight(2f).height(52.dp).shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = IP.copy(alpha = 0.3f), spotColor = IP.copy(alpha = 0.3f)).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(IP, IPD))).clickable { onAdd(note) }, contentAlignment = Alignment.Center) {
                    Text("เพิ่มลงตะกร้า ฿${st.total(pricing)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}
