package com.nimit.delivery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.R
import com.nimit.delivery.data.RiderInfo
import com.nimit.delivery.data.RiderRepo
import com.nimit.delivery.data.RiderSession
import kotlinx.coroutines.launch

@Composable
fun RiderLoginScreen(onLoggedIn: (RiderInfo) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val r: RiderInfo? = RiderRepo.findByPhone(phone)
                if (r == null) error = "เบอร์นี้ยังไม่ได้ลงทะเบียนเป็นไรเดอร์" else onLoggedIn(r)
            } catch (e: Exception) {
                error = "เชื่อมต่อไม่ได้ ลองใหม่อีกครั้ง"
            }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Nimit Rider", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Spacer(Modifier.height(4.dp))
        Text("เข้าสู่ระบบด้วยเบอร์โทรที่ลงทะเบียนไว้", fontSize = 14.sp, color = C.Subtext)
        Spacer(Modifier.height(28.dp))
        NimitInput(
            value = phone,
            onChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
            placeholder = "เบอร์โทรศัพท์",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        val e: String? = error
        if (e != null) {
            Spacer(Modifier.height(10.dp))
            Text(e, fontSize = 13.sp, color = Color(0xFFE5484D))
        }
        Spacer(Modifier.height(20.dp))
        GradientButton(
            text = if (loading) "กำลังตรวจสอบ…" else "เข้าสู่ระบบ",
            enabled = phone.length >= 9 && !loading
        ) { submit() }
    }
}

@Composable
fun RiderHomeScreen(session: RiderSession, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.Gray).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("สวัสดี ${session.riderName ?: "ไรเดอร์"}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Spacer(Modifier.height(4.dp))
        Text(session.riderPhone ?: "", fontSize = 14.sp, color = C.Subtext)
        Spacer(Modifier.height(16.dp))
        Text("หน้างานกำลังแปลงเป็น Native", fontSize = 14.sp, color = C.Subtext)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onLogout) { Text("ออกจากระบบ") }
    }
}
