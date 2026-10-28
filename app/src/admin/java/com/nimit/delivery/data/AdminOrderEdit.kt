package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await

object AdminOrderEdit {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun shopInfo(orderDocId: String, name: String, phone: String, addr: String) {
        val m = HashMap<String, Any>()
        m["shopName"] = name
        m["shopPhone"] = phone
        m["shopAddress"] = addr
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(orderDocId).update(m).await()
    }

    // แก้ข้อมูลลูกค้าในออเดอร์ (พิกัดเดิมคงไว้) และบันทึกลง customers ด้วยเหมือนเว็บ
    suspend fun customer(orderDocId: String, name: String, phone: String, addr: String) {
        val m = HashMap<String, Any>()
        m["customer.name"] = name
        m["customer.phone"] = phone
        m["customer.address"] = addr
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(orderDocId).update(m).await()
        if (phone.isNotEmpty()) {
            val c = HashMap<String, Any>()
            c["name"] = name
            c["phone"] = phone
            c["address"] = addr
            c["updatedAt"] = FieldValue.serverTimestamp()
            try { db.collection("customers").document(phone).set(c, SetOptions.merge()).await() } catch (e: Exception) { }
        }
    }

    suspend fun orderExtra(orderDocId: String, deliveryFee: Double, adminNote: String) {
        val m = HashMap<String, Any>()
        m["deliveryFee"] = deliveryFee
        m["adminNote"] = adminNote
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(orderDocId).update(m).await()
    }

    /** แก้ราคาร้านฝากซื้อ: ราคาใหม่ = ยอดเรียกเก็บทั้งหมด (ไม่มีค่าส่งแยก) คำนวณส่วนแบ่งจาก settings/pricing เหมือนเว็บ */
    suspend fun proxyPrice(orderDocId: String, newPrice: Long) {
        val ps = db.collection("settings").document("pricing").get().await()
        val wp: Double = (ps.get("walletPercent") as? Number)?.toDouble() ?: 30.0
        val rp: Double = (ps.get("riderPercent") as? Number)?.toDouble() ?: 70.0
        val walletCut: Long = Math.round(newPrice * wp / 100.0)
        val riderEarn: Long = Math.round(newPrice * rp / 100.0)
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            if (s.getString("shopType") != "proxy") throw AdminBizException("ใช้ได้เฉพาะออเดอร์ร้านฝากซื้อ")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว แก้ไขราคาไม่ได้")
            val m = HashMap<String, Any>()
            m["subtotal"] = newPrice
            m["grandTotal"] = newPrice
            m["totalDiff"] = newPrice
            m["deliveryFee"] = 0L
            m["walletCut"] = walletCut
            m["riderEarn"] = riderEarn
            m["priceEditedAt"] = FieldValue.serverTimestamp()
            m["priceEditedBy"] = "admin"
            m["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, m)
            null
        }).await()
    }
}
