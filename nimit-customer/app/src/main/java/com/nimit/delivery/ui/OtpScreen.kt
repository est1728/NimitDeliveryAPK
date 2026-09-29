package com.nimit.delivery.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            } else { busy = false; msg = "รหัสไม่ถูกต้อง กรุณาลองใหม่" }
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

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("ยืนยันเบอร์โทรศัพท์", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("กรอกรหัส OTP 6 หลัก", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Spacer(Modifier.height(6.dp))
            Text("เราส่งรหัสไปที่ $phone", fontSize = 14.sp, color = C.Subtext)
            Spacer(Modifier.height(20.dp))
            NimitInput(
                value = code, onChange = { code = it.filter(Char::isDigit).take(6); msg = null },
                placeholder = "------", height = 56.dp, fontSize = 22,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { verify() })
            )
            msg?.let { Text(it, fontSize = 13.sp, color = Color(0xFFE53935), modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(20.dp))
            GradientButton(if (busy) "กำลังตรวจสอบ..." else "ยืนยัน", enabled = code.length == 6 && !busy && verificationId != null) { verify() }
            Spacer(Modifier.height(16.dp))
            Text(
                if (countdown > 0) "ขอรหัสใหม่ได้ใน $countdown วินาที" else "ส่งรหัสอีกครั้ง",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (countdown > 0) C.Subtext else C.Primary,
                modifier = Modifier.fillMaxWidth().clickable(enabled = countdown == 0 && !busy) { send() }.padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
