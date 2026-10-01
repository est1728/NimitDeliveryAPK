package com.nimit.delivery.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.ceil

@Suppress("UNCHECKED_CAST")
fun asMap(x: Any?): Doc? = x as? Map<String, Any?>
fun asMapList(x: Any?): List<Doc> = (x as? List<*>)?.mapNotNull { asMap(it) } ?: emptyList()
fun num(x: Any?): Double? = when (x) { is Number -> x.toDouble(); is String -> x.toDoubleOrNull(); else -> null }
fun fmtNum(d: Double): String = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

fun deepJson(v: Any?): Any? = when (v) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), deepJson(x)) } }
    is List<*> -> JSONArray().also { a -> v.forEach { a.put(deepJson(it)) } }
    is String, is Number, is Boolean -> v
    else -> v.toString()
}

data class Tier(val min: Double, val max: Double, val gp: Double)
data class Kg(val kg: Double = 0.0, val kheed: Double = 0.0) { val totalKheed get() = kg * 10 + kheed }

class Pricing(
    val gpPercent: Double = 0.0, val gpSubPercent: Double = 0.0,
    val tiers: List<Tier> = emptyList(), val subTiers: List<Tier> = emptyList(), val shopType: String = "normal"
) {
    private fun tier(base: Double, ts: List<Tier>, fallback: Double): Double {
        if (ts.isNotEmpty()) {
            val s = ts.sortedBy { it.min }
            for ((i, t) in s.withIndex()) {
                val inRange = if (i == 0) base >= t.min && base <= t.max else base > t.min && base <= t.max
                if (inRange) return t.gp
            }
        }
        return fallback
    }
    fun calcPrice(base: Double, menuGp: Double): Int =
        if (shopType == "proxy") ceil(base).toInt() else ceil(base * (1 + (tier(base, tiers, gpPercent) + menuGp) / 100)).toInt()
    fun calcSub(opt: Double): Int =
        if (shopType == "proxy") ceil(opt).toInt() else ceil(opt * (1 + tier(opt, subTiers, gpSubPercent) / 100)).toInt()

    fun withShopType(t: String) = Pricing(gpPercent, gpSubPercent, tiers, subTiers, t)

    companion object {
        private fun tiers(x: Any?) = asMapList(x).map { Tier(num(it["min"]) ?: 0.0, num(it["max"]) ?: Double.POSITIVE_INFINITY, num(it["gp"]) ?: 0.0) }
        fun from(d: Doc?) = if (d == null) Pricing() else Pricing(num(d["gpPercent"]) ?: 0.0, num(d["gpSubPercent"]) ?: 0.0, tiers(d["gpTiers"]), tiers(d["gpSubTiers"]))
    }
}

// ---------- เปิด/ปิดร้าน (ตรรกะเดียวกับ menu.html: computeShopOpenNow) ----------
private fun withinWindow(open: String, close: String): Boolean {
    val (oh, om) = open.split(":").map { it.trim().toInt() }
    val (ch, cm) = close.split(":").map { it.trim().toInt() }
    val c = Calendar.getInstance()
    val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    val o = oh * 60 + om; val e = ch * 60 + cm
    if (o == e) return true
    return if (o < e) now in o until e else (now >= o || now < e)
}

fun isShopWithinSchedule(d: Doc): Boolean {
    val days = (d["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.isNotEmpty() } ?: (0..6).toList()
    if ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1) !in days) return false
    val o = d.str("scheduleOpen"); val c = d.str("scheduleClose")
    if (o.isEmpty() || c.isEmpty()) return true
    return try { withinWindow(o, c) } catch (_: Exception) { true }
}

/** วันปิดพิเศษ: รองรับทั้ง {type:'monthly',day} / {date:'yyyy-MM-dd'} และสตริงวันที่ล้วน */
fun isClosedDateToday(list: Any?): Boolean {
    val l = list as? List<*> ?: return false
    if (l.isEmpty()) return false
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val local = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
    return l.any { cd ->
        val m = cd as? Map<*, *>
        when {
            m == null -> cd.toString() == local
            m["type"] == "monthly" -> (m["day"] as? Number)?.toInt() == day
            else -> m["date"] == local
        }
    }
}

fun computeShopOpenNow(d: Doc): Boolean {
    if (isClosedDateToday(d["closedDates"])) return false
    if (d["isOpen"] == false) {
        val until = d.lng("closeUntil")
        if (until != null && until != 0L) { if (System.currentTimeMillis() < until) return false } else return false
    }
    return if (truthy(d["autoSchedule"])) isShopWithinSchedule(d) else true
}

fun Shop.openNow(): Boolean = computeShopOpenNow(d)

fun isMenuWithinSchedule(m: Doc): Boolean {
    if (!truthy(m["schedEnabled"]) || m.str("schedOpen").isEmpty() || m.str("schedClose").isEmpty()) return true
    return try { withinWindow(m.str("schedOpen"), m.str("schedClose")) } catch (_: Exception) { true }
}

// ---------- ตะกร้า (รูปแบบ JSON เดียวกับเว็บ: { shopId: { shopName, items:[...] } }) ----------
object CartStore {
    fun load(s: Session): JSONObject = try { JSONObject(s.cart ?: "{}") } catch (_: Exception) { JSONObject() }
    fun save(s: Session, o: JSONObject) { s.cart = o.toString() }

    fun qtyInCart(c: JSONObject, shopId: String, menuId: String): Int {
        val items = c.optJSONObject(shopId)?.optJSONArray("items") ?: return 0
        var n = 0
        for (i in 0 until items.length()) items.getJSONObject(i).let { if (it.optString("id") == menuId) n += it.optInt("qty") }
        return n
    }

    fun totals(c: JSONObject): Pair<Int, Double> {
        var q = 0; var t = 0.0
        c.keys().forEach { k ->
            val items = c.optJSONObject(k)?.optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) { val it = items.getJSONObject(i); q += it.optInt("qty"); t += it.optDouble("price") * it.optInt("qty") }
        }
        return q to t
    }

    fun add(c: JSONObject, shopId: String, shopName: String, item: JSONObject) {
        val shop = c.optJSONObject(shopId) ?: JSONObject().put("shopName", shopName).put("items", JSONArray()).also { c.put(shopId, it) }
        shop.getJSONArray("items").put(item)
    }

    fun removeOne(c: JSONObject, shopId: String, menuId: String) {
        val shop = c.optJSONObject(shopId) ?: return
        val items = shop.getJSONArray("items")
        var last = -1
        for (i in 0 until items.length()) if (items.getJSONObject(i).optString("id") == menuId) last = i
        if (last >= 0) {
            val it = items.getJSONObject(last)
            it.put("qty", it.optInt("qty") - 1)
            if (it.optInt("qty") <= 0) items.remove(last)
        }
        if (items.length() == 0) c.remove(shopId)
    }
}
