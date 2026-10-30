package com.nimit.delivery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class AdminRiderFull(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    val name: String get() = s("name")
    val active: Boolean get() = d["active"] != false
    val wallet: Double get() = (d["wallet"] as? Number)?.toDouble() ?: 0.0
    val slug: String get() = if (s("slug").isEmpty()) id else s("slug")

    /** ออนไลน์ = มีพิกัด และอัปเดตตำแหน่งล่าสุดไม่เกิน 3 นาที (เกณฑ์เดียวกับเว็บ) */
    fun online(now: Long): Boolean {
        if (d["currentLat"] == null || d["currentLng"] == null) return false
        val ts = d["locationUpdatedAt"]
        val ms: Long = if (ts is Timestamp) ts.toDate().time else (ts as? Number)?.toLong() ?: 0L
        return now - ms < 3 * 60 * 1000L
    }
}

object AdminRidersApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun listen(onChange: (List<AdminRiderFull>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("riders").addSnapshotListener { snap, e ->
            if (e != null) onError(e.message ?: "โหลดไรเดอร์ไม่ได้")
            if (snap != null) onChange(snap.documents.map { AdminRiderFull(it.id, it.data ?: emptyMap()) })
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        val m = HashMap<String, Any>()
        m["active"] = active
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("riders").document(id).update(m).await()
    }

    /** เติมเงินกระเป๋า (บวกเพิ่มแบบ atomic) */
    suspend fun topup(id: String, amount: Double) {
        val m = HashMap<String, Any>()
        m["wallet"] = FieldValue.increment(amount)
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("riders").document(id).update(m).await()
    }

    /** เพิ่ม (id = null) หรือแก้ไข (ชื่อ เบอร์ ทะเบียน slug) */
    suspend fun save(id: String?, name: String, phone: String, plate: String, slug: String) {
        val m = HashMap<String, Any>()
        m["name"] = name
        m["phone"] = phone
        m["plate"] = plate
        m["slug"] = slug
        m["updatedAt"] = FieldValue.serverTimestamp()
        if (id != null) {
            db.collection("riders").document(id).update(m).await()
        } else {
            m["wallet"] = 0L
            m["createdAt"] = FieldValue.serverTimestamp()
            db.collection("riders").add(m).await()
        }
    }

    suspend fun delete(id: String) {
        db.collection("riders").document(id).delete().await()
    }
}
