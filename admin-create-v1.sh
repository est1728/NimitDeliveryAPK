#!/bin/bash
# หน้าสร้างออเดอร์ของแอปแอดมิน (ถอดจาก order-create.html): เลือกร้าน -> เลือกเมนู (ตัวเลือก/ตัวเลือกย่อย/ราคา/กิโลขีด/เมนูชั่วคราว)
# -> เลือกลูกค้า -> คำนวณราคา+ค่าส่งตามระยะ -> สร้างออเดอร์ + แจ้งเตือนออเดอร์ใหม่  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
N=$J/ui/AdminNav.kt
[ -f $N ] || die "ไม่เจอ $N"
[ -f $J/data/AdminOrders.kt ] || die "ไม่เจอ AdminOrders.kt"
grep -q 'onCreate' $N || die "AdminNav.kt ยังไม่ผ่าน admin-ui-v1.sh"

# ---------- ข้อมูล: ราคา/เมนู/สร้างออเดอร์ ----------
cat > $J/data/AdminCreateOrder.kt <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun customers(): List<AdminCust> {
        val snap = db.collection("customers").get().await()
        return snap.documents.map {
            AdminCust(it.id, it.getString("name") ?: "", it.getString("address") ?: "", (it.get("lat") as? Number)?.toDouble(), (it.get("lng") as? Number)?.toDouble())
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
}
KT_EOF
echo "AdminCreateOrder.kt (data): เขียนแล้ว"

# ---------- หน้าจอสร้างออเดอร์ ----------
cat > $J/ui/AdminCreateOrder.kt <<'KT_EOF'
package com.nimit.delivery.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminCartItem
import com.nimit.delivery.data.AdminCartLine
import com.nimit.delivery.data.AdminChosen
import com.nimit.delivery.data.AdminCreateApi
import com.nimit.delivery.data.AdminCust
import com.nimit.delivery.data.AdminMenu
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminPricing
import com.nimit.delivery.data.AdminShopRef
import kotlinx.coroutines.launch

private val CGreen = Color(0xFF16A34A)
private val CGreenL = Color(0xFFDCFCE7)
private val CRed = Color(0xFFDC2626)
private val CRedL = Color(0xFFFEE2E2)
private val CYellow = Color(0xFFF59E0B)
private val CLine = Color(0xFFF0F2F5)

private fun cToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun cFmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private fun cKheed(p: Pair<Double, Double>): Double = p.first * 10.0 + p.second

// ===== สถานะตัวเลือกในหน้าต่างเลือกเมนู =====
private class AdminPopup(val menu: AdminMenu) {
    val single = mutableStateMapOf<Int, Int>()
    val multi = mutableStateMapOf<Int, Set<Int>>()
    val priceVal = mutableStateMapOf<Int, Double>()
    val kgVal = mutableStateMapOf<Int, Pair<Double, Double>>()
    val subSingle = mutableStateMapOf<String, Int>()
    val subMulti = mutableStateMapOf<String, Set<Int>>()
    val subQty = mutableStateMapOf<String, Int>()
    val subPrice = mutableStateMapOf<String, Double>()
    val subKg = mutableStateMapOf<String, Pair<Double, Double>>()
    val note = mutableStateOf("")

    init {
        for (gi in menu.groups.indices) {
            val g = menu.groups[gi]
            if (g.type == "price") priceVal[gi] = g.priceMin
            if (g.type == "kg") kgVal[gi] = Pair(0.0, 0.0)
        }
    }

    fun initSub(gi: Int, oi: Int) {
        val o = menu.groups[gi].options[oi]
        for (sgi in o.subGroups.indices) {
            val sg = o.subGroups[sgi]
            val key = gi.toString() + "-" + oi + "-" + sgi
            if (sg.required && sg.type == "price") subPrice[key] = sg.priceMin
            if (sg.required && sg.type == "kg") subKg[key] = Pair(0.0, 0.0)
        }
    }

    fun clearSub(gi: Int, oi: Int) {
        val prefix = gi.toString() + "-" + oi + "-"
        subSingle.keys.filter { it.startsWith(prefix) }.forEach { subSingle.remove(it) }
        subMulti.keys.filter { it.startsWith(prefix) }.forEach { subMulti.remove(it) }
        subQty.keys.filter { it.startsWith(prefix) }.forEach { subQty.remove(it) }
        subPrice.keys.filter { it.startsWith(prefix) }.forEach { subPrice.remove(it) }
        subKg.keys.filter { it.startsWith(prefix) }.forEach { subKg.remove(it) }
    }

    fun selectOption(gi: Int, oi: Int) {
        val g = menu.groups[gi]
        if (g.required) {
            val prev = single[gi]
            if (prev == oi) {
                single.remove(gi)
                clearSub(gi, oi)
            } else {
                single[gi] = oi
                if (prev != null) clearSub(gi, prev)
                initSub(gi, oi)
            }
        } else {
            val cur: Set<Int> = multi[gi] ?: emptySet()
            if (cur.contains(oi)) {
                multi[gi] = cur - oi
                clearSub(gi, oi)
            } else {
                if (g.maxSelect > 0 && cur.size >= g.maxSelect) return
                multi[gi] = cur + oi
                initSub(gi, oi)
            }
        }
    }
}

private class PopupMath(val pr: AdminPricing, val proxy: Boolean) {
    fun subExtra(p: AdminPopup, gi: Int, oi: Int): Long {
        val o = p.menu.groups[gi].options[oi]
        var s = 0L
        for (sgi in o.subGroups.indices) {
            val sg = o.subGroups[sgi]
            val key = gi.toString() + "-" + oi + "-" + sgi
            if (sg.type == "price") {
                val v = p.subPrice[key]
                if (v != null) s += pr.calcSub(v, proxy)
            } else if (sg.type == "kg") {
                val kk = p.subKg[key]
                if (kk != null) {
                    val base = cKheed(kk) * sg.kgRate
                    if (base != 0.0) s += pr.calcSub(base, proxy)
                }
            } else if (sg.multiSelect) {
                val set = p.subMulti[key]
                if (set != null) {
                    for (soi in set) {
                        val so = sg.options.getOrNull(soi)
                        val q = p.subQty[key + "-" + soi] ?: 1
                        s += pr.calcSub(so?.price ?: 0.0, proxy) * q
                    }
                }
            } else {
                val sel = p.subSingle[key]
                if (sel != null) {
                    val so = sg.options.getOrNull(sel)
                    if (so != null) s += pr.calcSub(so.price, proxy)
                }
            }
        }
        return s
    }

    fun groupExtra(p: AdminPopup, gi: Int): Long {
        val g = p.menu.groups[gi]
        if (g.type == "price") {
            val v = p.priceVal[gi] ?: return 0L
            return pr.calcPrice(v, p.menu.gp, proxy)
        }
        if (g.type == "kg") {
            val kk = p.kgVal[gi] ?: return 0L
            val base = cKheed(kk) * g.kgRate
            return if (base != 0.0) pr.calcPrice(base, p.menu.gp, proxy) else 0L
        }
        if (g.required) {
            val sel = p.single[gi] ?: return 0L
            val opt = g.options.getOrNull(sel) ?: return 0L
            return pr.calcSub(opt.price, proxy) + subExtra(p, gi, sel)
        }
        val set = p.multi[gi] ?: return 0L
        var s = 0L
        for (oi in set) {
            val opt = g.options.getOrNull(oi)
            s += pr.calcSub(opt?.price ?: 0.0, proxy) + subExtra(p, gi, oi)
        }
        return s
    }

    fun total(p: AdminPopup): Long {
        var t = pr.calcPrice(p.menu.price, p.menu.gp, proxy)
        for (gi in p.menu.groups.indices) t += groupExtra(p, gi)
        return t
    }

    private fun subChosen(p: AdminPopup, gi: Int, oi: Int, out: ArrayList<AdminChosen>) {
        val g = p.menu.groups[gi]
        val o = g.options[oi]
        for (sgi in o.subGroups.indices) {
            val sg = o.subGroups[sgi]
            val key = gi.toString() + "-" + oi + "-" + sgi
            val title = g.title + " → " + sg.title
            if (sg.type == "price") {
                val v = p.subPrice[key]
                if (v != null) out.add(AdminChosen(title, sg.title + " ฿" + cFmt(v), v))
            } else if (sg.type == "kg") {
                val kk = p.subKg[key]
                if (kk != null && cKheed(kk) > 0.0) out.add(AdminChosen(title, cFmt(kk.first) + " กก. " + cFmt(kk.second) + " ขีด", cKheed(kk) * sg.kgRate))
            } else if (sg.multiSelect) {
                val set = p.subMulti[key]
                if (set != null) {
                    for (soi in set) {
                        val so = sg.options.getOrNull(soi) ?: continue
                        val q = p.subQty[key + "-" + soi] ?: 1
                        out.add(AdminChosen(title, if (q > 1) so.name + " x" + q else so.name, so.price * q))
                    }
                }
            } else {
                val sel = p.subSingle[key]
                if (sel != null) {
                    val so = sg.options.getOrNull(sel)
                    if (so != null) out.add(AdminChosen(title, so.name, so.price))
                }
            }
        }
    }

    fun chosen(p: AdminPopup): List<AdminChosen> {
        val out = ArrayList<AdminChosen>()
        for (gi in p.menu.groups.indices) {
            val g = p.menu.groups[gi]
            if (g.type == "price") {
                val v = p.priceVal[gi]
                if (v != null) out.add(AdminChosen(g.title, g.title + " ฿" + cFmt(v), v))
            } else if (g.type == "kg") {
                val kk = p.kgVal[gi]
                if (kk != null && cKheed(kk) > 0.0) out.add(AdminChosen(g.title, cFmt(kk.first) + " กก. " + cFmt(kk.second) + " ขีด", cKheed(kk) * g.kgRate))
            } else if (g.required) {
                val sel = p.single[gi]
                if (sel != null) {
                    val opt = g.options.getOrNull(sel)
                    out.add(AdminChosen(g.title, opt?.name ?: "", opt?.price ?: 0.0))
                    subChosen(p, gi, sel, out)
                }
            } else {
                val set = p.multi[gi]
                if (set != null) {
                    for (oi in set) {
                        val opt = g.options.getOrNull(oi)
                        out.add(AdminChosen(g.title, opt?.name ?: "", opt?.price ?: 0.0))
                        subChosen(p, gi, oi, out)
                    }
                }
            }
        }
        return out
    }

    /** คืนชื่อกลุ่มที่ยังไม่ได้เลือก (บังคับ) หรือ null ถ้าครบแล้ว */
    fun missing(p: AdminPopup): String? {
        for (gi in p.menu.groups.indices) {
            val g = p.menu.groups[gi]
            if (g.type == "price") continue
            if (g.type == "kg") {
                val kk = p.kgVal[gi]
                if (g.required && (kk == null || cKheed(kk) == 0.0)) return g.title
                continue
            }
            val sels = ArrayList<Int>()
            if (g.required) {
                val s = p.single[gi]
                if (s == null) return g.title
                sels.add(s)
            } else {
                val set = p.multi[gi]
                if (set != null) sels.addAll(set)
            }
            for (oi in sels) {
                val o = g.options[oi]
                for (sgi in o.subGroups.indices) {
                    val sg = o.subGroups[sgi]
                    if (!sg.required) continue
                    val key = gi.toString() + "-" + oi + "-" + sgi
                    val bad = if (sg.type == "price") (p.subPrice[key] == null)
                    else if (sg.type == "kg") { val kk = p.subKg[key]; kk == null || cKheed(kk) == 0.0 }
                    else if (sg.multiSelect) { val set = p.subMulti[key]; set == null || set.isEmpty() }
                    else (p.subSingle[key] == null)
                    if (bad) return sg.title
                }
            }
        }
        return null
    }
}

@Composable
fun AdminCreateOrderScreen(store: AdminOrdersStore, onBack: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewId = remember { AdminCreateApi.randomOrderId() }
    var pricing by remember { mutableStateOf(AdminCreateApi.defaultPricing()) }
    var allShops by remember { mutableStateOf<List<AdminShopRef>?>(null) }
    var shopsError by remember { mutableStateOf<String?>(null) }
    var shop by remember { mutableStateOf<AdminShopRef?>(null) }
    var cus by remember { mutableStateOf<AdminCust?>(null) }
    val cart = remember { mutableStateListOf<AdminCartLine>() }
    var sheet by remember { mutableStateOf("") }
    var popup by remember { mutableStateOf<AdminPopup?>(null) }
    var menus by remember { mutableStateOf<List<AdminMenu>?>(null) }
    var menusError by remember { mutableStateOf<String?>(null) }
    var customers by remember { mutableStateOf<List<AdminCust>?>(null) }
    var cusError by remember { mutableStateOf<String?>(null) }
    var cusQuery by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var showTemp by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var tempPrice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { pricing = AdminCreateApi.pricing() }
    LaunchedEffect(Unit) {
        try { allShops = AdminCreateApi.shops() } catch (e: Exception) { shopsError = e.message ?: "โหลดร้านค้าไม่สำเร็จ" }
    }
    val shopForMenu: AdminShopRef? = shop
    LaunchedEffect(sheet, shopForMenu?.id) {
        val sh = shopForMenu
        if (sheet == "menu" && sh != null) {
            menus = null
            menusError = null
            try { menus = AdminCreateApi.menus(sh.id) } catch (e: Exception) { menusError = e.message ?: "โหลดเมนูไม่สำเร็จ" }
        }
    }
    LaunchedEffect(sheet) {
        if (sheet == "cus" && customers == null) {
            cusError = null
            try { customers = AdminCreateApi.customers() } catch (e: Exception) { cusError = e.message ?: "โหลดรายชื่อลูกค้าไม่สำเร็จ" }
        }
    }

    BackHandler {
        if (popup != null) popup = null
        else if (sheet.isNotEmpty()) sheet = ""
        else onBack()
    }

    val sh: AdminShopRef? = shop
    val cu: AdminCust? = cus
    val proxy: Boolean = sh?.shopType == "proxy"
    val items: List<AdminCartItem> = cart.map { it.item }
    val delivery: Double = if (sh != null && cu != null) pricing.deliveryFeeFor(sh.lat, sh.lng, cu.lat, cu.lng) else pricing.deliveryFee
    val calc = if (items.isEmpty()) null else pricing.calcOrder(items, proxy, delivery)
    val canCreate: Boolean = sh != null && cu != null && items.isNotEmpty() && !busy
    val math = PopupMath(pricing, proxy)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(C.Gray)) {
            // แถบบน
            Row(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Text("สร้างออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(previewId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CSection("รายละเอียด", null) {
                    CRow("รหัส", previewId, null)
                    CRow("สถานะ", "รอสร้าง", CYellow)
                    CRow("สร้างโดย", "แอดมิน", null)
                }
                CSection("ข้อมูลร้านค้า", null) {
                    CPlusBox(if (sh != null) "✓ " + sh.name else "กดเพื่อเลือกร้านค้า", sh != null) { sheet = "shop" }
                    CRow("ชื่อร้าน", sh?.name ?: "-", if (sh == null) C.Subtext else null)
                }
                CSection("ข้อมูลลูกค้า", null) {
                    CPlusBox(if (cu != null) "✓ " + (cu.name.ifEmpty { cu.phone }) else "กดเพื่อเลือกลูกค้า", cu != null) { sheet = "cus" }
                    CRow("ชื่อ", cu?.name?.ifEmpty { "-" } ?: "-", if (cu == null) C.Subtext else null)
                    CRow("โทร", cu?.phone?.ifEmpty { "-" } ?: "-", if (cu == null) C.Subtext else null)
                    CRow("ที่อยู่", cu?.address?.ifEmpty { "-" } ?: "-", if (cu == null) C.Subtext else null)
                    Text(
                        "เปลี่ยน  :  กดเพื่อเลือกใหม่", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                        modifier = Modifier.fillMaxWidth().clickable { sheet = "cus" }.padding(vertical = 7.dp)
                    )
                }
                CSectionEdit("สรุปคำสั่งซื้อ", "+ แก้ไข", { sheet = if (sh == null) "shop" else "menu" }) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("สินค้า", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, modifier = Modifier.weight(1f))
                        Text("จำนวน", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                        Text("ราคา", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, textAlign = TextAlign.End, modifier = Modifier.width(66.dp))
                        Spacer(Modifier.width(32.dp))
                    }
                    HorizontalDivider(thickness = 2.dp, color = C.Gray)
                    if (cart.isEmpty()) {
                        Text("ยังไม่มีรายการ", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    for (line in cart.toList()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.item.name, fontSize = 14.sp, color = C.Text)
                                for (o in line.item.options) Text("- " + o.name, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(start = 8.dp))
                                if (line.item.note.isNotEmpty()) Text("หมายเหตุ: " + line.item.note, fontSize = 12.sp, color = CRed, modifier = Modifier.padding(start = 8.dp))
                            }
                            Text("×" + line.item.qty, fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                            Text("฿" + (line.item.price * line.item.qty), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Primary, textAlign = TextAlign.End, modifier = Modifier.width(66.dp))
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(CRedL).clickable { cart.remove(line) },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = CRed) }
                        }
                        HorizontalDivider(color = Color(0xFFF5F6F8))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("รวม", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                        Text(if (calc == null) "-" else "฿" + calc.subtotal, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                    }
                }
                CSection("สรุปราคา", null) {
                    CPriceRow("เรียกเก็บลูกค้า", if (calc == null) "-" else "฿" + Math.round(calc.grandTotal), false)
                    CPriceRow("จ่ายร้าน (ต้นทุน)", if (calc == null) "-" else "฿" + Math.round(calc.baseCost), false)
                    CPriceRow("หักจากกระเป๋า", if (calc == null) "-" else "฿" + calc.walletCut, false)
                    CPriceRow("ค่าส่ง", if (calc == null) "-" else "฿" + Math.round(calc.deliveryFee), false)
                    CPriceRow("รายได้ไรเดอร์", if (calc == null) "-" else "฿" + calc.riderEarn, true)
                }
                Spacer(Modifier.height(4.dp))
            }

            // แถบปุ่มสร้าง
            Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (canCreate) Brush.linearGradient(listOf(CGreen, Color(0xFF15803D))) else Brush.linearGradient(listOf(Color(0xFFC5CFE8), Color(0xFFC5CFE8))))
                        .clickable(enabled = canCreate) {
                            val s2 = shop
                            val c2 = cus
                            val k2 = calc
                            if (s2 != null && c2 != null && k2 != null) {
                                busy = true
                                scope.launch {
                                    try {
                                        val oid = AdminCreateApi.create(previewId, s2, c2, items, k2)
                                        cToast(ctx, "สร้างออเดอร์ " + oid + " แล้ว")
                                        onCreated()
                                    } catch (e: Exception) {
                                        cToast(ctx, "เกิดข้อผิดพลาด: " + (e.message ?: ""))
                                    }
                                    busy = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(if (busy) "กำลังสร้าง..." else "สร้างออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            }
        }

        // ===== เลือกร้านค้า =====
        if (sheet == "shop") {
            CSheet("เลือกร้านค้า", { sheet = "" }, null) {
                val list = allShops
                val scopeIds = store.shopIds.value
                if (shopsError != null) {
                    CMsg("โหลดไม่ได้: " + shopsError)
                } else if (list == null) {
                    CMsg("กำลังโหลด...")
                } else {
                    val open = list.filter { it.isOpen && scopeIds.contains(it.id) }
                    if (open.isEmpty()) CMsg("ไม่มีร้านเปิดในเขตนี้")
                    for (s in open) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val cur = shop
                                if (cur != null && cur.id != s.id) cart.clear()
                                shop = s
                                sheet = "menu"
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.PrimaryLight), contentAlignment = Alignment.Center) {
                                Text(s.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
                            Text("›", fontSize = 18.sp, color = Color(0xFF94A3B8))
                        }
                        HorizontalDivider(color = Color(0xFFF5F6F8))
                    }
                }
            }
        }

        // ===== เลือกเมนู =====
        if (sheet == "menu" && sh != null) {
            CSheet(
                sh.name, { sheet = "" },
                {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).clickable { sheet = "" },
                            contentAlignment = Alignment.Center
                        ) { Text("ยืนยันเมนู", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                }
            ) {
                // เมนูชั่วคราว
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).height(38.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight)
                        .clickable { showTemp = !showTemp },
                    contentAlignment = Alignment.Center
                ) { Text("+ เพิ่มเมนูชั่วคราว (ไม่อยู่ในระบบ)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
                if (showTemp) {
                    OutlinedTextField(
                        value = tempName, onValueChange = { tempName = it }, singleLine = true,
                        placeholder = { Text("ชื่อเมนู เช่น ข้าวผัดหมู") }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tempPrice, onValueChange = { tempPrice = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                            placeholder = { Text("ราคา (บาท)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(C.Primary).clickable {
                                val price = tempPrice.toDoubleOrNull() ?: 0.0
                                if (tempName.trim().isEmpty()) {
                                    cToast(ctx, "กรุณาใส่ชื่อเมนู")
                                } else if (price <= 0.0) {
                                    cToast(ctx, "กรุณาใส่ราคาที่ถูกต้อง")
                                } else {
                                    val disp = pricing.calcPrice(price, 0.0, proxy)
                                    cart.add(AdminCartLine("temp", AdminCartItem(tempName.trim(), price, disp, 1, "", emptyList(), "เมนูชั่วคราว")))
                                    tempName = ""
                                    tempPrice = ""
                                }
                            }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("เพิ่ม", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                    Text(
                        if (proxy) "ร้านนี้เป็นร้านฝากซื้อ — ราคาที่ใส่คือราคาสุทธิ ไม่บวก GP" else "ราคาที่ใส่จะถูกบวก GP ตามปกติเหมือนเมนูอื่น",
                        fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                }
                val list = menus
                if (menusError != null) {
                    CMsg("โหลดไม่ได้: " + menusError)
                } else if (list == null) {
                    CMsg("กำลังโหลดเมนู...")
                } else if (list.isEmpty()) {
                    CMsg("ร้านนี้ยังไม่มีเมนู")
                } else {
                    for (m in list) {
                        val inCart = cart.filter { it.menuId == m.id }.sumOf { it.item.qty }
                        Row(
                            Modifier.fillMaxWidth().clickable { popup = AdminPopup(m) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                Text("฿" + pricing.calcPrice(m.price, m.gp, proxy), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (inCart > 0) Text("×" + inCart, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(end = 10.dp))
                            Box(Modifier.size(32.dp).clip(CircleShape).background(C.PrimaryLight), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF5F6F8))
                    }
                }
            }
        }

        // ===== เลือกลูกค้า =====
        if (sheet == "cus") {
            CSheet("เลือกลูกค้า", { sheet = "" }, null) {
                OutlinedTextField(
                    value = cusQuery, onValueChange = { cusQuery = it }, singleLine = true,
                    placeholder = { Text("ค้นหาชื่อหรือเบอร์...") }, modifier = Modifier.fillMaxWidth()
                )
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(38.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight).clickable { showManual = true },
                    contentAlignment = Alignment.Center
                ) { Text("+ กรอกข้อมูลลูกค้าเอง", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
                val list = customers
                if (cusError != null) {
                    CMsg("โหลดรายชื่อไม่ได้: " + cusError)
                } else if (list == null) {
                    CMsg("กำลังโหลด...")
                } else {
                    val q = cusQuery.trim().lowercase()
                    val shown = if (q.isEmpty()) list else list.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
                    if (shown.isEmpty()) CMsg("ไม่พบลูกค้า")
                    for (c in shown) {
                        Row(
                            Modifier.fillMaxWidth().clickable { cus = c; sheet = "" }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))),
                                contentAlignment = Alignment.Center
                            ) { Text((c.name.ifEmpty { c.phone }).take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name.ifEmpty { "-" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                                Text(c.phone, fontSize = 12.sp, color = C.Subtext)
                            }
                            Text("›", fontSize = 18.sp, color = Color(0xFF94A3B8))
                        }
                        HorizontalDivider(color = Color(0xFFF5F6F8))
                    }
                }
            }
        }

        // ===== ตัวเลือกของเมนู =====
        val pp = popup
        if (pp != null) {
            CSheet(
                pp.menu.name, { popup = null },
                {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                                .clickable { popup = null },
                            contentAlignment = Alignment.Center
                        ) { Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text) }
                        Box(
                            Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                                .clickable {
                                    val miss = math.missing(pp)
                                    if (miss != null) {
                                        cToast(ctx, "กรุณาเลือก " + miss)
                                    } else {
                                        val chosen = math.chosen(pp)
                                        val total = math.total(pp)
                                        cart.add(AdminCartLine(pp.menu.id, AdminCartItem(pp.menu.name, pp.menu.price, total, 1, pp.note.value, chosen, pp.menu.category)))
                                        popup = null
                                        sheet = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("ยืนยัน ฿" + math.total(pp), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                }
            ) {
                Text("฿" + pricing.calcPrice(pp.menu.price, pp.menu.gp, proxy), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.padding(bottom = 10.dp))
                PopupBody(pp, math, pricing, proxy)
                Text("คำแนะนำเพิ่มเติม", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                OutlinedTextField(
                    value = pp.note.value, onValueChange = { pp.note.value = it },
                    placeholder = { Text("เช่น ไม่ใส่ผัก, หวานน้อย") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showManual) {
            ManualCustomerDialog(
                onDismiss = { showManual = false },
                onSave = { c -> cus = c; showManual = false; sheet = "" }
            )
        }
    }
}

@Composable
private fun PopupBody(p: AdminPopup, math: PopupMath, pricing: AdminPricing, proxy: Boolean) {
    for (gi in p.menu.groups.indices) {
        val g = p.menu.groups[gi]
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                if (g.type != "price") {
                    Spacer(Modifier.width(8.dp))
                    CBadge(g.required)
                }
                if (g.type == "normal") {
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (g.required) "เลือก 1" else (if (g.maxSelect > 0) "เลือกได้สูงสุด " + g.maxSelect + " อัน" else "เลือกได้หลายอย่าง"),
                        fontSize = 12.sp, color = C.Subtext
                    )
                }
            }
            if (g.type == "price") {
                val v = p.priceVal[gi] ?: g.priceMin
                CPricePicker(
                    custVal = pricing.calcPrice(v, p.menu.gp, proxy), shopVal = v, min = g.priceMin, max = g.priceMax,
                    onMinus = { p.priceVal[gi] = maxOf(g.priceMin, v - g.priceStep) },
                    onPlus = { p.priceVal[gi] = minOf(g.priceMax, v + g.priceStep) }
                )
            } else if (g.type == "kg") {
                val cur = p.kgVal[gi] ?: Pair(0.0, 0.0)
                CKgInput(
                    cur, g.kgRate, { base -> if (base != 0.0) pricing.calcPrice(base, p.menu.gp, proxy) else 0L },
                    { kg -> p.kgVal[gi] = Pair(kg, cur.second) },
                    { kh -> p.kgVal[gi] = Pair(cur.first, minOf(9.0, kh)) }
                )
            } else {
                for (oi in g.options.indices) {
                    val o = g.options[oi]
                    val sel = if (g.required) (p.single[gi] == oi) else ((p.multi[gi] ?: emptySet()).contains(oi))
                    COptRow(o.name, if (o.price != 0.0) "+฿" + pricing.calcSub(o.price, proxy) else "", sel, !g.required) { p.selectOption(gi, oi) }
                    if (sel) {
                        for (sgi in o.subGroups.indices) {
                            SubGroupBody(p, gi, oi, sgi, pricing, proxy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubGroupBody(p: AdminPopup, gi: Int, oi: Int, sgi: Int, pricing: AdminPricing, proxy: Boolean) {
    val sg = p.menu.groups[gi].options[oi].subGroups[sgi]
    val key = gi.toString() + "-" + oi + "-" + sgi
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(sg.title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Spacer(Modifier.width(8.dp))
            CBadge(sg.required)
        }
        if (sg.type == "price") {
            val engaged = p.subPrice[key] != null
            if (!sg.required) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (engaged) p.subPrice.remove(key) else p.subPrice[key] = sg.priceMin
                }) {
                    Checkbox(checked = engaged, onCheckedChange = { on -> if (on) p.subPrice[key] = sg.priceMin else p.subPrice.remove(key) })
                    Text("เพิ่ม " + sg.title, fontSize = 13.sp, color = C.Text)
                }
            }
            val v = p.subPrice[key]
            if (v != null) {
                CPricePicker(
                    custVal = pricing.calcSub(v, proxy), shopVal = v, min = sg.priceMin, max = sg.priceMax,
                    onMinus = { p.subPrice[key] = maxOf(sg.priceMin, v - sg.priceStep) },
                    onPlus = { p.subPrice[key] = minOf(sg.priceMax, v + sg.priceStep) }
                )
            }
        } else if (sg.type == "kg") {
            val engaged = p.subKg[key] != null
            if (!sg.required) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (engaged) p.subKg.remove(key) else p.subKg[key] = Pair(0.0, 0.0)
                }) {
                    Checkbox(checked = engaged, onCheckedChange = { on -> if (on) p.subKg[key] = Pair(0.0, 0.0) else p.subKg.remove(key) })
                    Text("เพิ่ม " + sg.title, fontSize = 13.sp, color = C.Text)
                }
            }
            val cur = p.subKg[key]
            if (cur != null) {
                CKgInput(
                    cur, sg.kgRate, { base -> if (base != 0.0) pricing.calcSub(base, proxy) else 0L },
                    { kg -> p.subKg[key] = Pair(kg, cur.second) },
                    { kh -> p.subKg[key] = Pair(cur.first, minOf(9.0, kh)) }
                )
            }
        } else if (sg.multiSelect) {
            val set: Set<Int> = p.subMulti[key] ?: emptySet()
            for (soi in sg.options.indices) {
                val so = sg.options[soi]
                val on = set.contains(soi)
                val qty = p.subQty[key + "-" + soi] ?: 1
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) C.PrimaryLight else Color.White).clickable {
                            if (on) {
                                p.subMulti[key] = set - soi
                                p.subQty.remove(key + "-" + soi)
                            } else {
                                p.subMulti[key] = set + soi
                                p.subQty[key + "-" + soi] = 1
                            }
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (on) "☑" else "☐", fontSize = 16.sp, color = if (on) C.Primary else Color(0xFF94A3B8))
                    Spacer(Modifier.width(10.dp))
                    Text(so.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.weight(1f))
                    if (on) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(C.Primary).clickable {
                            p.subQty[key + "-" + soi] = maxOf(1, qty - 1)
                        }, contentAlignment = Alignment.Center) { Text("−", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                        Text(qty.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(C.Primary).clickable {
                            p.subQty[key + "-" + soi] = qty + 1
                        }, contentAlignment = Alignment.Center) { Text("+", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (so.price != 0.0) Text("+฿" + (pricing.calcSub(so.price, proxy) * (if (on) qty else 1)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                }
            }
        } else {
            val selIdx = p.subSingle[key]
            for (soi in sg.options.indices) {
                val so = sg.options[soi]
                COptRow(so.name, if (so.price != 0.0) "+฿" + pricing.calcSub(so.price, proxy) else "", selIdx == soi, false) {
                    if (selIdx == soi) p.subSingle.remove(key) else p.subSingle[key] = soi
                }
            }
        }
    }
}

@Composable
private fun CBadge(required: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (required) CRedL else C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(if (required) "บังคับ" else "ไม่บังคับ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (required) CRed else C.Primary)
    }
}

@Composable
private fun COptRow(name: String, priceText: String, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.PrimaryLight else Color(0xFFF8FAFC)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (multi) (if (selected) "☑" else "☐") else (if (selected) "●" else "○"),
            fontSize = 16.sp, color = if (selected) C.Primary else Color(0xFF94A3B8)
        )
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.weight(1f))
        if (priceText.isNotEmpty()) Text(priceText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary)
    }
}

@Composable
private fun CPricePicker(custVal: Long, shopVal: Double, min: Double, max: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAFBFC)).padding(14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (shopVal <= min) Color(0xFFCBD5E1) else C.Primary).clickable(enabled = shopVal > min) { onMinus() },
                contentAlignment = Alignment.Center
            ) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("฿" + custVal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                Text("ร้านได้ ฿" + cFmt(shopVal), fontSize = 12.sp, color = C.Subtext)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (shopVal >= max) Color(0xFFCBD5E1) else C.Primary).clickable(enabled = shopVal < max) { onPlus() },
                contentAlignment = Alignment.Center
            ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
        Text("เลือกได้ตั้งแต่ ฿" + cFmt(min) + " - ฿" + cFmt(max), fontSize = 12.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
private fun CKgInput(cur: Pair<Double, Double>, rate: Double, custOf: (Double) -> Long, onKg: (Double) -> Unit, onKheed: (Double) -> Unit) {
    val total = cKheed(cur)
    val base = total * rate
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CNumField("กิโลกรัม", cur.first, Modifier.weight(1f)) { onKg(maxOf(0.0, it)) }
            CNumField("ขีด", cur.second, Modifier.weight(1f)) { onKheed(maxOf(0.0, it)) }
        }
        Text(
            "฿" + cFmt(rate) + "/ขีด" + (if (total > 0.0) " · ราคาที่ต้องจ่าย ฿" + custOf(base) else ""),
            fontSize = 12.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun CNumField(label: String, value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    var txt by remember { mutableStateOf(if (value == 0.0) "" else cFmt(value)) }
    OutlinedTextField(
        value = txt, onValueChange = { s ->
            val clean = s.filter { c -> c.isDigit() || c == '.' }
            txt = clean
            onChange(clean.toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier
    )
}

@Composable
private fun ManualCustomerDialog(onDismiss: () -> Unit, onSave: (AdminCust) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ข้อมูลลูกค้า") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("ชื่อ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() } }, label = { Text("เบอร์โทร") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("ที่อยู่จัดส่ง") }, modifier = Modifier.fillMaxWidth())
                Text("ไม่มีพิกัด ค่าส่งจะใช้ค่าคงที่", fontSize = 11.sp, color = C.Subtext)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (phone.trim().isEmpty()) cToast(ctx, "กรุณาใส่เบอร์โทร")
                else onSave(AdminCust(phone.trim(), name.trim(), addr.trim(), null, null))
            }) { Text("ใช้ข้อมูลนี้") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun CMsg(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center)
    }
}

// แผ่นเลื่อนขึ้นจากด้านล่าง (ใช้ทำทั้งเลือกร้าน/เมนู/ลูกค้า/ตัวเลือก)
@Composable
private fun CSheet(title: String, onClose: () -> Unit, footer: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x80000000))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color.White)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).background(C.Gray).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 15.sp, color = C.Subtext)
                }
            }
            HorizontalDivider(color = CLine)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
            if (footer != null) {
                HorizontalDivider(color = CLine)
                Box(Modifier.navigationBarsPadding()) { footer() }
            }
        }
    }
}

@Composable
private fun CPlusBox(text: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(if (done) CGreenL else Color(0xFFF8FAFF)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (done) CGreen else C.Primary, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = C.Subtext)
    }
}

@Composable
private fun CSection(title: String, unused: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 2.dp, color = C.Gray)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun CSectionEdit(title: String, editLabel: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).clickable { onEdit() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(editLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 2.dp, color = C.Gray)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// แถว "ชื่อ : ข้อมูล" ต่อกันชิดซ้าย
@Composable
private fun CRow(label: String, value: String, color: Color?) {
    val txt = buildAnnotatedString {
        withStyle(SpanStyle(color = C.Subtext, fontWeight = FontWeight.Normal)) { append(label + "  :  ") }
        withStyle(SpanStyle(color = color ?: C.Text, fontWeight = FontWeight.Bold)) { append(value) }
    }
    Text(txt, fontSize = 13.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp))
}

@Composable
private fun CPriceRow(label: String, value: String, big: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, fontSize = if (big) 15.sp else 14.sp, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Normal, color = if (big) CGreen else C.Text, modifier = Modifier.weight(1f))
        Text(value, fontSize = if (big) 15.sp else 14.sp, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Normal, color = if (big) CGreen else C.Text)
    }
}
KT_EOF
echo "AdminCreateOrder.kt (ui): เขียนแล้ว"

# ---------- ต่อเข้ากับเมนูหลัก ----------
if grep -q 'AdminCreateOrderScreen' $N; then
  echo "AdminNav: ต่อแล้ว ข้าม"
else
  grep -q 'var showMenu by remember { mutableStateOf(false) }' $N || die "AdminNav: ไม่เจอจุดยึด showMenu"
  grep -q '    if (cid != null) {' $N || die "AdminNav: ไม่เจอจุดยึด cid"
  grep -q 'onCreate = { Toast.makeText(ctx, "หน้าสร้างออเดอร์ยังไม่เปิดใช้", Toast.LENGTH_SHORT).show() }' $N || die "AdminNav: ไม่เจอจุดยึด onCreate"
  sed -i 's|^    var showMenu by remember { mutableStateOf(false) }$|    var showMenu by remember { mutableStateOf(false) }\n    var creating by remember { mutableStateOf(false) }|' $N
  sed -i 's|^    if (cid != null) {$|    if (creating) {\n        BackHandler { creating = false }\n        Box(Modifier.fillMaxSize().statusBarsPadding()) {\n            AdminCreateOrderScreen(store, onBack = { creating = false }, onCreated = { creating = false })\n        }\n    } else if (cid != null) {|' $N
  sed -i 's|onCreate = { Toast.makeText(ctx, "หน้าสร้างออเดอร์ยังไม่เปิดใช้", Toast.LENGTH_SHORT).show() }|onCreate = { creating = true }|' $N
  grep -q 'AdminCreateOrderScreen' $N || die "ต่อ AdminNav ไม่สำเร็จ"
  echo "AdminNav: ต่อแล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $J/data/AdminCreateOrder.kt $J/ui/AdminCreateOrder.kt $N; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -n 'creating' $N | cut -c1-120
echo "เสร็จ"
