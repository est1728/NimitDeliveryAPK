package com.nimit.delivery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// ผลรวมต่อเขต (เฉพาะออเดอร์สถานะ done) ชื่อคอลัมน์ตรงกับฟิลด์ในออเดอร์: grandTotal, deliveryFee, walletCut, riderEarn
data class DevFinRow(
    val zoneId: String,
    val zoneName: String,
    val orders: Int,
    val gross: Double,
    val delivery: Double,
    val walletCut: Double,
    val riderEarn: Double
)

data class DevFinResult(
    val rows: List<DevFinRow>,
    val total: DevFinRow,
    val scanned: Int,
    val capped: Boolean,
    val error: String?,
    val byField: Int,
    val byGps: Int,
    val byShop: Int,
    val unknown: Int
)

object DevFinanceRepo {
    private const val CAP = 3000L
    private val bkk: TimeZone = TimeZone.getTimeZone("Asia/Bangkok")

    /** ช่วงเวลา (มิลลิวินาที) ตามเวลาไทย: month = เดือนนี้, lastmonth = เดือนก่อน, d7 / d30 = ย้อนหลัง */
    fun range(kind: String): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance(bkk)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return when (kind) {
            "d7" -> Pair(now - 7L * 86400000L, now + 60000L)
            "d30" -> Pair(now - 30L * 86400000L, now + 60000L)
            "lastmonth" -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                val end = c.timeInMillis
                c.add(Calendar.MONTH, -1)
                Pair(c.timeInMillis, end)
            }
            else -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                Pair(c.timeInMillis, now + 60000L)
            }
        }
    }

    private fun emptyRow(id: String, name: String) = DevFinRow(id, name, 0, 0.0, 0.0, 0.0, 0.0)

    private fun row(id: String, name: String, a: DoubleArray) = DevFinRow(id, name, a[0].toInt(), a[1], a[2], a[3], a[4])

    private fun d2(a: DevPt, b: DevPt?): Double {
        if (b == null) return Double.MAX_VALUE
        val dx = a.lat - b.lat
        val dy = a.lng - b.lng
        return dx * dx + dy * dy
    }

    /**
     * รวมยอดออเดอร์ที่สำเร็จ "ตามเขตที่ลูกค้าสั่ง" (ออเดอร์หนึ่งอยู่เขตเดียว ไม่นับซ้ำ) ลำดับการตัดสินเขต:
     *  1) ฟิลด์เขตในออเดอร์ (franchiseId / zoneId) ถ้าแอปลูกค้าบันทึกไว้
     *  2) พิกัดลูกค้า (customer.lat/lng) อยู่ในรูปเขตไหน ถ้าอยู่ในเขตทับซ้อน เลือกเขตที่มีร้านของออเดอร์นั้นอยู่
     *     แล้วเลือกเขตที่จุดศูนย์กลางใกล้ลูกค้าที่สุด
     *  3) เขตของร้าน ถ้าร้านอยู่เขตเดียว
     *  4) นอกเหนือจากนี้ = "ไม่ระบุเขต"
     * ชนิดของ createdAt ในออเดอร์ตรวจจากออเดอร์ล่าสุด (Timestamp หรือตัวเลข) แล้วค่อยกรองช่วงเวลา
     */
    suspend fun load(kind: String, zones: List<DevFranchise>): DevFinResult {
        val (from, to) = range(kind)
        val db = FirebaseFirestore.getInstance()
        val shopZones = HashMap<String, List<String>>()
        db.collection("shops").get().await().documents.forEach { d ->
            val z = (d.get("franchiseIds") as? List<*>)?.mapNotNull { it as? String }
                ?: (d.getString("franchiseId")?.let { listOf(it) } ?: emptyList())
            if (z.isNotEmpty()) shopZones[d.id] = z
        }
        val polys: Map<String, List<DevPt>> = try {
            DevZoneGeoRepo.loadAll().filterValues { it.size >= 3 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        val centers = polys.mapValues { en -> DevPt(en.value.map { it.lat }.average(), en.value.map { it.lng }.average()) }
        val zoneIds = zones.map { it.id }.toSet()
        val totalEmpty = emptyRow("", "รวม")
        val probe = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get().await()
            .documents.firstOrNull()
        if (probe == null) return DevFinResult(zones.map { emptyRow(it.id, it.name) }, totalEmpty, 0, false, null, 0, 0, 0, 0)
        val sample = probe.get("createdAt")
        val col = db.collection("orders")
        val q: Query = when (sample) {
            is Timestamp -> col
                .whereGreaterThanOrEqualTo("createdAt", Timestamp(Date(from)))
                .whereLessThan("createdAt", Timestamp(Date(to)))
            is Number -> {
                val inMs = sample.toDouble() > 1e11
                col.whereGreaterThanOrEqualTo("createdAt", if (inMs) from else from / 1000L)
                    .whereLessThan("createdAt", if (inMs) to else to / 1000L)
            }
            else -> return DevFinResult(emptyList(), totalEmpty, 0, false, "รูปแบบเวลาของออเดอร์ไม่รองรับ (ต้องเป็น Timestamp หรือตัวเลข)", 0, 0, 0, 0)
        }
        val docs = q.limit(CAP).get().await().documents
        val per = HashMap<String, DoubleArray>()
        val unk = DoubleArray(5)
        val tot = DoubleArray(5)
        var byField = 0
        var byGps = 0
        var byShop = 0
        var unknown = 0
        for (d in docs) {
            if (d.getString("status") != "done") continue
            val shop = d.getString("shopId") ?: ""
            val v = doubleArrayOf(
                1.0,
                d.getDouble("grandTotal") ?: 0.0,
                d.getDouble("deliveryFee") ?: 0.0,
                d.getDouble("walletCut") ?: 0.0,
                d.getDouble("riderEarn") ?: 0.0
            )
            var zone: String? = null
            val f = d.getString("franchiseId") ?: d.getString("zoneId")
            if (!f.isNullOrEmpty() && f in zoneIds) {
                zone = f
                byField++
            }
            if (zone == null) {
                val c = (d.get("customer") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
                val la = (c?.get("lat") as? Number)?.toDouble()
                val lo = (c?.get("lng") as? Number)?.toDouble()
                if (la != null && lo != null) {
                    val pt = DevPt(la, lo)
                    val hit = polys.filter { DevGeo.inside(pt, it.value) }.keys
                    if (hit.isNotEmpty()) {
                        val shopZs = shopZones[shop] ?: emptyList()
                        val pref = hit.filter { it in shopZs }
                        val cand = if (pref.isNotEmpty()) pref else hit.toList()
                        zone = cand.minByOrNull { id -> d2(pt, centers[id]) }
                        byGps++
                    }
                }
            }
            if (zone == null) {
                val zs = shopZones[shop]
                if (zs != null && zs.size == 1) {
                    zone = zs[0]
                    byShop++
                }
            }
            val target = if (zone != null) per.getOrPut(zone) { DoubleArray(5) } else unk
            if (zone == null) unknown++
            for (i in 0 until 5) {
                target[i] += v[i]
                tot[i] += v[i]
            }
        }
        val rows = ArrayList<DevFinRow>()
        rows.addAll(zones.map { z -> row(z.id, z.name, per[z.id] ?: DoubleArray(5)) }.sortedByDescending { it.gross })
        if (unknown > 0) rows.add(row("", "ไม่ระบุเขต", unk))
        return DevFinResult(rows, row("", "รวม", tot), docs.size, docs.size >= CAP, null, byField, byGps, byShop, unknown)
    }
}
