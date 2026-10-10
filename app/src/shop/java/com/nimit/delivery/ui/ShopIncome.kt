package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nimit.delivery.data.ShopOrd
import com.nimit.delivery.data.ShopStore
import com.nimit.delivery.data.shopNum
import com.nimit.delivery.data.shopTime
import java.util.Calendar
import java.util.Date

private val TH_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private const val CHEVRON_DOWN = "M7 10l5 5 5-5z"
private const val CHEVRON_RIGHT = "M9 18l6-6-6-6"

private fun dayStart(y: Int, m: Int, d: Int): Long {
    val c = Calendar.getInstance()
    c.set(y, m - 1, d, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun inDay(t: Date?, y: Int, m: Int, d: Int): Boolean {
    if (t == null) return false
    val s = dayStart(y, m, d)
    return t.time >= s && t.time < s + 24L * 3600_000L
}

/** หน้ารายได้ร้าน: ใช้ baseCost (ราคาที่ร้านได้รับ) ตาม shop-admin.html */
@Composable
fun ShopIncomeScreen(store: ShopStore, onOpenOrder: (String) -> Unit, onBack: () -> Unit) {
    val today = Calendar.getInstance()
    var dd by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    var mm by remember { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var yy by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var picker by remember { mutableStateOf(false) }

    val all: List<ShopOrd> = store.orders.value
    val dayOrders: List<ShopOrd> = all.filter { inDay(it.created, yy, mm, dd) }
    val done: List<ShopOrd> = dayOrders.filter { it.status == "done" }
    val cancelled: List<ShopOrd> = dayOrders.filter { it.status == "rejected" }
    val gross: Double = done.sumOf { val s = it.n("subtotal"); if (s != 0.0) s else it.n("grandTotal") }
    val net: Double = done.sumOf { it.n("baseCost") }
    val display: List<ShopOrd> = (done + cancelled).sortedByDescending { it.created?.time ?: 0L }

    // 7 วันล่าสุด (นับถึงวันที่เลือก)
    val trend: List<Pair<String, Double>> = (6 downTo 0).map { back ->
        val c = Calendar.getInstance()
        c.set(yy, mm - 1, dd, 12, 0, 0)
        c.add(Calendar.DAY_OF_MONTH, -back)
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
        val sum: Double = all.filter { it.status == "done" && inDay(it.created, y, m, d) }.sumOf { it.n("baseCost") }
        Pair("$d/$m", sum)
    }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true)
            }
            Spacer(Modifier.width(12.dp))
            Text("รายได้ร้าน", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp).shadow(1.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { picker = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("วันที่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                        Text("$dd ${TH_MONTHS[mm - 1]} ${yy + 543}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                    PathIcon(CHEVRON_DOWN, C.Subtext, 18.dp)
                }
            }
            item {
                IncomeCard("สรุปรายได้ร้าน") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryBox("ยอดขายรวม", "฿" + shopMoney(gross), C.Text, Modifier.weight(1f))
                        SummaryBox("รายได้ร้านสุทธิ", "฿" + shopMoney(net), ShopGreen, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryBox("สำเร็จ", done.size.toString(), C.Text, Modifier.weight(1f))
                        SummaryBox("ยกเลิก", cancelled.size.toString(), ShopRed, Modifier.weight(1f))
                    }
                }
            }
            item {
                IncomeCard("แนวโน้มรายได้ 7 วันล่าสุด") {
                    val max: Double = maxOf(1.0, trend.maxOf { it.second })
                    Row(Modifier.fillMaxWidth().height(140.dp).padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                        trend.forEachIndexed { i, p ->
                            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(
                                    Modifier.fillMaxWidth(0.6f).height((100.dp * (p.second / max).toFloat())).clip(RoundedCornerShape(4.dp))
                                        .background(if (i == trend.size - 1) C.Primary else Color(0xFFC7D7F5))
                                )
                                Text(p.first, fontSize = 10.sp, color = C.Subtext, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            if (display.isEmpty()) {
                item {
                    Text("ไม่มีประวัติวันนี้", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp))
                }
            } else {
                itemsIndexed(display, key = { _, o -> o.id }) { _, o ->
                    val isDone = o.status == "done"
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).shadow(1.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onOpenOrder(o.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(o.orderId, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                            Text(shopTime(o.created), fontSize = 12.sp, color = C.Subtext)
                        }
                        Text(if (isDone) "+฿" + shopNum(o.n("baseCost")) else "ยกเลิก", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (isDone) ShopGreen else ShopRed)
                        Spacer(Modifier.width(6.dp))
                        PathIcon(CHEVRON_RIGHT, C.Subtext, 16.dp, stroke = true)
                    }
                }
            }
        }
    }

    if (picker) ShopDatePicker(dd, mm, yy, onCancel = { picker = false }, onConfirm = { d, m, y ->
        picker = false
        var nd = d; var nm = m; var ny = y
        val sel = dayStart(y, m, d)
        val t = Calendar.getInstance()
        val todayStart = dayStart(t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH))
        if (sel > todayStart) { nd = t.get(Calendar.DAY_OF_MONTH); nm = t.get(Calendar.MONTH) + 1; ny = t.get(Calendar.YEAR) }
        dd = nd; mm = nm; yy = ny
    })
}

private fun shopMoney(n: Double): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))

@Composable
private fun IncomeCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
private fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.Gray).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(bottom = 4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

/** ตัวเลือกวันที่แบบเลื่อน (วัน/เดือน/ปี) ตามหน้าเว็บ */
@Composable
private fun ShopDatePicker(d0: Int, m0: Int, y0: Int, onCancel: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val days: List<String> = (1..31).map { it.toString() }
    val months: List<String> = TH_MONTHS
    val years: List<Int> = (0 until 5).map { thisYear - it }
    var selD by remember { mutableIntStateOf(d0 - 1) }
    var selM by remember { mutableIntStateOf(m0 - 1) }
    var selY by remember { mutableIntStateOf(maxOf(0, years.indexOf(y0))) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onCancel() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color.White).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }.padding(bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext, modifier = Modifier.clickable { onCancel() })
                    Text("เลือกวันที่", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("เสร็จสิ้น", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.clickable { onConfirm(selD + 1, selM + 1, years[selY]) })
                }
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    Box(Modifier.align(Alignment.Center).padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray))
                    Row(Modifier.fillMaxSize()) {
                        ShopWheel(days, selD, Modifier.weight(1f)) { selD = it }
                        ShopWheel(months, selM, Modifier.weight(1f)) { selM = it }
                        ShopWheel(years.map { it.toString() }, selY, Modifier.weight(1f)) { selY = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopWheel(items: List<String>, initial: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val state: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, items.size - 1))
    val half = with(LocalDensity.current) { 22.dp.toPx() }
    val sel: Int = (state.firstVisibleItemIndex + (if (state.firstVisibleItemScrollOffset > half) 1 else 0)).coerceIn(0, items.size - 1)
    LaunchedEffect(sel) { onChange(sel) }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) state.animateScrollToItem(sel)
    }
    LazyColumn(modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 78.dp)) {
        itemsIndexed(items) { i, t ->
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(t, fontSize = if (i == sel) 17.sp else 16.sp, fontWeight = if (i == sel) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (i == sel) C.Text else C.Subtext)
            }
        }
    }
}
