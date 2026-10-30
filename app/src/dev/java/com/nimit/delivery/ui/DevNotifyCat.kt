package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevBroadcast
import com.nimit.delivery.data.DevCatPlan
import com.nimit.delivery.data.DevNotifyCat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun devCatWho(b: DevBroadcast): String {
    val role = when (b.role) {
        "shop" -> "ร้านค้า"
        "admin" -> "แอดมิน"
        "rider" -> "ไรเดอร์"
        else -> "ลูกค้า"
    }
    val scope = when (b.scope) {
        "all" -> "ทุกเขต"
        "zone" -> "ในเขต" + (if (b.zoneName.isNotEmpty()) " " + b.zoneName else "")
        else -> "รายคน"
    }
    val rep = when (b.repeat) {
        "once" -> "ครั้งเดียว"
        "daily" -> "ทุกวัน"
        else -> "กดส่งเอง"
    }
    return role + " · " + scope + " · " + rep
}

@Composable
private fun DevCatChip(text: String, on: Boolean, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (!enabled) Color(0xFF94A3B8) else if (on) Color.White else accent,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (!enabled) Color(0xFFF1F5F9) else if (on) accent else accent.copy(alpha = 0.1f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun DevCatInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}

// จัดการหมวดหมู่: เพิ่ม / แก้ชื่อ / ลบ (ลบหมวดไม่ลบข้อความ)
@Composable
fun DevCategoryManageDialog(cats: List<String>, docs: List<DevBroadcast>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var delAsk by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun run(after: () -> Unit, block: suspend () -> String?) {
        busy = true
        err = ""
        scope.launch {
            val r = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "ทำรายการไม่สำเร็จ"
            }
            busy = false
            if (r == null) {
                after()
                onChanged()
            } else {
                err = r
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("จัดการหมวดหมู่", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (cats.isEmpty()) Text("ยังไม่มีหมวดหมู่", fontSize = 13.sp, color = DevC.S, modifier = Modifier.padding(bottom = 8.dp))
                cats.forEach { c ->
                    val n = docs.count { it.category == c }
                    if (editing == c) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DevCatInput(editName, { editName = it }, "ชื่อหมวด", Modifier.weight(1f))
                            DevCatChip("บันทึก", true, DevC.P, !busy) { run({ editing = null }) { DevNotifyCat.rename(cats, c, editName, docs) } }
                            DevCatChip("ยกเลิก", false, DevC.S) { editing = null }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c + " (" + n + ")", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DevC.T, modifier = Modifier.weight(1f))
                            Text(
                                "แก้ชื่อ", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.P,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) {
                                    editing = c
                                    editName = c
                                }.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            Text(
                                "ลบ", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DevC.R,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { delAsk = c }.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Text("เพิ่มหมวดหมู่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevCatInput(newName, { newName = it }, "เช่น โปรโมชั่น", Modifier.weight(1f))
                    DevCatChip("เพิ่ม", true, DevC.Gn, !busy) { run({ newName = "" }) { DevNotifyCat.add(cats, newName) } }
                }
                if (err.isNotEmpty()) Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("ปิด", color = DevC.P, fontWeight = FontWeight.Bold) } }
    )
    val d = delAsk
    if (d != null) {
        AlertDialog(
            onDismissRequest = { delAsk = null },
            containerColor = Color.White,
            title = { Text("ลบหมวดหมู่ " + d + "?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = { Text("ข้อความในหมวดนี้จะไม่ถูกลบ แต่จะกลับไปเป็น \"ไม่มีหมวด\"", fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = {
                    delAsk = null
                    run({ }) { DevNotifyCat.delete(cats, d, docs) }
                }) { Text("ลบ", color = DevC.R, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { delAsk = null }) { Text("ยกเลิก", color = DevC.S) } }
        )
    }
}

// ส่งทั้งหมวด: เลือกเฉพาะบางข้อความได้ และเลือกส่งทุกข้อความ หรือสุ่มคนละข้อความ
@Composable
fun DevCategorySendDialog(category: String, items: List<DevBroadcast>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var sel by remember { mutableStateOf(items.map { it.id }.toSet()) }
    var random by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<DevCatPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val chosen = items.filter { it.id in sel }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("ส่งทั้งหมวด: " + category, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val p = plan
                if (p == null) {
                    Text("วิธีส่ง", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        DevCatChip("ส่งทุกข้อความที่เลือก", !random, DevC.P) { random = false }
                        DevCatChip("สุ่มคนละข้อความ", random, DevC.Gn) { random = true }
                    }
                    Text(
                        if (random) "แต่ละเครื่องที่เป็นผู้รับจะได้ 1 ข้อความ สุ่มจากข้อความที่เลือก" else "ส่งทุกข้อความที่เลือกไปยังผู้รับของแต่ละข้อความ",
                        fontSize = 11.sp, color = DevC.S, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Text("เลือกข้อความ (" + chosen.size + "/" + items.size + ")", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 4.dp))
                    items.forEach { b ->
                        val on = b.id in sel
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { sel = if (on) sel - b.id else sel + b.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DevC.P))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(b.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DevC.T, maxLines = 1)
                                Text(devCatWho(b), fontSize = 11.sp, color = DevC.S, maxLines = 1)
                            }
                        }
                    }
                } else {
                    val total = p.tokens.map { it.size }.sum()
                    val distinct = p.tokens.flatten().toSet().size
                    Text(
                        if (random) "สุ่มคนละ 1 ข้อความ: " + distinct + " เครื่อง จาก " + p.items.size + " ข้อความ"
                        else p.items.size + " ข้อความ รวม " + total + " ครั้งส่ง",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.T, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    for (i in p.items.indices) {
                        Text(p.items[i].title + " → " + p.tokens[i].size + " เครื่อง", fontSize = 12.sp, color = DevC.T, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    p.notes.forEach { n -> Text(n, fontSize = 11.5.sp, color = DevC.O, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (err.isNotEmpty()) Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && chosen.isNotEmpty(),
                onClick = {
                    val p = plan
                    busy = true
                    err = ""
                    scope.launch {
                        try {
                            if (p == null) {
                                plan = DevNotifyCat.plan(chosen)
                                busy = false
                            } else {
                                val t = if (random) DevNotifyCat.sendRandom(p) else DevNotifyCat.sendAll(p)
                                busy = false
                                onDone(t)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            busy = false
                            err = "ทำรายการไม่สำเร็จ"
                        }
                    }
                }
            ) { Text(if (busy) "กำลังทำงาน..." else if (plan == null) "ตรวจผู้รับ" else "ส่งเลย", color = if (plan == null) DevC.P else DevC.R, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { if (plan != null) plan = null else onDismiss() }) {
                Text(if (plan != null) "ย้อนกลับ" else "ยกเลิก", color = DevC.S)
            }
        }
    )
}

// ย้ายข้อความที่ตั้งไว้ไปหมวดอื่น
@Composable
fun DevMoveCategoryDialog(b: DevBroadcast, cats: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun pick(cat: String) {
        if (cat == b.category) {
            onDismiss()
            return
        }
        busy = true
        err = ""
        scope.launch {
            val r = try {
                DevNotifyCat.setCategory(b.id, cat)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "ทำรายการไม่สำเร็จ"
            }
            busy = false
            if (r == null) onDone() else err = r
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("ย้ายไปหมวด", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(b.title, fontSize = 13.sp, color = DevC.S, modifier = Modifier.padding(bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevCatChip("ไม่มีหมวด", b.category.isEmpty(), DevC.S, !busy) { pick("") }
                    cats.forEach { c -> DevCatChip(c, b.category == c, DevC.P, !busy) { pick(c) } }
                }
                if (err.isNotEmpty()) Text(err, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DevC.R, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ปิด", color = DevC.P, fontWeight = FontWeight.Bold) } }
    )
}
