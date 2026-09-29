package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.Config
import com.nimit.delivery.data.LoginFlow
import com.nimit.delivery.data.Session
import kotlinx.coroutines.launch

private val CheckCircle: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}

@Composable
fun LoginScreen(session: Session, onNeedOtp: () -> Unit, onLoggedIn: () -> Unit, onNewCustomer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val savedPhone = remember { session.customerPhone.orEmpty() }
    var phone by remember { mutableStateOf(savedPhone) }
    var showSaved by remember { mutableStateOf(savedPhone.isNotEmpty()) }
    var savedText by remember { mutableStateOf("เบอร์ที่จำไว้: $savedPhone") }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    val canNext = phone.length >= 6 && !loading

    fun next() {
        val p = phone
        if (p.length < 6 || !Regex("^0[0-9]{9}$").matches(p)) { error = true; return }
        error = false
        loading = true
        scope.launch {
            val prev = session.customerPhone
            session.customerPhone = p
            if (prev != null && prev != p) session.clearCustomerCache()
            if (Config.OTP_ENABLED && session.verifiedPhone != p) {
                loading = false
                onNeedOtp()
            } else {
                val registered = LoginFlow.route(session, p)
                loading = false
                if (registered) onLoggedIn() else onNewCustomer()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        // topbar
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Nimit Delivery", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        }
        HorizontalDivider(color = Color(0xFFF0F0F0))

        // hero
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 48.dp)
        ) {
            Text("ยินดีต้อนรับ", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("ใส่เบอร์โทรเพื่อเริ่มสั่งอาหาร", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
        }

        // container
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text("เบอร์โทรศัพท์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))

            val shape = RoundedCornerShape(16.dp)
            BasicTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { ch -> ch.isLetterOrDigit() && ch.code < 128 || ch == '_' || ch == '-' }
                    error = false; showSaved = false
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, color = C.Text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { next() }),
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .clip(shape).background(Color.White)
                    .border(2.dp, if (focused) C.Primary else Color(0xFFE0E0E0), shape)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                        if (phone.isEmpty()) Text("08X-XXX-XXXX", fontSize = 18.sp, color = Color(0xFF9CA3AF))
                        inner()
                    }
                }
            )

            if (showSaved) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(C.PrimaryLight).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(CheckCircle, null, tint = C.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(savedText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.weight(1f))
                    Text(
                        "เปลี่ยน", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            session.clearSaved(); phone = ""; showSaved = false
                        }
                    )
                }
            }

            if (error) {
                Text(
                    "กรุณากรอกเบอร์โทรศัพท์ให้ถูกต้อง (10 หลัก ขึ้นต้นด้วย 0)",
                    fontSize = 13.sp, color = Color(0xFFE53935), modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            val btnShape = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth().height(56.dp)
                    .then(if (canNext) Modifier.shadow(10.dp, btnShape, ambientColor = C.Primary.copy(alpha = 0.3f), spotColor = C.Primary.copy(alpha = 0.3f)) else Modifier)
                    .clip(btnShape)
                    .background(if (canNext) Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)) else SolidColor(Color(0xFFC5CFE8)))
                    .clickable(enabled = canNext) { next() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (loading) "กำลังเข้า..." else "ถัดไป", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "ระบบจะจำเบอร์ของคุณสำหรับครั้งถัดไป\nไม่ต้องสมัครสมาชิก",
                fontSize = 13.sp, lineHeight = 21.sp, color = C.Subtext,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
