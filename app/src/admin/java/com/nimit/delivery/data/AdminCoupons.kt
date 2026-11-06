package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class AdminCoupon(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
    val type: String get() = s("type")
    val endAt: Long get() = (d["endAt"] as? Number)?.toLong() ?: 0L
    val createdSec: Long get() = (d["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L
}

object AdminCouponsApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun numAny(d: Double): Any = if (d % 1.0 == 0.0) d.toLong() else d

    fun listen(onChange: (List<AdminCoupon>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("coupons").addSnapshotListener { snap, e ->
            if (e != null) onError(e.message ?: "โหลดคูปองไม่ได้")
            if (snap != null) onChange(snap.documents.map { AdminCoupon(it.id, it.data ?: emptyMap()) })
        }
    }

    /** เพิ่มคูปอง (โครงข้อมูลเหมือนเว็บ) */
    suspend fun add(title: String, type: String, value: Double, maxDiscount: Double, minOrder: Double, shopId: String, shopName: String, endAt: Long) {
        val m = HashMap<String, Any>()
        m["title"] = title
        m["type"] = type
        m["value"] = numAny(value)
        m["maxDiscount"] = numAny(maxDiscount)
        m["minOrderAmount"] = numAny(minOrder)
        m["shopId"] = shopId
        m["shopName"] = shopName
        m["endAt"] = endAt
        m["active"] = true
        m["createdAt"] = FieldValue.serverTimestamp()
        db.collection("coupons").add(m).await()
    }

    suspend fun delete(id: String) {
        db.collection("coupons").document(id).delete().await()
    }
}
