#!/bin/bash
# สร้าง flavor admin: หน้าล็อกอิน (เบอร์+รหัส), เข้าแล้วไม่ตรวจซ้ำ, ตัวอัปเดต, ไอคอน, workflow
# รันซ้ำได้ ไม่ใส่รหัสผ่าน/keystore
set -u
die() { echo "หยุด: $1"; exit 1; }

[ -f app/build.gradle.kts ] || die "ไม่เจอ app/build.gradle.kts (ต้องรันในโฟลเดอร์ ~/nimit-customer)"
grep -q 'create("admin")' app/build.gradle.kts || die "build.gradle.kts ไม่มี create(\"admin\")"
DU=app/src/dev/java/com/nimit/delivery/ui/DevUpdate.kt
[ -f "$DU" ] || die "ไม่เจอ $DU"
WF=.github/workflows/build-apk.yml
[ -f "$WF" ] || die "ไม่เจอ $WF"

A=app/src/admin
J=$A/java/com/nimit/delivery
mkdir -p $J/data $J/ui $A/res/values $A/res/drawable $A/res/mipmap-anydpi $A/res/mipmap-anydpi-v26

# ---------- strings ----------
cat > $A/res/values/strings.xml <<'KT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Nimit Admin</string>
</resources>
KT_EOF

# ---------- ไอคอน ----------
cat > $A/res/drawable/admin_ic_bg.xml <<'KT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#0C4AA6" android:pathData="M0,0h108v108h-108z" />
</vector>
KT_EOF
cat > $A/res/drawable/admin_ic_fg.xml <<'KT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M54,54m-24,0a24,24 0,1,1 48,0a24,24 0,1,1 -48,0" />
    <path android:fillColor="#0C4AA6" android:pathData="M54,54m-12,0a12,12 0,1,1 24,0a12,12 0,1,1 -24,0" />
</vector>
KT_EOF
for n in ic_launcher ic_launcher_round; do
cat > $A/res/mipmap-anydpi-v26/$n.xml <<'KT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/admin_ic_bg" />
    <foreground android:drawable="@drawable/admin_ic_fg" />
</adaptive-icon>
KT_EOF
cat > $A/res/mipmap-anydpi/$n.xml <<'KT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/admin_ic_bg" />
    <item android:drawable="@drawable/admin_ic_fg" />
</layer-list>
KT_EOF
done

# ---------- MainActivity ----------
cat > $J/MainActivity.kt <<'KT_EOF'
package com.nimit.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nimit.delivery.ui.AdminNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { AdminNav() }
    }
}
KT_EOF

# ---------- ข้อมูล: เซสชัน + ล็อกอินเบอร์/รหัส ----------
cat > $J/data/AdminRepo.kt <<'KT_EOF'
package com.nimit.delivery.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AdminSession(val franchiseId: String, val name: String, val groupId: String, val phone: String)

/*
 * ล็อกอินแอดมินเขต: ตรวจเบอร์+รหัสครั้งเดียวตอนล็อกอิน แล้วเก็บเซสชันในเครื่อง
 * เปิดแอปครั้งต่อไปอ่านเซสชันนี้ตรงๆ ไม่ตรวจซ้ำ จนกว่าจะกดออกจากระบบ
 * หมายเหตุ: ตอนนี้ตรวจ PBKDF2 ในเครื่อง (pinHash อ่านได้จาก franchises) ขั้นถัดไปจะย้ายไปตรวจที่เซิร์ฟเวอร์
 */
object AdminRepo {
    private const val PREF = "nimit_admin"
    private const val BAD = "เบอร์หรือรหัสไม่ถูกต้อง"

    fun load(ctx: Context): AdminSession? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val id = p.getString("fid", null) ?: return null
        return AdminSession(id, p.getString("name", "") ?: "", p.getString("gid", id) ?: id, p.getString("phone", "") ?: "")
    }

    private fun save(ctx: Context, s: AdminSession) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("fid", s.franchiseId).putString("name", s.name)
            .putString("gid", s.groupId).putString("phone", s.phone).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // คืน null = สำเร็จ, ไม่ใช่ null = ข้อความผิดพลาด
    suspend fun login(ctx: Context, phone: String, pin: String): String? {
        val ph = phone.trim()
        if (ph.isEmpty() || pin.isEmpty()) return "กรุณากรอกเบอร์และรหัส"
        return try {
            val snap = FirebaseFirestore.getInstance().collection("franchises")
                .whereEqualTo("phone", ph).limit(1).get().await()
            val d = snap.documents.firstOrNull() ?: return BAD
            if (d.getString("status") == "suspended") return "เขตนี้ถูกระงับการใช้งาน"
            if (d.getString("pinAlg") != "pbkdf2-sha1-100000") return BAD
            val salt = d.getString("pinSalt") ?: return BAD
            val hash = d.getString("pinHash") ?: return BAD
            val ok = withContext(Dispatchers.Default) {
                val spec = PBEKeySpec(pin.toCharArray(), hexToBytes(salt), 100000, 256)
                val h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
                MessageDigest.isEqual(h, hexToBytes(hash))
            }
            if (!ok) return BAD
            val gid = d.getString("backendGroupId") ?: d.id
            save(ctx, AdminSession(d.id, d.getString("name") ?: "", gid, ph))
            null
        } catch (e: Exception) {
            "เข้าสู่ระบบไม่สำเร็จ: " + (e.message ?: "")
        }
    }
}
KT_EOF

# ---------- หน้าล็อกอิน ----------
cat > $J/ui/AdminLogin.kt <<'KT_EOF'
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
KT_EOF

# ---------- นำทางหลัก + หน้าแรกชั่วคราว ----------
cat > $J/ui/AdminNav.kt <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("จัดการระบบ", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { onLogout() }.padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("ออกจากระบบ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("หน้าออเดอร์อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
        }
    }
}
KT_EOF

# ---------- ตัวอัปเดต: ก๊อปจาก DevUpdate.kt แล้วเปลี่ยนชื่อ/แท็ก ----------
sed -e 's/DEV_/ADMIN_/g' \
    -e 's/\bdev\([A-Z]\)/admin\1/g' \
    -e 's/Dev\([A-Z]\)/Admin\1/g' \
    -e 's/dev-build-/admin-build-/g' \
    -e 's/ระบบผู้พัฒนา/ระบบแอดมิน/g' \
    "$DU" > $J/ui/AdminUpdate.kt
grep -q 'admin-build-' $J/ui/AdminUpdate.kt || die "สร้าง AdminUpdate.kt ไม่สำเร็จ (ไม่เจอ dev-build- ในต้นฉบับ)"
grep -q 'fun AdminUpdatePrompt' $J/ui/AdminUpdate.kt || die "AdminUpdate.kt ไม่มี AdminUpdatePrompt"

# ---------- workflow ----------
if grep -q 'assembleAdminRelease' "$WF"; then
  echo "workflow: มีขั้นตอน admin อยู่แล้ว ข้าม"
else
  LN=$(grep -n 'ไฟล์ AAB สำหรับอัปโหลด' "$WF" | head -1 | cut -d: -f1)
  [ -n "$LN" ] || die "workflow: ไม่เจอจุดยึด 'ไฟล์ AAB สำหรับอัปโหลด'"
  cat > .admin-wf-block.tmp <<'WF_EOF'
      - run: gradle assembleAdminRelease --no-daemon
      - name: Publish admin
        env:
          GH_TOKEN: ${{ secrets.RELEASES_TOKEN }}
        run: |
          cp app/build/outputs/apk/admin/release/*.apk NimitAdmin-build-${{ github.run_number }}.apk
          gh release create "admin-build-${{ github.run_number }}" NimitAdmin-build-${{ github.run_number }}.apk --repo est1728/NimitDelivery-releases --title "Admin ${{ github.run_number }}" --notes "admin test" --prerelease

WF_EOF
  head -n $((LN-1)) "$WF" > .admin-wf-new.tmp
  cat .admin-wf-block.tmp >> .admin-wf-new.tmp
  tail -n +$LN "$WF" >> .admin-wf-new.tmp
  mv .admin-wf-new.tmp "$WF"
  rm -f .admin-wf-block.tmp
  echo "workflow: เพิ่มขั้นตอน admin แล้ว"
fi

# ---------- ผลตรวจ ----------
echo "---- ผลตรวจ ----"
for f in $(find $A -name '*.kt' | sort); do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -c 'assembleAdminRelease' "$WF" | sed 's/^/assembleAdminRelease ใน workflow: /'
grep -n 'admin-build-' $J/ui/AdminUpdate.kt | head -3
find $A -type f | sort
echo "เสร็จ"
