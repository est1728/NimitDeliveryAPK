package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class DevShopRow(val id: String, val name: String, val isOpen: Boolean?)

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
            .map { d -> DevShopRow(d.id, d.getString("name") ?: "ร้าน", d.getBoolean("isOpen")) }
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
}
