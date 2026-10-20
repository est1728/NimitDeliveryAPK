package com.nimit.delivery.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.nimit.delivery.data.*
import org.json.JSONArray
import org.json.JSONObject

// ป๊อบอัพแบบใหม่ของระบบการ์ดร้านใหม่ (ตามไฟล์ต้นแบบ): ถามตัวเลือกทีละหัวข้อ / ดู-ลบเมนูที่เลือกไว้ / โปรไฟล์ร้าน / ดูรูปเต็มจอ

private val RD_FG = Color(0xFF0B2C63)
private val RD_SUB = Color(0xFF64748B)
private val RD_CHIP = Color(0x140B2C63)
private val RD_LINE = Color(0x1A0B2C63)
private val RD_ACC = Color(0xFF1550B8)
private val RD_BLUE = Color(0xFF2E6FE0)

private val NoRipple = MutableInteractionSource()

@Composable
private fun RingSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }.padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.84f).dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
                content = content
            )
        }
    }
}

@Composable
private fun RoundBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(RD_CHIP).clickable { onClick() }.then(modifier), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 17.sp, color = RD_FG)
    }
}

@Composable
private fun BigBtn(text: String, enabled: Boolean = true, primary: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(46.dp).alpha(if (enabled) 1f else 0.35f).clip(shape)
            .background(if (primary) Brush.linearGradient(listOf(RD_BLUE, RD_ACC)) else Brush.linearGradient(listOf(RD_CHIP, RD_CHIP)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else RD_FG) }
}

private fun fmtN(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

// ------------------------------------------------------------------ วิซาร์ดเลือกตัวเลือกเมนู

private class WzStep(val kind: Char, val gi: Int, val key: String, val g: Doc, val title: String)

private fun wzSteps(st: PopupState): List<WzStep> {
    val out = mutableListOf<WzStep>()
    st.groups.forEachIndexed { gi, g ->
        out += WzStep('g', gi, "$gi", g, g.str("title"))
        if (st.type(g) == "normal") {
            val sel = st.sel[gi]
            val ois: List<Int> = if (st.isSingle(g)) listOfNotNull(sel as? Int) else ((sel as? Set<*>)?.map { it as Int }?.sorted() ?: emptyList())
            ois.forEach { oi ->
                val o = st.options(g).getOrNull(oi)
                if (o != null) asMapList(o["subGroups"]).forEachIndexed { sgi, sg ->
                    out += WzStep('s', gi, "$gi-$oi-$sgi", sg, "${o.str("name")} → ${sg.str("title")}")
                }
            }
        }
    }
    out += WzStep('n', -1, "note", emptyMap(), "")
    if (st.groups.isNotEmpty()) out += WzStep('m', -1, "sum", emptyMap(), "")
    return out
}

private fun wzMissing(st: PopupState, s: WzStep): Boolean {
    if (s.kind == 'n' || s.kind == 'm' || !truthy(s.g["required"])) return false
    val sel: Any? = if (s.kind == 'g') st.sel[s.gi] else st.subEff(s.key, s.g)
    return when (st.type(s.g)) {
        "price" -> if (s.kind == 'g') false else sel == null
        "kg" -> ((sel as? Kg)?.totalKheed ?: 0.0) == 0.0
        else -> {
            val multi = if (s.kind == 'g') !st.isSingle(s.g) else st.sgIsMulti(s.g)
            if (multi) ((sel as? Set<*>)?.isEmpty() ?: true) else sel !is Int
        }
    }
}

@Composable
private fun QtyTotal(st: PopupState, pricing: Pricing) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("จำนวน", fontSize = 14.sp, color = RD_FG)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(RD_BLUE).clickable { st.qty = maxOf(1, st.qty - 1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = Color.White) }
            Text("${st.qty}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RD_FG)
            Box(Modifier.size(36.dp).clip(CircleShape).background(RD_BLUE).clickable { st.qty += 1 }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = Color.White) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("รวมเมนูนี้ ${st.qty} ชิ้น", fontSize = 15.sp, color = RD_FG)
        Text("฿${st.total(pricing)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RD_ACC)
    }
}

@Composable
private fun StepBody(st: PopupState, pricing: Pricing, s: WzStep, note: String, onNote: (String) -> Unit, last: Boolean) {
    when (s.kind) {
        'n' -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("เขียนเพิ่มเติม", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RD_FG)
                Text("ไม่บังคับ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = RD_BLUE, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1A2E6FE0)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            NimitInput(note, onNote, "เช่น ไม่ใส่ผัก, หวานน้อย", height = 96.dp, singleLine = false, fontSize = 16)
            if (last) QtyTotal(st, pricing)
        }
        'm' -> {
            Text("สรุปเมนูที่เลือก", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RD_FG, modifier = Modifier.padding(bottom = 6.dp))
            val by = LinkedHashMap<String, MutableList<String>>()
            val arr = st.selectedOptions()
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); by.getOrPut(o.optString("groupTitle")) { mutableListOf() }.add(o.optString("name")) }
            if (note.isNotBlank()) by["เขียนเพิ่มเติม"] = mutableListOf(note)
            if (by.isEmpty()) Text("ไม่ได้เลือกตัวเลือกเพิ่มเติม", fontSize = 12.sp, color = RD_SUB)
            by.forEach { (t, names) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t, fontSize = 13.5.sp, color = RD_SUB, modifier = Modifier.weight(1f))
                    Text(names.joinToString(", "), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = RD_FG, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x140B2C63)))
            }
            QtyTotal(st, pricing)
        }
        else -> GroupStep(st, pricing, s)
    }
}

@Composable
private fun GroupStep(st: PopupState, pricing: Pricing, s: WzStep) {
    val g = s.g
    val top = s.kind == 'g'
    val required = truthy(g["required"])
    val type = st.type(g)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RD_FG, modifier = Modifier.weight(1f, fill = false))
        Text(
            if (required) "บังคับ" else "ไม่บังคับ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = if (required) RD_ACC else RD_BLUE,
            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (required) Color(0x242E6FE0) else Color(0x1A2E6FE0)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
    val sel: Any? = if (top) st.sel[s.gi] else st.subEff(s.key, g)
    if (type == "price") {
        val mn = num(g["priceMin"]) ?: 0.0
        val mx = num(g["priceMax"]) ?: 0.0
        val spn = num(g["priceStep"]) ?: 5.0
        val cur = sel as? Double
        fun setV(v: Double?) { if (top) st.setPrice(s.gi, v ?: mn) else st.setSub(s.key, v) }
        if (cur == null) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { setV(mn) }.padding(horizontal = 14.dp, vertical = 11.dp)
                    .drawBehind { drawRoundRect(Color(0x4D0B2C63), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
            ) { Text("+ เพิ่ม ${g.str("title")}", fontSize = 14.sp, color = RD_FG) }
        } else {
            val shown = if (top) pricing.calcPrice(cur, st.menuGp) else pricing.calcSub(cur)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).alpha(if (cur <= mn) 0.3f else 1f).clip(CircleShape).background(RD_BLUE).clickable(enabled = cur > mn) { setV((cur - spn).coerceIn(mn, mx)) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 20.sp, color = Color.White) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("฿$shown", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RD_FG)
                    Text("ร้านได้ ฿${fmtN(cur)}", fontSize = 11.sp, color = RD_SUB)
                }
                Box(Modifier.size(40.dp).alpha(if (cur >= mx) 0.3f else 1f).clip(CircleShape).background(RD_BLUE).clickable(enabled = cur < mx) { setV((cur + spn).coerceIn(mn, mx)) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 20.sp, color = Color.White) }
            }
            Text("เลือกได้ตั้งแต่ ฿${fmtN(mn)} - ฿${fmtN(mx)}", fontSize = 12.sp, color = RD_SUB, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!top && !required) Text("ไม่เอา", fontSize = 13.sp, color = RD_SUB, textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { setV(null) }.padding(top = 8.dp))
        }
        return
    }
    if (type == "kg") {
        val rate = num(g["kgRate"]) ?: 0.0
        val cur = sel as? Kg
        fun setK(k: Kg) { if (top) st.setKg(s.gi, k) else st.setSub(s.key, k) }
        if (cur == null && !top && !required) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { setK(Kg()) }.padding(horizontal = 14.dp, vertical = 11.dp)
                    .drawBehind { drawRoundRect(Color(0x4D0B2C63), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
            ) { Text("+ เพิ่ม ${g.str("title")}", fontSize = 14.sp, color = RD_FG) }
            return
        }
        val k = cur ?: Kg()
        var kgT by remember(s.key) { mutableStateOf(if (k.kg == 0.0) "" else fmtN(k.kg)) }
        var kdT by remember(s.key) { mutableStateOf(if (k.kheed == 0.0) "" else fmtN(k.kheed)) }
        val kbd = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("กิโลกรัม", fontSize = 12.sp, color = RD_SUB)
                NimitInput(kgT, { t -> kgT = t; setK(Kg(maxOf(0.0, t.toDoubleOrNull() ?: 0.0), k.kheed)) }, "0", keyboardOptions = kbd)
            }
            Column(Modifier.weight(1f)) {
                Text("ขีด", fontSize = 12.sp, color = RD_SUB)
                NimitInput(kdT, { t -> kdT = t; setK(Kg(k.kg, (t.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.0))) }, "0", keyboardOptions = kbd)
            }
        }
        val base = k.totalKheed * rate
        val shown = if (base > 0) (if (top) pricing.calcPrice(base, st.menuGp) else pricing.calcSub(base)) else 0
        Text("฿${fmtN(rate)}/ขีด" + if (k.totalKheed > 0) " · ราคาที่ต้องจ่าย ฿$shown" else "", fontSize = 12.sp, color = RD_SUB, modifier = Modifier.padding(top = 8.dp))
        return
    }
    // ตัวเลือกปกติ
    val multi = if (top) !st.isSingle(g) else st.sgIsMulti(g)
    val max = if (top) (num(g["maxSelect"])?.toInt() ?: 0) else st.sgMax(g)
    Text(
        (if (multi) (if (max > 0) "เลือกได้สูงสุด $max อัน" else "เลือกได้หลายอย่าง") else "เลือก 1") + (if (top && required && multi) " (อย่างน้อย 1)" else ""),
        fontSize = 12.sp, color = RD_SUB, modifier = Modifier.padding(bottom = 10.dp)
    )
    st.options(g).forEachIndexed { oi, o ->
        val on = if (top) { if (multi) (sel as? Set<*>)?.any { it == oi } == true else (sel as? Int) == oi }
                 else { if (multi) (sel as? Set<*>)?.any { it == oi } == true else (sel as? Int) == oi }
        val qty = if (!top && multi && on) (st.subQty["${s.key}-$oi"] ?: 1) else 0
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(if (on) Color(0x292E6FE0) else Color.Transparent)
                .border(1.5.dp, if (on) RD_BLUE else Color(0x2E0B2C63), shape)
                .clickable {
                    if (top) st.selectOption(s.gi, oi) else if (multi) st.selectSubMulti(s.key, oi, g) else st.selectSubSingle(s.key, oi)
                }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(20.dp).clip(if (multi) RoundedCornerShape(6.dp) else CircleShape).border(2.dp, if (on) RD_BLUE else Color(0xFF94A3B8), if (multi) RoundedCornerShape(6.dp) else CircleShape)
                    .background(if (on) RD_BLUE else Color.Transparent)
            )
            Text(o.str("name"), fontSize = 14.sp, color = RD_FG, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            if (qty > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(RD_BLUE).clickable { st.adjustSubQty(s.key, oi, -1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 14.sp, color = Color.White) }
                Text("$qty", fontSize = 14.sp, color = RD_FG)
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(RD_BLUE).clickable { st.adjustSubQty(s.key, oi, 1) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 14.sp, color = Color.White) }
                Spacer(Modifier.width(4.dp))
            }
            val pr = num(o["price"]) ?: 0.0
            if (pr != 0.0) Text("+฿${pricing.calcSub(pr) * (if (qty > 0) qty else 1)}", fontSize = 13.sp, color = RD_ACC)
        }
    }
}

/** วิซาร์ดเลือกตัวเลือกเมนูแบบใหม่ — ผู้เรียกเป็นคนเพิ่มลงตะกร้าใน onConfirm */
@Composable
fun RingWizard(st: PopupState, pricing: Pricing, onDismiss: () -> Unit, onConfirm: (note: String) -> Unit) {
    var idx by remember(st) { mutableIntStateOf(0) }
    var note by remember(st) { mutableStateOf("") }
    var imgFull by remember(st) { mutableStateOf(false) }
    val steps = wzSteps(st)
    val i = idx.coerceIn(0, steps.size - 1)
    val s = steps[i]
    val last = i == steps.size - 1
    val img = st.m.str("imgUrl")
    RingSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (i > 0) RoundBtn("‹") { idx = i - 1 }
            if (img.isNotEmpty()) Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F6FC)).clickable { imgFull = true }) {
                AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(st.m.str("name"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RD_FG, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ขั้นที่ ${i + 1} / ${steps.size}" + (st.m.str("desc").let { if (it.isNotEmpty()) " · $it" else "" }), fontSize = 11.5.sp, color = RD_SUB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundBtn("✕") { onDismiss() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RD_LINE))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepBody(st, pricing, s, note, { note = it }, last)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RD_LINE))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (last) {
                BigBtn("ยกเลิก", primary = false, modifier = Modifier.weight(1f)) { onDismiss() }
                BigBtn("ยืนยัน", modifier = Modifier.weight(1f)) { onConfirm(note) }
            } else {
                val miss = wzMissing(st, s)
                BigBtn(if (miss) "เลือกก่อนไปต่อ" else "ถัดไป", enabled = !miss, modifier = Modifier.weight(1f)) { idx = i + 1 }
            }
        }
    }
    if (imgFull && img.isNotEmpty()) RingImageViewer(img) { imgFull = false }
}

// ------------------------------------------------------------------ ดูรูปเต็มจอ

@Composable
fun RingImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xE0000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }.padding(16.dp), contentAlignment = Alignment.Center) {
            AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
            Box(Modifier.align(Alignment.TopEnd).size(38.dp).clip(CircleShape).background(Color(0x2EFFFFFF)).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 16.sp, color = Color.White) }
        }
    }
}

// ------------------------------------------------------------------ เมนูที่เลือกไว้ (ดู/ลบ/แก้จำนวน)

@Composable
fun RingCartSheet(session: Session, shopId: String, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val cart = remember(tick) { CartStore.load(session) }
    val arr: JSONArray? = cart.optJSONObject(shopId)?.optJSONArray("items")
    val n = arr?.length() ?: 0
    if (n == 0) { LaunchedEffect(Unit) { onChanged(); onDismiss() }; return }
    var qn = 0; var tot = 0.0
    for (k in 0 until n) { val it = arr!!.getJSONObject(k); val q = maxOf(1, it.optInt("qty", 1)); qn += q; tot += it.optDouble("price", 0.0) * q }
    fun save() { CartStore.save(session, cart); tick++; onChanged() }
    RingSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("เมนูที่เลือกไว้", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RD_FG)
                Text("$qn ชิ้น", fontSize = 11.5.sp, color = RD_SUB)
            }
            RoundBtn("✕") { onDismiss() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RD_LINE))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            for (k in 0 until n) {
                val it = arr!!.getJSONObject(k)
                val q = maxOf(1, it.optInt("qty", 1))
                val ops = it.optJSONArray("options")
                val opText = (0 until (ops?.length() ?: 0)).map { j -> ops!!.getJSONObject(j).optString("name") }.filter { x -> x.isNotEmpty() }.joinToString(", ")
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(it.optString("name"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RD_FG)
                        if (opText.isNotEmpty()) Text(opText, fontSize = 11.5.sp, color = RD_SUB, modifier = Modifier.padding(top = 2.dp))
                        if (it.optString("note").isNotEmpty()) Text("โน้ต: ${it.optString("note")}", fontSize = 11.5.sp, color = RD_SUB, modifier = Modifier.padding(top = 2.dp))
                        Text("฿${fmtN(it.optDouble("price", 0.0) * q)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RD_ACC, modifier = Modifier.padding(top = 3.dp))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(30.dp).alpha(if (q <= 1) 0.3f else 1f).clip(CircleShape).background(RD_BLUE).clickable(enabled = q > 1) { it.put("qty", q - 1); save() }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = Color.White) }
                            Text("$q", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RD_FG)
                            Box(Modifier.size(30.dp).clip(CircleShape).background(RD_BLUE).clickable { it.put("qty", q + 1); save() }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = Color.White) }
                        }
                    }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x2EE5484D)).clickable {
                        arr!!.remove(k)
                        if (arr.length() == 0) cart.remove(shopId)
                        save()
                    }, contentAlignment = Alignment.Center) { Text("🗑", fontSize = 15.sp) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x140B2C63)))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RD_LINE))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("รวม", fontSize = 15.sp, color = RD_FG)
            Text("฿${fmtN(tot)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RD_ACC)
        }
    }
}

// ------------------------------------------------------------------ โปรไฟล์ร้าน

@Composable
fun RingProfileSheet(s: Shop, open: Boolean, onReviews: () -> Unit, onGoMenu: () -> Unit, onDismiss: () -> Unit) {
    var imgFull by remember { mutableStateOf<String?>(null) }
    RingSheet(onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.linearGradient(listOf(RD_BLUE, RD_FG)))) {
                    if (s.bannerUrl.isNotEmpty()) AsyncImage(s.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable { imgFull = s.bannerUrl })
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp)) {
                    Row(Modifier.offset(y = (-28).dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(RD_FG).border(3.dp, Color.White, RoundedCornerShape(16.dp))
                                .clickable(enabled = s.avatarUrl.isNotEmpty()) { imgFull = s.avatarUrl },
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.avatarUrl.isNotEmpty()) AsyncImage(s.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else PathIcon(P.RESTAURANT, Color.White, 30.dp)
                        }
                        Column(Modifier.padding(top = 32.dp)) {
                            Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = RD_FG)
                            Text(
                                if (open) "เปิดอยู่" else "ปิดอยู่", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (open) Color(0xFF15803D) else Color(0xFFDC2626),
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (open) Color(0x2216A34A) else Color(0x22EF4444)).padding(horizontal = 9.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Column(Modifier.offset(y = (-28).dp)) {
                        if (s.desc.isNotEmpty()) Text(s.desc, fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PathIcon(P.STAR, Color(0xFFF5B301), 13.dp)
                            Text(" " + "%.1f".format(s.rating) + " · ", fontSize = 13.sp, color = RD_SUB)
                            Text("ดูรีวิว", fontSize = 13.sp, color = RD_ACC, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onDismiss(); onReviews() })
                        }
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PathIcon(P.CLOCK, RD_SUB, 13.dp)
                            val dt = s.deliveryTime.let { if (it.contains("นาที")) it else "$it นาที" }
                            val dist = s.dist?.let { " · " + if (it < 1) "${Math.round(it * 1000)}m" else "%.2fkm".format(it) } ?: ""
                            Text(" $dt$dist", fontSize = 13.sp, color = RD_SUB)
                        }
                        if (truthy(s.d["autoSchedule"]) && s.d.str("scheduleOpen").isNotEmpty() && s.d.str("scheduleClose").isNotEmpty())
                            Text("🗓 เปิด ${s.d.str("scheduleOpen")}-${s.d.str("scheduleClose")} น.", fontSize = 13.sp, color = RD_SUB, modifier = Modifier.padding(vertical = 4.dp))
                        if (truthy(s.d["loyaltyEnabled"])) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PathIcon(P.TAG, RD_SUB, 13.dp); Text(" สะสมแต้ม", fontSize = 13.sp, color = RD_SUB)
                        }
                        if (!open && s.d.str("closeReason").isNotEmpty()) Text("🔒 ${s.d.str("closeReason")}", fontSize = 13.sp, color = RD_SUB, modifier = Modifier.padding(vertical = 4.dp))
                        if (open) BigBtn("ดูเมนูร้านนี้", modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { onDismiss(); onGoMenu() }
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(34.dp).clip(CircleShape).background(Color(0xE6FFFFFF)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 15.sp, color = RD_FG)
            }
        }
    }
    imgFull?.let { u -> RingImageViewer(u) { imgFull = null } }
}
