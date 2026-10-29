package com.nimit.delivery.ui

import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        AdminHome(s, onLogout = { AdminAlertService.stop(ctx); AdminFcm.unregister(ctx, s.franchiseId); AdminRepo.clear(ctx); session = null })
    }
    AdminUpdatePrompt()
}

private val AdminMenu: List<String> = listOf("ออเดอร์", "ประวัติ", "ร้านค้า", "ไรเดอร์", "ลูกค้า", "ตั้งค่า")
private val AdminMenuIcons: List<ImageVector> = listOf(AdminIcOrders, AdminIcHistory, AdminIcShops, AdminIcRiders, AdminIcCustomers, AdminIcSettings)

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var chatId by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    val store = remember(s.franchiseId) { AdminOrdersStore(s) }
    var showBattery by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        AdminAlertService.start(ctx)
        AdminFcm.register(ctx, s.franchiseId)
    }
    LaunchedEffect(s.franchiseId) {
        AdminAlertService.ensureChannels(ctx)
        val need: Boolean = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (need) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AdminAlertService.start(ctx)
            AdminFcm.register(ctx, s.franchiseId)
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val asked: Boolean = ctx.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE).getBoolean("batteryAsked", false)
        if (!asked && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) showBattery = true
    }
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    val cid = chatId
    val oid = openId
    val eid = editId
    if (creating) {
        BackHandler { creating = false }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminCreateOrderScreen(store, onBack = { creating = false }, onCreated = { creating = false })
        }
    } else if (eid != null) {
        BackHandler { editId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminCreateOrderScreen(store, onBack = { editId = null }, onCreated = { editId = null }, editOrderId = eid)
        }
    } else if (cid != null) {
        BackHandler { chatId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) { ChatScreen(cid, "admin", { chatId = null }) }
    } else if (oid != null) {
        BackHandler { openId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid }, onEditMenu = { editId = oid })
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Gray)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 0) {
                    AdminOrdersPage(
                        s, store,
                        onOpen = { openId = it },
                        onMenu = { showMenu = true },
                        onCreate = { creating = true }
                    )
                } else {
                    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                        if (tab == 5) AdminSettingsPanel() else if (tab == 1) AdminHistoryPage(store, onOpen = { openId = it }, onRecalled = { tab = 0 }) else if (tab == 2) AdminShopsPage(store) else Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F2F5))
            Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                for (i in AdminMenu.indices) {
                    val on = tab == i
                    val col = if (on) C.Primary else C.Subtext
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(top = 8.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(AdminMenuIcons[i], null, tint = col, modifier = Modifier.size(24.dp))
                        Text(AdminMenu[i], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }

    if (showBattery) {
        val markAsked = { ctx.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE).edit().putBoolean("batteryAsked", true).apply() }
        AlertDialog(
            onDismissRequest = { markAsked(); showBattery = false },
            title = { Text("ให้แอปทำงานเบื้องหลัง") },
            text = { Text("เพื่อให้เสียงแจ้งเตือนออเดอร์ดังแม้ปิดจอหรือปิดแอป ควรอนุญาตให้แอปไม่ถูกจำกัดแบตเตอรี่") },
            confirmButton = {
                TextButton(onClick = {
                    markAsked()
                    showBattery = false
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + ctx.packageName)))
                    } catch (e: Exception) { }
                }) { Text("ตั้งค่า") }
            },
            dismissButton = { TextButton(onClick = { markAsked(); showBattery = false }) { Text("ไว้ทีหลัง") } }
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("เมนู") },
            text = {
                Column {
                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(s.phone, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false; onLogout() }) { Text("ออกจากระบบ", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text("ปิด") } }
        )
    }
}
