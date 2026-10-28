package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object AdminHistoryApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** ออเดอร์ล่าสุด 200 ใบ แล้วเก็บเฉพาะที่เสร็จสิ้น/ยกเลิก (เหมือนเว็บ) */
    suspend fun load(): List<AdminOrd> {
        val snap = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
        val out = ArrayList<AdminOrd>()
        for (d in snap.documents) {
            val st: String = d.getString("status") ?: ""
            if (st == "done" || st == "rejected") out.add(AdminOrd(d.id, d.data ?: emptyMap()))
        }
        return out
    }

    /** เรียกกลับ: ออเดอร์กลับเป็นรอรับ ล้างไรเดอร์และสถานะอ่าน บันทึกหมายเหตุและเวลาเรียกกลับ */
    suspend fun recall(orderDocId: String, note: String) {
        val m = HashMap<String, Any?>()
        m["status"] = "pending"
        m["riderId"] = null
        m["riderName"] = null
        m["riderPhone"] = null
        m["riderReadAt"] = null
        m["riderViewingAt"] = null
        m["adminNote"] = note
        m["recalledAt"] = FieldValue.serverTimestamp()
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(orderDocId).update(m).await()
    }
}
