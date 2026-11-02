#!/bin/bash
# การเงินรวม: จับรายได้เข้า "เขตที่ลูกค้าสั่ง" (ออเดอร์ละเขตเดียว) + ป้ายหักเครดิตไรเดอร์ (เขียนทับ 2 ไฟล์ + แก้ DevGeo.inside ให้เรียกใช้ได้) รันซ้ำได้
set -e
D=app/src/dev/java/com/nimit/delivery
G="$D/data/DevZoneGeo.kt"
[ -f "$D/data/DevFinance.kt" ] && [ -f "$G" ] || { echo "หยุด: ไม่เจอหน้าการเงิน/ตัวกำหนดเขต (ต้องรัน dev-hubfin.sh และ dev-map-v2.sh ก่อน และรันใน ~/nimit-customer)"; exit 1; }
if grep -q "    private fun inside(pt: DevPt, poly: List<DevPt>): Boolean {" "$G"; then
  sed -i 's/    private fun inside(pt: DevPt, poly: List<DevPt>): Boolean {/    fun inside(pt: DevPt, poly: List<DevPt>): Boolean {/' "$G"
elif ! grep -q "    fun inside(pt: DevPt, poly: List<DevPt>): Boolean {" "$G"; then
  echo "หยุด: ไม่เจอฟังก์ชัน inside ใน $G (ส่งผล grep -n inside $G มาให้ดู)"; exit 1
fi
cat > "$D/data/DevFinance.kt" <<'KT_EOF'
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
KT_EOF
cat > "$D/ui/DevFinance.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevFinResult
import com.nimit.delivery.data.DevFinRow
import com.nimit.delivery.data.DevFinanceRepo
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException

private fun devMoney(v: Double): String = String.format("%,.0f", v)

private val devFinPeriods = listOf("month" to "เดือนนี้", "lastmonth" to "เดือนก่อน", "d7" to "7 วัน", "d30" to "30 วัน")

// การเงินรวม: ตารางสรุปต่อเขต จากออเดอร์ที่สำเร็จ (อ่านอย่างเดียว)
@Composable
fun DevFinanceScreen() {
    var kind by remember { mutableStateOf("month") }
    var result by remember { mutableStateOf<DevFinResult?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(kind, tick) {
        result = null
        err = false
        try {
            val zones = DevRepo.franchises()
            result = DevFinanceRepo.load(kind, zones)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }

    DevCard {
        Text("การเงินรวมทุกเขต", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "รวมจากออเดอร์ที่สำเร็จ (done) จับเข้า \"เขตที่ลูกค้าสั่ง\" ออเดอร์ละเขตเดียว ไม่นับซ้ำ: ใช้เขตที่บันทึกในออเดอร์ (ถ้ามี) " +
                "แล้วดูพิกัดลูกค้าว่าอยู่ในรูปเขตไหน (ถ้าทับซ้อน เลือกเขตที่มีร้านนั้นอยู่ แล้วเลือกเขตที่ศูนย์กลางใกล้ที่สุด) " +
                "แล้วจึงใช้เขตของร้านถ้าร้านอยู่เขตเดียว รายได้แอดมิน = หักเครดิตไรเดอร์ (walletCut) ไม่นับการเติมเครดิต อ่านอย่างเดียว",
            fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        devFinPeriods.forEach { (id, label) ->
            Text(
                label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (kind == id) Color.White else DevC.P,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (kind == id) DevC.P else DevC.PL)
                    .clickable { kind = id }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
    val r = result
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลการเงินไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
    } else if (r == null) {
        DevCard { Text("กำลังคำนวณ...", fontSize = 13.sp, color = DevC.S) }
    } else if (r.error != null) {
        DevCard { Text(r.error, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.R) }
    } else {
        DevCard {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                DevFinHead()
                r.rows.forEach { DevFinLine(it, false) }
                DevFinLine(r.total, true)
            }
        }
        Text(
            "อ่านออเดอร์ในช่วงนี้ " + r.scanned + " รายการ จับเขตจาก: ในออเดอร์ " + r.byField + " · พิกัดลูกค้า " + r.byGps +
                " · เขตของร้าน " + r.byShop + " · ไม่ระบุเขต " + r.unknown +
                (if (r.capped) " (ครบเพดาน 3,000 รายการ ตัวเลขอาจน้อยกว่าจริง ลองเลือกช่วงสั้นลง)" else ""),
            fontSize = 11.sp, color = if (r.capped) DevC.O else DevC.S, lineHeight = 16.sp
        )
    }
}

private val devFinCols: List<Pair<String, Dp>> = listOf(
    "เขต" to 130.dp, "ออเดอร์" to 64.dp, "ยอดรวม (grandTotal)" to 120.dp,
    "ค่าส่ง" to 80.dp, "หักเครดิตไรเดอร์ (walletCut)" to 130.dp, "รายได้ไรเดอร์" to 100.dp
)

@Composable
private fun DevFinHead() {
    Row(Modifier.padding(bottom = 6.dp)) {
        devFinCols.forEachIndexed { i, (label, w) ->
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = DevC.S, maxLines = 2,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.width(w).padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun DevFinLine(r: DevFinRow, total: Boolean) {
    val weight = if (total) FontWeight.ExtraBold else FontWeight.SemiBold
    val vals = listOf(r.zoneName, r.orders.toString(), devMoney(r.gross), devMoney(r.delivery), devMoney(r.walletCut), devMoney(r.riderEarn))
    Row(
        Modifier
            .then(if (total) Modifier.padding(top = 4.dp).background(DevC.PL) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        vals.forEachIndexed { i, v ->
            Text(
                v, fontSize = 12.sp, fontWeight = weight, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.width(devFinCols[i].second).padding(horizontal = 4.dp)
            )
        }
    }
}
KT_EOF
echo "== ตรวจผล =="
grep -n "fun inside" "$G"
for f in data/DevFinance.kt ui/DevFinance.kt; do printf "%s  %s บรรทัด\n" "$f" "$(wc -l < "$D/$f")"; done
grep -c "byGps" "$D/data/DevFinance.kt" | sed "s/^/DevFinance: byGps = /"
git status --short
