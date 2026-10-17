package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * เพิ่ม/แก้ไขเขตแฟรนไชส์ (franchises/{id}) สัญญาข้อมูลสำหรับแอปแอดมิน:
 *  - name = ชื่อเขตที่แสดงให้ลูกค้าเห็น (เช่น แม่จัน), province = พื้นที่/จังหวัด
 *  - phone = เบอร์ (เฉพาะตัวเลข ขึ้นต้น 0) ใช้เป็นชื่อผู้ใช้ ห้ามซ้ำกันระหว่างเขต
 *  - รหัสเข้าใช้ ไม่เก็บเป็นข้อความธรรมดา: pinAlg="pbkdf2-sha1-100000", pinSalt (hex 16 ไบต์),
 *    pinHash (hex 32 ไบต์ = PBKDF2WithHmacSHA1(รหัสตามที่พิมพ์, salt, 100000 รอบ, 256 บิต)) ตรวจรหัสโดยคำนวณแล้วเทียบ
 *  - status = active | suspended, monthlyFee = ตัวเลข
 * ทุกการเพิ่ม/แก้ไขเขียนประวัติลง franchiseAudit พร้อมกันในครั้งเดียว (ไม่เก็บรหัส เก็บเบอร์เป็น 4 ตัวท้าย)
 */
object DevZoneRepo {
    private const val ITER = 100000
    private const val ALG = "pbkdf2-sha1-100000"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val phoneRe = Regex("^0[0-9]{8,9}$")

    fun generateCode(): String {
        val r = SecureRandom()
        val sb = StringBuilder()
        repeat(8) { sb.append(ALPHABET[r.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    fun cleanPhone(p: String): String = p.filter { it.isDigit() }

    fun validPhone(p: String): Boolean = phoneRe.matches(cleanPhone(p))

    private fun mask(p: String): String = if (p.length > 4) "******" + p.takeLast(4) else p

    private fun hex(b: ByteArray): String = b.joinToString("") { String.format("%02x", it) }

    private fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return hex(b)
    }

    private fun hashPin(code: String, saltHex: String): String {
        val salt = ByteArray(saltHex.length / 2) { i -> saltHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val spec = PBEKeySpec(code.toCharArray(), salt, ITER, 256)
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded)
    }

    private suspend fun phoneTaken(phone: String, exceptId: String?): Boolean {
        val s = FirebaseFirestore.getInstance().collection("franchises").whereEqualTo("phone", phone).get().await()
        return s.documents.any { it.id != exceptId }
    }

    private fun feeText(f: Double): String = String.format("%,.0f", f)

    /** เพิ่มเขตใหม่ พร้อมรหัสเข้าใช้ คืนข้อความผิดพลาด หรือ null ถ้าสำเร็จ */
    suspend fun createZone(name: String, province: String, phoneRaw: String, fee: Double, code: String): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val phone = cleanPhone(phoneRaw)
        return try {
            if (phoneTaken(phone, null)) return "เบอร์นี้ถูกใช้กับเขตอื่นแล้ว"
            val salt = newSalt()
            val hash = withContext(Dispatchers.Default) { hashPin(code, salt) }
            val db = FirebaseFirestore.getInstance()
            val ref = db.collection("franchises").document()
            val b = db.batch()
            b.set(
                ref,
                mapOf(
                    "name" to name.trim(),
                    "province" to province.trim(),
                    "phone" to phone,
                    "monthlyFee" to fee,
                    "status" to "active",
                    "pinAlg" to ALG,
                    "pinSalt" to salt,
                    "pinHash" to hash,
                    "codeUpdatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "createdBy" to email
                )
            )
            b.set(
                db.collection("franchiseAudit").document(),
                mapOf(
                    "action" to "create_zone",
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "groupId" to "",
                    "zoneIds" to listOf(ref.id),
                    "zoneNames" to listOf(name.trim()),
                    "codeChanged" to true,
                    "before" to emptyMap<String, String>(),
                    "after" to mapOf(
                        "name" to name.trim(),
                        "province" to province.trim(),
                        "phone" to mask(phone),
                        "monthlyFee" to feeText(fee),
                        "status" to "active"
                    )
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }

    /** แก้ไขเขตเดิม newCode = null/ว่าง คือไม่เปลี่ยนรหัส */
    suspend fun updateZone(
        f: DevFranchise, name: String, province: String, phoneRaw: String,
        fee: Double, status: String, newCode: String?
    ): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val phone = cleanPhone(phoneRaw)
        val upd = HashMap<String, Any>()
        val before = HashMap<String, String>()
        val after = HashMap<String, String>()
        if (name.trim() != f.name) {
            upd["name"] = name.trim(); before["name"] = f.name; after["name"] = name.trim()
        }
        if (province.trim() != f.province) {
            upd["province"] = province.trim(); before["province"] = f.province; after["province"] = province.trim()
        }
        if (phone != f.phone) {
            upd["phone"] = phone; before["phone"] = mask(f.phone); after["phone"] = mask(phone)
        }
        if (fee != f.monthlyFee) {
            upd["monthlyFee"] = fee; before["monthlyFee"] = feeText(f.monthlyFee); after["monthlyFee"] = feeText(fee)
        }
        if (status != f.status) {
            upd["status"] = status; before["status"] = f.status; after["status"] = status
        }
        val codeChanged = !newCode.isNullOrEmpty()
        if (upd.isEmpty() && !codeChanged) return "ไม่มีการเปลี่ยนแปลง"
        return try {
            if (phone != f.phone && phoneTaken(phone, f.id)) return "เบอร์นี้ถูกใช้กับเขตอื่นแล้ว"
            if (codeChanged) {
                val salt = newSalt()
                val hash = withContext(Dispatchers.Default) { hashPin(newCode ?: "", salt) }
                upd["pinAlg"] = ALG
                upd["pinSalt"] = salt
                upd["pinHash"] = hash
                upd["codeUpdatedAt"] = FieldValue.serverTimestamp()
            }
            val db = FirebaseFirestore.getInstance()
            val b = db.batch()
            b.update(db.collection("franchises").document(f.id), upd)
            b.set(
                db.collection("franchiseAudit").document(),
                mapOf(
                    "action" to "edit_zone",
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "groupId" to f.groupId,
                    "zoneIds" to listOf(f.id),
                    "zoneNames" to listOf(if (name.trim().isEmpty()) f.name else name.trim()),
                    "codeChanged" to codeChanged,
                    "before" to before,
                    "after" to after
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }
}
