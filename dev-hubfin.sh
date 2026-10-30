#!/bin/bash
# หน้า "ศูนย์ข้อมูลเขต" (บอร์ดรวมทุกเขต + ประวัติรายเขต) และ "การเงินรวม" (ตารางต่อเขต) รันซ้ำได้
set -e
D=app/src/dev/java/com/nimit/delivery
N="$D/ui/DevNav.kt"
[ -f "$N" ] && grep -q "DevNotifyConfigScreen" "$N" || { echo "หยุด: ต้องรัน dev-ncfg.sh ก่อน (ไม่เจอหน้าตั้งค่าแจ้งเตือนใน DevNav) และรันใน ~/nimit-customer"; exit 1; }
[ -f "$D/data/DevAudit.kt" ] && [ -f "$D/data/DevZoneDetail.kt" ] || { echo "หยุด: ไม่เจอไฟล์ประวัติ/รายละเอียดเขต (ต้องรัน dev-audit.sh, dev-zone.sh ก่อน)"; exit 1; }
cat > "$D/data/DevFinance.kt" <<'KT_EOF'
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
            "รวมจากออเดอร์ที่สำเร็จ (done) จับเข้าเขตตามร้านของออเดอร์ ร้านที่อยู่หลายเขตนับในทุกเขตที่อยู่ แต่แถว \"รวม\" ไม่นับซ้ำ " +
                "ชื่อคอลัมน์ตรงกับฟิลด์ในออเดอร์ อ่านอย่างเดียว",
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
            "อ่านออเดอร์ในช่วงนี้ " + r.scanned + " รายการ" +
                (if (r.capped) " (ครบเพดาน 3,000 รายการ ตัวเลขอาจน้อยกว่าจริง ลองเลือกช่วงสั้นลง)" else ""),
            fontSize = 11.sp, color = if (r.capped) DevC.O else DevC.S, lineHeight = 16.sp
        )
    }
}

private val devFinCols: List<Pair<String, Dp>> = listOf(
    "เขต" to 130.dp, "ออเดอร์" to 64.dp, "ยอดรวม (grandTotal)" to 120.dp,
    "ค่าส่ง" to 80.dp, "walletCut" to 90.dp, "รายได้ไรเดอร์" to 100.dp
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
cat > "$D/ui/DevHub.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevAuditItem
import com.nimit.delivery.data.DevAuditRepo
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevRepo
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun devHubLabel(a: String): String = when (a) {
    "link_zones" -> "ผูกเขต"
    "unlink_zone" -> "เลิกผูกเขต"
    "create_zone" -> "เพิ่มเขต"
    "edit_zone" -> "แก้ไขเขต"
    "move_shop" -> "ย้ายร้าน"
    "copy_shop" -> "คัดลอกร้าน"
    "set_zone" -> "กำหนดเขตบนแผนที่"
    else -> a
}

private fun devHubTime(ms: Long): String =
    if (ms <= 0L) "กำลังบันทึก" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

// ศูนย์ข้อมูลเขต: ภาพรวมทุกเขต + กดดูประวัติของแต่ละเขต (อ่านอย่างเดียว) รายละเอียด/แก้ไขอยู่ที่เมนู "เขตแฟรนไชส์"
@Composable
fun DevHubScreen() {
    var zones by remember { mutableStateOf<List<DevFranchise>?>(null) }
    var audit by remember { mutableStateOf<List<DevAuditItem>?>(null) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) {
        zones = null
        audit = null
        err = false
        try {
            zones = DevRepo.franchises()
            audit = try {
                DevAuditRepo.recent(200)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
    }

    val z = zones
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดข้อมูลเขตไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    if (z == null) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
        return
    }
    val active = z.count { it.status == "active" }
    val suspended = z.count { it.status == "suspended" }
    val groups = z.map { it.groupId }.filter { it.isNotEmpty() }.toSet().size
    val withMap = z.count { it.zonePoints >= 3 }

    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DevStat("เขตทั้งหมด", z.size.toString(), DevC.P, Modifier.weight(1f))
        DevStat("ใช้งาน", active.toString(), DevC.Gn, Modifier.weight(1f))
        DevStat("ระงับ", suspended.toString(), DevC.R, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DevStat("มีรูปเขต", withMap.toString(), DevC.P, Modifier.weight(1f))
        DevStat("กลุ่มหลังบ้านร่วม", groups.toString(), DevC.O, Modifier.weight(1f))
        DevStat("รายการประวัติ", (audit?.size ?: 0).toString(), DevC.S, Modifier.weight(1f))
    }
    if (z.isEmpty()) {
        DevCard { Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S) }
    }
    val items = audit
    z.forEach { f ->
        val mine = items?.filter { f.id in it.zoneIds } ?: emptyList()
        val last = mine.firstOrNull()
        val isOpen = open == f.id
        DevCard(Modifier.clickable { open = if (isOpen) null else f.id }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (f.province.isEmpty()) "ไม่ระบุพื้นที่" else f.province) + " · " + f.shopCount + " ร้าน",
                        fontSize = 11.5.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                when (f.status) {
                    "active" -> DevBadge("ใช้งาน", DevC.GnL, DevC.Gn)
                    "suspended" -> DevBadge("ระงับ", DevC.RL, DevC.R)
                    else -> DevBadge(if (f.status.isEmpty()) "-" else f.status, Color(0xFFF1F5F9), DevC.S)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.zonePoints >= 3) DevBadge("มีรูปเขต", DevC.GnL, DevC.Gn) else DevBadge("ยังไม่กำหนดเขต", Color(0xFFF1F5F9), DevC.S)
                if (f.groupId.isNotEmpty()) DevBadge("หลังบ้านร่วม", DevC.OL, DevC.O)
                if (!f.hasCode) DevBadge("ยังไม่ตั้งรหัส", DevC.OL, DevC.O)
            }
            Text(
                if (last == null) "ยังไม่มีประวัติ" else "ล่าสุด: " + devHubLabel(last.action) + " · " + devHubTime(last.atMillis),
                fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp)
            )
            if (isOpen) {
                Text("ประวัติของเขตนี้ (" + mine.size + ")", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                if (items == null) {
                    Text("โหลดประวัติไม่สำเร็จ", fontSize = 12.sp, color = DevC.R)
                } else if (mine.isEmpty()) {
                    Text("ยังไม่มีประวัติ", fontSize = 12.sp, color = DevC.S)
                } else {
                    mine.take(10).forEach { a ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(devHubLabel(a.action) + " · " + devHubTime(a.atMillis), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                            Text(if (a.by.isEmpty()) "ไม่ทราบผู้ทำ" else a.by, fontSize = 11.sp, color = DevC.S)
                            a.details.forEach { line -> Text(line, fontSize = 11.sp, color = DevC.T) }
                        }
                    }
                }
                Text("รายละเอียด/แก้ไขเขต: เมนู เขตแฟรนไชส์", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DevStat(label: String, value: String, color: Color, modifier: Modifier) {
    DevCard(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, color = DevC.S, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
KT_EOF
if ! grep -q "DevHubScreen" "$N"; then
  grep -q "DevNotifyConfigScreen() else DevCard {" "$N" || { echo "หยุด: ไม่เจอจุดยึดเส้นทางหน้าใน DevNav.kt (ส่งผล grep -n 'cur ==' $N มาให้ดู)"; exit 1; }
  sed -i 's/DevNotifyConfigScreen() else DevCard {/DevNotifyConfigScreen() else if (cur == "hub") DevHubScreen() else if (cur == "fin") DevFinanceScreen() else DevCard {/' "$N"
fi
echo "== ตรวจผล =="
grep -n "DevHubScreen" "$N"
for f in data/DevFinance.kt ui/DevFinance.kt ui/DevHub.kt; do printf "%s  %s บรรทัด\n" "$f" "$(wc -l < "$D/$f")"; done
git status --short
