package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await

class AdminRider(val id: String, val name: String, val phone: String)

class AdminBizException(msg: String) : Exception(msg)

fun adminErrorText(e: Throwable): String {
    var t: Throwable? = e
    while (t != null) {
        if (t is AdminBizException) return t.message ?: "เกิดข้อผิดพลาด"
        t = t.cause
    }
    return "เกิดข้อผิดพลาด"
}

object AdminOrderApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun num(n: Double): Any = if (n % 1.0 == 0.0) n.toLong() else n
    private fun inc(n: Double): FieldValue = if (n % 1.0 == 0.0) FieldValue.increment(n.toLong()) else FieldValue.increment(n)

    suspend fun riders(): List<AdminRider> {
        val snap = db.collection("riders").get().await()
        return snap.documents.map { AdminRider(it.id, it.getString("name") ?: "ไรเดอร์", it.get("phone")?.toString() ?: "") }
    }

    // สถิติลูกค้า: (สั่งสำเร็จ, ยกเลิกที่เป็นความผิดลูกค้า)
    suspend fun customerStats(phone: String): Pair<Int, Int>? {
        return try {
            val snap = db.collection("orders").whereEqualTo("customer.phone", phone).get().await()
            var done = 0
            var cancel = 0
            for (d in snap.documents) {
                val st = d.getString("status")
                if (st == "done") done++
                else if (st == "rejected" && d.getString("cancelFault") != "shop") cancel++
            }
            Pair(done, cancel)
        } catch (e: Exception) {
            null
        }
    }

    /** โยนงาน: ตั้งไรเดอร์ใหม่ pending -> accepted (สถานะอื่นที่กำลังทำอยู่คงเดิม) ไม่ให้โยนออเดอร์ที่จบแล้ว */
    suspend fun assign(orderDocId: String, r: AdminRider) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว")
            val upd = HashMap<String, Any?>()
            upd["riderId"] = r.id
            upd["riderName"] = r.name
            upd["riderPhone"] = r.phone
            upd["status"] = if (st == "pending") "accepted" else st
            upd["riderReadAt"] = null
            upd["riderViewingAt"] = null
            upd["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, upd)
            null
        }).await()
    }

    /** ถอดไรเดอร์: ล้างฟิลด์ไรเดอร์ แล้วกลับเป็น pending */
    suspend fun unassign(orderDocId: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว")
            val upd = HashMap<String, Any?>()
            upd["riderId"] = ""
            upd["riderName"] = ""
            upd["riderPhone"] = ""
            upd["status"] = "pending"
            upd["riderReadAt"] = null
            upd["riderViewingAt"] = null
            upd["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, upd)
            null
        }).await()
    }

    /** เลื่อน/ย้อนสถานะแบบ compare-and-set */
    suspend fun advance(orderDocId: String, from: String, to: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val st: String? = tx.get(ref).getString("status")
            if (st == from) tx.update(ref, "status", to, "updatedAt", FieldValue.serverTimestamp())
            else if (st != to) throw AdminBizException("สถานะออเดอร์เปลี่ยนไปแล้ว")
            null
        }).await()
    }

    /** ส่งสำเร็จ: เปลี่ยนเป็น done + หักกระเป๋าไรเดอร์ + เพิ่มกระเป๋ากลาง ใน transaction เดียว (กันซ้ำด้วย walletSettled เหมือนแอปไรเดอร์) */
    suspend fun completeDelivery(orderDocId: String, riderId: String, riderName: String): Boolean {
        val oref = db.collection("orders").document(orderDocId)
        val central = db.collection("wallet").document("central")
        val txRef = db.collection("walletTx").document("settle_" + orderDocId)
        return db.runTransaction(Transaction.Function<Boolean> { tx ->
            val o: DocumentSnapshot = tx.get(oref)
            val st: String? = o.getString("status")
            val settled: Boolean = o.getBoolean("walletSettled") == true
            var fresh = false
            if (!o.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            if (st != "done" && !settled) {
                if (st != "delivering") throw AdminBizException("สถานะออเดอร์เปลี่ยนไปแล้ว")
                val cut: Double = (o.get("walletCut") as? Number)?.toDouble() ?: 0.0
                val rid: String = o.getString("riderId").let { if (it.isNullOrEmpty()) riderId else it }
                val rname: String = o.getString("riderName").let { if (it.isNullOrEmpty()) riderName else it }
                val oid: String = o.getString("orderId").let { if (it.isNullOrEmpty()) orderDocId else it }
                tx.update(oref, "status", "done", "updatedAt", FieldValue.serverTimestamp(), "walletSettled", true)
                if (cut > 0.0 && rid.isNotEmpty()) {
                    val entry = HashMap<String, Any>()
                    entry["riderId"] = rid
                    entry["riderName"] = rname
                    entry["amount"] = num(-cut)
                    entry["type"] = "หักจากออเดอร์"
                    entry["orderId"] = oid
                    entry["note"] = ""
                    entry["createdAt"] = FieldValue.serverTimestamp()
                    entry["by"] = "system"
                    tx.set(txRef, entry)
                    tx.update(db.collection("riders").document(rid), "wallet", inc(-cut))
                    val bal = HashMap<String, Any>()
                    bal["balance"] = inc(cut)
                    tx.set(central, bal, SetOptions.merge())
                }
                fresh = true
            }
            fresh
        }).await()
    }

    /** ยกเลิกออเดอร์: fault = "customer" (หักเครดิตลูกค้า) หรือ "shop" (ไม่หัก) */
    suspend fun cancel(orderDocId: String, note: String, fault: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้เสร็จสิ้นแล้ว")
            tx.update(ref, "status", "rejected", "cancelNote", note, "cancelFault", fault, "updatedAt", FieldValue.serverTimestamp())
            null
        }).await()
    }
}
