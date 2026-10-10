package com.nimit.delivery.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ShopInfo(val id: String, val name: String, val phone: String)

/** จำร้านที่ล็อกอินไว้ในเครื่อง */
class ShopSession(ctx: Context) {
    private val p = ctx.getSharedPreferences("nimit_shop", Context.MODE_PRIVATE)

    private fun get(k: String): String? = p.getString(k, null)
    private fun set(k: String, v: String?) {
        p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }

    var shopId: String? get() = get("shopId"); set(v) = set("shopId", v)
    var shopName: String? get() = get("shopName"); set(v) = set("shopName", v)
    var shopPhone: String? get() = get("shopPhone"); set(v) = set("shopPhone", v)

    fun logout() { shopId = null; shopName = null; shopPhone = null }
}

object ShopRepo {
    fun normPhone(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("66")) "0" + d.substring(2) else d
    }

    /** หาร้านจากเบอร์ในคอลเลกชัน shops (ฟิลด์ phone) อาจได้หลายร้านถ้าเบอร์ซ้ำ  TODO: เพิ่ม OTP ภายหลัง */
    suspend fun findByPhone(input: String): List<ShopInfo> {
        val p = normPhone(input)
        if (p.length < 9) return emptyList()
        val col = FirebaseFirestore.getInstance().collection("shops")
        var docs: List<DocumentSnapshot> = col.whereEqualTo("phone", p).get().await().documents
        if (docs.isEmpty()) {
            docs = col.get().await().documents.filter { normPhone(it.get("phone")?.toString() ?: "") == p }
        }
        return docs.map { ShopInfo(it.id, it.getString("name") ?: "ร้านค้า", it.get("phone")?.toString() ?: p) }
    }
}

// ---------- ตัวช่วยอ่านข้อมูล ----------
fun shopAsMap(a: Any?): Map<String, Any?> {
    val m = a as? Map<*, *> ?: return emptyMap()
    val r = HashMap<String, Any?>()
    for ((k, v) in m) { r[k.toString()] = v }
    return r
}

fun shopAsList(a: Any?): List<Any?> {
    val l = a as? List<*> ?: return emptyList()
    return l.map { it }
}

/** ตัวเลขแบบไม่มี .0 ถ้าเป็นจำนวนเต็ม */
fun shopNum(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

private val SHOP_LOCALE: Locale = Locale("th", "TH")
fun shopTime(d: Date?): String = if (d == null) "-" else SimpleDateFormat("HH:mm", SHOP_LOCALE).format(d)
fun shopDateTime(d: Date?): String = if (d == null) "-" else SimpleDateFormat("d MMM HH:mm", SHOP_LOCALE).format(d)

const val SHOP_ADMIN_PHONE = "0832582353"
val SHOP_ACTIVE_STATUSES: List<String> = listOf("pending", "accepted", "picking", "arrived", "delivering")
val SHOP_STATUS_TH: Map<String, String> = mapOf(
    "pending" to "รอรับ", "accepted" to "ไรเดอร์รับแล้ว", "picking" to "ไรเดอร์กำลังมา", "arrived" to "ไรเดอร์ถึงร้านแล้ว",
    "delivering" to "กำลังจัดส่ง", "done" to "เสร็จสิ้น", "rejected" to "ยกเลิก"
)

class ShopOrd(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
    fun date(k: String): Date? = (d[k] as? Timestamp)?.toDate()
    fun cust(k: String): String = shopAsMap(d["customer"])[k]?.toString() ?: ""
    fun items(): List<Map<String, Any?>> = shopAsList(d["items"]).map { shopAsMap(it) }
    fun qty(): Int {
        var t = 0
        for (i in items()) { t += (i["qty"] as? Number)?.toInt() ?: 0 }
        return t
    }
    val status: String get() = s("status")
    val orderId: String get() = if (s("orderId").isEmpty()) id else s("orderId")
    val created: Date? get() = date("createdAt")
}

// ---------- ตรรกะเปิด-ปิดร้าน (พอร์ตจาก shop-admin.html) ----------
private fun shopDow(cal: Calendar): Int = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = อาทิตย์ เหมือน Date.getDay()

fun shopOpenDays(s: Map<String, Any>): List<Int> {
    val l = (s["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
    return if (l.isEmpty()) listOf(0, 1, 2, 3, 4, 5, 6) else l
}

fun shopClosedDateToday(list: Any?): Boolean {
    val l = list as? List<*> ?: return false
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val date = String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
    for (x in l) {
        val m = x as? Map<*, *> ?: continue
        if (m["type"]?.toString() == "monthly") { if ((m["day"] as? Number)?.toInt() == day) return true }
        else if (m["date"]?.toString() == date) return true
    }
    return false
}

fun shopWithinSchedule(openTime: String, closeTime: String): Boolean {
    if (openTime.isEmpty() || closeTime.isEmpty()) return true
    val o = openTime.split(":"); val c = closeTime.split(":")
    if (o.size < 2 || c.size < 2) return true
    val cal = Calendar.getInstance()
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val openMin = (o[0].toIntOrNull() ?: 0) * 60 + (o[1].toIntOrNull() ?: 0)
    val closeMin = (c[0].toIntOrNull() ?: 0) * 60 + (c[1].toIntOrNull() ?: 0)
    if (openMin == closeMin) return true
    return if (openMin < closeMin) (nowMin >= openMin && nowMin < closeMin) else (nowMin >= openMin || nowMin < closeMin)
}

/** เวลา (ms) ของรอบเปิดร้านถัดไป ใช้ตอนกด "ปิดยาว" คืน null ถ้าไม่ได้ตั้งเวลาอัตโนมัติ */
fun shopNextOpenTime(s: Map<String, Any>): Long? {
    if (s["autoSchedule"] != true) return null
    val open = s["scheduleOpen"]?.toString().orEmpty()
    val close = s["scheduleClose"]?.toString().orEmpty()
    if (open.isEmpty() || close.isEmpty()) return null
    val parts = open.split(":")
    if (parts.size < 2) return null
    val oh = parts[0].toIntOrNull() ?: return null
    val om = parts[1].toIntOrNull() ?: return null
    val days = shopOpenDays(s)
    val now = System.currentTimeMillis()
    for (add in 0..8) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, add)
        cal.set(Calendar.HOUR_OF_DAY, oh); cal.set(Calendar.MINUTE, om); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) continue
        if (days.contains(shopDow(cal))) return cal.timeInMillis
    }
    return null
}

/** "ตอนนี้ควรเปิดจริงหรือยัง" จากเงื่อนไขทั้งหมด */
fun shopEffectiveOpen(s: Map<String, Any>): Boolean {
    if (shopClosedDateToday(s["closedDates"])) return false
    if (s["isOpen"] == false) {
        val until = (s["closeUntil"] as? Number)?.toLong()
        if (until != null) { if (System.currentTimeMillis() < until) return false } else return false
    }
    if (s["autoSchedule"] != true) return true
    if (!shopOpenDays(s).contains(shopDow(Calendar.getInstance()))) return false
    return shopWithinSchedule(s["scheduleOpen"]?.toString().orEmpty(), s["scheduleClose"]?.toString().orEmpty())
}

// ---------- สถานะสดจาก Firestore ----------
class ShopStore(val shopId: String) {
    val shop = mutableStateOf<Map<String, Any>>(emptyMap())
    val shopLoaded = mutableStateOf(false)
    val orders = mutableStateOf<List<ShopOrd>>(emptyList())
    val banner = mutableStateOf<String?>(null)
    var onNewOrder: ((ShopOrd) -> Unit)? = null

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var shopReg: ListenerRegistration? = null
    private var ordReg: ListenerRegistration? = null
    private var curIds: List<String> = emptyList()
    private val known = HashSet<String>()
    private var ordersLoaded = false

    fun start() {
        stop()
        shopReg = db.collection("shops").document(shopId).addSnapshotListener { s, _ ->
            val m: Map<String, Any>? = s?.data
            if (m != null) {
                shop.value = m
                shopLoaded.value = true
                val slug: String = m["slug"]?.toString() ?: ""
                val ids: List<String> = listOf(shopId, slug).filter { it.isNotEmpty() }.distinct()
                if (ids != curIds) listenOrders(ids)
            }
        }
    }

    // ดึงเฉพาะออเดอร์ของร้านนี้ (id หรือ slug เหมือนหน้าเว็บ)
    private fun listenOrders(ids: List<String>) {
        ordReg?.remove()
        curIds = ids
        known.clear()
        ordersLoaded = false
        ordReg = db.collection("orders").whereIn("shopId", ids).addSnapshotListener { snap, _ ->
            if (snap != null) {
                val list: List<ShopOrd> = snap.documents
                    .map { x: DocumentSnapshot -> ShopOrd(x.id, x.data ?: emptyMap()) }
                    .sortedByDescending { it.created?.time ?: 0L }
                orders.value = list
                for (o in list) {
                    if (SHOP_ACTIVE_STATUSES.contains(o.status)) {
                        if (known.add(o.id) && ordersLoaded) onNewOrder?.invoke(o)
                    }
                }
                ordersLoaded = true
            }
        }
    }

    fun stop() {
        shopReg?.remove(); shopReg = null
        ordReg?.remove(); ordReg = null
        curIds = emptyList()
        known.clear()
        ordersLoaded = false
    }
}

// ---------- การกระทำต่อ Firestore ----------
object ShopApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** เปิดร้าน: field ตรงกับที่แอดมินต้องเขียน (isOpen=true, ล้างเหตุผล/ประเภท/เวลา) */
    suspend fun openShop(shopId: String) {
        val upd = HashMap<String, Any?>()
        upd["isOpen"] = true; upd["closeReason"] = ""; upd["closeType"] = null; upd["closeUntil"] = null
        db.collection("shops").document(shopId).update(upd).await()
        try {
            val log = HashMap<String, Any?>()
            log["action"] = "open"; log["reason"] = ""; log["at"] = FieldValue.serverTimestamp()
            db.collection("shops").document(shopId).collection("statusLogs").add(log).await()
        } catch (e: Exception) { }
    }

    /** ปิดร้าน: closeType = "long" | "temp", closeUntil = ms หรือ null */
    suspend fun closeShop(shopId: String, reason: String, closeType: String, closeUntil: Long?) {
        val upd = HashMap<String, Any?>()
        upd["isOpen"] = false; upd["closeReason"] = reason; upd["closeType"] = closeType; upd["closeUntil"] = closeUntil
        db.collection("shops").document(shopId).update(upd).await()
        try {
            val log = HashMap<String, Any?>()
            log["action"] = "close"; log["reason"] = reason; log["closeType"] = closeType; log["at"] = FieldValue.serverTimestamp()
            db.collection("shops").document(shopId).collection("statusLogs").add(log).await()
        } catch (e: Exception) { }
    }

    suspend fun setLoyalty(shopId: String, on: Boolean, target: Int) {
        val upd = HashMap<String, Any?>()
        upd["loyaltyEnabled"] = on; upd["loyaltyTarget"] = target
        db.collection("shops").document(shopId).update(upd).await()
    }
}
