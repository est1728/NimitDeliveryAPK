#!/bin/bash
# แจ้งเตือนฉบับสมบูรณ์: ส่งทันที/ตั้งเวลาครั้งเดียว/ทุกวัน/บันทึกไว้กดส่งเอง + รายการที่ตั้งไว้ (รวมแก้ tokens[] และทดสอบ new_order แล้ว) เขียนทับ 2 ไฟล์ รันซ้ำได้
set -e
D=app/src/dev/java/com/nimit/delivery
[ -f "$D/ui/DevNotify.kt" ] && [ -f "$D/data/DevNotify.kt" ] || { echo "หยุด: ยังไม่ได้รัน dev-notify.sh (ไม่เจอไฟล์แจ้งเตือนของ dev) หรือไม่ได้อยู่ใน ~/nimit-customer"; exit 1; }
cat > "$D/data/DevNotify.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DevBroadcast(
    val id: String,
    val title: String,
    val body: String,
    val role: String,
    val scope: String,
    val zoneId: String,
    val zoneName: String,
    val person: String,
    val repeat: String,
    val timeOfDay: String,
    val nextRunMillis: Long,
    val status: String,
    val lastRunMillis: Long,
    val lastResult: String
)

// ส่งแจ้งเตือนผ่านเซิร์ฟเวอร์เดิม: ใช้รูปแบบเดียวกับหน้าทดสอบเดิม (tokens, title, body, url)
object DevNotify {
    private const val ENDPOINT = "https://nimitdelivery.vercel.app/api/send-order-notification"

    private fun addToken(set: MutableSet<String>, t: String?) {
        if (t != null && t.isNotBlank()) set.add(t)
    }

    // เอกสาร fcmTokens ของร้านเก็บ tokens[] (ทุกเครื่อง) + token (ล่าสุด): เก็บทั้งสองอย่าง
    private fun addDoc(set: MutableSet<String>, d: DocumentSnapshot) {
        addToken(set, d.getString("token"))
        (d.get("tokens") as? List<*>)?.forEach { addToken(set, it as? String) }
    }

    /** หา token ผู้รับ: role = shop|admin|rider|customer, scope = all|zone|one. คืน (tokens, ข้อความผิดพลาด) */
    suspend fun resolve(role: String, scope: String, zoneId: String, person: String): Pair<List<String>, String?> {
        try {
            val db = FirebaseFirestore.getInstance()
            val tokens = LinkedHashSet<String>()
            when (scope) {
                "all" -> {
                    db.collection("fcmTokens").whereEqualTo("role", role).get().await().documents
                        .forEach { d -> addDoc(tokens, d) }
                }
                "zone" -> {
                    val ids = LinkedHashSet<String>()
                    db.collection("shops").whereArrayContains("franchiseIds", zoneId).get().await().documents
                        .forEach { ids.add(it.id) }
                    db.collection("shops").whereEqualTo("franchiseId", zoneId).get().await().documents
                        .forEach { ids.add(it.id) }
                    if (ids.isEmpty()) return Pair(emptyList(), "ไม่มีร้านในเขตนี้")
                    ids.toList().chunked(10).forEach { chunk ->
                        db.collection("fcmTokens").whereIn("shopId", chunk).get().await().documents
                            .filter { it.getString("role") == "shop" }
                            .forEach { d -> addDoc(tokens, d) }
                    }
                }
                else -> {
                    // เบอร์ใช้ระบุตัวตนเท่านั้น ไม่ได้ส่งผ่านเบอร์
                    val p = person.trim()
                    val keys = LinkedHashSet<String>()
                    if (role == "shop") {
                        db.collection("shops").whereEqualTo("phone", p).get().await().documents
                            .forEach { keys.add(it.id + "_shop") }
                    }
                    keys.add(p + "_" + role)
                    for (k in keys) {
                        val d = db.collection("fcmTokens").document(k).get().await()
                        addDoc(tokens, d)
                    }
                }
            }
            if (tokens.isEmpty()) {
                return Pair(emptyList(), "ไม่พบ token ของผู้รับ (ยังไม่มีใครเปิดแจ้งเตือนสำเร็จ หรือข้อมูลที่ใส่ไม่ตรง)")
            }
            return Pair(tokens.toList(), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Pair(emptyList(), "ค้นหาผู้รับไม่สำเร็จ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)")
        }
    }

    /** ส่งจริงไปที่เซิร์ฟเวอร์ (แบ่งก้อนละ 500 token) คืน (สำเร็จทุกก้อนไหม, ข้อความผลลัพธ์ดิบ) */
    suspend fun send(
        tokens: List<String>,
        title: String,
        body: String,
        extra: Map<String, String> = emptyMap()
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            var allOk = true
            val sb = StringBuilder()
            try {
                tokens.chunked(500).forEachIndexed { i, chunk ->
                    val c = URL(ENDPOINT).openConnection() as HttpURLConnection
                    try {
                        c.requestMethod = "POST"
                        c.connectTimeout = 15000
                        c.readTimeout = 30000
                        c.doOutput = true
                        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        val payload = JSONObject()
                            .put("tokens", JSONArray(chunk))
                            .put("title", title)
                            .put("body", body)
                            .put("url", "/")
                        extra.forEach { (k, v) -> payload.put(k, v) }
                        c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        val code = c.responseCode
                        val stream = if (code in 200..299) c.inputStream else c.errorStream
                        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
                        val ok = try {
                            JSONObject(txt).optBoolean("success", false)
                        } catch (e: Exception) {
                            false
                        }
                        if (!ok) allOk = false
                        if (i > 0) sb.append("\n")
                        sb.append("ก้อน ").append(i + 1).append(": HTTP ").append(code).append("\n").append(txt)
                    } finally {
                        c.disconnect()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allOk = false
                sb.append("ส่งไม่สำเร็จ: ").append(e.message ?: "ข้อผิดพลาดเครือข่าย")
            }
            Pair(allOk, sb.toString())
        }

    /** บันทึกประวัติการส่งจริง (ไม่เก็บเบอร์เต็ม) คืน true ถ้าบันทึกได้ */
    suspend fun log(role: String, scope: String, zoneId: String, person: String, title: String, count: Int, ok: Boolean): Boolean {
        return try {
            FirebaseFirestore.getInstance().collection("devNotifyLog").add(
                mapOf(
                    "by" to (DevRepo.currentEmail() ?: ""),
                    "role" to role,
                    "scope" to scope,
                    "zoneId" to zoneId,
                    "personTail" to person.trim().takeLast(4),
                    "title" to title,
                    "count" to count,
                    "ok" to ok,
                    "at" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // ===== ข้อความที่ตั้งเวลา/บันทึกไว้ (devBroadcasts) =====
    // once/daily: เซิร์ฟเวอร์ (api/run-broadcasts) ตรวจทุกนาทีแล้วส่งเมื่อถึงเวลา | manual: แอปส่งเองเมื่อกดปุ่ม
    private val bkk: TimeZone = TimeZone.getTimeZone("Asia/Bangkok")

    fun validTime(t: String): Boolean = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(t.trim())

    fun normTime(t: String): String {
        val p = t.trim().split(":")
        if (p.size != 2) return t.trim()
        val h = p[0].toIntOrNull() ?: return t.trim()
        return String.format(Locale.US, "%02d:%s", h, p[1])
    }

    /** เวลาถัดไปของ HH:mm ตามเวลาไทย (มิลลิวินาที) เรียกเมื่อ validTime ผ่านแล้วเท่านั้น */
    fun nextDaily(t: String): Long {
        val p = normTime(t).split(":")
        val c = Calendar.getInstance(bkk)
        c.set(Calendar.HOUR_OF_DAY, p[0].toInt())
        c.set(Calendar.MINUTE, p[1].toInt())
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** วว/ดด/ปปปป (รับปี พ.ศ. ด้วย) + ชช:นน เวลาไทย -> มิลลิวินาที หรือ null ถ้าไม่ถูกต้อง */
    fun parseOnce(date: String, time: String): Long? {
        if (!validTime(time)) return null
        val d = date.trim().split("/")
        if (d.size != 3) return null
        val dd = d[0].toIntOrNull() ?: return null
        val mm = d[1].toIntOrNull() ?: return null
        var yy = d[2].toIntOrNull() ?: return null
        if (yy > 2400) yy -= 543
        val f = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        f.timeZone = bkk
        f.isLenient = false
        return try {
            f.parse(String.format(Locale.US, "%02d/%02d/%04d %s", dd, mm, yy, normTime(time)))?.time
        } catch (e: Exception) {
            null
        }
    }

    fun fmt(ms: Long): String {
        if (ms <= 0L) return "-"
        val f = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        f.timeZone = bkk
        return f.format(Date(ms))
    }

    suspend fun saveBroadcast(
        role: String, scope: String, zoneId: String, zoneName: String, person: String,
        title: String, body: String, repeat: String, runAtMillis: Long, timeOfDay: String
    ): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val m = hashMapOf<String, Any>(
            "title" to title,
            "body" to body,
            "role" to role,
            "scope" to scope,
            "zoneId" to zoneId,
            "zoneName" to zoneName,
            "person" to person.trim(),
            "repeat" to repeat,
            "timeOfDay" to timeOfDay,
            "status" to "active",
            "createdBy" to email,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (repeat != "manual") m["nextRunAt"] = Timestamp(Date(runAtMillis))
        return try {
            FirebaseFirestore.getInstance().collection("devBroadcasts").add(m).await()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "บันทึกไม่สำเร็จ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์เขียนข้อมูล)"
        }
    }

    suspend fun listBroadcasts(): List<DevBroadcast> {
        val snap = FirebaseFirestore.getInstance().collection("devBroadcasts")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(50).get().await()
        return snap.documents.map { d ->
            DevBroadcast(
                id = d.id,
                title = d.getString("title") ?: "",
                body = d.getString("body") ?: "",
                role = d.getString("role") ?: "shop",
                scope = d.getString("scope") ?: "all",
                zoneId = d.getString("zoneId") ?: "",
                zoneName = d.getString("zoneName") ?: "",
                person = d.getString("person") ?: "",
                repeat = d.getString("repeat") ?: "manual",
                timeOfDay = d.getString("timeOfDay") ?: "",
                nextRunMillis = d.getTimestamp("nextRunAt")?.toDate()?.time ?: 0L,
                status = d.getString("status") ?: "active",
                lastRunMillis = d.getTimestamp("lastRunAt")?.toDate()?.time ?: 0L,
                lastResult = d.getString("lastResult") ?: ""
            )
        }
    }

    suspend fun setBroadcastStatus(id: String, status: String, nextRunMillis: Long?): Boolean {
        val upd = hashMapOf<String, Any>("status" to status)
        if (nextRunMillis != null) upd["nextRunAt"] = Timestamp(Date(nextRunMillis))
        return try {
            FirebaseFirestore.getInstance().collection("devBroadcasts").document(id).update(upd).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteBroadcast(id: String): Boolean {
        return try {
            FirebaseFirestore.getInstance().collection("devBroadcasts").document(id).delete().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun markSent(id: String, ok: Boolean, count: Int) {
        try {
            FirebaseFirestore.getInstance().collection("devBroadcasts").document(id).update(
                mapOf(
                    "lastRunAt" to FieldValue.serverTimestamp(),
                    "lastResult" to ((if (ok) "ส่งถึง " else "ส่งไม่สำเร็จ ") + count + " เครื่อง")
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }
}
KT_EOF
cat > "$D/ui/DevNotify.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevBroadcast
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevNotify
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun devRoleLabel(r: String): String = when (r) {
    "shop" -> "ร้านค้า"
    "admin" -> "แอดมิน"
    "rider" -> "ไรเดอร์"
    else -> "ลูกค้า"
}

private fun devScopeLabel(s: String): String = when (s) {
    "all" -> "ทั้งหมดทุกเขต"
    "zone" -> "ทั้งหมดในเขต"
    else -> "รายคน"
}

private fun devPlanLabel(p: String): String = when (p) {
    "now" -> "ส่งทันที"
    "once" -> "ตั้งเวลาครั้งเดียว"
    "daily" -> "ทุกวัน"
    else -> "บันทึกไว้กดส่งเอง"
}

private data class DevSendSpec(
    val role: String,
    val scope: String,
    val zoneId: String,
    val person: String,
    val title: String,
    val body: String,
    val real: Boolean,
    val savedId: String?
)

private data class DevConfirm(
    val kind: String,
    val tokens: List<String>,
    val spec: DevSendSpec,
    val plan: String,
    val runAt: Long,
    val timeOfDay: String
)

// หน้าแจ้งเตือน: ทดสอบ / ส่งจริง + กลุ่มผู้รับ + ขอบเขต + การส่ง (ทันที / ครั้งเดียว / ทุกวัน / บันทึกไว้กดส่งเอง)
@Composable
fun DevNotifyScreen() {
    var real by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("shop") }
    var sc by remember { mutableStateOf("all") }
    var plan by remember { mutableStateOf("now") }
    var zones by remember { mutableStateOf<List<DevFranchise>>(emptyList()) }
    var zoneId by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var onceDate by remember { mutableStateOf("") }
    var onceTime by remember { mutableStateOf("") }
    var dailyTime by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var resultOk by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<DevConfirm?>(null) }
    var blist by remember { mutableStateOf<List<DevBroadcast>?>(null) }
    var bTick by remember { mutableStateOf(0) }
    var delTarget by remember { mutableStateOf<DevBroadcast?>(null) }
    val cs = rememberCoroutineScope()

    LaunchedEffect(sc) {
        if (sc == "zone" && zones.isEmpty()) {
            try {
                zones = DevRepo.franchises()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = "โหลดรายชื่อเขตไม่สำเร็จ"
                resultOk = false
            }
        }
    }
    LaunchedEffect(bTick) {
        try {
            blist = DevNotify.listBroadcasts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            blist = emptyList()
        }
    }

    fun validate(): String? {
        if (title.isBlank()) return "กรุณาใส่หัวข้อ"
        if (body.isBlank()) return "กรุณาใส่ข้อความ"
        if (sc == "zone" && zoneId.isEmpty()) return "กรุณาเลือกเขต"
        if (sc == "one" && person.isBlank()) return "กรุณาใส่เบอร์โทรหรือรหัสของผู้รับ"
        if (real && plan == "once") {
            val ms = DevNotify.parseOnce(onceDate, onceTime)
                ?: return "วันที่/เวลาไม่ถูกต้อง (รูปแบบ วว/ดด/ปปปป และ ชช:นน)"
            if (ms < System.currentTimeMillis() + 60000L) return "เวลาที่ตั้งต้องอยู่ในอนาคตอย่างน้อย 1 นาที"
        }
        if (real && plan == "daily" && !DevNotify.validTime(dailyTime)) return "เวลาไม่ถูกต้อง (รูปแบบ ชช:นน เช่น 09:30)"
        return null
    }

    fun doSend(spec: DevSendSpec, tokens: List<String>) {
        cs.launch {
            busy = true
            result = "กำลังส่ง..."
            resultOk = true
            // ทดสอบร้านค้า: ส่งแบบ new_order ทดสอบ (test:"1", orderId ขึ้นต้น TEST-) ให้แอปร้านดังโดยไม่ต้องมีออเดอร์จริง
            val extra: Map<String, String> = if (!spec.real && spec.role == "shop") {
                mapOf("type" to "new_order", "test" to "1", "orderId" to "TEST-" + (System.currentTimeMillis() % 100000))
            } else emptyMap()
            val r = DevNotify.send(tokens, spec.title, spec.body, extra)
            resultOk = r.first
            var text = "ส่งถึง " + tokens.size + " เครื่อง\n" + r.second
            if (spec.real) {
                val logged = DevNotify.log(spec.role, spec.scope, spec.zoneId, spec.person, spec.title, tokens.size, r.first)
                if (!logged) text += "\n(บันทึกประวัติการส่งไม่สำเร็จ)"
            }
            val sid = spec.savedId
            if (sid != null) {
                DevNotify.markSent(sid, r.first, tokens.size)
                bTick++
            }
            result = text
            busy = false
        }
    }

    fun doSchedule(c: DevConfirm) {
        cs.launch {
            busy = true
            val zn = zones.firstOrNull { it.id == c.spec.zoneId }?.name ?: ""
            val err = DevNotify.saveBroadcast(
                c.spec.role, c.spec.scope, c.spec.zoneId, zn, c.spec.person,
                c.spec.title, c.spec.body, c.plan, c.runAt, c.timeOfDay
            )
            busy = false
            if (err == null) {
                result = "บันทึกแล้ว (" + devPlanLabel(c.plan) + ")"
                resultOk = true
                bTick++
            } else {
                result = err
                resultOk = false
            }
        }
    }

    fun onSend() {
        val err = validate()
        if (err != null) {
            result = err
            resultOk = false
            return
        }
        cs.launch {
            busy = true
            result = "กำลังหาผู้รับ..."
            resultOk = true
            val r = DevNotify.resolve(role, sc, zoneId, person)
            busy = false
            if (r.second != null) {
                result = r.second ?: ""
                resultOk = false
                return@launch
            }
            val spec = DevSendSpec(role, sc, zoneId, person, title.trim(), body.trim(), real, null)
            if (!real) {
                doSend(spec, r.first)
                return@launch
            }
            result = ""
            if (plan == "now") {
                confirm = DevConfirm("send", r.first, spec, plan, 0L, "")
            } else {
                val runAt = if (plan == "once") (DevNotify.parseOnce(onceDate, onceTime) ?: 0L)
                else if (plan == "daily") DevNotify.nextDaily(dailyTime) else 0L
                confirm = DevConfirm("schedule", r.first, spec, plan, runAt, DevNotify.normTime(dailyTime))
            }
        }
    }

    fun sendSaved(b: DevBroadcast) {
        cs.launch {
            busy = true
            result = "กำลังหาผู้รับ..."
            resultOk = true
            val r = DevNotify.resolve(b.role, b.scope, b.zoneId, b.person)
            busy = false
            if (r.second != null) {
                result = r.second ?: ""
                resultOk = false
                return@launch
            }
            result = ""
            val spec = DevSendSpec(b.role, b.scope, b.zoneId, b.person, b.title, b.body, true, b.id)
            confirm = DevConfirm("saved", r.first, spec, "manual", 0L, "")
        }
    }

    fun togglePause(b: DevBroadcast) {
        cs.launch {
            val ok = if (b.status == "active") DevNotify.setBroadcastStatus(b.id, "paused", null)
            else DevNotify.setBroadcastStatus(b.id, "active", DevNotify.nextDaily(b.timeOfDay))
            if (!ok) {
                result = "อัปเดตสถานะไม่สำเร็จ"
                resultOk = false
            }
            bTick++
        }
    }

    DevCard {
        DevNLabel("โหมด")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            DevChip("ทดสอบ", !real, true, DevC.P) {
                real = false
                plan = "now"
            }
            DevChip("ส่งจริง", real, true, DevC.R) { real = true }
        }
        DevNLabel("กลุ่มผู้รับ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            listOf("shop", "admin", "rider", "customer").forEach { r ->
                DevChip(devRoleLabel(r), role == r, true, DevC.P) {
                    role = r
                    if (r != "shop" && sc == "zone") sc = "all"
                }
            }
        }
        if (!real && role == "shop") {
            Text(
                "ทดสอบร้านค้า: แอปร้านจะดังเหมือนออเดอร์ใหม่ประมาณ 20 วินาที ส่วนโหมดส่งจริงจะไม่ดังแบบออเดอร์",
                fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        DevNLabel("ขอบเขต")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)
        ) {
            DevChip(devScopeLabel("all"), sc == "all", true, DevC.P) { sc = "all" }
            DevChip(devScopeLabel("zone"), sc == "zone", role == "shop", DevC.P) { sc = "zone" }
            DevChip(devScopeLabel("one"), sc == "one", true, DevC.P) { sc = "one" }
        }
        if (role != "shop") {
            Text(
                "ส่งตามเขตได้เฉพาะร้านค้า (กลุ่มอื่นยังไม่มีข้อมูลเขตในระบบ)",
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (sc == "zone") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 8.dp)
            ) {
                if (zones.isEmpty()) Text("กำลังโหลดเขต...", fontSize = 12.sp, color = DevC.S)
                zones.forEach { z -> DevChip(z.name, zoneId == z.id, true, DevC.Gn) { zoneId = z.id } }
            }
        }
        if (sc == "one") {
            Box(Modifier.padding(top = 6.dp))
            DevNLabel(
                if (role == "shop") "เบอร์โทรร้าน (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)"
                else if (role == "admin") "รหัส/อีเมลแอดมิน"
                else "เบอร์โทร (ใช้ระบุตัวตน ไม่ได้ส่งผ่านเบอร์)"
            )
            DevInput(person, { person = it }, "เช่น 0812345678", 1)
        }
        Box(Modifier.padding(top = 8.dp))
        DevNLabel("หัวข้อ")
        DevInput(title, { title = it }, "หัวข้อแจ้งเตือน", 1)
        DevNLabel("ข้อความ")
        DevInput(body, { body = it }, "ข้อความที่จะส่ง", 3)
        if (real) {
            DevNLabel("การส่ง")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)
            ) {
                listOf("now", "once", "daily", "manual").forEach { p ->
                    DevChip(devPlanLabel(p), plan == p, true, DevC.P) { plan = p }
                }
            }
            if (plan == "once") {
                DevNLabel("วันที่ (วว/ดด/ปปปป) และเวลา (ชช:นน) เวลาไทย")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { DevInput(onceDate, { onceDate = it }, "25/12/2026", 1) }
                    Box(Modifier.weight(1f)) { DevInput(onceTime, { onceTime = it }, "09:30", 1) }
                }
                Text("ส่งครั้งเดียวตามเวลาที่ตั้ง แล้วรายการจะเปลี่ยนเป็น \"ส่งแล้ว\"", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (plan == "daily") {
                DevNLabel("เวลาที่ส่งทุกวัน (ชช:นน) เวลาไทย")
                DevInput(dailyTime, { dailyTime = it }, "09:30", 1)
            }
            if (plan == "once" || plan == "daily") {
                Text(
                    "การตั้งเวลาทำงานผ่านเซิร์ฟเวอร์ (ปิดมือถือได้) ต้องตั้งตัวเรียก run-broadcasts ทุก 1 นาทีก่อน",
                    fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            if (plan == "manual") {
                Text(
                    "บันทึกข้อความไว้ในรายการด้านล่าง กด \"ส่งเลย\" เมื่อไรก็ได้ ส่งซ้ำได้ (กดหนึ่งครั้งส่งหนึ่งรอบ)",
                    fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (busy) Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF94A3B8)))
                    else if (real) Brush.linearGradient(listOf(DevC.R, Color(0xFF991B1B)))
                    else Brush.linearGradient(listOf(DevC.P, DevC.PD))
                )
                .clickable(enabled = !busy) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (busy) "กำลังดำเนินการ..."
                else if (!real) "ส่งทดสอบ"
                else if (plan == "now") "ส่งจริง"
                else if (plan == "manual") "บันทึกข้อความ"
                else "ตั้งเวลาส่ง",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
            )
        }
    }
    if (result.isNotEmpty()) {
        DevCard {
            Text(
                result, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                color = if (resultOk) DevC.T else DevC.R
            )
        }
    }

    val bl = blist
    Text(
        if (bl == null) "ข้อความที่ตั้งไว้" else "ข้อความที่ตั้งไว้ (" + bl.size + ")",
        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
    )
    if (bl == null) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
    } else if (bl.isEmpty()) {
        DevCard { Text("ยังไม่มีข้อความที่ตั้งเวลาหรือบันทึกไว้", fontSize = 13.sp, color = DevC.S) }
    } else {
        bl.forEach { b ->
            DevBroadcastCard(b, busy, { sendSaved(b) }, { togglePause(b) }, { delTarget = b })
        }
    }

    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color.White,
            title = {
                Text(
                    if (c.kind == "schedule") "ยืนยันตั้งเวลา?" else if (c.kind == "saved") "ส่งข้อความที่บันทึกไว้?" else "ยืนยันส่งจริง?",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    (if (c.kind == "schedule") "ผู้รับตอนนี้ " else "จะส่งถึง ") + c.tokens.size + " เครื่อง\nกลุ่ม: " + devRoleLabel(c.spec.role) +
                        "\nขอบเขต: " + devScopeLabel(c.spec.scope) + "\nหัวข้อ: " + c.spec.title +
                        (if (c.kind == "schedule") "\nการส่ง: " + devPlanLabel(c.plan) +
                            (if (c.plan == "once") " " + DevNotify.fmt(c.runAt) else if (c.plan == "daily") " " + c.timeOfDay + " น." else "") else ""),
                    fontSize = 13.sp, color = DevC.T, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (c.kind == "schedule") doSchedule(c) else doSend(c.spec, c.tokens)
                }) { Text(if (c.kind == "schedule") "ตั้งเวลา" else "ส่งเลย", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
    }
    val dt = delTarget
    if (dt != null) {
        AlertDialog(
            onDismissRequest = { delTarget = null },
            containerColor = Color.White,
            title = { Text("ลบข้อความนี้?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = { Text(dt.title, fontSize = 13.sp, color = DevC.S) },
            confirmButton = {
                TextButton(onClick = {
                    delTarget = null
                    cs.launch {
                        if (!DevNotify.deleteBroadcast(dt.id)) {
                            result = "ลบไม่สำเร็จ"
                            resultOk = false
                        }
                        bTick++
                    }
                }) { Text("ลบ", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
    }
}

@Composable
private fun DevBroadcastCard(
    b: DevBroadcast,
    busy: Boolean,
    onSend: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val who = devRoleLabel(b.role) + " · " + devScopeLabel(b.scope) +
        (if (b.scope == "zone" && b.zoneName.isNotEmpty()) " " + b.zoneName else "")
    val whenText = when (b.repeat) {
        "once" -> if (b.status == "done") "ครั้งเดียว (ส่งแล้ว)" else "ครั้งเดียว " + DevNotify.fmt(b.nextRunMillis)
        "daily" -> "ทุกวัน " + b.timeOfDay + " น." + (if (b.status == "active") " (ครั้งต่อไป " + DevNotify.fmt(b.nextRunMillis) + ")" else "")
        else -> "กดส่งเอง"
    }
    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(b.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1)
                Text(who, fontSize = 11.5.sp, color = DevC.S, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
            }
            when (b.status) {
                "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                "paused" -> DevBadge("หยุดชั่วคราว", DevC.OL, DevC.O)
                "done" -> DevBadge("ส่งแล้ว", Color(0xFFF1F5F9), DevC.S)
                else -> DevBadge(b.status, Color(0xFFF1F5F9), DevC.S)
            }
        }
        Text(whenText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DevC.T, modifier = Modifier.padding(top = 8.dp))
        if (b.lastRunMillis > 0L || b.lastResult.isNotEmpty()) {
            Text(
                "ล่าสุด " + DevNotify.fmt(b.lastRunMillis) + (if (b.lastResult.isNotEmpty()) " · " + b.lastResult else ""),
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (b.repeat == "manual") {
                Text(
                    "ส่งเลย", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onSend() }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (b.repeat == "daily" && b.status != "done") {
                Text(
                    if (b.status == "active") "หยุดชั่วคราว" else "เปิดใช้งาน", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.O,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onToggle() }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Text(
                "ลบ", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { onDelete() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun DevNLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DevChip(text: String, on: Boolean, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (!enabled) Color(0xFF94A3B8) else if (on) Color.White else accent,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (!enabled) Color(0xFFF1F5F9) else if (on) accent else accent.copy(alpha = 0.1f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun DevInput(value: String, onChange: (String) -> Unit, hint: String, lines: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
KT_EOF
echo "== ตรวจผล =="
grep -c "devBroadcasts" "$D/data/DevNotify.kt" | sed "s/^/data: devBroadcasts = /"
grep -c "DevBroadcastCard" "$D/ui/DevNotify.kt" | sed "s/^/ui: DevBroadcastCard = /"
git status --short
