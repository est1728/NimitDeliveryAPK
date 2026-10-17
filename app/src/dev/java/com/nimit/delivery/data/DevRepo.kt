package com.nimit.delivery.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** ข้อความผิดพลาดตอนเขียนข้อมูล: แยกกรณีสิทธิ์ (Firestore rules) ออกจากปัญหาอื่น เพื่อไม่ต้องเดาสาเหตุ */
fun devWriteError(ex: Exception): String {
    if (ex is FirebaseFirestoreException && ex.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "ไม่มีสิทธิ์เขียนข้อมูล (Firestore rules ยังไม่อนุญาตบัญชีนี้)"
    }
    return "บันทึกไม่สำเร็จ: " + (ex.message ?: ex.javaClass.simpleName).take(120)
}

data class DevFranchise(
    val id: String,
    val name: String,
    val province: String,
    val phone: String,
    val hasCode: Boolean,
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
                phone = d.getString("phone") ?: "",
                hasCode = (d.getString("pinHash") ?: "").isNotEmpty(),
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
            devWriteError(ex)
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
            devWriteError(ex)
        }
    }
}
