package com.nimit.delivery.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.math.*

typealias Doc = Map<String, Any?>

fun Doc.str(k: String): String = (this[k] as? String).orEmpty()
fun Doc.dbl(k: String): Double? = when (val v = this[k]) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null }
fun Doc.lng(k: String): Long? = (this[k] as? Number)?.toLong()
fun truthy(v: Any?): Boolean = when (v) { null -> false; is Boolean -> v; is Number -> v.toDouble() != 0.0; is String -> v.isNotEmpty(); else -> true }

class Shop(val id: String, val d: Doc) {
    var dist: Double? = null
    val name get() = d.str("name")
    val desc get() = d.str("desc")
    val bannerUrl get() = d.str("bannerUrl")
    val avatarUrl get() = d.str("avatarUrl")
    val rating get() = d.dbl("rating") ?: 0.0
    val ratingCount get() = d.dbl("ratingCount") ?: 0.0
    val orderCount get() = d.dbl("orderCount") ?: 0.0
    val deliveryTime get() = d["deliveryTime"]?.toString()?.takeIf { it.isNotBlank() } ?: "20-30"
    val score get() = min(rating, 5.0) / 5 * .4 + min(orderCount, 500.0) / 500 * .4 + min(ratingCount, 100.0) / 100 * .2

    fun isPopular() = rating >= 4.5 && ratingCount >= 50 && orderCount >= 200
    fun badge(): String? {
        if (isPopular()) return "pop"
        if (rating >= 4.5 && ratingCount >= 3) return "top"
        if (truthy(d["isNew"])) return "new"
        val dt = Regex("^\\s*(\\d+)").find(d["deliveryTime"]?.toString().orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (dt != null && dt <= 15) return "fast"
        if (truthy(d["promo"])) return "promo"
        return null
    }

    fun effectiveOpen(): Boolean {
        val today = java.time.Instant.now().toString().take(10)
        val closedDates = (d["closedDates"] as? List<*>)?.map { it.toString() }
        if (closedDates != null && today in closedDates) return false
        if (d["isOpen"] == false) {
            val until = d.lng("closeUntil")
            if (until != null && until != 0L) { if (System.currentTimeMillis() < until) return false } else return false
        }
        if (!truthy(d["autoSchedule"])) return true
        val days = (d["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.isNotEmpty() } ?: (0..6).toList()
        val cal = java.util.Calendar.getInstance()
        if ((cal.get(java.util.Calendar.DAY_OF_WEEK) - 1) !in days) return false
        val so = d.str("scheduleOpen"); val sc = d.str("scheduleClose")
        if (so.isNotEmpty() && sc.isNotEmpty()) {
            val (oh, om) = so.split(":").map { it.toInt() }; val (ch, cm) = sc.split(":").map { it.toInt() }
            val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val o = oh * 60 + om; val c = ch * 60 + cm
            if (o != c) {
                val within = if (o < c) now in o until c else (now >= o || now < c)
                if (!within) return false
            }
        }
        return true
    }
}

class ZoneResult(val franchiseId: String, val inside: Boolean, val dist: Double)

object ShopsRepo {
    private val db get() = Firebase.firestore

    private suspend fun all(col: String): List<Pair<String, Doc>> =
        db.collection(col).get().await().documents.map { it.id to (it.data ?: emptyMap()) }

    suspend fun refreshCustomer(session: Session) {
        val phone = session.customerPhone ?: return
        try {
            val snap = db.collection("customers").document(phone).get().await()
            snap.data?.let { session.customerData = it.toJson().toString() }
        } catch (_: Exception) {}
    }

    fun customerLatLng(session: Session): Pair<Double, Double>? = try {
        val o = JSONObject(session.customerData ?: "{}")
        if (o.has("lat") && o.has("lng") && !o.isNull("lat") && !o.isNull("lng")) o.getDouble("lat") to o.getDouble("lng") else null
    } catch (_: Exception) { null }

    suspend fun shops(session: Session): Pair<List<Shop>, ZoneResult?> {
        var list = try { all("shops").map { Shop(it.first, it.second) } } catch (_: Exception) { emptyList() }
        var zone: ZoneResult? = null
        try {
            zone = franchiseZone(customerLatLng(session))
            if (zone != null) list = list.filter { s ->
                val ids = (s.d["franchiseIds"] as? List<*>)?.map { it.toString() }
                    ?: s.d.str("franchiseId").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
                ids.isEmpty() || zone.franchiseId in ids
            }
        } catch (_: Exception) {}
        return list to zone
    }

    suspend fun ads() = try { all("ads") } catch (_: Exception) { emptyList() }
    suspend fun categoryButtons() = try { all("categoryButtons").sortedBy { it.second.dbl("order") ?: 0.0 } } catch (_: Exception) { emptyList() }
    suspend fun flashDeals(): List<Pair<String, Doc>> = try {
        val now = System.currentTimeMillis()
        all("flashDeals").filter { (it.second.lng("endAt") ?: 0L) > now }.sortedBy { it.second.lng("endAt") }
    } catch (_: Exception) { emptyList() }

    suspend fun couponCount(): Int = try {
        val now = System.currentTimeMillis()
        all("coupons").count { it.second["active"] != false && (it.second.lng("endAt") ?: 0L) > now }
    } catch (_: Exception) { 0 }

    suspend fun unreadNotifs(lastRead: Long): Int = try {
        db.collection("notifications").get().await().documents.count {
            ((it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L) > lastRead
        }
    } catch (_: Exception) { 0 }

    val activeStatuses = listOf("pending", "accepted", "picking", "arrived", "delivering")
    val statusLabel = mapOf(
        "pending" to "รอรับออเดอร์", "accepted" to "รับงานแล้ว", "picking" to "ไรเดอร์กำลังไปร้าน",
        "arrived" to "ถึงร้านแล้ว", "delivering" to "ไรเดอร์กำลังส่งมาหาคุณ"
    )

    suspend fun activeOrder(phone: String?): Pair<String, Doc>? {
        if (phone.isNullOrEmpty()) return null
        return try {
            db.collection("orders").whereEqualTo("customer.phone", phone).get().await().documents
                .filter { it.getString("status") in activeStatuses }
                .maxByOrNull { (it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L }
                ?.let { it.id to (it.data ?: emptyMap()) }
        } catch (_: Exception) { null }
    }

    fun distKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0; val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun toXY(lat: Double, lng: Double, refLat: Double, refLng: Double) =
        ((lng - refLng) * 111320.0 * cos(Math.toRadians(refLat))) to ((lat - refLat) * 110540.0)

    private suspend fun franchiseZone(pt: Pair<Double, Double>?): ZoneResult? {
        if (pt == null) return null
        var best: ZoneResult? = null
        for ((id, f) in all("franchiseZones")) {
            val poly = (f["zone"] as? List<*>)?.mapNotNull { p ->
                val m = p as? Map<*, *> ?: return@mapNotNull null
                val la = (m["lat"] as? Number)?.toDouble(); val lo = (m["lng"] as? Number)?.toDouble()
                if (la != null && lo != null) la to lo else null
            } ?: continue
            if (poly.size < 3) continue
            var inside = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val (yi, xi) = poly[i]; val (yj, xj) = poly[j]
                if ((yi > pt.first) != (yj > pt.first) && pt.second < (xj - xi) * (pt.first - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            var dist = 0.0
            if (!inside) {
                dist = Double.MAX_VALUE
                val xy = poly.map { toXY(it.first, it.second, pt.first, pt.second) }
                for (i in xy.indices) {
                    val a = xy[i]; val b = xy[(i + 1) % xy.size]
                    val dx = b.first - a.first; val dy = b.second - a.second
                    val l2 = dx * dx + dy * dy
                    val t = if (l2 == 0.0) 0.0 else ((-a.first) * dx + (-a.second) * dy).div(l2).coerceIn(0.0, 1.0)
                    dist = min(dist, hypot(a.first + t * dx, a.second + t * dy))
                }
            }
            val fid = f.str("franchiseId").ifEmpty { id }
            if (best == null || dist < best.dist) best = ZoneResult(fid, inside, dist)
        }
        return best
    }
}
