package com.nimit.delivery.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.google.firebase.FirebaseException
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.nimit.delivery.data.LoginFlow
import com.nimit.delivery.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun OtpScreen(session: Session, onBack: () -> Unit, onDone: (registered: Boolean) -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val phone = session.customerPhone.orEmpty()
    val intl = "+66" + phone.removePrefix("0")
    val auth = Firebase.auth

    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    fun signIn(cred: PhoneAuthCredential) {
        busy = true
        auth.signInWithCredential(cred).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                session.verifiedPhone = phone
                scope.launch { val reg = LoginFlow.route(session, phone); busy = false; onDone(reg) }
            } else { busy = false; code = ""; msg = "รหัสไม่ถูกต้อง กรุณาลองใหม่" }
        }
    }

    fun send() {
        busy = true; msg = null
        val cb = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(c: PhoneAuthCredential) { signIn(c) }
            override fun onVerificationFailed(e: FirebaseException) { busy = false; msg = "ส่งรหัสไม่สำเร็จ: ${e.localizedMessage}" }
            override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id; resendToken = t; busy = false; countdown = 60
            }
        }
        val b = PhoneAuthOptions.newBuilder(auth).setPhoneNumber(intl)
            .setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(cb)
        resendToken?.let { b.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }

    LaunchedEffect(Unit) { send() }
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown-- } }

    fun verify() {
        val id = verificationId ?: return
        if (code.length < 6) return
        signIn(PhoneAuthProvider.getCredential(id, code))
    }

    LaunchedEffect(code) { if (code.length == 6 && verificationId != null && !busy) verify() }

    Column(Modifier.fillMaxSize().background(C.Gray).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).statusBarsPadding()) {
            Text(
                "‹", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clickable { onBack() }.padding(horizontal = 18.dp, vertical = 6.dp)
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("📱", fontSize = 32.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("ยืนยันเบอร์โทรศัพท์", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Nimit Delivery", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
            }
        }
        Column(
            Modifier.padding(16.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("กรอกรหัส OTP 6 หลัก", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text("เราส่งรหัสไปที่ $phone", fontSize = 13.sp, color = C.Subtext)
            OtpBoxes(code, { code = it; msg = null }) { verify() }
            msg?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935)) }
            GradientButton(if (busy) "กำลังตรวจสอบ..." else "ยืนยัน", enabled = code.length == 6 && !busy && verificationId != null) { verify() }
            Text(
                if (countdown > 0) "ขอรหัสใหม่ได้ใน $countdown วินาที" else "ส่งรหัสอีกครั้ง",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (countdown > 0) C.Subtext else C.Primary,
                modifier = Modifier.fillMaxWidth().clickable(enabled = countdown == 0 && !busy) { send() }.padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "เปลี่ยนเบอร์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { onBack() }.padding(8.dp)
            )
        }
    }
}

@Composable
private fun OtpBoxes(code: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
    BasicTextField(
        value = code, onValueChange = { v: String -> onChange(v.filter { c -> c.isDigit() }.take(6)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = TextStyle(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val active = i == code.length || (i == 5 && code.length == 6)
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray)
                            .border(if (active) 2.dp else 1.dp, if (active) C.Primary else Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(if (i < code.length) code[i].toString() else "", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Text) }
                }
            }
        }
    )
}
