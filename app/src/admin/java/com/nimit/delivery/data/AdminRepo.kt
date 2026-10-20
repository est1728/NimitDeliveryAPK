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
 * (เวอร์ชันวินิจฉัย: ข้อความผิดพลาดแยกตามขั้น ต้องเปลี่ยนกลับเป็นข้อความทั่วไปก่อนใช้งานจริง)
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
    suspend fun login(ctx: Context, phone: String, pinRaw: String): String? {
        val ph = phone.trim()
        val pin = pinRaw.trim()
        if (ph.isEmpty() || pin.isEmpty()) return "กรุณากรอกเบอร์และรหัส"
        return try {
            val snap = FirebaseFirestore.getInstance().collection("franchises")
                .whereEqualTo("phone", ph).limit(1).get().await()
            val d = snap.documents.firstOrNull() ?: return BAD
            if (d.getString("status") == "suspended") return "เขตนี้ถูกระงับการใช้งาน"
            val alg = d.getString("pinAlg") ?: ""
            val salt = d.getString("pinSalt") ?: ""
            val hash = d.getString("pinHash") ?: ""
            if (hash.isEmpty() || salt.isEmpty()) return BAD
            if (alg != "pbkdf2-sha1-100000") return BAD
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
