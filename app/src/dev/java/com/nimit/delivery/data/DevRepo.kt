package com.nimit.delivery.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class DevFranchise(
    val id: String,
    val name: String,
    val province: String,
    val ownerEmail: String,
    val monthlyFee: Double,
    val status: String,
    val zonePoints: Int,
    val shopCount: Int
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
        return fs.map { d ->
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
                shopCount = shopCount
            )
        }.sortedBy { it.name }
    }
}
