#!/bin/bash
# เขียนไฟล์ flavor dev ชุดล่าสุดทับทั้งไฟล์ (sidebar ขวา + ล็อกอิน + หน้าเขต) รันซ้ำได้
set -e
D=app/src/dev/java/com/nimit/delivery
[ -d app/src/dev ] || { echo "หยุด: ไม่เจอ app/src/dev (รันใน ~/nimit-customer หรือเปล่า)"; exit 1; }
mkdir -p "$D/data" "$D/ui"
cat > "$D/data/DevRepo.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class DevFranchise(
    val id: String,
    val name: String,
    val province: String,
    val ownerEmail: String,
    val monthlyFee: Double,
    val status: String,
    val zonePoints: Int,
    val shopCount: Int
)

object DevRepo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    fun currentEmail(): String? = auth.currentUser?.email?.lowercase()

    fun logout() {
        auth.signOut()
    }

    /** คืนข้อความผิดพลาด หรือ null ถ้าเข้าสู่ระบบและมีสิทธิ์ */
    suspend fun login(email: String, pw: String): String? {
        val e = email.trim().lowercase()
        if (e.isEmpty() || pw.isEmpty()) return "กรุณากรอกอีเมลและรหัสผ่าน"
        try {
            auth.signInWithEmailAndPassword(e, pw).await()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: FirebaseAuthInvalidCredentialsException) {
            return "อีเมลหรือรหัสผ่านไม่ถูกต้อง"
        } catch (ex: FirebaseAuthInvalidUserException) {
            return "อีเมลหรือรหัสผ่านไม่ถูกต้อง"
        } catch (ex: Exception) {
            return "เข้าสู่ระบบไม่สำเร็จ ตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
        }
        return checkAccess()
    }

    /** กติกาเดียวกับหน้า HQ เดิม: ต้องมีเอกสาร admins/{อีเมล} สถานะ active และ role ไม่ขึ้นต้นด้วย franchise */
    suspend fun checkAccess(): String? {
        val e = currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        return try {
            val d = db.collection("admins").document(e).get().await()
            val role = d.getString("role") ?: ""
            if (!d.exists() || d.getString("status") != "active" || role.startsWith("franchise")) {
                auth.signOut()
                "บัญชีนี้ไม่มีสิทธิ์เข้าระบบผู้พัฒนา"
            } else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            auth.signOut()
            "ตรวจสอบสิทธิ์ไม่สำเร็จ"
        }
    }

    /** อ่านอย่างเดียว: franchises + นับร้านจาก shops (franchiseIds / franchiseId เดิม) */
    suspend fun franchises(): List<DevFranchise> {
        val fs = db.collection("franchises").get().await().documents
        val shops = db.collection("shops").get().await().documents
        return fs.map { d ->
            val shopCount = shops.count { s ->
                s.getString("franchiseId") == d.id ||
                    (s.get("franchiseIds") as? List<*>)?.contains(d.id) == true
            }
            DevFranchise(
                id = d.id,
                name = d.getString("name") ?: "แฟรนไชส์",
                province = d.getString("province") ?: "",
                ownerEmail = d.getString("ownerEmail") ?: "",
                monthlyFee = d.getDouble("monthlyFee") ?: 0.0,
                status = d.getString("status") ?: "",
                zonePoints = (d.get("zone") as? List<*>)?.size ?: 0,
                shopCount = shopCount
            )
        }.sortedBy { it.name }
    }
}
KT_EOF
cat > "$D/ui/DevLogin.kt" <<'KT_EOF'
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.launch

// หน้าล็อกอิน: ถอดจาก #authGate ของ franchise-hq.html (ag-box 380, ag-card radius 20 padding 22)
@Composable
fun DevLoginScreen(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(DevC.G).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nimit Delivery", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DevC.P)
                Text("ระบบผู้พัฒนา", fontSize = 12.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(22.dp)) {
                if (busy) {
                    DevSpinner("กำลังตรวจสอบสิทธิ์...")
                } else {
                    DevLabel("อีเมล")
                    DevField(email, { email = it }, "you@email.com", KeyboardType.Email, VisualTransformation.None)
                    DevLabel("รหัสผ่าน")
                    DevField(pw, { pw = it }, "********", KeyboardType.Password, PasswordVisualTransformation())
                    Text(
                        err, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DevC.R,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp).padding(bottom = 8.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                            .clickable {
                                err = ""
                                busy = true
                                scope.launch {
                                    val r = DevRepo.login(email, pw)
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
fun DevChecking() {
    Box(Modifier.fillMaxSize().background(DevC.G), contentAlignment = Alignment.Center) {
        DevSpinner("กำลังตรวจสอบสิทธิ์...")
    }
}

@Composable
private fun DevSpinner(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = DevC.P, trackColor = DevC.PL, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(text, fontSize = 13.sp, color = DevC.S, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DevLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DevField(
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
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
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
KT_EOF
cat > "$D/ui/DevNav.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.launch

private data class DevPage(val key: String, val title: String, val desc: String)

// ลำดับ sidebar ตามที่ตกลงกัน
private val devMain = listOf(
    DevPage("zones", "เขตแฟรนไชส์", ""),
    DevPage("hub", "ศูนย์ข้อมูลเขต", "บอร์ดรวมทุกเขต: จำนวนเขต สถานะ และประวัติของแต่ละเขต กดเข้าดูรายเขตได้"),
    DevPage("fin", "การเงินรวม", "ตารางใหญ่ประวัติการเงินทุกเขต กรองตามเขตและช่วงวันที่"),
    DevPage("audit", "ประวัติการเปลี่ยนแปลง", "บันทึกทุกการเปลี่ยนเขต ผูกเขต และตั้งค่า (ใคร/ทำอะไร/ก่อน→หลัง/เวลา) แก้ไขหรือลบไม่ได้"),
    DevPage("notif", "แจ้งเตือน", "ทดสอบระบบ push ตามหน้า developer.html เดิม")
)
private val devTools = listOf(
    DevPage("deploy", "Deploy", "อัปโหลดไฟล์ขึ้น GitHub"),
    DevPage("diff", "Diff", "เทียบไฟล์ก่อน push"),
    DevPage("files", "ไฟล์", "รายการไฟล์ใน repo"),
    DevPage("ai", "AI", "แชท Gemini"),
    DevPage("deployhist", "ประวัติ Deploy", "ประวัติการ push")
)
private val devSettings = DevPage("set", "ตั้งค่า", "GitHub / Gemini / ราคา GP")

@Composable
fun DevNav() {
    var gate by remember { mutableStateOf(if (DevRepo.currentEmail() != null) "checking" else "login") }
    var email by remember { mutableStateOf(DevRepo.currentEmail() ?: "") }
    LaunchedEffect(Unit) {
        if (gate == "checking") {
            gate = if (DevRepo.checkAccess() == null) "app" else "login"
        }
    }
    when (gate) {
        "app" -> DevMain(email) {
            DevRepo.logout()
            gate = "login"
        }
        "login" -> DevLoginScreen {
            email = DevRepo.currentEmail() ?: ""
            gate = "app"
        }
        else -> DevChecking()
    }
}

@Composable
private fun DevMain(email: String, onLogout: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("zones") }
    val all = devMain + devTools + devSettings
    val curPage = all.first { it.key == cur }

    @Composable
    fun item(p: DevPage) {
        NavigationDrawerItem(
            label = { Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            selected = cur == p.key,
            onClick = {
                cur = p.key
                scope.launch { drawer.close() }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = DevC.PL,
                selectedTextColor = DevC.P,
                unselectedTextColor = DevC.T
            )
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(290.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                        .padding(16.dp)
                ) {
                    Text("Nimit Developer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ศูนย์ผู้พัฒนา", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    devMain.forEach { item(it) }
                    Text(
                        "เครื่องมือ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DevC.S,
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp)
                    )
                    devTools.forEach { item(it) }
                    Box(Modifier.padding(top = 8.dp)) { item(devSettings) }
                }
                }
            }
        }
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().background(DevC.G)) {
            // bar: ถอดจาก .bar ใน developer.html
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(curPage.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(email, color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1)
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = .18f))
                        .clickable { onLogout() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("ออก", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = .18f))
                        .clickable { scope.launch { drawer.open() } },
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { Box(Modifier.width(16.dp).height(2.dp).background(Color.White)) }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp)
            ) {
                if (cur == "zones") DevZonesScreen() else DevCard {
                    DevSTitle(curPage.title)
                    Text(curPage.desc, fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp)
                    Text("ยังไม่เปิดใช้งานในเวอร์ชันนี้", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        }
    }
    }
}
KT_EOF
cat > "$D/ui/DevZones.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException

// หน้าแรก: การ์ดเขตแฟรนไชส์ (อ่านข้อมูลเดิมจาก franchises + shops, ยังเป็นอ่านอย่างเดียว)
@Composable
fun DevZonesScreen() {
    var list by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        list = null
        err = false
        try {
            list = DevRepo.franchises()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }
    val n = list?.size
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (n == null) "เขตแฟรนไชส์ทั้งหมด" else "เขตแฟรนไชส์ทั้งหมด ($n)",
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T, modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.height(38.dp).clip(RoundedCornerShape(20.dp)).background(DevC.P).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ เพิ่มเขต", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    val l = list
    when {
        err -> DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        l == null -> DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        l.isEmpty() -> DevCard {
            Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S)
            Text(
                "เพิ่มเขตหลังคุยกับผู้สมัครทางไลน์ แล้วใช้ปุ่ม \"เชื่อมหลังบ้าน\" เพื่อผูกหลายเขตเข้าหลังบ้านเดียวกัน",
                fontSize = 11.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        else -> l.forEach { f -> DevZoneCard(f) }
    }
}

@Composable
private fun DevZoneCard(f: DevFranchise) {
    val fee = if (f.monthlyFee > 0) " · ค่าแฟรนไชส์/เดือน ฿" + String.format("%,.0f", f.monthlyFee) else ""
    val sub = (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + f.shopCount + " ร้าน" + fee
    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 11.5.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            when (f.status) {
                "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                "suspended" -> DevBadge("ระงับ", DevC.RL, DevC.R)
                else -> DevBadge(if (f.status.isEmpty()) "-" else f.status, Color(0xFFF1F5F9), DevC.S)
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            if (f.zonePoints >= 3) DevBadge("มีเขตแล้ว", DevC.GnL, DevC.Gn)
            else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
        }
    }
}

@Composable
fun DevBadge(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
KT_EOF
echo "== ตรวจผล =="
for f in data/DevRepo.kt ui/DevLogin.kt ui/DevNav.kt ui/DevZones.kt; do printf "%s  %s บรรทัด\n" "$f" "$(wc -l < "$D/$f")"; done
grep -c "LayoutDirection.Rtl" "$D/ui/DevNav.kt" | sed "s/^/DevNav: จำนวน Rtl = /"
grep -c "DevLoginScreen" "$D/ui/DevNav.kt" | sed "s/^/DevNav: จำนวน DevLoginScreen = /"
git status --short
