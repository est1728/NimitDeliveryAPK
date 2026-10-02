package com.nimit.delivery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.nimit.delivery.data.Ord
import com.nimit.delivery.data.RiderApi
import com.nimit.delivery.data.RiderStore
import com.nimit.delivery.data.fmtDate
import com.nimit.delivery.data.fmtDateTime
import com.nimit.delivery.data.money
import java.util.Calendar

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 14.sp, color = C.Subtext, modifier = Modifier.width(130.dp))
        Text(if (value.isEmpty()) "-" else value, fontSize = 14.sp, color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RiderShiftScreen(store: RiderStore, onBack: () -> Unit) {
    val r: Map<String, Any> = store.rider.value
    val active: Boolean = (r["shiftActive"] as? Boolean) != false
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("กะงาน", onBack)
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) {
                Text("เวลาทำงาน", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                Spacer(Modifier.height(8.dp))
                InfoRow("เวลาทำงาน", r["shift"]?.toString() ?: "ยังไม่กำหนด")
                InfoRow("สถานะกะ", if (active) "เปิดใช้งาน" else "ปิดใช้งาน")
            }
            Spacer(Modifier.height(10.dp))
            Text("ไรเดอร์ไม่สามารถแก้ไขกะงานได้ กรุณาติดต่อแอดมิน", fontSize = 12.sp, color = C.Subtext)
        }
    }
}

@Composable
fun RiderAccountScreen(store: RiderStore, onBack: () -> Unit) {
    val r: Map<String, Any> = store.rider.value
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("บัญชีของฉัน", onBack)
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) {
                Text("ข้อมูลส่วนตัว", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                Spacer(Modifier.height(8.dp))
                InfoRow("ชื่อ", r["name"]?.toString() ?: "")
                InfoRow("เบอร์โทร", r["phone"]?.toString() ?: "")
                InfoRow("ทะเบียนรถ", r["plate"]?.toString() ?: "")
                InfoRow("เลขบัตรประชาชน", r["idCard"]?.toString() ?: "")
            }
            Spacer(Modifier.height(10.dp))
            Text("ข้อมูลนี้แก้ไขได้โดยแอดมินเท่านั้น", fontSize = 12.sp, color = C.Subtext)
        }
    }
}

@Composable
fun RiderWalletScreen(store: RiderStore, onBack: () -> Unit) {
    val bal: Double = (store.rider.value["wallet"] as? Number)?.toDouble() ?: 0.0
    var txs by remember { mutableStateOf<List<Map<String, Any>>?>(null) }
    LaunchedEffect(store.riderId) {
        txs = try { RiderApi.walletHistory(store.riderId) } catch (e: Exception) { emptyList<Map<String, Any>>() }
    }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("กระเป๋าเงิน", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Primary).padding(20.dp)) {
                Text("ยอดเงินในกระเป๋า", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Text(money(bal), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("อัปเดตอัตโนมัติ", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(14.dp))
            Text("ประวัติเครดิต", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
            Spacer(Modifier.height(8.dp))
            val list: List<Map<String, Any>>? = txs
            if (list == null) {
                Text("กำลังโหลด...", fontSize = 12.sp, color = C.Subtext)
            } else if (list.isEmpty()) {
                Text("ยังไม่มีรายการ", fontSize = 12.sp, color = C.Subtext)
            } else {
                for (t in list) {
                    val amt: Double = (t["amount"] as? Number)?.toDouble() ?: 0.0
                    val pos: Boolean = amt >= 0.0
                    val oid: String = t["orderId"]?.toString() ?: ""
                    val dt: java.util.Date? = (t["createdAt"] as? Timestamp)?.toDate()
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t["type"]?.toString() ?: "-", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                            Text((if (pos) "+" else "-") + money(Math.abs(amt)), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (pos) Color(0xFF16A34A) else Color(0xFFDC2626))
                        }
                        Text((if (oid.isNotEmpty()) "#" + oid + " · " else "") + fmtDateTime(dt), fontSize = 11.sp, color = C.Subtext)
                    }
                }
            }
        }
    }
}

private class DayPt(val day: Int, val earn: Double, val grand: Double)

private fun orNum(a: Double, b: Double): Double = if (a != 0.0) a else b

@Composable
fun RiderStatementScreen(store: RiderStore, onBack: () -> Unit) {
    val now = remember { Calendar.getInstance() }
    val curY: Int = now.get(Calendar.YEAR)
    val curM: Int = now.get(Calendar.MONTH)
    var year by remember { mutableStateOf(curY) }
    var month by remember { mutableStateOf(curM) }
    var chart by remember { mutableStateOf(false) }
    val rows: List<Ord> = store.myOrders.value
        .filter { it.status == "done" && inMonth(it.created, year, month) }
        .sortedBy { it.created?.time ?: 0L }
    val canNext: Boolean = year < curY || (year == curY && month < curM)

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        TopBar("รายได้", onBack)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◀", fontSize = 20.sp, color = C.Primary, modifier = Modifier.clickable {
                if (month == 0) { month = 11; year -= 1 } else { month -= 1 }
            }.padding(horizontal = 16.dp, vertical = 6.dp))
            Text(TH_MONTHS_FULL[month] + " " + (year + 543), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
            Text("▶", fontSize = 20.sp, color = if (canNext) C.Primary else Color(0xFFCBD5E1), modifier = Modifier.clickable(enabled = canNext) {
                if (month == 11) { month = 0; year += 1 } else { month += 1 }
            }.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("รายการ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (!chart) Color.White else C.Primary,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (!chart) C.Primary else C.PrimaryLight).clickable { chart = false }.padding(horizontal = 16.dp, vertical = 6.dp))
            Text("กราฟ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (chart) Color.White else C.Primary,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (chart) C.Primary else C.PrimaryLight).clickable { chart = true }.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (rows.isEmpty()) {
                Text("ไม่มีออเดอร์ในเดือนนี้", color = C.Subtext, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else if (!chart) {
                var total = 0.0
                val groups = LinkedHashMap<String, ArrayList<Ord>>()
                for (o in rows) {
                    val k: String = fmtDate(o.created)
                    val g: ArrayList<Ord> = groups[k] ?: ArrayList<Ord>()
                    g.add(o)
                    groups[k] = g
                }
                for ((date, list) in groups) {
                    Text(date, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("รหัสคำสั่งซื้อ", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.weight(1.4f))
                            Text("ยอดรวม", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.weight(1f))
                            Text("จ่ายร้าน", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.weight(1f))
                            Text("รายได้", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.weight(1f))
                        }
                        for (o in list) {
                            val grand: Double = orNum(o.n("grandTotal"), o.n("subtotal"))
                            val shopPay: Double = orNum(o.n("baseCost"), Math.round(grand * 0.7).toDouble())
                            val earn: Double = orNum(orNum(o.n("riderEarn"), o.n("walletCut")), Math.round(grand * 0.18).toDouble())
                            total += earn
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(o.orderId, fontSize = 12.sp, color = C.Text, modifier = Modifier.weight(1.4f))
                                Text(money(grand), fontSize = 12.sp, color = C.Text, modifier = Modifier.weight(1f))
                                Text(money(shopPay), fontSize = 12.sp, color = C.Text, modifier = Modifier.weight(1f))
                                Text(money(earn), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Primary).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("รวมทั้งเดือน", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(money(total) + " รายได้", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                val byDay = java.util.TreeMap<Int, DoubleArray>()
                val cal = Calendar.getInstance()
                for (o in rows) {
                    val dt: java.util.Date? = o.created
                    if (dt != null) {
                        cal.time = dt
                        val k: Int = cal.get(Calendar.DAY_OF_MONTH)
                        val a: DoubleArray = byDay[k] ?: DoubleArray(2)
                        a[0] = a[0] + orNum(o.n("riderEarn"), o.n("walletCut"))
                        a[1] = a[1] + o.n("grandTotal")
                        byDay[k] = a
                    }
                }
                val pts = ArrayList<DayPt>()
                for ((k, a) in byDay) { pts.add(DayPt(k, a[0], a[1])) }
                var te = 0.0
                var tg = 0.0
                for (p in pts) { te += p.earn; tg += p.grand }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    StatementChart(pts)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("● รายได้ไรเดอร์", color = Color(0xFF1565C0), fontSize = 13.sp)
                    Text(money(te), color = Color(0xFF1565C0), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("● ยอดรวมทั้งหมด", color = C.Subtext, fontSize = 13.sp)
                    Text(money(tg), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatementChart(pts: List<DayPt>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val w: Float = size.width
        val h: Float = size.height
        val padL = 56f
        val padR = 16f
        val padT = 20f
        val padB = 36f
        val cw: Float = w - padL - padR
        val ch: Float = h - padT - padB
        var maxV = 1.0
        for (p in pts) { if (p.earn > maxV) maxV = p.earn; if (p.grand > maxV) maxV = p.grand }
        val n: Int = pts.size
        val stepX: Float = if (n > 1) cw / (n - 1).toFloat() else 0f
        fun px(i: Int): Float = padL + i.toFloat() * stepX
        fun py(v: Double): Float = (padT + ch - (v / maxV * ch)).toFloat()
        for (g in 0..4) {
            val y: Float = padT + ch / 4f * g.toFloat()
            drawLine(color = Color(0xFFEEF1F7), start = Offset(padL, y), end = Offset(w - padR, y), strokeWidth = 1f)
        }
        if (n > 1) {
            val gp = Path()
            for (i in 0 until n) { if (i == 0) gp.moveTo(px(i), py(pts[i].grand)) else gp.lineTo(px(i), py(pts[i].grand)) }
            drawPath(gp, Color(0xFFCBD5E1), style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        val ep = Path()
        for (i in 0 until n) { if (i == 0) ep.moveTo(px(i), py(pts[i].earn)) else ep.lineTo(px(i), py(pts[i].earn)) }
        drawPath(ep, Color(0xFF1565C0), style = Stroke(width = 5f))
        for (i in 0 until n) {
            drawCircle(color = Color.White, radius = 8f, center = Offset(px(i), py(pts[i].earn)))
            drawCircle(color = Color(0xFF1565C0), radius = 8f, center = Offset(px(i), py(pts[i].earn)), style = Stroke(width = 4f))
        }
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            paint.textSize = 24f
            paint.isAntiAlias = true
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            for (g in 0..4) {
                val v: Double = maxV - maxV / 4.0 * g
                c.nativeCanvas.drawText("฿" + Math.round(v), padL - 6f, padT + ch / 4f * g.toFloat() + 8f, paint)
            }
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0 until n) {
                if (n <= 10 || i % 2 == 0) c.nativeCanvas.drawText(pts[i].day.toString(), px(i), h - padB + 28f, paint)
            }
        }
    }
}
