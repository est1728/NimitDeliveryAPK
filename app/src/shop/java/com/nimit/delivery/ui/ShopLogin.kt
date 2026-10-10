package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.ShopInfo
import com.nimit.delivery.data.ShopRepo
import kotlinx.coroutines.launch

/** หน้าเข้าสู่ระบบร้านด้วยเบอร์โทร (ยังไม่มี OTP) — HTML ไม่มีหน้า login จึงใช้สไตล์เดียวกับส่วนหัวของแอป */
@Composable
fun ShopLoginScreen(onLoggedIn: (ShopInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<ShopInfo>>(emptyList()) }

    fun submit() {
        if (loading) return
        error = ""
        loading = true
        scope.launch {
            try {
                val found: List<ShopInfo> = ShopRepo.findByPhone(phone)
                if (found.isEmpty()) error = "ไม่พบเบอร์นี้ในระบบร้านค้า กรุณาติดต่อแอดมิน"
                else if (found.size == 1) onLoggedIn(found[0])
                else choices = found
            } catch (e: Exception) {
                error = "เชื่อมต่อไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).statusBarsPadding().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                PathIcon(ShopP.SHOP, Color.White, 36.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Nimit ร้านค้า", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("พอร์ทัลจัดการร้านค้า", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
        }
        Column(
            Modifier.padding(16.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (choices.isEmpty()) {
                Text("เข้าสู่ระบบ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("เบอร์โทรร้าน", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                NimitInput(
                    phone, { phone = it.filter { c -> c.isDigit() || c == '-' || c == ' ' }; error = "" }, "08X-XXX-XXXX",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                if (error.isNotEmpty()) Text(error, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed)
                GradientButton(if (loading) "กำลังตรวจสอบ..." else "เข้าสู่ระบบ", enabled = !loading && phone.length >= 9) { submit() }
            } else {
                Text("เบอร์นี้มีหลายร้าน", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("เลือกร้านที่ต้องการจัดการ", fontSize = 13.sp, color = C.Subtext)
                for (s in choices) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Gray).clickable { onLoggedIn(s) }.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) { Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text) }
                }
                Text(
                    "ย้อนกลับ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { choices = emptyList() }.padding(8.dp)
                )
            }
        }
    }
}
