package com.nimit.delivery.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.nimit.delivery.data.ShopInfo
import com.nimit.delivery.data.ShopRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf

/** หน้าเข้าสู่ระบบร้านด้วยเบอร์โทร + ยืนยัน OTP (Firebase Phone Auth) — HTML ไม่มีหน้า login จึงใช้สไตล์เดียวกับส่วนหัวของแอป */
@Composable
fun ShopLoginScreen(onLoggedIn: (ShopInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<ShopInfo>>(emptyList()) }
    // ขั้น OTP
    val activity = LocalContext.current as Activity
    val auth = Firebase.auth
    var pending by remember { mutableStateOf<List<ShopInfo>>(emptyList()) }
    var otpMode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var otpPhone by remember { mutableStateOf("") }

    fun finishLogin(list: List<ShopInfo>) {
        if (list.size == 1) onLoggedIn(list[0]) else choices = list
    }

    fun signIn(cred: PhoneAuthCredential) {
        if (!otpMode) return
        loading = true
        auth.signInWithCredential(cred).addOnCompleteListener { t ->
            loading = false
            if (t.isSuccessful) { otpMode = false; finishLogin(pending) }
            else { error = "รหัสไม่ถูกต้อง กรุณาลองใหม่"; code = "" }
        }
    }

    fun sendOtp() {
        loading = true; error = ""
        val intl: String = "+66" + otpPhone.removePrefix("0")
        val cb = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(c: PhoneAuthCredential) { signIn(c) }
            override fun onVerificationFailed(e: FirebaseException) {
                loading = false
                val ec: String = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode ?: ""
                error = when (ec) {
                    "ERROR_OPERATION_NOT_ALLOWED" -> "ระบบส่ง OTP ยังไม่เปิดใช้งาน กรุณาติดต่อแอดมิน"
                    "ERROR_TOO_MANY_REQUESTS" -> "ขอรหัสบ่อยเกินไป กรุณารอสักครู่แล้วลองใหม่"
                    "ERROR_INVALID_PHONE_NUMBER" -> "เบอร์โทรไม่ถูกต้อง"
                    else -> "ส่งรหัสไม่สำเร็จ: " + (e.localizedMessage ?: "")
                }
            }
            override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id; resendToken = t; loading = false; countdown = 60
            }
        }
        val b = PhoneAuthOptions.newBuilder(auth).setPhoneNumber(intl)
            .setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(cb)
        resendToken?.let { b.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }

    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown-- } }

    fun verify() {
        val id: String = verificationId ?: return
        if (code.length < 6 || loading) return
        error = ""
        signIn(PhoneAuthProvider.getCredential(id, code))
    }

    // กรอกครบ 6 หลักแล้วยืนยันให้เอง
    LaunchedEffect(code) { if (otpMode && code.length == 6 && verificationId != null) verify() }

    fun submit() {
        if (loading) return
        error = ""
        loading = true
        scope.launch {
            try {
                val found: List<ShopInfo> = ShopRepo.findByPhone(phone)
                if (found.isEmpty()) { error = "ไม่พบเบอร์นี้ในระบบร้านค้า กรุณาติดต่อแอดมิน"; loading = false }
                else {
                    pending = found
                    otpPhone = ShopRepo.normPhone(phone)
                    code = ""; verificationId = null; resendToken = null
                    otpMode = true
                    sendOtp()
                }
            } catch (e: Exception) {
                error = "เชื่อมต่อไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
                loading = false
            }
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
            if (otpMode) {
                Text("กรอกรหัส OTP 6 หลัก", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("เราส่งรหัสไปที่ $otpPhone", fontSize = 13.sp, color = C.Subtext)
                OtpBoxes(code, { code = it; error = "" }, { verify() })
                if (error.isNotEmpty()) Text(error, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed)
                GradientButton(if (loading) "กำลังตรวจสอบ..." else "ยืนยัน", enabled = !loading && code.length == 6 && verificationId != null) { verify() }
                Text(
                    if (countdown > 0) "ขอรหัสใหม่ได้ใน $countdown วินาที" else "ส่งรหัสอีกครั้ง",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (countdown > 0) C.Subtext else C.Primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = countdown == 0 && !loading) { sendOtp() }.padding(8.dp)
                )
                Text(
                    "เปลี่ยนเบอร์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { otpMode = false; loading = false; error = ""; code = "" }.padding(8.dp)
                )
            } else if (choices.isEmpty()) {
                Text("เข้าสู่ระบบ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("เบอร์โทรร้าน", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                NimitInput(
                    phone, { phone = it.filter { c -> c.isDigit() || c == '-' || c == ' ' }; error = "" }, "08X-XXX-XXXX",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                if (error.isNotEmpty()) Text(error, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ShopRed)
                GradientButton(if (loading) "กำลังตรวจสอบ..." else "ขอรหัส OTP", enabled = !loading && phone.length >= 9) { submit() }
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
                    modifier = Modifier.fillMaxWidth().clickable { choices = emptyList(); auth.signOut() }.padding(8.dp)
                )
            }
        }
    }
}

/** ช่องกรอก OTP 6 ช่อง (ช่องเดียวจริงๆ ซ่อนอยู่ แล้ววาดเป็นกล่อง 6 กล่อง) แตะที่กล่องเพื่อเปิดคีย์บอร์ด วางรหัสได้ */
@Composable
private fun OtpBoxes(code: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
    BasicTextField(
        value = code,
        onValueChange = { v: String -> onChange(v.filter { c -> c.isDigit() }.take(6)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val active: Boolean = i == code.length || (i == 5 && code.length == 6)
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray)
                            .border(if (active) 2.dp else 1.dp, if (active) C.Primary else Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (i < code.length) code[i].toString() else "", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                }
            }
        }
    )
}
