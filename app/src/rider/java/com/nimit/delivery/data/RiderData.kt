package com.nimit.delivery.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RiderInfo(val id: String, val name: String, val phone: String)

/** จำไรเดอร์ที่ล็อกอินไว้ในเครื่อง */
class RiderSession(ctx: Context) {
    private val p = ctx.getSharedPreferences("nimit_rider", Context.MODE_PRIVATE)

    private fun get(k: String): String? = p.getString(k, null)
    private fun set(k: String, v: String?) {
        p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }

    var riderId: String? get() = get("riderId"); set(v) = set("riderId", v)
    var riderName: String? get() = get("riderName"); set(v) = set("riderName", v)
    var riderPhone: String? get() = get("riderPhone"); set(v) = set("riderPhone", v)

    fun logout() { riderId = null; riderName = null; riderPhone = null }
}

object RiderRepo {
    fun normPhone(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("66")) "0" + d.substring(2) else d
    }

    /** หาไรเดอร์จากเบอร์ที่แอดมินลงไว้ในคอลเลกชัน riders (ฟิลด์ phone) คืน null ถ้าไม่พบ */
    suspend fun findByPhone(input: String): RiderInfo? {
        val p = normPhone(input)
        if (p.length < 9) return null
        val col = FirebaseFirestore.getInstance().collection("riders")
        val q: QuerySnapshot = col.whereEqualTo("phone", p).get().await()
        var found: DocumentSnapshot? = q.documents.firstOrNull()
        if (found == null) {
            val all: QuerySnapshot = col.get().await()
            found = all.documents.firstOrNull { normPhone(it.get("phone")?.toString() ?: "") == p }
        }
        val d: DocumentSnapshot = found ?: return null
        return RiderInfo(d.id, d.getString("name") ?: "ไรเดอร์", d.get("phone")?.toString() ?: p)
    }
}

// ---------- ตัวช่วยอ่านข้อมูลออเดอร์ ----------
fun asMap(a: Any?): Map<String, Any?> {
    val m = a as? Map<*, *> ?: return emptyMap()
    val r = HashMap<String, Any?>()
    for ((k, v) in m) { r[k.toString()] = v }
    return r
}

fun asList(a: Any?): List<Any?> {
    val l = a as? List<*> ?: return emptyList()
    return l.map { it }
}

class Ord(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
    fun date(k: String): Date? = (d[k] as? Timestamp)?.toDate()
    fun cust(k: String): String = asMap(d["customer"])[k]?.toString() ?: ""
    fun custNum(k: String): Double? = (asMap(d["customer"])[k] as? Number)?.toDouble()
    fun items(): List<Map<String, Any?>> = asList(d["items"]).map { asMap(it) }
    fun qty(): Int {
        var t = 0
        for (i in items()) { t += (i["qty"] as? Number)?.toInt() ?: 0 }
        return t
    }
    val status: String get() = s("status")
    val orderId: String get() = if (s("orderId").isEmpty()) id else s("orderId")
    val created: Date? get() = date("createdAt")
}

fun money(n: Double): String = "฿" + NumberFormat.getIntegerInstance(Locale.US).format(Math.round(n))

private val TH: Locale = Locale("th", "TH")
fun fmtDate(d: Date?): String = if (d == null) "" else SimpleDateFormat("d MMMM yyyy", TH).format(d)
fun fmtTime(d: Date?): String = if (d == null) "-" else SimpleDateFormat("HH:mm", TH).format(d)
fun fmtDateTime(d: Date?): String = if (d == null) "-" else SimpleDateFormat("d MMM yyyy HH:mm", TH).format(d)

val ACTIVE_STATUSES: List<String> = listOf("accepted", "picking", "arrived", "delivering")
val NEXT_STATUS: Map<String, String> = mapOf("accepted" to "picking", "picking" to "arrived", "arrived" to "delivering", "delivering" to "done")
val NEXT_LABEL: Map<String, String> = mapOf("accepted" to "เริ่มงาน", "picking" to "ถึงร้านแล้ว", "arrived" to "ขับรถไปหาลูกค้า", "delivering" to "ส่งสำเร็จ")
val STATUS_TH: Map<String, String> = mapOf("pending" to "รอรับ", "accepted" to "รับแล้ว", "picking" to "ไปร้าน", "arrived" to "ถึงร้าน", "delivering" to "กำลังส่ง", "done" to "เสร็จ", "rejected" to "ยกเลิก")

// ---------- สถานะสดจาก Firestore ----------
class RiderStore(val riderId: String) {
    val myOrders = mutableStateOf<List<Ord>>(emptyList())
    val pending = mutableStateOf<List<Ord>>(emptyList())
    val rider = mutableStateOf<Map<String, Any>>(emptyMap())
    val banner = mutableStateOf<String?>(null)
    val unread = mutableStateMapOf<String, Int>()
    var onNewJob: ((Ord) -> Unit)? = null

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val regs = ArrayList<ListenerRegistration>()
    private val chatRegs = HashMap<String, ListenerRegistration>()
    private val known = HashSet<String>()
    private var pendingLoaded = false

    fun start() {
        stop()
        regs.add(db.collection("riders").document(riderId).addSnapshotListener { s, _ ->
            val m: Map<String, Any>? = s?.data
            if (m != null) rider.value = m
        })
        regs.add(db.collection("orders").whereEqualTo("riderId", riderId).addSnapshotListener { snap, _ ->
            if (snap != null) {
                val list: List<Ord> = snap.documents
                    .map { x: DocumentSnapshot -> Ord(x.id, x.data ?: emptyMap()) }
                    .sortedByDescending { it.created?.time ?: 0L }
                myOrders.value = list
                syncChats(list)
            }
        })
        regs.add(db.collection("orders").whereEqualTo("status", "pending").addSnapshotListener { snap, _ ->
            if (snap != null) {
                val list: List<Ord> = snap.documents
                    .map { x: DocumentSnapshot -> Ord(x.id, x.data ?: emptyMap()) }
                    .sortedByDescending { it.created?.time ?: 0L }
                pending.value = list
                val fresh: List<Ord> = list.filter { !known.contains(it.id) }
                for (o in list) known.add(o.id)
                if (pendingLoaded) {
                    for (o in fresh) {
                        val t: Long = o.created?.time ?: 0L
                        if (System.currentTimeMillis() - t < 5 * 60 * 1000L) onNewJob?.invoke(o)
                    }
                }
                pendingLoaded = true
            }
        })
    }

    private fun syncChats(list: List<Ord>) {
        val active = HashSet<String>()
        for (o in list) { if (ACTIVE_STATUSES.contains(o.status)) active.add(o.id) }
        val gone: List<String> = chatRegs.keys.filter { !active.contains(it) }
        for (id in gone) { chatRegs.remove(id)?.remove(); unread.remove(id) }
        for (id in active) {
            if (!chatRegs.containsKey(id)) {
                chatRegs[id] = db.collection("chats").document(id).addSnapshotListener { s, _ ->
                    unread[id] = (s?.getLong("unread_rider") ?: 0L).toInt()
                }
            }
        }
    }

    fun stop() {
        for (r in regs) r.remove()
        regs.clear()
        for (r in chatRegs.values) r.remove()
        chatRegs.clear()
        pendingLoaded = false
        known.clear()
    }
}

/** ข้อผิดพลาดทางธุรกิจที่แสดงให้ไรเดอร์เห็นได้ตรงๆ (เช่น งานถูกรับไปแล้ว) */
class RiderBizException(msg: String) : Exception(msg)

/** scope ระดับแอป: งานที่ต้องจบแม้ออกจากหน้าจอ (เช่น ส่งแจ้งเตือนลูกค้า) */
object AppScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

fun riderErrorText(e: Throwable): String {
    var t: Throwable? = e
    while (t != null) {
        if (t is RiderBizException) return t.message ?: "เกิดข้อผิดพลาด"
        t = t.cause
    }
    return "เกิดข้อผิดพลาด"
}

// ---------- การกระทำต่อ Firestore ----------
object RiderApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun num(n: Double): Any = if (n % 1.0 == 0.0) n.toLong() else n
    private fun inc(n: Double): FieldValue = if (n % 1.0 == 0.0) FieldValue.increment(n.toLong()) else FieldValue.increment(n)

    /** รับงานแบบ transaction: ต้องยัง pending และยังไม่มีไรเดอร์ ไม่งั้นถือว่าถูกรับไปแล้ว */
    suspend fun acceptJob(orderDocId: String, riderId: String, name: String, phone: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            val taken: Boolean = !s.exists() || s.getString("status") != "pending" || !s.getString("riderId").isNullOrEmpty()
            if (taken) throw RiderBizException("งานนี้ถูกรับไปแล้ว")
            val upd = HashMap<String, Any?>()
            upd["status"] = "accepted"
            upd["riderId"] = riderId
            upd["riderName"] = name
            upd["riderPhone"] = phone
            upd["riderReadAt"] = null
            upd["riderViewingAt"] = null
            upd["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, upd)
            null
        }).await()
    }

    /** เลื่อนสถานะแบบ compare-and-set: ต้องอยู่ที่สถานะ from จริง (ถ้าอยู่ที่ to แล้วถือว่าสำเร็จ ไม่ทำซ้ำ) */
    suspend fun advance(orderDocId: String, from: String, to: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val st: String? = tx.get(ref).getString("status")
            if (st == from) tx.update(ref, "status", to, "updatedAt", FieldValue.serverTimestamp())
            else if (st != to) throw RiderBizException("สถานะออเดอร์เปลี่ยนไปแล้ว กรุณารีเฟรช")
            null
        }).await()
    }

    /**
     * ส่งสำเร็จ: ทำทั้งหมดใน transaction เดียว (เปลี่ยนสถานะ done + หักกระเป๋าไรเดอร์ + เพิ่มกระเป๋ากลาง + บันทึก walletTx)
     * กันซ้ำด้วยธง walletSettled และ doc id คงที่ settle_<orderDocId> คืน true ถ้าเพิ่งส่งสำเร็จรอบนี้
     */
    suspend fun completeDelivery(orderDocId: String, riderId: String, riderName: String): Boolean {
        val oref = db.collection("orders").document(orderDocId)
        val central = db.collection("wallet").document("central")
        val txRef = db.collection("walletTx").document("settle_" + orderDocId)
        return db.runTransaction(Transaction.Function<Boolean> { tx ->
            val o: DocumentSnapshot = tx.get(oref)
            val st: String? = o.getString("status")
            val settled: Boolean = o.getBoolean("walletSettled") == true
            var fresh = false
            if (!o.exists()) throw RiderBizException("ไม่พบออเดอร์นี้")
            if (st != "done" && !settled) {
                if (st != "delivering") throw RiderBizException("สถานะออเดอร์เปลี่ยนไปแล้ว กรุณารีเฟรช")
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

    /** ส่งแจ้งเตือนลูกค้าด้วย scope ระดับแอป (ไม่ถูกยกเลิกเมื่อออกจากหน้าจอ) เรียกหลังอัปเดตสถานะสำเร็จเท่านั้น */
    fun notifyCustomerAsync(o: Ord, status: String) {
        AppScope.scope.launch { notifyCustomer(o, status) }
    }

    suspend fun setStatus(orderDocId: String, status: String) {
        db.collection("orders").document(orderDocId).update("status", status, "updatedAt", FieldValue.serverTimestamp()).await()
    }

    suspend fun markRead(orderDocId: String) {
        try {
            val s: DocumentSnapshot = db.collection("orders").document(orderDocId).get().await()
            if (s.exists() && s.get("riderReadAt") == null) {
                db.collection("orders").document(orderDocId).update("riderReadAt", FieldValue.serverTimestamp()).await()
            }
        } catch (e: Exception) { }
    }

    fun setViewing(orderDocId: String, on: Boolean) {
        try {
            db.collection("orders").document(orderDocId).update("riderViewingAt", if (on) FieldValue.serverTimestamp() else null)
        } catch (e: Exception) { }
    }

    fun writeLocation(riderId: String, lat: Double, lng: Double) {
        try {
            db.collection("riders").document(riderId).update("currentLat", lat, "currentLng", lng, "locationUpdatedAt", FieldValue.serverTimestamp())
        } catch (e: Exception) { }
    }

    /** ตอนส่งสำเร็จ: หักกระเป๋าไรเดอร์ + บันทึกประวัติ + เพิ่มกระเป๋ากลาง (เหมือนเว็บเดิม) */
    suspend fun finishWallet(o: Ord, riderId: String, riderName: String) {
        val cut: Double = o.n("walletCut")
        val rid: String = if (o.s("riderId").isEmpty()) riderId else o.s("riderId")
        val rname: String = if (o.s("riderName").isEmpty()) riderName else o.s("riderName")
        if (cut <= 0.0 || rid.isEmpty()) return
        try {
            val tx = HashMap<String, Any>()
            tx["riderId"] = rid
            tx["riderName"] = rname
            tx["amount"] = num(-cut)
            tx["type"] = "หักจากออเดอร์"
            tx["orderId"] = o.orderId
            tx["note"] = ""
            tx["createdAt"] = FieldValue.serverTimestamp()
            tx["by"] = "system"
            db.collection("walletTx").add(tx).await()
        } catch (e: Exception) { }
        try {
            db.collection("riders").document(rid).update("wallet", inc(-cut)).await()
        } catch (e: Exception) { }
        try {
            val ref = db.collection("wallet").document("central")
            val snap: DocumentSnapshot = ref.get().await()
            if (snap.exists()) {
                ref.update("balance", inc(cut)).await()
            } else {
                val m = HashMap<String, Any>()
                m["balance"] = num(cut)
                ref.set(m).await()
            }
        } catch (e: Exception) { }
    }

    private fun postJson(urlStr: String, body: JSONObject) {
        try {
            val c = URL(urlStr).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            c.responseCode
            c.disconnect()
        } catch (e: Exception) { }
    }

    /** แจ้งลูกค้าว่าสถานะเปลี่ยน (ผ่านเซิร์ฟเวอร์ Vercel เดิม ย้ายไป Firebase ในเฟส 5) */
    suspend fun notifyCustomer(o: Ord, status: String) {
        val title: String
        val body: String
        when (status) {
            "accepted" -> { title = "ไรเดอร์รับงานแล้ว"; body = "ไรเดอร์กำลังเตรียมไปรับอาหารจากร้านค้าของคุณ" }
            "picking" -> { title = "ไรเดอร์กำลังไปร้าน"; body = "ไรเดอร์กำลังเดินทางไปที่ร้านค้าแล้ว" }
            "arrived" -> { title = "ไรเดอร์ถึงร้านแล้ว"; body = "ไรเดอร์ถึงร้านค้าแล้ว กำลังรอรับอาหารของคุณ" }
            "delivering" -> { title = "กำลังนำอาหารมาส่ง"; body = "ไรเดอร์รับอาหารแล้ว กำลังเดินทางมาส่งคุณ" }
            "done" -> { title = "จัดส่งสำเร็จแล้ว"; body = "ขอบคุณที่ใช้บริการ Nimit Delivery" }
            else -> return
        }
        val phone: String = o.cust("phone")
        if (phone.isEmpty()) return
        try {
            val snap: DocumentSnapshot = db.collection("fcmTokens").document(phone + "_customer").get().await()
            val token: String = snap.getString("token") ?: return
            val j = JSONObject()
            j.put("token", token)
            j.put("title", title)
            j.put("body", body)
            j.put("orderId", o.orderId)
            j.put("orderDocId", o.id)
            j.put("route", "track/" + o.id)
            j.put("url", "/track.html")
            withContext(Dispatchers.IO) { postJson("https://nimitdelivery.vercel.app/api/send-order-notification", j) }
        } catch (e: Exception) { }
    }

    suspend fun walletHistory(riderId: String): List<Map<String, Any>> {
        val snap: QuerySnapshot = db.collection("walletTx").whereEqualTo("riderId", riderId).get().await()
        val list = ArrayList<Map<String, Any>>()
        for (d in snap.documents) { val m: Map<String, Any>? = d.data; if (m != null) list.add(m) }
        list.sortByDescending { (it["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L }
        return list.take(30)
    }
}
