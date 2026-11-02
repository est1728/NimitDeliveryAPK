#!/bin/bash
# Sidebar ของแอปแอดมิน (ถอดจาก sidebarPanel ใน admin.html): กดปุ่มเมนูกลม (มุมบนขวาของหน้าออเดอร์) แล้วเลื่อนออกจากขวา
# หัว "เมนูจัดการ / Nimit Delivery Admin" และปุ่มเหมือนเว็บ: ประวัติระบบ, จัดการโฆษณา, ดีลลับนับถอยหลัง, จัดการคูปอง,
# จัดการปุ่มหมวดหมู่, กระเป๋ากลาง, เปิดแจ้งเตือน (สวิตช์), ออกจากระบบ  (ไม่มี "ติดตั้งแอป" เพราะเป็นของเว็บ และ "จัดการแอดมิน" เฉพาะ owner ของเว็บ)
# ตอนนี้: ประวัติระบบ/หมวดหมู่/กระเป๋ากลาง เปิดหน้าเว็บ  โฆษณา/ดีลลับ/คูปอง เป็นหน้าเปล่ารอทำต่อ  สวิตช์แจ้งเตือนและออกจากระบบใช้งานได้จริง  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
N=$J/ui/AdminNav.kt
IC=$J/ui/AdminIcons.kt
AL=$J/ui/AdminAlert.kt
FC=$J/ui/AdminFcm.kt
for f in $N $IC $AL $FC; do [ -f $f ] || die "ไม่เจอ $f (ต้องรัน admin-notify-v1.sh ก่อน)"; done
grep -q 'object AdminAlert' $AL || die "AdminAlert.kt ไม่ใช่เวอร์ชันที่คาด"
grep -q 'private fun adminVec' $IC || die "AdminIcons.kt ไม่ใช่เวอร์ชันที่คาด"

# ---------- ไอคอน ----------
if grep -q 'AdminIcAccount' $IC; then
  echo "ไอคอน: มีแล้ว ข้าม"
else
  cat >> $IC <<'KT_EOF'

val AdminIcAccount: ImageVector = adminVec("a_account", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z")
val AdminIcArrowRight: ImageVector = adminVec("a_arrow_r", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")
KT_EOF
  echo "ไอคอน: เพิ่มแล้ว"
fi

# ---------- สวิตช์แจ้งเตือน: เปิด/ปิดได้จริง (เก็บค่าในเครื่อง) ----------
if grep -q 'fun setEnabled' $AL; then
  echo "AdminAlert: แก้แล้ว ข้าม"
else
  grep -q '^        fun stop(ctx: Context) {$' $AL || die "AdminAlert: ไม่เจอจุดยึด stop"
  grep -q '^        fun start(ctx: Context, ring: Boolean = false, testId: String = "", title: String = "", body: String = "") {$' $AL || die "AdminAlert: ไม่เจอจุดยึด start"
  sed -i 's|^        fun stop(ctx: Context) {$|        fun enabled(ctx: Context): Boolean = ctx.applicationContext.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE).getBoolean("alertEnabled", true)\n\n        fun setEnabled(ctx: Context, v: Boolean) {\n            ctx.applicationContext.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE).edit().putBoolean("alertEnabled", v).apply()\n        }\n\n        fun stop(ctx: Context) {|' $AL
  sed -i 's|^        fun start(ctx: Context, ring: Boolean = false, testId: String = "", title: String = "", body: String = "") {$|&\n            if (!enabled(ctx)) return|' $AL
  grep -q 'fun setEnabled' $AL || die "แก้ AdminAlert ไม่สำเร็จ"
  echo "AdminAlert: แก้แล้ว"
fi
if grep -q 'AdminAlertService.enabled(ctx)' $FC; then
  echo "AdminFcm: แก้แล้ว ข้าม"
else
  grep -q '^    fun register(ctx: Context, franchiseId: String) {$' $FC || die "AdminFcm: ไม่เจอจุดยึด register"
  sed -i 's|^    fun register(ctx: Context, franchiseId: String) {$|&\n        if (!AdminAlertService.enabled(ctx)) return|' $FC
  grep -q 'AdminAlertService.enabled(ctx)' $FC || die "แก้ AdminFcm ไม่สำเร็จ"
  echo "AdminFcm: แก้แล้ว"
fi

# ---------- Sidebar + หน้าเปล่า ----------
cat > $J/ui/AdminSidebar.kt <<'KT_EOF'
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
KT_EOF
echo "AdminSidebar.kt: เขียนแล้ว"

# ---------- ต่อเข้ากับหน้าหลัก ----------
if grep -q 'AdminSidebar(' $N; then
  echo "AdminNav: ต่อแล้ว ข้าม"
else
  grep -q '^    var showMenu by remember { mutableStateOf(false) }$' $N || die "AdminNav: ไม่เจอจุดยึด showMenu"
  grep -q 'onMenu = { showMenu = true },' $N || die "AdminNav: ไม่เจอจุดยึด onMenu"
  grep -q '^    } else if (eid != null) {$' $N || die "AdminNav: ไม่เจอจุดยึด eid"
  grep -q '^    if (showBattery) {$' $N || die "AdminNav: ไม่เจอจุดยึด showBattery"
  sed -i 's|^    var showMenu by remember { mutableStateOf(false) }$|    var showMenu by remember { mutableStateOf(false) }\n    var showSidebar by remember { mutableStateOf(false) }\n    var sidePage by remember { mutableStateOf("") }|' $N
  sed -i 's|onMenu = { showMenu = true },|onMenu = { showSidebar = true },|' $N
  sed -i 's|^    } else if (eid != null) {$|    } else if (sidePage.isNotEmpty()) {\n        BackHandler { sidePage = "" }\n        Box(Modifier.fillMaxSize().statusBarsPadding()) { AdminSidePlaceholder(sidePage, onBack = { sidePage = "" }) }\n    } else if (eid != null) {|' $N
  cat > .admin-side.tmp <<'KT_EOF'
    AdminSidebar(
        visible = showSidebar,
        s = s,
        onClose = { showSidebar = false },
        onHistory = { showSidebar = false; adminOpenWeb(ctx, "history.html") },
        onAds = { showSidebar = false; sidePage = "ads" },
        onFlash = { showSidebar = false; sidePage = "flash" },
        onCoupon = { showSidebar = false; sidePage = "coupon" },
        onCategories = { showSidebar = false; adminOpenWeb(ctx, "categories-admin.html") },
        onWallet = { showSidebar = false; adminOpenWeb(ctx, "wallet.html") },
        onLogout = { showSidebar = false; onLogout() }
    )

KT_EOF
  LN=$(grep -n '^    if (showBattery) {$' $N | head -1 | cut -d: -f1)
  head -n $((LN-1)) $N > .admin-side-new.tmp
  cat .admin-side.tmp >> .admin-side-new.tmp
  tail -n +$LN $N >> .admin-side-new.tmp
  mv .admin-side-new.tmp $N
  rm -f .admin-side.tmp
  grep -q 'AdminSidebar(' $N && grep -q 'onMenu = { showSidebar = true },' $N || die "ต่อ AdminNav ไม่สำเร็จ"
  echo "AdminNav: ต่อแล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $J/ui/AdminSidebar.kt $IC $AL $FC $N; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
echo "เสร็จ"
