package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.*
import org.json.JSONArray
import org.json.JSONObject

private val PRIMARY = Color(0xFF0C4AA6)
private val PRIMARY_LIGHT = Color(0xFFE8F0FE)
private val RED = Color(0xFFDC2626)
private const val CHECK = "M20 6L9 17l-5-5"

/** สถานะป๊อบอัพเลือกตัวเลือกเมนู — ตรรกะเดียวกับ menu.html (popupSelections / popupSubSelections / popupSubQty) */
class PopupState(val id: String, val m: Doc) {
    val groups = asMapList(m["optionGroups"])
    val menuGp = num(m["gp"]) ?: 0.0
    var sel by mutableStateOf<Map<Int, Any>>(
        groups.withIndex().filter { it.value.str("type") == "price" }.associate { it.index to (num(it.value["priceMin"]) ?: 0.0) }
    )
    var subSel by mutableStateOf<Map<String, Any>>(emptyMap())
    var subQty by mutableStateOf<Map<String, Int>>(emptyMap())
    var qty by mutableIntStateOf(1)
    var errors by mutableStateOf<Set<String>>(emptySet())

    private fun touch() { if (errors.isNotEmpty()) errors = emptySet() }
    fun type(g: Doc) = g.str("type").ifEmpty { "normal" }
    fun isSingle(g: Doc) = (num(g["maxSelect"])?.toInt() ?: 0) == 1
    fun sgIsMulti(sg: Doc) = if (sg.containsKey("maxSelect")) num(sg["maxSelect"])?.toInt() != 1 else truthy(sg["multiSelect"])
    fun sgMax(sg: Doc) = if (sg.containsKey("maxSelect")) (num(sg["maxSelect"])?.toInt() ?: 0) else 0
    fun options(g: Doc) = asMapList(g["options"])

    /** ตัวเลือกย่อยแบบบังคับ (ราคา/กก.) มีค่าเริ่มต้นเสมอ เหมือนเว็บที่ตั้งค่าตอน render */
    fun subEff(key: String, sg: Doc): Any? {
        val v = subSel[key]
        if (v != null) return v
        if (truthy(sg["required"])) return when (type(sg)) { "price" -> num(sg["priceMin"]) ?: 0.0; "kg" -> Kg(); else -> null }
        return null
    }

    fun selectOption(gi: Int, oi: Int) {
        val g = groups[gi]; touch()
        if (isSingle(g)) {
            val prev = sel[gi] as? Int
            sel = if (prev == oi) sel - gi else sel + (gi to oi)
            if (prev != null && prev != oi) clearSub(gi, prev)
        } else {
            val cur = (sel[gi] as? Set<*>)?.map { it as Int }?.toSet() ?: emptySet()
            if (oi in cur) { sel = sel + (gi to (cur - oi)); clearSub(gi, oi) }
            else {
                val mx = num(g["maxSelect"])?.toInt() ?: 0
                if (mx > 0 && cur.size >= mx) return
                sel = sel + (gi to (cur + oi))
            }
        }
    }
    private fun clearSub(gi: Int, oi: Int) {
        val p = "$gi-$oi-"
        subSel = subSel.filterKeys { !it.startsWith(p) }
        subQty = subQty.filterKeys { !it.startsWith(p) }
    }
    fun setPrice(gi: Int, v: Double) { touch(); sel = sel + (gi to v) }
    fun setKg(gi: Int, k: Kg) { touch(); sel = sel + (gi to k) }
    fun selectSubSingle(key: String, soi: Int) { touch(); subSel = if (subSel[key] == soi) subSel - key else subSel + (key to soi) }
    fun selectSubMulti(key: String, soi: Int, sg: Doc) {
        touch()
        val cur = (subSel[key] as? Set<*>)?.map { it as Int }?.toSet() ?: emptySet()
        if (soi in cur) { subSel = subSel + (key to (cur - soi)); subQty = subQty - "$key-$soi" }
        else {
            val mx = sgMax(sg)
            if (mx > 0 && cur.size >= mx) return
            subSel = subSel + (key to (cur + soi)); subQty = subQty + ("$key-$soi" to 1)
        }
    }
    fun adjustSubQty(key: String, soi: Int, d: Int) { subQty = subQty + ("$key-$soi" to maxOf(1, (subQty["$key-$soi"] ?: 1) + d)) }
    fun setSub(key: String, v: Any?) { touch(); subSel = if (v == null) subSel - key else subSel + (key to v) }

    // ---------- ราคา ----------
    private fun subExtra(g: Doc, gi: Int, oi: Int, p: Pricing): Int {
        val o = options(g).getOrNull(oi) ?: return 0
        var s = 0
        asMapList(o["subGroups"]).forEachIndexed { sgi, sg ->
            val key = "$gi-$oi-$sgi"
            when (val v = subEff(key, sg)) {
                is Double -> if (type(sg) == "price") s += p.calcSub(v)
                is Kg -> { val b = v.totalKheed * (num(sg["kgRate"]) ?: 0.0); if (b > 0) s += p.calcSub(b) }
                is Set<*> -> v.forEach { soi -> val so = options(sg).getOrNull(soi as Int); s += p.calcSub(num(so?.get("price")) ?: 0.0) * (subQty["$key-$soi"] ?: 1) }
                is Int -> { val so = options(sg).getOrNull(v); if (so != null) s += p.calcSub(num(so["price"]) ?: 0.0) }
            }
        }
        return s
    }

    private fun groupExtra(g: Doc, gi: Int, p: Pricing): Int {
        return when (type(g)) {
            "price" -> (sel[gi] as? Double)?.let { p.calcPrice(it, menuGp) } ?: 0
            "kg" -> (sel[gi] as? Kg)?.let { val b = it.totalKheed * (num(g["kgRate"]) ?: 0.0); if (b > 0) p.calcPrice(b, menuGp) else 0 } ?: 0
            else -> if (isSingle(g)) {
                (sel[gi] as? Int)?.let { oi -> p.calcSub(num(options(g).getOrNull(oi)?.get("price")) ?: 0.0) + subExtra(g, gi, oi, p) } ?: 0
            } else {
                ((sel[gi] as? Set<*>)?.sumOf { oi -> p.calcSub(num(options(g).getOrNull(oi as Int)?.get("price")) ?: 0.0) + subExtra(g, gi, oi, p) }) ?: 0
            }
        }
    }

    fun unitPrice(p: Pricing): Int = p.calcPrice(num(m["price"]) ?: 0.0, menuGp) + groups.withIndex().sumOf { groupExtra(it.value, it.index, p) }
    fun total(p: Pricing) = unitPrice(p) * qty

    // ---------- ตรวจข้อมูลบังคับ ----------
    private fun subMissing(g: Doc, gi: Int, oi: Int, out: MutableSet<String>) {
        val o = options(g).getOrNull(oi) ?: return
        asMapList(o["subGroups"]).forEachIndexed { sgi, sg ->
            if (!truthy(sg["required"])) return@forEachIndexed
            val key = "$gi-$oi-$sgi"; val v = subEff(key, sg)
            val miss = when (type(sg)) {
                "price" -> v == null
                "kg" -> (v as? Kg)?.totalKheed?.let { it == 0.0 } ?: true
                else -> if (sgIsMulti(sg)) (v as? Set<*>)?.isEmpty() ?: true else v !is Int
            }
            if (miss) out += "sog-$key"
        }
    }

    fun missing(): Set<String> {
        val out = mutableSetOf<String>()
        groups.forEachIndexed { gi, g ->
            val req = truthy(g["required"])
            val miss = when (type(g)) {
                "price" -> false
                "kg" -> req && ((sel[gi] as? Kg)?.totalKheed ?: 0.0) == 0.0
                else -> if (isSingle(g)) req && sel[gi] !is Int else req && ((sel[gi] as? Set<*>)?.isEmpty() ?: true)
            }
            if (miss) out += "og-$gi"
            if (type(g) == "normal") {
                if (isSingle(g)) (sel[gi] as? Int)?.let { subMissing(g, gi, it, out) }
                else (sel[gi] as? Set<*>)?.forEach { subMissing(g, gi, it as Int, out) }
            }
        }
        return out
    }

    // ---------- ตัวเลือกที่เลือก (บันทึกลงตะกร้า) ----------
    private fun opt(title: String, name: String, price: Double) = JSONObject().put("groupTitle", title).put("name", name).put("price", price)

    private fun subOptions(g: Doc, gi: Int, oi: Int, out: JSONArray) {
        val o = options(g).getOrNull(oi) ?: return
        asMapList(o["subGroups"]).forEachIndexed { sgi, sg ->
            val key = "$gi-$oi-$sgi"; val t = "${g.str("title")} → ${sg.str("title")}"
            when (val v = subEff(key, sg)) {
                is Double -> if (type(sg) == "price") out.put(opt(t, "${sg.str("title")} ฿${fmtNum(v)}", v))
                is Kg -> if (v.totalKheed > 0) out.put(opt(t, "${fmtNum(v.kg)} กก. ${fmtNum(v.kheed)} ขีด", v.totalKheed * (num(sg["kgRate"]) ?: 0.0)))
                is Set<*> -> v.forEach { soi ->
                    val so = options(sg).getOrNull(soi as Int) ?: return@forEach
                    val q = subQty["$key-$soi"] ?: 1
                    out.put(opt(t, if (q > 1) "${so.str("name")} x$q" else so.str("name"), (num(so["price"]) ?: 0.0) * q))
                }
                is Int -> options(sg).getOrNull(v)?.let { out.put(opt(t, it.str("name"), num(it["price"]) ?: 0.0)) }
            }
        }
    }

    fun selectedOptions(): JSONArray {
        val out = JSONArray()
        groups.forEachIndexed { gi, g ->
            when (type(g)) {
                "price" -> (sel[gi] as? Double)?.let { out.put(opt(g.str("title"), "${g.str("title")} ฿${fmtNum(it)}", it)) }
                "kg" -> (sel[gi] as? Kg)?.let { if (it.totalKheed > 0) out.put(opt(g.str("title"), "${fmtNum(it.kg)} กก. ${fmtNum(it.kheed)} ขีด", it.totalKheed * (num(g["kgRate"]) ?: 0.0))) }
                else -> if (isSingle(g)) {
                    (sel[gi] as? Int)?.let { oi ->
                        val o = options(g).getOrNull(oi)
                        out.put(opt(g.str("title"), o?.str("name") ?: "", num(o?.get("price")) ?: 0.0)); subOptions(g, gi, oi, out)
                    }
                } else (sel[gi] as? Set<*>)?.forEach { oi ->
                    val o = options(g).getOrNull(oi as Int)
                    out.put(opt(g.str("title"), o?.str("name") ?: "", num(o?.get("price")) ?: 0.0)); subOptions(g, gi, oi, out)
                }
            }
        }
        return out
    }
}

// ================= UI ของตัวเลือก =================
@Composable
private fun Badge(required: Boolean) {
    Text(
        if (required) "บังคับ" else "ไม่บังคับ", fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = if (required) RED else PRIMARY,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (required) Color(0xFFFEE2E2) else PRIMARY_LIGHT).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun SectionBox(err: Boolean, sub: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = if (sub) 16.dp else 0.dp, top = if (sub) 6.dp else 0.dp, bottom = if (sub) 0.dp else 16.dp)
            .then(if (sub) Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC)).padding(10.dp) else Modifier)
            .then(if (err) Modifier.border(2.dp, RED, RoundedCornerShape(12.dp)).padding(4.dp) else Modifier),
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleRow(title: String, required: Boolean?, hint: String?, small: Boolean) {
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        if (required != null) Badge(required)
        if (hint != null) Text(hint, fontSize = if (small) 11.sp else 12.sp, color = C.Subtext)
    }
}

@Composable
private fun OptionRow(single: Boolean, radio: Boolean, selected: Boolean, name: String, price: String, onClick: () -> Unit, mid: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(shape).background(if (selected) PRIMARY_LIGHT else Color.White)
            .border(2.dp, if (selected) PRIMARY else Color(0xFFF0F2F5), shape).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (radio) {
            Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) PRIMARY else Color(0xFFCBD5E1), CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(PRIMARY))
            }
        } else {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) PRIMARY else Color.White)
                .border(2.dp, if (selected) PRIMARY else Color(0xFFCBD5E1), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (selected) PathIcon(CHECK, Color.White, 14.dp, stroke = true)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.weight(1f))
        mid?.invoke()
        if (price.isNotEmpty()) Text(price, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PRIMARY)
    }
}

@Composable
private fun StepBtn(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (enabled) PRIMARY else Color(0xFFCBD5E1)).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun PricePicker(value: Double, min: Double, max: Double, step: Double, shown: Int, onChange: (Double) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAFBFC)).border(2.dp, Color(0xFFF0F2F5), RoundedCornerShape(12.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
        ) {
            StepBtn("−", value > min) { onChange((value - step).coerceIn(min, max)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("฿$shown", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PRIMARY)
                Text("ร้านได้ ฿${fmtNum(value)}", fontSize = 11.sp, color = C.Subtext)
            }
            StepBtn("+", value < max) { onChange((value + step).coerceIn(min, max)) }
        }
        Text("เลือกได้ตั้งแต่ ฿${fmtNum(min)} - ฿${fmtNum(max)}", fontSize = 11.5.sp, color = C.Subtext, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun KgInputs(kg: Kg, rate: Double, shown: (Double) -> Int, onChange: (Kg) -> Unit) {
    var kgT by remember { mutableStateOf(if (kg.kg == 0.0) "" else fmtNum(kg.kg)) }
    var kdT by remember { mutableStateOf(if (kg.kheed == 0.0) "" else fmtNum(kg.kheed)) }
    val kbd = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                NimitInput(kgT, { t -> kgT = t; onChange(Kg(maxOf(0.0, t.toDoubleOrNull() ?: 0.0), kg.kheed)) }, "0", keyboardOptions = kbd)
                Text("กิโลกรัม", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                NimitInput(kdT, { t -> kdT = t; onChange(Kg(kg.kg, (t.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.0))) }, "0", keyboardOptions = kbd)
                Text("ขีด", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 4.dp))
            }
        }
        val base = kg.totalKheed * rate
        Text(
            "฿${fmtNum(rate)}/ขีด" + if (kg.totalKheed > 0) " · ราคาที่ต้องจ่าย ฿${shown(base)}" else "",
            fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun EnableRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(bottom = if (on) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (on) PRIMARY else Color.White).border(2.dp, if (on) PRIMARY else Color(0xFFCBD5E1), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            if (on) PathIcon(CHECK, Color.White, 13.dp, stroke = true)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = C.Text)
    }
}

@Composable
fun PopupBody(st: PopupState, p: Pricing) {
    st.groups.forEachIndexed { gi, g ->
        val type = st.type(g)
        val title = g.str("title"); val req = truthy(g["required"])
        when (type) {
            "price" -> {
                val min = num(g["priceMin"]) ?: 0.0; val max = num(g["priceMax"]) ?: 0.0; val step = num(g["priceStep"]) ?: 5.0
                val v = (st.sel[gi] as? Double) ?: min
                SectionBox("og-$gi" in st.errors, false) {
                    TitleRow(title, null, null, false)
                    PricePicker(v, min, max, step, p.calcPrice(v, st.menuGp)) { st.setPrice(gi, it) }
                }
            }
            "kg" -> {
                val cur = (st.sel[gi] as? Kg) ?: Kg()
                SectionBox("og-$gi" in st.errors, false) {
                    TitleRow(title, req, null, false)
                    KgInputs(cur, num(g["kgRate"]) ?: 0.0, { p.calcPrice(it, st.menuGp) }) { st.setKg(gi, it) }
                }
            }
            else -> {
                val single = st.isSingle(g)
                val mx = num(g["maxSelect"])?.toInt() ?: 0
                val hint = if (single) "เลือก 1" else if (mx > 0) "เลือกได้สูงสุด $mx อัน" else "เลือกได้หลายอย่าง"
                SectionBox("og-$gi" in st.errors, false) {
                    TitleRow(title, req, hint, false)
                    st.options(g).forEachIndexed { oi, o ->
                        val selected = if (single) st.sel[gi] == oi else (st.sel[gi] as? Set<*>)?.contains(oi) == true
                        val pr = num(o["price"]) ?: 0.0
                        OptionRow(single, single, selected, o.str("name"), if (pr > 0) "+฿${p.calcSub(pr)}" else "", { st.selectOption(gi, oi) })
                        if (selected) asMapList(o["subGroups"]).forEachIndexed { sgi, sg -> SubGroup(st, p, "$gi-$oi-$sgi", sg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubGroup(st: PopupState, p: Pricing, key: String, sg: Doc) {
    val t = st.type(sg); val req = truthy(sg["required"]); val title = sg.str("title")
    val err = "sog-$key" in st.errors
    when (t) {
        "price" -> {
            val min = num(sg["priceMin"]) ?: 0.0; val max = num(sg["priceMax"]) ?: 0.0; val step = num(sg["priceStep"]) ?: 5.0
            val v = st.subEff(key, sg) as? Double
            SectionBox(err, true) {
                TitleRow(title, req, null, true)
                if (!req) EnableRow("เพิ่ม $title", v != null) { st.setSub(key, if (v != null) null else min) }
                if (v != null) PricePicker(v, min, max, step, p.calcSub(v)) { st.setSub(key, it) }
            }
        }
        "kg" -> {
            val v = st.subEff(key, sg) as? Kg
            SectionBox(err, true) {
                TitleRow(title, req, null, true)
                if (!req) EnableRow("เพิ่ม $title", v != null) { st.setSub(key, if (v != null) null else Kg()) }
                if (v != null) KgInputs(v, num(sg["kgRate"]) ?: 0.0, { p.calcSub(it) }) { st.setSub(key, it) }
            }
        }
        else -> {
            val multi = st.sgIsMulti(sg)
            val mx = st.sgMax(sg)
            SectionBox(err, true) {
                TitleRow(title, req, if (multi) (if (mx > 0) "เลือกได้สูงสุด $mx อัน" else "เลือกได้หลายอย่าง") else null, true)
                st.options(sg).forEachIndexed { soi, so ->
                    val pr = num(so["price"]) ?: 0.0
                    if (multi) {
                        val selected = (st.subSel[key] as? Set<*>)?.contains(soi) == true
                        val q = st.subQty["$key-$soi"] ?: 1
                        OptionRow(false, false, selected, so.str("name"), if (pr > 0) "+฿${p.calcSub(pr) * (if (selected) q else 1)}" else "",
                            { st.selectSubMulti(key, soi, sg) },
                            mid = if (selected) ({
                                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(PRIMARY).clickable { st.adjustSubQty(key, soi, -1) }, contentAlignment = Alignment.Center) { Text("−", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                                    Text("$q", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(PRIMARY).clickable { st.adjustSubQty(key, soi, 1) }, contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                                }
                            }) else null
                        )
                    } else {
                        val selected = st.subSel[key] == soi
                        OptionRow(false, false, selected, so.str("name"), if (pr > 0) "+฿${p.calcSub(pr)}" else "", { st.selectSubSingle(key, soi) })
                    }
                }
            }
        }
    }
}
