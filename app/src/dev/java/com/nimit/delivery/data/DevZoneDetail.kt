package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// zoneIds = เขตทั้งหมดที่ร้านนี้ผูกอยู่ (franchiseIds ใหม่ หรือ franchiseId เดี่ยวแบบเก่า) ร้านอยู่ได้หลายเขต
data class DevShopRow(val id: String, val name: String, val isOpen: Boolean?, val zoneIds: List<String>)

data class DevOrderRow(val orderId: String, val shopName: String, val status: String, val total: Double)

// orders / audit เป็น null = โหลดส่วนนั้นไม่สำเร็จ (ส่วนอื่นยังแสดงได้)
data class DevZoneDetail(
    val shops: List<DevShopRow>,
    val orders: List<DevOrderRow>?,
    val audit: List<DevAuditItem>?
)

// ข้อมูลหลังบ้านของเขต (อ่านอย่างเดียว): ร้านในเขต, ออเดอร์ล่าสุด, ประวัติของเขตนี้
object DevDetailRepo {
    suspend fun load(f: DevFranchise): DevZoneDetail {
        val db = FirebaseFirestore.getInstance()
        val shopDocs = LinkedHashMap<String, DocumentSnapshot>()
        db.collection("shops").whereArrayContains("franchiseIds", f.id).get().await().documents.forEach { shopDocs[it.id] = it }
        db.collection("shops").whereEqualTo("franchiseId", f.id).get().await().documents.forEach { shopDocs[it.id] = it }
        val shops = shopDocs.values
            .map { d ->
                val z = (d.get("franchiseIds") as? List<*>)?.mapNotNull { it as? String }
                    ?: (d.getString("franchiseId")?.let { listOf(it) } ?: emptyList())
                DevShopRow(d.id, d.getString("name") ?: "ร้าน", d.getBoolean("isOpen"), z)
            }
            .sortedBy { it.name }
        val ids = shopDocs.keys
        // ไม่ใช้ whereIn + orderBy (ต้องสร้าง index) อ่าน 200 ออเดอร์ล่าสุดของระบบแล้วกรองเฉพาะร้านในเขต
        val orders: List<DevOrderRow>? = if (ids.isEmpty()) {
            emptyList()
        } else try {
            db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await().documents
                .filter { (it.getString("shopId") ?: "") in ids }
                .map { d ->
                    DevOrderRow(
                        d.getString("orderId") ?: d.id,
                        d.getString("shopName") ?: "",
                        d.getString("status") ?: "",
                        d.getDouble("grandTotal") ?: 0.0
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val audit: List<DevAuditItem>? = try {
            DevAuditRepo.recent(100).filter { f.id in it.zoneIds }.take(10)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return DevZoneDetail(shops, orders, audit)
    }

    /**
     * ย้ายร้านไปอีกเขต: copy=false ย้ายไปเลย (ออกจากเขตเดิม) / copy=true คัดลอก (อยู่ทั้งสองเขต)
     * เขียน shops/{id}.franchiseIds ตามรูปแบบเดิม (array หลายเขตได้) ลบ franchiseId เดี่ยวแบบเก่า + บันทึกประวัติใน transaction เดียวกัน
     * คืนข้อความผิดพลาด หรือ null ถ้าสำเร็จ
     */
    suspend fun moveShop(shopId: String, shopName: String, from: DevFranchise, to: DevFranchise, copy: Boolean): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        if (from.id == to.id) return "เลือกเขตปลายทางที่ต่างจากเขตเดิม"
        val db = FirebaseFirestore.getInstance()
        val shopRef = db.collection("shops").document(shopId)
        val auditRef = db.collection("franchiseAudit").document()
        var problem: String? = null
        return try {
            db.runTransaction(Transaction.Function<Void?> { t ->
                val s = t.get(shopRef)
                if (!s.exists()) {
                    problem = "ไม่พบร้านนี้แล้ว"
                    return@Function null
                }
                val cur = (s.get("franchiseIds") as? List<*>)?.mapNotNull { it as? String }
                    ?: (s.getString("franchiseId")?.let { listOf(it) } ?: emptyList())
                if (!cur.contains(from.id)) {
                    problem = "ร้านนี้ไม่ได้อยู่ในเขตต้นทางแล้ว (ข้อมูลเปลี่ยนไป) ลองโหลดใหม่"
                    return@Function null
                }
                val next = ArrayList<String>()
                cur.forEach { if (it != from.id || copy) next.add(it) }
                if (!next.contains(to.id)) next.add(to.id)
                t.update(shopRef, mapOf("franchiseIds" to next, "franchiseId" to FieldValue.delete()))
                t.set(
                    auditRef,
                    mapOf(
                        "action" to (if (copy) "copy_shop" else "move_shop"),
                        "by" to email,
                        "at" to FieldValue.serverTimestamp(),
                        "groupId" to "",
                        "zoneIds" to listOf(from.id, to.id),
                        "zoneNames" to listOf(from.name, to.name),
                        "shopId" to shopId,
                        "shopName" to shopName,
                        "beforeShopZones" to cur,
                        "afterShopZones" to next
                    )
                )
                null
            }).await()
            problem
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            devWriteError(e)
        }
    }
}
