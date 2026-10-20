package com.nimit.delivery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminRepo
import com.nimit.delivery.data.AdminSession

// เริ่มต้นตัดสินจากเซสชันที่เก็บไว้ทันที (อ่านในเครื่อง ไม่มีหน้า "ตรวจสอบสิทธิ์")
// มีเซสชัน = เข้าหน้าแอดมินเลย / ไม่มี = หน้าล็อกอิน
@Composable
fun AdminNav() {
    val ctx = LocalContext.current
    var session by remember { mutableStateOf(AdminRepo.load(ctx)) }
    val s = session
    if (s == null) {
        AdminLoginScreen(onDone = { session = AdminRepo.load(ctx) })
    } else {
        AdminHome(s, onLogout = { AdminRepo.clear(ctx); session = null })
    }
    AdminUpdatePrompt()
}

private val AdminMenu: List<String> = listOf("ออเดอร์", "ประวัติ", "ร้านค้า", "ไรเดอร์", "ลูกค้า", "ตั้งค่า")

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var chatId by remember { mutableStateOf<String?>(null) }
    val store = remember(s.franchiseId) { AdminOrdersStore(s) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    val cid = chatId
    val oid = openId
    if (cid != null) {
        BackHandler { chatId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) { ChatScreen(cid, "admin", { chatId = null }) }
    } else if (oid != null) {
        BackHandler { openId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid })
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Gray)) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                if (tab == 0) {
                    AdminOrdersPage(s, store, onOpen = { openId = it })
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
                        }
                        if (tab == 5) {
                            Box(
                                Modifier.padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp)).background(C.Primary)
                                    .clickable { onLogout() }.padding(horizontal = 20.dp, vertical = 10.dp)
                            ) { Text("ออกจากระบบ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F2F5))
            Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                for (i in AdminMenu.indices) {
                    val on = tab == i
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) C.Primary else Color.Transparent))
                        Text(
                            AdminMenu[i], fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (on) C.Primary else C.Subtext, modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
