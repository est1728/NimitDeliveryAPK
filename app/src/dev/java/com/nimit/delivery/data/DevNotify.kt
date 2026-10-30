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
    val lastResult: String,
    val category: String
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
        title: String, body: String, repeat: String, runAtMillis: Long, timeOfDay: String,
        category: String = ""
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
            "category" to category,
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
                lastResult = d.getString("lastResult") ?: "",
                category = d.getString("category") ?: ""
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
