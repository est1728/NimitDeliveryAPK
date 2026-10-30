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
