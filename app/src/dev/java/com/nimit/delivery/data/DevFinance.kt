package com.nimit.delivery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    val error: String?
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

    /**
     * รวมยอดออเดอร์ที่สำเร็จต่อเขต: จับออเดอร์เข้าเขตผ่าน shopId -> shops.franchiseIds (ร้านหลายเขตนับในทุกเขต แต่แถวรวมไม่นับซ้ำ)
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
        val totalEmpty = emptyRow("", "รวม (ไม่นับซ้ำ)")
        val probe = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get().await()
            .documents.firstOrNull()
        if (probe == null) return DevFinResult(zones.map { emptyRow(it.id, it.name) }, totalEmpty, 0, false, null)
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
            else -> return DevFinResult(emptyList(), totalEmpty, 0, false, "รูปแบบเวลาของออเดอร์ไม่รองรับ (ต้องเป็น Timestamp หรือตัวเลข)")
        }
        val docs = q.limit(CAP).get().await().documents
        val per = HashMap<String, DoubleArray>()
        val tot = DoubleArray(5)
        for (d in docs) {
            if (d.getString("status") != "done") continue
            val shop = d.getString("shopId") ?: continue
            val zs = shopZones[shop] ?: continue
            val v = doubleArrayOf(
                1.0,
                d.getDouble("grandTotal") ?: 0.0,
                d.getDouble("deliveryFee") ?: 0.0,
                d.getDouble("walletCut") ?: 0.0,
                d.getDouble("riderEarn") ?: 0.0
            )
            for (z in zs) {
                val a = per.getOrPut(z) { DoubleArray(5) }
                for (i in 0 until 5) a[i] += v[i]
            }
            for (i in 0 until 5) tot[i] += v[i]
        }
        val rows = zones.map { z ->
            val a = per[z.id] ?: DoubleArray(5)
            DevFinRow(z.id, z.name, a[0].toInt(), a[1], a[2], a[3], a[4])
        }.sortedByDescending { it.gross }
        val total = DevFinRow("", "รวม (ไม่นับซ้ำ)", tot[0].toInt(), tot[1], tot[2], tot[3], tot[4])
        return DevFinResult(rows, total, docs.size, docs.size >= CAP, null)
    }
}
