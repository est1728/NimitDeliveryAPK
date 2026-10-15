#!/bin/bash
# ปุ่มผูกเขต + เส้นเชื่อม + เลิกผูก + บันทึกประวัติ (เขียนทับ 2 ไฟล์ของแอป dev, รันซ้ำได้)
set -e
D=app/src/dev/java/com/nimit/delivery
[ -d "$D/ui" ] && [ -f "$D/data/DevRepo.kt" ] || { echo "หยุด: ไม่เจอไฟล์ dev ชุดก่อนหน้า (รันใน ~/nimit-customer และวาง dev-apply.sh แล้วหรือยัง)"; exit 1; }
cat > "$D/data/DevRepo.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class DevFranchise(
    val id: String,
    val name: String,
    val province: String,
    val ownerEmail: String,
    val monthlyFee: Double,
    val status: String,
    val zonePoints: Int,
    val shopCount: Int,
    val groupId: String
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
        val list = fs.map { d ->
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
                shopCount = shopCount,
                groupId = d.getString("backendGroupId") ?: ""
            )
        }
        // เขตที่ผูกกันให้เรียงติดกัน (ใช้ชื่อแรกของกลุ่มเป็นตัวจัดลำดับ)
        val minName = list.filter { it.groupId.isNotEmpty() }
            .groupBy { it.groupId }
            .mapValues { e -> e.value.minOf { it.name } }
        return list.sortedWith(
            compareBy<DevFranchise>({ if (it.groupId.isEmpty()) it.name else (minName[it.groupId] ?: it.name) }, { it.name })
        )
    }

    /** ผูกเขต: เขตที่เลือก (และกลุ่มเดิมของเขตเหล่านั้น) ใช้ backendGroupId เดียวกัน + บันทึกประวัติใน franchiseAudit (เขียนครั้งเดียวทั้งก้อน) */
    suspend fun linkZones(ids: List<String>, all: List<DevFranchise>): String? {
        if (ids.size < 2) return "เลือกอย่างน้อย 2 เขต"
        val email = currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val picked = all.filter { it.id in ids }
        val oldGroups = picked.map { it.groupId }.filter { it.isNotEmpty() }.toSet()
        val gid = oldGroups.firstOrNull() ?: ("bg_" + UUID.randomUUID().toString().take(8))
        val members = all.filter { it.id in ids || (it.groupId.isNotEmpty() && it.groupId in oldGroups) }
        return try {
            val b = db.batch()
            members.forEach { m -> b.update(db.collection("franchises").document(m.id), "backendGroupId", gid) }
            b.set(
                db.collection("franchiseAudit").document(),
                mapOf(
                    "action" to "link_zones",
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "groupId" to gid,
                    "zoneIds" to members.map { it.id },
                    "zoneNames" to members.map { it.name },
                    "before" to members.associate { it.id to it.groupId },
                    "after" to members.associate { it.id to gid }
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            "บันทึกไม่สำเร็จ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์เขียนข้อมูล)"
        }
    }

    /** เลิกผูกเขตเดียว: ถ้าในกลุ่มเหลือเขตเดียวก็ล้างกลุ่มนั้นด้วย + บันทึกประวัติ */
    suspend fun unlinkZone(f: DevFranchise, all: List<DevFranchise>): String? {
        if (f.groupId.isEmpty()) return "เขตนี้ยังไม่ได้ผูก"
        val email = currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val rest = all.filter { it.groupId == f.groupId && it.id != f.id }
        val clear = if (rest.size == 1) listOf(f) + rest else listOf(f)
        return try {
            val b = db.batch()
            clear.forEach { m -> b.update(db.collection("franchises").document(m.id), "backendGroupId", FieldValue.delete()) }
            b.set(
                db.collection("franchiseAudit").document(),
                mapOf(
                    "action" to "unlink_zone",
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "groupId" to f.groupId,
                    "zoneIds" to clear.map { it.id },
                    "zoneNames" to clear.map { it.name },
                    "before" to clear.associate { it.id to f.groupId },
                    "after" to clear.associate { it.id to "" }
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            "บันทึกไม่สำเร็จ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์เขียนข้อมูล)"
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val devGroupColors = listOf(
    Color(0xFF0C4AA6), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFF7C3AED), Color(0xFFDB2777)
)

private fun devGroupColor(g: String): Color = devGroupColors[Math.abs(g.hashCode() % devGroupColors.size)]

// หน้าแรก: การ์ดเขตแฟรนไชส์ (อ่านจาก franchises + shops) พร้อมปุ่มผูกเขต / เลิกผูก
@Composable
fun DevZonesScreen() {
    var list by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var showLink by remember { mutableStateOf(false) }
    var unlinkTarget by remember { mutableStateOf<DevFranchise?>(null) }
    var unlinkBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var noticeErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
    val l = list
    val canLink = l != null && l.size >= 2

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (l == null) "เขตแฟรนไชส์ทั้งหมด" else "เขตแฟรนไชส์ทั้งหมด (" + l.size + ")",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .padding(end = 8.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (canLink) DevC.PL else Color(0xFFF1F5F9))
                .clickable(enabled = canLink) { showLink = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("ผูกเขต", color = if (canLink) DevC.P else DevC.S, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(20.dp)).background(DevC.P).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ เพิ่มเขต", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    if (notice.isNotEmpty()) {
        DevCard {
            Text(notice, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (noticeErr) DevC.R else DevC.Gn)
        }
    }
    when {
        err -> DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        l == null -> DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        l.isEmpty() -> DevCard {
            Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S)
            Text(
                "เพิ่มเขตหลังคุยกับผู้สมัครทางไลน์ แล้วใช้ปุ่ม \"ผูกเขต\" เพื่อให้หลายเขตใช้หลังบ้านแอดมินเดียวกัน",
                fontSize = 11.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        else -> l.forEachIndexed { i, f ->
            val prevSame = f.groupId.isNotEmpty() && i > 0 && l[i - 1].groupId == f.groupId
            val nextSame = f.groupId.isNotEmpty() && i < l.size - 1 && l[i + 1].groupId == f.groupId
            DevZoneRow(f, l, prevSame, nextSame) { unlinkTarget = f }
        }
    }

    if (showLink && l != null) {
        DevLinkDialog(
            all = l,
            onDismiss = { showLink = false },
            onDone = {
                showLink = false
                notice = "ผูกเขตเรียบร้อย และบันทึกประวัติแล้ว"
                noticeErr = false
                tick++
            }
        )
    }
    val t = unlinkTarget
    if (t != null) {
        AlertDialog(
            onDismissRequest = { if (!unlinkBusy) unlinkTarget = null },
            containerColor = Color.White,
            title = { Text("เลิกผูก " + t.name + "?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "เขตนี้จะไม่ใช้หลังบ้านแอดมินร่วมกับเขตอื่นอีก ร้านและออเดอร์เดิมไม่ถูกลบ",
                    fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !unlinkBusy,
                    onClick = {
                        unlinkBusy = true
                        scope.launch {
                            val r = DevRepo.unlinkZone(t, l ?: emptyList())
                            unlinkBusy = false
                            unlinkTarget = null
                            if (r == null) {
                                notice = "เลิกผูก " + t.name + " แล้ว และบันทึกประวัติแล้ว"
                                noticeErr = false
                                tick++
                            } else {
                                notice = r
                                noticeErr = true
                            }
                        }
                    }
                ) { Text(if (unlinkBusy) "กำลังบันทึก..." else "เลิกผูก", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !unlinkBusy, onClick = { unlinkTarget = null }) { Text("ยกเลิก", color = DevC.S) }
            }
        )
    }
}

// แถวการ์ด + เส้นเชื่อมด้านหน้า (จุดที่การ์ดของเขตที่ผูกกัน เส้นต่อลงไปหาการ์ดถัดไปของกลุ่มเดียวกัน)
@Composable
private fun DevZoneRow(
    f: DevFranchise,
    all: List<DevFranchise>,
    prevSame: Boolean,
    nextSame: Boolean,
    onUnlink: () -> Unit
) {
    val col = if (f.groupId.isEmpty()) Color.Transparent else devGroupColor(f.groupId)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(18.dp)
                .fillMaxHeight()
                .drawBehind {
                    if (f.groupId.isNotEmpty()) {
                        val cx = 6.dp.toPx()
                        val cy = (size.height - 12.dp.toPx()) / 2f
                        val sw = 3.dp.toPx()
                        if (prevSame) drawLine(col, Offset(cx, 0f), Offset(cx, cy), strokeWidth = sw)
                        if (nextSame) drawLine(col, Offset(cx, cy), Offset(cx, size.height), strokeWidth = sw)
                        drawCircle(col, radius = 5.dp.toPx(), center = Offset(cx, cy))
                    }
                }
        )
        Column(Modifier.weight(1f)) { DevZoneCard(f, all, col, onUnlink) }
    }
}

@Composable
private fun DevZoneCard(f: DevFranchise, all: List<DevFranchise>, col: Color, onUnlink: () -> Unit) {
    val fee = if (f.monthlyFee > 0) " · ค่าแฟรนไชส์/เดือน ฿" + String.format("%,.0f", f.monthlyFee) else ""
    val sub = (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + f.shopCount + " ร้าน" + fee
    val grouped = f.groupId.isNotEmpty()
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
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (f.zonePoints >= 3) DevBadge("มีเขตแล้ว", DevC.GnL, DevC.Gn)
            else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
            if (grouped) DevBadge("หลังบ้านร่วม", col.copy(alpha = 0.12f), col)
            Box(Modifier.weight(1f))
            if (grouped) {
                Text(
                    "เลิกผูก", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUnlink() }.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        if (grouped) {
            val others = all.filter { it.groupId == f.groupId && it.id != f.id }.joinToString(", ") { it.name }
            if (others.isNotEmpty()) {
                Text("ใช้หลังบ้านร่วมกับ: " + others, fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DevLinkDialog(all: List<DevFranchise>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("ผูกเขต", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "เลือกเขตที่จะให้ใช้หลังบ้านแอดมินร่วมกัน (อย่างน้อย 2 เขต) ร้านและข้อมูลอื่นยังแยกตามเขตเหมือนเดิม ถ้าเลือกเขตที่ผูกอยู่แล้ว กลุ่มเดิมจะรวมเข้าด้วย",
                    fontSize = 12.sp, color = DevC.S, lineHeight = 18.sp
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
                    all.forEach { f ->
                        val on = f.id in sel
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { sel = if (on) sel - f.id else sel + f.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DevC.P))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                                if (f.groupId.isNotEmpty()) {
                                    Text("ผูกอยู่แล้ว", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = devGroupColor(f.groupId))
                                }
                            }
                        }
                    }
                }
                if (err.isNotEmpty()) {
                    Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && sel.size >= 2,
                onClick = {
                    busy = true
                    err = ""
                    scope.launch {
                        val r = DevRepo.linkZones(sel.toList(), all)
                        busy = false
                        if (r == null) onDone() else err = r
                    }
                }
            ) { Text(if (busy) "กำลังบันทึก..." else "ผูก (" + sel.size + ")", color = DevC.P, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก", color = DevC.S) }
        }
    )
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
for f in data/DevRepo.kt ui/DevZones.kt; do printf "%s  %s บรรทัด\n" "$f" "$(wc -l < "$D/$f")"; done
grep -c "linkZones" "$D/data/DevRepo.kt" | sed "s/^/DevRepo: linkZones = /"
grep -c "DevLinkDialog" "$D/ui/DevZones.kt" | sed "s/^/DevZones: DevLinkDialog = /"
git status --short
