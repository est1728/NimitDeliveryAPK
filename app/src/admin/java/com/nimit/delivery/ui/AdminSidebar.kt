package com.nimit.delivery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nimit.delivery.data.AdminSession

private const val SIDE_WEB_BASE = "https://nimitdelivery.vercel.app/"

/** เปิดหน้าเว็บของระบบ (ใช้ชั่วคราวกับหน้าที่ยังไม่ได้ทำในแอป) */
fun adminOpenWeb(ctx: Context, path: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIDE_WEB_BASE + path))) } catch (e: Exception) {
        Toast.makeText(ctx, "เปิดลิงก์ไม่ได้", Toast.LENGTH_SHORT).show()
    }
}

private fun sidePermOk(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun AdminSidebar(
    visible: Boolean,
    s: AdminSession,
    onClose: () -> Unit,
    onHistory: () -> Unit,
    onAds: () -> Unit,
    onFlash: () -> Unit,
    onCoupon: () -> Unit,
    onCategories: () -> Unit,
    onWallet: () -> Unit,
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current
    BackHandler(enabled = visible) { onClose() }
    var on by remember(visible) { mutableStateOf(AdminAlertService.enabled(ctx) && sidePermOk(ctx)) }

    val enable = {
        AdminAlertService.setEnabled(ctx, true)
        AdminAlertService.ensureChannels(ctx)
        AdminAlertService.start(ctx)
        AdminFcm.register(ctx, s.franchiseId)
        on = true
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) enable() else {
            on = false
            Toast.makeText(ctx, "ยังไม่ได้รับสิทธิ์แจ้งเตือน เปิดได้ที่ตั้งค่าของเครื่อง", Toast.LENGTH_LONG).show()
        }
    }
    val toggle = {
        if (on) {
            AdminAlertService.setEnabled(ctx, false)
            AdminAlertService.stop(ctx)
            AdminFcm.unregister(ctx, s.franchiseId)
            on = false
        } else if (!sidePermOk(ctx)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enable()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                Modifier.width(280.dp).fillMaxHeight().background(Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                // หัว
                Column(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(AdminIcAccount, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Text("เมนูจัดการ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Nimit Delivery Admin", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
                }
                // เมนู
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    SideItem("ประวัติระบบ", "ดูและจัดการข้อมูลย้อนหลัง", true, onHistory)
                    SideItem("จัดการโฆษณา", "แบนเนอร์/วิดีโอโฆษณาในแอป", true, onAds)
                    SideItem("ดีลลับนับถอยหลัง", "ตั้งเวลาลดราคาสินค้าแบบ Flash Sale", true, onFlash)
                    SideItem("จัดการคูปอง", "คูปองส่วนลด/ส่งฟรี ให้ลูกค้าเก็บและใช้ตอนชำระเงิน", true, onCoupon)
                    SideItem("จัดการปุ่มหมวดหมู่", "ปุ่มหน้าร้าน + ติดแท็กเมนู", true, onCategories)
                    SideItem("กระเป๋ากลาง", "ยอดคงเหลือ เติมเงิน ประวัติ", true, onWallet)
                    // เปิดแจ้งเตือน (สวิตช์)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("เปิดแจ้งเตือน", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                            Text(
                                if (on) "เปิดอยู่ จะมีเสียงเตือนเมื่อมีออเดอร์ใหม่" else "กดสวิตช์เพื่อขอสิทธิ์แจ้งเตือน",
                                fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = on, onCheckedChange = { toggle() },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF16A34A), checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFCBD5E1), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFF0F2F5))
                    SideItem("ออกจากระบบ", (if (s.name.isNotEmpty()) s.name + " · " else "") + s.phone, false, onLogout)
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SideItem(title: String, sub: String, arrow: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
            }
            if (arrow) Icon(AdminIcArrowRight, null, tint = C.Subtext, modifier = Modifier.size(16.dp))
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
    }
}

/** หน้าเปล่าของเมนูที่ยังไม่ได้ทำ (โฆษณา/ดีลลับ/คูปอง) */
@Composable
fun AdminSidePlaceholder(page: String, onBack: () -> Unit) {
    val title: String = when (page) {
        "ads" -> "จัดการโฆษณา"
        "flash" -> "ดีลลับนับถอยหลัง"
        "coupon" -> "จัดการคูปอง"
        else -> "เมนู"
    }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("หน้านี้กำลังพัฒนา", fontSize = 14.sp, color = C.Subtext)
        }
    }
}
