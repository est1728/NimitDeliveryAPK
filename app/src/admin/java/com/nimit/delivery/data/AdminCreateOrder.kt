package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AdminTier(val min: Double, val max: Double, val v: Double)

/** ค่าตั้งราคาจาก settings/pricing (สูตรเดียวกับ order-create.html / menu.html) */
class AdminPricing(
    val gpPercent: Double,
    val gpSubPercent: Double,
    val walletPercent: Double,
    val riderPercent: Double,
    val deliveryFee: Double,
    val gpTiers: List<AdminTier>,
    val gpSubTiers: List<AdminTier>,
    val deliveryTiers: List<AdminTier>
) {
    // ช่วงแรกสุดรวมจุดเริ่มต้น ช่วงถัดไปตัดจุดเริ่มต้นออก (เหมือนเว็บ)
    private fun tierVal(tiers: List<AdminTier>, base: Double, fallback: Double): Double {
        val sorted = tiers.sortedBy { it.min }
        for (i in sorted.indices) {
            val t = sorted[i]
            val inRange = if (i == 0) (base >= t.min && base <= t.max) else (base > t.min && base <= t.max)
            if (inRange) return t.v
        }
        return fallback
    }

    fun calcPrice(base: Double, menuGp: Double, proxy: Boolean): Long {
        if (proxy) return Math.ceil(base).toLong()
        val g = tierVal(gpTiers, base, gpPercent) + menuGp
        return Math.ceil(base * (1.0 + g / 100.0)).toLong()
    }

    fun calcSub(opt: Double, proxy: Boolean): Long {
        if (proxy) return Math.ceil(opt).toLong()
        return Math.ceil(opt * (1.0 + tierVal(gpSubTiers, opt, gpSubPercent) / 100.0)).toLong()
    }

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLng = (lng2 - lng1) * Math.PI / 180.0
        val a = Math.pow(Math.sin(dLat / 2.0), 2.0) + Math.cos(lat1 * Math.PI / 180.0) * Math.cos(lat2 * Math.PI / 180.0) * Math.pow(Math.sin(dLng / 2.0), 2.0)
        return r * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
    }

    /** ค่าส่งตามช่วงระยะทาง ถ้าไม่มีพิกัดหรือไม่ตรงช่วงใด ใช้ค่าส่งคงที่ */
    fun deliveryFeeFor(sLat: Double?, sLng: Double?, cLat: Double?, cLng: Double?): Double {
        if (sLat != null && sLng != null && cLat != null && cLng != null && deliveryTiers.isNotEmpty()) {
            val km = distanceKm(sLat, sLng, cLat, cLng)
            return tierVal(deliveryTiers, km, deliveryFee)
        }
        return deliveryFee
    }

    fun calcOrder(items: List<AdminCartItem>, proxy: Boolean, delivery: Double): AdminOrderCalc {
        var subtotal = 0L
        var baseCost = 0.0
        for (i in items) {
            subtotal += i.price * i.qty
            if (!proxy) {
                var optBase = 0.0
                for (o in i.options) optBase += o.price
                baseCost += (i.basePrice + optBase) * i.qty
            }
        }
        val del = if (proxy) 0.0 else delivery
        val grand = subtotal + del
        val diff = if (proxy) (subtotal + del) else ((subtotal - baseCost) + del)
        val wp = if (walletPercent == 0.0) 10.0 else walletPercent
        val rp = if (riderPercent == 0.0) 16.0 else riderPercent
        return AdminOrderCalc(subtotal, baseCost, del, grand, diff, Math.round(diff * wp / 100.0), Math.round(diff * rp / 100.0))
    }
}

class AdminChosen(val groupTitle: String, val name: String, val price: Double)
class AdminCartItem(val name: String, val basePrice: Double, val price: Long, val qty: Int, val note: String, val options: List<AdminChosen>, val category: String)
class AdminCartLine(val menuId: String, val item: AdminCartItem)
class AdminOrderCalc(val subtotal: Long, val baseCost: Double, val deliveryFee: Double, val grandTotal: Double, val totalDiff: Double, val walletCut: Long, val riderEarn: Long)

class AdminShopRef(val id: String, val name: String, val phone: String, val address: String, val lat: Double?, val lng: Double?, val shopType: String, val isOpen: Boolean)
class AdminCust(val phone: String, val name: String, val address: String, val lat: Double?, val lng: Double?)

class AdminSubOpt(val name: String, val price: Double)
class AdminSubGroup(
    val title: String, val type: String, val required: Boolean, val multiSelect: Boolean, val options: List<AdminSubOpt>,
    val priceMin: Double, val priceMax: Double, val priceStep: Double, val kgRate: Double
)
class AdminOpt(val name: String, val price: Double, val subGroups: List<AdminSubGroup>)
class AdminGroup(
    val title: String, val type: String, val required: Boolean, val maxSelect: Int, val options: List<AdminOpt>,
    val priceMin: Double, val priceMax: Double, val priceStep: Double, val kgRate: Double
)
class AdminMenu(val id: String, val name: String, val price: Double, val gp: Double, val category: String, val groups: List<AdminGroup>)

private fun dbl(v: Any?, def: Double): Double = (v as? Number)?.toDouble() ?: def
private fun str(m: Map<*, *>, k: String): String = m[k]?.toString() ?: ""
private fun stepOf(m: Map<*, *>): Double {
    val s = dbl(m["priceStep"], 5.0)
    return if (s == 0.0) 5.0 else s
}
private fun numAny(d: Double): Any = if (d % 1.0 == 0.0) d.toLong() else d

private fun tiersOf(raw: Any?, key: String): List<AdminTier> {
    val l = raw as? List<*> ?: return emptyList()
    val out = ArrayList<AdminTier>()
    for (x in l) {
        val m = x as? Map<*, *> ?: continue
        out.add(AdminTier(dbl(m["min"], 0.0), dbl(m["max"], Double.POSITIVE_INFINITY), dbl(m[key], 0.0)))
    }
    return out
}

private fun parseSubGroup(m: Map<*, *>): AdminSubGroup {
    val opts = ArrayList<AdminSubOpt>()
    val raw = m["options"] as? List<*> ?: emptyList<Any?>()
    for (o in raw) {
        val om = o as? Map<*, *> ?: continue
        opts.add(AdminSubOpt(str(om, "name"), dbl(om["price"], 0.0)))
    }
    val type = str(m, "type").ifEmpty { "normal" }
    return AdminSubGroup(str(m, "title"), type, m["required"] == true, m["multiSelect"] == true, opts, dbl(m["priceMin"], 0.0), dbl(m["priceMax"], 0.0), stepOf(m), dbl(m["kgRate"], 0.0))
}

private fun parseGroup(m: Map<*, *>): AdminGroup {
    val opts = ArrayList<AdminOpt>()
    val raw = m["options"] as? List<*> ?: emptyList<Any?>()
    for (o in raw) {
        val om = o as? Map<*, *> ?: continue
        val subs = ArrayList<AdminSubGroup>()
        val sraw = om["subGroups"] as? List<*> ?: emptyList<Any?>()
        for (s in sraw) {
            val sm = s as? Map<*, *> ?: continue
            subs.add(parseSubGroup(sm))
        }
        opts.add(AdminOpt(str(om, "name"), dbl(om["price"], 0.0), subs))
    }
    val type = str(m, "type").ifEmpty { "normal" }
    return AdminGroup(str(m, "title"), type, m["required"] == true, (m["maxSelect"] as? Number)?.toInt() ?: 0, opts, dbl(m["priceMin"], 0.0), dbl(m["priceMax"], 0.0), stepOf(m), dbl(m["kgRate"], 0.0))
}

object AdminCreateApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private const val NOTIFY_URL = "https://nimitdelivery.vercel.app/api/send-order-notification"

    fun defaultPricing(): AdminPricing = AdminPricing(38.0, 0.0, 10.0, 16.0, 9.0, emptyList(), emptyList(), emptyList())

    suspend fun pricing(): AdminPricing {
        return try {
            val d = db.collection("settings").document("pricing").get().await()
            if (!d.exists()) return defaultPricing()
            val m: Map<String, Any> = d.data ?: emptyMap()
            AdminPricing(
                dbl(m["gpPercent"], 38.0), dbl(m["gpSubPercent"], 0.0), dbl(m["walletPercent"], 10.0), dbl(m["riderPercent"], 16.0), dbl(m["deliveryFee"], 9.0),
                tiersOf(m["gpTiers"], "gp"), tiersOf(m["gpSubTiers"], "gp"), tiersOf(m["deliveryTiers"], "fee")
            )
        } catch (e: Exception) {
            defaultPricing()
        }
    }

    suspend fun shops(): List<AdminShopRef> {
        val snap = db.collection("shops").get().await()
        return snap.documents.map {
            AdminShopRef(
                it.id, it.getString("name") ?: "", it.getString("phone") ?: "", it.getString("address") ?: "",
                (it.get("lat") as? Number)?.toDouble(), (it.get("lng") as? Number)?.toDouble(),
                it.getString("shopType") ?: "normal", it.getBoolean("isOpen") == true
            )
        }
    }

    suspend fun menus(shopId: String): List<AdminMenu> {
        val snap = db.collection("shops").document(shopId).collection("menus").get().await()
        val out = ArrayList<AdminMenu>()
        for (d in snap.documents) {
            if (d.get("available") == false) continue
            val groups = ArrayList<AdminGroup>()
            val raw = d.get("optionGroups") as? List<*> ?: emptyList<Any?>()
            for (g in raw) {
                val gm = g as? Map<*, *> ?: continue
                groups.add(parseGroup(gm))
            }
            out.add(AdminMenu(d.id, d.getString("name") ?: "", dbl(d.get("price"), 0.0), dbl(d.get("gp"), 0.0), d.getString("category") ?: "", groups))
        }
        return out
    }

    /** รายชื่อลูกค้า: อ่านจาก customers ถ้าไม่ได้สิทธิ์ ใช้ข้อมูลลูกค้าจากออเดอร์ล่าสุด 500 ใบแทน */
    suspend fun customers(): List<AdminCust> {
        try {
            val snap = db.collection("customers").get().await()
            return snap.documents.map {
                AdminCust(it.id, it.getString("name") ?: "", it.getString("address") ?: "", (it.get("lat") as? Number)?.toDouble(), (it.get("lng") as? Number)?.toDouble())
            }
        } catch (e: Exception) {
            val snap = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(500).get().await()
            val seen = LinkedHashMap<String, AdminCust>()
            for (d in snap.documents) {
                val cm = d.get("customer") as? Map<*, *> ?: continue
                val ph: String = cm["phone"]?.toString() ?: ""
                if (ph.isEmpty() || seen.containsKey(ph)) continue
                seen[ph] = AdminCust(ph, cm["name"]?.toString() ?: "", cm["address"]?.toString() ?: "", (cm["lat"] as? Number)?.toDouble(), (cm["lng"] as? Number)?.toDouble())
            }
            return seen.values.toList()
        }
    }

    fun randomOrderId(): String = "NM-" + (1000 + (Math.random() * 9000.0).toInt()).toString()

    private fun genGroupId(): String =
        "GRP-" + java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString((Math.random() * 1679616.0).toLong(), 36)

    /** สร้างออเดอร์ (โครงข้อมูลเหมือน order-create.html) คืนรหัสออเดอร์จริง ไม่เขียนทับออเดอร์เดิมถ้ารหัสซ้ำ */
    suspend fun create(preferredId: String, shop: AdminShopRef, cus: AdminCust, items: List<AdminCartItem>, c: AdminOrderCalc): String {
        var oid = ""
        var cand = preferredId
        for (i in 0 until 8) {
            val snap = db.collection("orders").document(cand).get().await()
            if (!snap.exists()) { oid = cand; break }
            cand = randomOrderId()
        }
        if (oid.isEmpty()) throw AdminBizException("สร้างรหัสออเดอร์ไม่สำเร็จ ลองใหม่อีกครั้ง")

        val itemMaps = ArrayList<Map<String, Any?>>()
        for (ci in items) {
            val opts = ArrayList<Map<String, Any?>>()
            for (o in ci.options) {
                val om = HashMap<String, Any?>()
                om["groupTitle"] = o.groupTitle
                om["name"] = o.name
                om["price"] = numAny(o.price)
                opts.add(om)
            }
            val im = HashMap<String, Any?>()
            im["name"] = ci.name
            im["price"] = ci.price
            im["basePrice"] = numAny(ci.basePrice)
            im["qty"] = ci.qty
            im["options"] = opts
            im["note"] = ci.note
            im["imgUrl"] = ""
            im["category"] = ci.category
            itemMaps.add(im)
        }
        val cm = HashMap<String, Any?>()
        cm["name"] = cus.name
        cm["phone"] = cus.phone
        cm["address"] = cus.address
        cm["lat"] = cus.lat
        cm["lng"] = cus.lng
        val data = HashMap<String, Any?>()
        data["orderId"] = oid
        data["groupId"] = genGroupId()
        data["shopId"] = shop.id
        data["shopName"] = shop.name
        data["shopType"] = shop.shopType
        data["shopPhone"] = shop.phone
        data["shopAddress"] = shop.address
        data["shopLat"] = shop.lat
        data["shopLng"] = shop.lng
        data["customer"] = cm
        data["items"] = itemMaps
        data["subtotal"] = c.subtotal
        data["baseCost"] = numAny(c.baseCost)
        data["deliveryFee"] = numAny(c.deliveryFee)
        data["grandTotal"] = numAny(c.grandTotal)
        data["totalDiff"] = numAny(c.totalDiff)
        data["walletCut"] = c.walletCut
        data["riderEarn"] = c.riderEarn
        data["paymentMethod"] = "cash"
        data["status"] = "pending"
        data["createdBy"] = "admin"
        data["createdAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(oid).set(data).await()

        // แจ้งเตือนออเดอร์ใหม่ (ไม่รอผล ไม่ทำให้การสร้างล้มเหลวถ้าแจ้งเตือนพลาด)
        CoroutineScope(Dispatchers.IO).launch {
            try { notifyNew(shop.id, shop.name, oid) } catch (e: Exception) { }
        }
        return oid
    }

    private suspend fun notifyNew(shopId: String, shopName: String, orderId: String) {
        val tokens = ArrayList<String>()
        val admins = db.collection("fcmTokens").whereEqualTo("role", "admin").get().await()
        for (d in admins.documents) { val t = d.getString("token"); if (!t.isNullOrEmpty()) tokens.add(t) }
        val riders = db.collection("fcmTokens").whereEqualTo("role", "rider").get().await()
        for (d in riders.documents) { val t = d.getString("token"); if (!t.isNullOrEmpty()) tokens.add(t) }
        val shopTok = db.collection("fcmTokens").document(shopId + "_shop").get().await()
        val st = shopTok.getString("token")
        if (shopTok.exists() && !st.isNullOrEmpty()) tokens.add(st)
        if (tokens.isEmpty()) return
        val j = JSONObject()
        j.put("tokens", JSONArray(tokens))
        j.put("title", "มีออเดอร์ใหม่เข้ามา 🔔")
        j.put("body", orderId + " จาก " + (if (shopName.isEmpty()) "ร้านค้า" else shopName))
        j.put("orderId", orderId)
        j.put("url", "/admin.html")
        withContext(Dispatchers.IO) {
            val conn = URL(NOTIFY_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.outputStream.use { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }

    /** โหลดออเดอร์เดิมมาแก้: ร้าน/ลูกค้า/รายการ ใช้ข้อมูลที่เก็บไว้ในออเดอร์เอง (ประเภทร้านอ่านจาก shops) */
    suspend fun loadForEdit(orderDocId: String): AdminLoadedOrder {
        val snap = db.collection("orders").document(orderDocId).get().await()
        if (!snap.exists()) throw AdminBizException("ไม่พบออเดอร์นี้ อาจถูกลบไปแล้ว")
        val m: Map<String, Any> = snap.data ?: emptyMap()
        val sid: String = str(m, "shopId")
        var shopType: String = str(m, "shopType").ifEmpty { "normal" }
        try {
            val ss = db.collection("shops").document(sid).get().await()
            if (ss.exists()) shopType = ss.getString("shopType") ?: "normal"
        } catch (e: Exception) { }
        val shop = AdminShopRef(sid, str(m, "shopName"), str(m, "shopPhone"), str(m, "shopAddress"), (m["shopLat"] as? Number)?.toDouble(), (m["shopLng"] as? Number)?.toDouble(), shopType, true)
        val cm = m["customer"] as? Map<*, *>
        val cus = AdminCust(
            cm?.get("phone")?.toString() ?: "", cm?.get("name")?.toString() ?: "", cm?.get("address")?.toString() ?: "",
            (cm?.get("lat") as? Number)?.toDouble(), (cm?.get("lng") as? Number)?.toDouble()
        )
        val lines = ArrayList<AdminCartLine>()
        val raw = m["items"] as? List<*> ?: emptyList<Any?>()
        for (i in raw.indices) {
            val im = raw[i] as? Map<*, *> ?: continue
            val opts = ArrayList<AdminChosen>()
            val ol = im["options"] as? List<*> ?: emptyList<Any?>()
            for (o in ol) {
                val om = o as? Map<*, *> ?: continue
                opts.add(AdminChosen(str(om, "groupTitle"), str(om, "name"), dbl(om["price"], 0.0)))
            }
            val price: Long = Math.round(dbl(im["price"], 0.0))
            val qty: Int = (im["qty"] as? Number)?.toInt() ?: 1
            lines.add(AdminCartLine("edit_" + i, AdminCartItem(str(im, "name"), dbl(im["basePrice"], 0.0), price, qty, str(im, "note"), opts, str(im, "category"))))
        }
        return AdminLoadedOrder(shop, cus, lines, str(m, "status"))
    }

    /** บันทึกการแก้ไขออเดอร์เดิม: เขียนทับเฉพาะ ร้าน/ลูกค้า/รายการ/ราคา ไม่แตะสถานะ ไรเดอร์ รหัส เวลาสร้าง */
    suspend fun update(orderDocId: String, shop: AdminShopRef, cus: AdminCust, items: List<AdminCartItem>, c: AdminOrderCalc) {
        val ref = db.collection("orders").document(orderDocId)
        val cur = ref.get().await()
        if (!cur.exists()) throw AdminBizException("ไม่พบออเดอร์นี้ อาจถูกลบไปแล้ว")
        val st: String = cur.getString("status") ?: "pending"
        if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว แก้ไขรายการไม่ได้")
        val itemMaps = ArrayList<Map<String, Any?>>()
        for (ci in items) {
            val opts = ArrayList<Map<String, Any?>>()
            for (o in ci.options) {
                val om = HashMap<String, Any?>()
                om["groupTitle"] = o.groupTitle
                om["name"] = o.name
                om["price"] = numAny(o.price)
                opts.add(om)
            }
            val im = HashMap<String, Any?>()
            im["name"] = ci.name
            im["price"] = ci.price
            im["basePrice"] = numAny(ci.basePrice)
            im["qty"] = ci.qty
            im["options"] = opts
            im["note"] = ci.note
            im["imgUrl"] = ""
            im["category"] = ci.category
            itemMaps.add(im)
        }
        val cm = HashMap<String, Any?>()
        cm["name"] = cus.name
        cm["phone"] = cus.phone
        cm["address"] = cus.address
        cm["lat"] = cus.lat
        cm["lng"] = cus.lng
        val data = HashMap<String, Any?>()
        data["shopId"] = shop.id
        data["shopName"] = shop.name
        data["shopType"] = shop.shopType
        data["shopPhone"] = shop.phone
        data["shopAddress"] = shop.address
        data["shopLat"] = shop.lat
        data["shopLng"] = shop.lng
        data["customer"] = cm
        data["items"] = itemMaps
        data["subtotal"] = c.subtotal
        data["baseCost"] = numAny(c.baseCost)
        data["deliveryFee"] = numAny(c.deliveryFee)
        data["grandTotal"] = numAny(c.grandTotal)
        data["totalDiff"] = numAny(c.totalDiff)
        data["walletCut"] = c.walletCut
        data["riderEarn"] = c.riderEarn
        data["paymentMethod"] = "cash"
        data["updatedAt"] = FieldValue.serverTimestamp()
        ref.update(data).await()
    }
}

class AdminLoadedOrder(val shop: AdminShopRef, val cus: AdminCust, val lines: List<AdminCartLine>, val status: String)
