package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminRepo
import kotlinx.coroutines.launch

// หน้าล็อกอินเต็มจอ (เบอร์ + รหัส) เปิดแอปมาถ้ายังไม่มีเซสชันจะเจอหน้านี้ก่อนเสมอ
@Composable
fun AdminLoginScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(C.Gray).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nimit Delivery", fontSize = 20.sp, fontWeight = FontWeight.Black, color = C.Primary)
                Text("เข้าสู่ระบบหลังบ้าน", fontSize = 12.5.sp, color = C.Subtext, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(22.dp)) {
                if (busy) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = C.Primary, trackColor = C.PrimaryLight, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Text("กำลังเข้าสู่ระบบ...", fontSize = 13.sp, color = C.Subtext, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    }
                } else {
                    AdminLabel("เบอร์โทร")
                    AdminField(phone, { phone = it.filter { c -> c.isDigit() } }, "0812345678", KeyboardType.Phone, VisualTransformation.None)
                    AdminLabel("รหัส")
                    AdminField(pin, { pin = it }, "********", KeyboardType.Password, PasswordVisualTransformation())
                    Text(
                        err, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp).padding(bottom = 8.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                            .clickable {
                                err = ""
                                busy = true
                                scope.launch {
                                    val r = AdminRepo.login(ctx, phone, pin)
                                    busy = false
                                    if (r == null) onDone() else err = r
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("เข้าสู่ระบบ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

@Composable
private fun AdminLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun AdminField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    type: KeyboardType,
    vt: VisualTransformation
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = vt,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        textStyle = TextStyle(fontSize = 14.sp, color = C.Text),
        cursorBrush = SolidColor(C.Primary),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
