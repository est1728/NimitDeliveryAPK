#!/bin/bash
# แก้การหา token ของแอป dev: อ่าน tokens[] ทุกเครื่องของร้าน ไม่ใช่แค่ token ล่าสุด (เขียนทับ 1 ไฟล์ รันซ้ำได้)
set -e
D=app/src/dev/java/com/nimit/delivery
[ -f "$D/ui/DevNotify.kt" ] && [ -f "$D/data/DevNotify.kt" ] || { echo "หยุด: ยังไม่ได้รัน dev-notify.sh (ไม่เจอไฟล์แจ้งเตือนของ dev)"; exit 1; }
cat > "$D/data/DevNotify.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    suspend fun send(tokens: List<String>, title: String, body: String): Pair<Boolean, String> =
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
}
KT_EOF
echo "== ตรวจผล =="
grep -c "addDoc(" "$D/data/DevNotify.kt" | sed "s/^/จำนวน addDoc = /"
git status --short
