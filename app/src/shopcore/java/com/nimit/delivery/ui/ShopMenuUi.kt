package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nimit.delivery.data.shopNum
import kotlinx.coroutines.launch

private const val P_CUP = "M18.06 22.99h1.66c.84 0 1.53-.64 1.63-1.46L23 5.05h-5V1h-1.97v4.05h-4.97l.3 2.34c1.71.47 3.31 1.32 4.27 2.26 1.44 1.42 2.43 2.89 2.43 5.29v8.05zM1 21.99V21h15.03v.99c0 .55-.45 1-1.01 1H2.01c-.56 0-1.01-.45-1.01-1zm15.03-7c0-4.5-6.72-5-8.03-5-1.28 0-8 .5-8 5h16.03z"
private const val P_UP = "M7 14l5-5 5 5z"
private const val P_DOWN = "M7 10l5 5 5-5z"
private const val P_MOVE = "M9 3L5 6.99h3V14h2V6.99h3L9 3zm7 14.01V10h-2v7.01h-3L15 21l4-3.99h-3z"
private const val P_TRASH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
private const val P_X = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
private const val P_PLUS = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
private const val P_COPY = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"
private const val P_TRI = "M8 5v14l11-7z"

private val SBorder = Color(0xFFEEF0F4)
private val SSub = Color(0xFF64748B)

internal fun shopNumericFilter(v: String): String {
    var dot = false
    val sb = StringBuilder()
    for (c in v) {
        if (c.isDigit()) sb.append(c)
        else if (c == '.' && !dot) { dot = true; sb.append(c) }
    }
    return sb.toString()
}

/** ช่องกรอกแบบใช้ซ้ำในฟอร์มเมนู (ขนาด/ขอบ/ตัวอักษรปรับได้ตามคลาสต่างๆ ใน HTML) */
@Composable
internal fun SInput(
    value: String, onChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier,
    h: Dp = 46.dp, r: Dp = 12.dp, bw: Dp = 2.dp, fs: Int = 15, bold: Boolean = false, hp: Dp = 14.dp,
    align: Alignment = Alignment.CenterStart, numeric: Boolean = false, bg: Color = Color.White
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(r)
    BasicTextField(
        value = value,
        onValueChange = { v: String -> onChange(if (numeric) shopNumericFilter(v) else v) },
        singleLine = true,
        textStyle = TextStyle(fontSize = fs.sp, color = C.Text, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.height(h).clip(shape).background(bg).border(bw, if (focused) C.Primary else SBorder, shape).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = hp), contentAlignment = align) {
                if (value.isEmpty()) Text(placeholder, fontSize = fs.sp, color = Color(0xFF9CA3AF), maxLines = 1)
                inner()
            }
        }
    )
}

@Composable
private fun SPick(value: String, placeholder: String, h: Dp, modifier: Modifier = Modifier, arrow: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(h).clip(shape).background(Color.White).border(2.dp, SBorder, shape).clickable { onClick() }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = Color(0xFF9CA3AF), maxLines = 1)
            else Text(value, fontSize = 15.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (arrow) PathIcon(P_DOWN, SSub, 20.dp)
    }
}

@Composable
private fun FLabel(t: String, fs: Int = 13, mb: Dp = 5.dp) {
    Text(t, fontSize = fs.sp, fontWeight = FontWeight.Bold, color = SSub, modifier = Modifier.padding(bottom = mb))
}

@Composable
private fun EmptyMenu(t: String) {
    Text(t, fontSize = 14.sp, color = SSub, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp))
}

@Composable
private fun DashedBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(C.PrimaryLight)
            .drawBehind {
                drawRoundRect(Color(0xFFC7D7F5), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
}

@Composable
private fun TinyBtn(text: String, size: Dp, bg: Color, fg: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size).alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(8.dp)).background(bg).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, color = fg) }
}

@Composable
private fun IconBtn(path: String, size: Dp, radius: Dp, bg: Color, tint: Color, iconSize: Dp = 14.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size).alpha(if (enabled) 1f else 0.3f).clip(RoundedCornerShape(radius)).background(bg).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { PathIcon(path, tint, iconSize) }
}

@Composable
private fun GBadge(text: String, fs: Int = 10) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, fontSize = fs.sp, fontWeight = FontWeight.Bold, color = C.Primary, maxLines = 1)
    }
}

@Composable
private fun ReqRow(required: Boolean, bottom: Dp, onToggle: () -> Unit) {
    Row(Modifier.padding(bottom = bottom), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShopSwitch(required, ShopOrange, Color(0xFFCBD5E1), 34.dp, 20.dp) { onToggle() }
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (required) ShopOrangeLight else C.Gray).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(if (required) "บังคับ" else "ไม่บังคับ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (required) ShopOrange else SSub)
        }
    }
}

// =====================================================================
// ส่วนหมวดหมู่ + รายการเมนู (วางต่อท้ายหน้าแก้ไขข้อมูลร้าน)
// =====================================================================
@Composable
fun ShopMenuSection(ms: ShopMenuState) {
    // หมวดหมู่เมนู
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("หมวดหมู่เมนู", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(C.Primary)
                    .clickable { ms.dialog = MDialog("prompt", "ชื่อหมวดหมู่ เช่น เมนูตำถาด", "") { ms.addCategory(it) } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PathIcon(P_PLUS, Color.White, 14.dp)
                Text("เพิ่มหมวดหมู่", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        for ((i, c) in ms.categories.withIndex()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.Gray).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TinyBtn("▲", 28.dp, Color.White, C.Text, i != 0) { ms.moveCategory(i, -1) }
                TinyBtn("▼", 28.dp, Color.White, C.Text, i != ms.categories.size - 1) { ms.moveCategory(i, 1) }
                TinyBtn("✎", 28.dp, C.PrimaryLight, C.Primary) { ms.dialog = MDialog("prompt", "แก้ไขชื่อหมวดหมู่", c.name) { n -> ms.renameCategory(i, n) } }
                TinyBtn("×", 28.dp, ShopRedLight, ShopRed) {
                    ms.dialog = MDialog("confirm", "ลบหมวดหมู่ \"" + c.name + "\"? เมนูในหมวดนี้จะย้ายไปเป็น \"ไม่มีหมวดหมู่\"", "") { ms.deleteCategory(i) }
                }
            }
        }
        Text("เพิ่มหมวดหมู่ก่อน แล้วกดที่แถบหมวดหมู่เพื่อเพิ่มเมนูในหมวดนั้น", fontSize = 11.sp, color = SSub, modifier = Modifier.padding(top = 6.dp))
    }

    val err: String? = ms.loadError
    if (err != null) {
        EmptyMenu("โหลดเมนูไม่ได้: " + err + "\nเมนูที่เคยบันทึกไว้ยังอยู่ในระบบ ลองเข้าหน้านี้ใหม่อีกครั้ง")
    } else if (ms.categories.isEmpty() && ms.menus.isEmpty()) {
        EmptyMenu("ยังไม่มีหมวดหมู่ กด \"+ เพิ่มหมวดหมู่\" ด้านบน")
    } else {
        for (c in ms.categories) key(c.id) { CatGroup(ms, c.id, c.name, ms.itemsOf(c.id)) }
        val un: List<MMenu> = ms.uncategorized()
        if (un.isNotEmpty()) CatGroup(ms, "", "ไม่มีหมวดหมู่", un)
    }
}

@Composable
private fun CatGroup(ms: ShopMenuState, catId: String, name: String, items: List<MMenu>) {
    val isOpen: Boolean = ms.openCatId == catId
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Box(
            Modifier.fillMaxWidth().clickable { ms.toggleCat(catId) }
                .drawBehind {
                    val w: Float = 2.dp.toPx()
                    drawLine(Color(0xFFF5F6F8), Offset(0f, w / 2), Offset(size.width, w / 2), w)
                    drawLine(Color(0xFFF5F6F8), Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(name + " (" + items.size + ")", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.background(Color.White).padding(horizontal = 12.dp))
        }
        if (isOpen) {
            Column(Modifier.padding(top = 12.dp)) {
                if (catId.isNotEmpty()) DashedBtn("+ เพิ่มเมนูในหมวดนี้", Modifier.padding(bottom = 10.dp)) { ms.addModeCat = catId }
                if (items.isEmpty()) EmptyMenu("ยังไม่มีเมนูในหมวดนี้")
                else for ((i, m) in items.withIndex()) key(m.id) { MenuCard(ms, m, i, items.size, false) }
            }
        }
    }
}

@Composable
private fun MenuCard(ms: ShopMenuState, m: MMenu, idx: Int, total: Int, bulk: Boolean) {
    val isOpen: Boolean = if (bulk) ms.bulkOpenId == m.id else ms.openMenuId == m.id
    val avail: Boolean = m.available
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).alpha(if (avail || bulk) 1f else 0.6f).clip(shape)
            .border(2.dp, if (isOpen) C.Primary else Color(0xFFF0F2F5), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color.White)
                .clickable { if (bulk) ms.bulkOpenId = if (ms.bulkOpenId == m.id) null else m.id else ms.toggleMenu(m.id) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(C.Gray), contentAlignment = Alignment.Center) {
                if (m.imgUrl.isNotEmpty()) AsyncImage(m.imgUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else PathIcon(P_CUP, Color(0xFFCBD5E1), 20.dp)
            }
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                val nm: String = if (bulk) (m.name.ifEmpty { "เมนูใหม่ #" + (idx + 1) }) else m.name.ifEmpty { "ไม่มีชื่อ" }
                Text(
                    buildAnnotatedString {
                        append(nm)
                        if (!avail && !bulk) withStyle(SpanStyle(fontSize = 11.sp, color = ShopRed, fontWeight = FontWeight.Bold)) { append(" (ปิดขาย)") }
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text
                )
                Text("฿" + shopNum(pd(m.price)) + (if (m.groups.isNotEmpty()) " · " + m.groups.size + " กลุ่ม" else ""), fontSize = 12.sp, color = SSub, modifier = Modifier.padding(top = 2.dp))
            }
            if (bulk) {
                IconBtn(P_TRASH, 32.dp, 9.dp, ShopRedLight, ShopRed, enabled = total > 1) { ms.removeBulkRow(m.id) }
            } else {
                Column(Modifier.width(65.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ShopSwitch(avail, ShopGreen, Color(0xFFCBD5E1), 65.dp, 22.dp) { ms.toggleAvailable(m) }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        IconBtn(P_UP, 30.dp, 9.dp, C.Gray, SSub, 14.dp, idx != 0) { ms.moveMenuOrder(m, -1) }
                        IconBtn(P_DOWN, 30.dp, 9.dp, C.Gray, SSub, 14.dp, idx != total - 1) { ms.moveMenuOrder(m, 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        IconBtn(P_MOVE, 30.dp, 9.dp, C.PrimaryLight, C.Primary) { ms.moveCatFor = m }
                        IconBtn(P_TRASH, 30.dp, 9.dp, ShopRedLight, ShopRed) {
                            ms.dialog = MDialog("confirm", "ลบเมนูนี้?", "") {
                                scope.launch { try { ms.deleteMenu(m) } catch (e: Exception) { ms.toast("ลบไม่สำเร็จ") } }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(C.Gray)
                    .clickable { if (bulk) ms.bulkOpenId = if (ms.bulkOpenId == m.id) null else m.id else ms.toggleMenu(m.id) },
                contentAlignment = Alignment.Center
            ) { Box(Modifier.rotate(if (isOpen) 180f else 0f)) { PathIcon(P_DOWN, SSub, 16.dp) } }
        }
        if (isOpen) MenuForm(ms, m, bulk)
    }
}

@Composable
private fun MenuForm(ms: ShopMenuState, m: MMenu, bulk: Boolean) {
    val ctx = LocalContext.current
    val dirty: () -> Unit = { if (!bulk) ms.markDirty(m.id) }
    val proxy: Boolean = ms.shopType == "proxy"
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFAFBFC))
            .drawBehind { val w: Float = 2.dp.toPx(); drawLine(Color(0xFFF5F6F8), Offset(0f, w / 2), Offset(size.width, w / 2), w) }
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FLabel("ชื่อเมนู")
                SInput(m.name, { m.name = it; dirty() }, "ชื่อเมนู", Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                FLabel("ราคาร้าน (฿)")
                SInput(m.price, { m.price = it; dirty() }, "50", Modifier.fillMaxWidth(), numeric = true)
                val p: Double = pd(m.price)
                if (!proxy && p != 0.0) Text(
                    shopNum(p) + " → " + ms.previewCustomerPrice(p, pd(m.gp)) + " (ราคาที่ลูกค้าเห็น)",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FLabel("หมวดหมู่")
                SPick(m.category.ifEmpty { if (m.categoryId.isEmpty()) "ไม่มีหมวดหมู่" else ms.catName(m.categoryId).ifEmpty { "ไม่มีหมวดหมู่" } }, "", 46.dp, Modifier.fillMaxWidth(), true) { ms.pickCatFor = m }
            }
            Column(Modifier.weight(1f)) {
                FLabel(if (proxy) "GP หลัก (%)" else "GP% เพิ่มเติม")
                SInput(m.gp, { m.gp = it; dirty() }, "", Modifier.fillMaxWidth(), numeric = true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            FLabel("คำอธิบาย")
            SInput(m.desc, { m.desc = it; dirty() }, "อร่อยมาก", Modifier.fillMaxWidth())
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            FLabel("รูปเมนู")
            Box(
                Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray)
                    .drawBehind { drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))) }
                    .clickable { ms.requestImage(m.id) },
                contentAlignment = Alignment.Center
            ) {
                if (m.imgUrl.isNotEmpty()) AsyncImage(m.imgUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else PathIcon(P_CUP, Color(0xFFCBD5E1), 28.dp)
            }
        }
        // ตั้งเวลาขายอัตโนมัติ
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, SBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ตั้งเวลาขายอัตโนมัติ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSub, modifier = Modifier.weight(1f))
                ShopSwitch(m.schedEnabled, ShopGreen, Color(0xFFCBD5E1), 40.dp, 22.dp) { m.schedEnabled = !m.schedEnabled; dirty() }
            }
            if (m.schedEnabled) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FLabel("เวลาเปิดขาย", 11)
                        SPick(m.schedOpen, "--:--", 38.dp, Modifier.fillMaxWidth()) { shopPickTime(ctx, m.schedOpen) { m.schedOpen = it; dirty() } }
                    }
                    Column(Modifier.weight(1f)) {
                        FLabel("เวลาปิดขาย", 11)
                        SPick(m.schedClose, "--:--", 38.dp, Modifier.fillMaxWidth()) { shopPickTime(ctx, m.schedClose) { m.schedClose = it; dirty() } }
                    }
                }
            }
            Text("นอกช่วงเวลานี้ระบบจะขึ้น \"สินค้าหมด\" ให้อัตโนมัติ (รองรับข้ามเที่ยงคืน เช่น 20:00-02:00)", fontSize = 11.sp, color = SSub, lineHeight = 16.5.sp, modifier = Modifier.padding(top = 8.dp))
            Box(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight)
                    .clickable {
                        if (m.schedOpen.isEmpty() || m.schedClose.isEmpty()) ms.toast("กรุณาตั้งเวลาเปิด-ปิดขายของเมนูนี้ก่อน")
                        else { ms.applyMenus.clear(); ms.applyFor = m }
                    },
                contentAlignment = Alignment.Center
            ) { Text("ใช้เวลานี้กับหลายเมนู", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
        }
        // กลุ่มตัวเลือก
        Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("กลุ่มตัวเลือก", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).clickable { ms.gtypeFor = m }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PathIcon(P_PLUS, C.Primary, 12.dp)
                    Text("เพิ่มกลุ่ม", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                }
            }
            for ((gi, g) in m.groups.withIndex()) key(g) { GroupCard(ms, m, g, gi, m.groups.size, dirty) }
        }
    }
}

@Composable
private fun GroupCard(ms: ShopMenuState, m: MMenu, g: MGroup, gi: Int, total: Int, dirty: () -> Unit) {
    val proxy: Boolean = ms.shopType == "proxy"
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(Color.White).border(2.dp, Color(0xFFE2E8F0), shape).padding(12.dp)) {
        // หัวกลุ่ม
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyBtn("▲", 26.dp, Color.White, C.Text, gi != 0) {
                m.groups.add(gi - 1, m.groups.removeAt(gi)); dirty()
            }
            TinyBtn("▼", 26.dp, Color.White, C.Text, gi != total - 1) {
                m.groups.add(gi + 1, m.groups.removeAt(gi)); dirty()
            }
            SInput(g.title, { g.title = it; dirty() }, "ชื่อกลุ่ม เช่น ระดับเผ็ด", Modifier.weight(1f), h = 38.dp, r = 10.dp, fs = 14, bold = true, hp = 12.dp)
            if (g.type == "price") GBadge("ตั้งราคา") else if (g.type == "kg") GBadge("กิโลกรัม")
            IconBtn(P_COPY, 26.dp, 8.dp, C.PrimaryLight, C.Primary) {
                ms.copyGroups.clear(); ms.copyGroups.add(gi); ms.copyMenus.clear(); ms.copyFor = m
            }
            IconBtn(P_TRASH, 32.dp, 8.dp, ShopRedLight, ShopRed) { m.groups.removeAt(gi); dirty() }
        }
        if (g.type == "price") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { FLabel("ราคาต่ำสุด (฿)", 11); SInput(g.priceMin, { g.priceMin = it; dirty() }, "", Modifier.fillMaxWidth(), h = 38.dp, numeric = true) }
                Column(Modifier.weight(1f)) { FLabel("ราคาสูงสุด (฿)", 11); SInput(g.priceMax, { g.priceMax = it; dirty() }, "", Modifier.fillMaxWidth(), h = 38.dp, numeric = true) }
                Column(Modifier.weight(1f)) { FLabel("ปรับทีละ (฿)", 11); SInput(g.priceStep, { g.priceStep = it; dirty() }, "", Modifier.fillMaxWidth(), h = 38.dp, numeric = true) }
            }
            val mn: Double = pd(g.priceMin)
            Text(
                "ลูกค้าจะเห็นช่วงราคาพร้อม GP บวกแล้ว เช่น ราคาร้าน " + shopNum(mn) + "฿" + (if (proxy) "" else " → ลูกค้าจ่าย " + ms.previewCustomerPrice(mn, 0.0) + "฿"),
                fontSize = 11.sp, color = SSub
            )
        } else if (g.type == "kg") {
            ReqRow(g.required, 10.dp) { g.required = !g.required; dirty() }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                FLabel("ราคาต่อขีด (฿)", 11)
                SInput(g.kgRate, { g.kgRate = it; dirty() }, "", Modifier.fillMaxWidth(), h = 38.dp, numeric = true)
            }
            val rate: Double = pd(g.kgRate)
            if (!proxy && rate != 0.0) Text(shopNum(rate) + " → " + ms.previewSubPrice(rate) + " (ราคาที่ลูกค้าเห็นต่อขีด)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary)
            Text("ลูกค้าจะกรอกจำนวนกิโลกรัม/ขีดเอง ระบบคำนวณราคาอัตโนมัติ", fontSize = 11.sp, color = SSub)
        } else {
            ReqRow(g.required, 10.dp) { g.required = !g.required; if (g.required && g.maxSelect.trim().isEmpty()) g.maxSelect = "1"; dirty() }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("จำนวนที่เลือกได้:", fontSize = 12.sp, color = SSub)
                SInput(g.maxSelect, { g.maxSelect = it.filter { c -> c.isDigit() }; dirty() }, "ทุกอัน", Modifier.width(64.dp), h = 34.dp, r = 10.dp, fs = 13, hp = 8.dp, align = Alignment.Center)
                Text("(เว้นว่าง = เลือกได้ทุกอัน)", fontSize = 11.sp, color = SSub)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((oi, o) in g.options.withIndex()) key(o) { OptionRow(ms, m, g, gi, oi, o, dirty) }
            }
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .drawBehind { drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))) }
                    .clickable { g.options.add(MOption()); dirty() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                PathIcon(P_PLUS, SSub, 13.dp)
                Spacer(Modifier.width(4.dp))
                Text("เพิ่มตัวเลือก", fontSize = 12.sp, color = SSub)
            }
        }
    }
}

@Composable
private fun OptionRow(ms: ShopMenuState, m: MMenu, g: MGroup, gi: Int, oi: Int, o: MOption, dirty: () -> Unit) {
    val proxy: Boolean = ms.shopType == "proxy"
    val key: String = m.id + "|" + gi + "-" + oi
    val isExp: Boolean = ms.expanded.contains(key)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SInput(o.name, { o.name = it; dirty() }, "ชื่อตัวเลือก", Modifier.weight(1f), h = 36.dp, r = 10.dp, fs = 13, hp = 10.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("+฿", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SSub)
                SInput(o.price, { o.price = it; dirty() }, "", Modifier.width(62.dp), h = 36.dp, r = 10.dp, fs = 13, hp = 8.dp, align = Alignment.CenterEnd, numeric = true)
            }
            IconBtn(P_X, 28.dp, 8.dp, ShopRedLight, ShopRed, 12.dp) { g.options.removeAt(oi); dirty() }
        }
        val op: Double = pd(o.price)
        if (!proxy && op != 0.0) Text(shopNum(op) + " → " + ms.previewSubPrice(op), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.Primary)
        Row(
            Modifier.fillMaxWidth().clickable {
                if (ms.expanded.contains(key)) ms.expanded.remove(key) else ms.expanded.add(key)
            }.padding(top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.rotate(if (isExp) 90f else 0f)) { PathIcon(P_TRI, C.Primary, 13.dp) }
            Text("ตัวเลือกเพิ่ม" + (if (o.subGroups.isNotEmpty()) " (" + o.subGroups.size + ")" else ""), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary)
        }
        if (isExp) {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC)).padding(10.dp)) {
                for ((sgi, sg) in o.subGroups.withIndex()) key(sg) { SubGroupCard(ms, o, sgi, sg, dirty) }
                Box(
                    Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))
                        .drawBehind { drawRoundRect(Color(0xFFCBD5E1), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))) }
                        .clickable { ms.sgtypeFor = Triple(m, gi, oi) },
                    contentAlignment = Alignment.Center
                ) { Text("+ เพิ่มกลุ่มตัวเลือกย่อยสำหรับ \"" + o.name.ifEmpty { "ตัวเลือกนี้" } + "\"", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun SubGroupCard(ms: ShopMenuState, o: MOption, sgi: Int, sg: MSubGroup, dirty: () -> Unit) {
    val proxy: Boolean = ms.shopType == "proxy"
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(Color.White).border(1.5.dp, SBorder, shape).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SInput(sg.title, { sg.title = it; dirty() }, "ชื่อกลุ่มย่อย เช่น เพิ่มชีส", Modifier.weight(1f), h = 32.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 8.dp)
            if (sg.type == "price") GBadge("ตั้งราคา") else if (sg.type == "kg") GBadge("กิโลกรัม")
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEE2E2)).clickable { o.subGroups.removeAt(sgi); dirty() },
                contentAlignment = Alignment.Center
            ) { Text("✕", fontSize = 12.sp, color = ShopRed) }
        }
        ReqRow(sg.required, 6.dp) { sg.required = !sg.required; if (sg.required && sg.type == "normal" && sg.maxSelect.trim().isEmpty()) sg.maxSelect = "1"; dirty() }
        if (sg.type == "normal") {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("จำนวนที่เลือกได้:", fontSize = 11.sp, color = SSub)
                SInput(sg.maxSelect, { sg.maxSelect = it.filter { c -> c.isDigit() }; dirty() }, "ทุกอัน", Modifier.width(70.dp), h = 30.dp, r = 10.dp, fs = 12, hp = 8.dp, align = Alignment.Center)
                Text("(เว้นว่าง=หลายอัน)", fontSize = 10.sp, color = SSub)
            }
        }
        if (sg.type == "price") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) { Text("ต่ำสุด (฿)", fontSize = 10.sp, color = SSub, modifier = Modifier.padding(bottom = 2.dp)); SInput(sg.priceMin, { sg.priceMin = it; dirty() }, "", Modifier.fillMaxWidth(), h = 32.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 6.dp, numeric = true) }
                Column(Modifier.weight(1f)) { Text("สูงสุด (฿)", fontSize = 10.sp, color = SSub, modifier = Modifier.padding(bottom = 2.dp)); SInput(sg.priceMax, { sg.priceMax = it; dirty() }, "", Modifier.fillMaxWidth(), h = 32.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 6.dp, numeric = true) }
                Column(Modifier.weight(1f)) { Text("ทีละ (฿)", fontSize = 10.sp, color = SSub, modifier = Modifier.padding(bottom = 2.dp)); SInput(sg.priceStep, { sg.priceStep = it; dirty() }, "", Modifier.fillMaxWidth(), h = 32.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 6.dp, numeric = true) }
            }
        } else if (sg.type == "kg") {
            Text("ราคาต่อขีด (฿)", fontSize = 10.sp, color = SSub, modifier = Modifier.padding(bottom = 2.dp))
            SInput(sg.kgRate, { sg.kgRate = it; dirty() }, "", Modifier.fillMaxWidth(), h = 32.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 8.dp, numeric = true)
            val rate: Double = pd(sg.kgRate)
            if (!proxy && rate != 0.0) Text(shopNum(rate) + " → " + ms.previewSubPrice(rate) + " (ราคาที่ลูกค้าเห็นต่อขีด)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.padding(top = 4.dp))
        } else {
            for ((soi, so) in sg.options.withIndex()) key(so) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SInput(so.name, { so.name = it; dirty() }, "ชื่อตัวเลือกย่อย", Modifier.weight(1f), h = 30.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 8.dp)
                    Text("+฿", fontSize = 11.sp, color = SSub)
                    SInput(so.price, { so.price = it; dirty() }, "", Modifier.width(56.dp), h = 30.dp, r = 8.dp, bw = 1.5.dp, fs = 12, hp = 6.dp, numeric = true)
                    Box(Modifier.size(26.dp).clickable { sg.options.removeAt(soi); dirty() }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 12.sp, color = SSub) }
                }
            }
            Box(
                Modifier.padding(top = 2.dp).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(C.PrimaryLight).clickable { sg.options.add(MSubOpt()); dirty() },
                contentAlignment = Alignment.Center
            ) { Text("+ ตัวเลือกย่อย", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
        }
    }
}

// =====================================================================
// ป๊อบอัพทั้งหมดของส่วนเมนู (วางที่ชั้นบนสุดของหน้า)
// =====================================================================
@Composable
fun ShopMenuOverlays(ms: ShopMenuState) {
    val scope = rememberCoroutineScope()

    val d: MDialog? = ms.dialog
    if (d != null) {
        var txt by remember(d) { mutableStateOf(d.text) }
        MSheet({ ms.dialog = null }) {
            if (d.kind == "prompt") {
                MModalTitle(d.title)
                SInput(txt, { txt = it }, "", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                MBtn("ตกลง", C.Primary, Color.White, FontWeight.Bold) { ms.dialog = null; d.onOk(txt) }
            } else if (d.kind == "confirm") {
                Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(bottom = 14.dp))
                MBtn("ยืนยัน", C.Primary, Color.White, FontWeight.Bold) { ms.dialog = null; d.onOk("") }
            } else {
                Text(d.text, fontSize = 14.sp, color = C.Text, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 14.dp))
                MBtn("ตกลง", C.Primary, Color.White, FontWeight.Bold) { ms.dialog = null }
            }
            if (d.kind != "alert") MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { ms.dialog = null }
        }
    }

    val mv: MMenu? = ms.moveCatFor
    if (mv != null) MSheet({ ms.moveCatFor = null }) {
        MModalTitle("ย้ายเมนูไปหมวดหมู่")
        val opts: List<Pair<String, String>> = listOf("" to "ไม่มีหมวดหมู่") + ms.categories.map { it.id to it.name }
        for ((id, nm) in opts) CatChoice(nm, mv.categoryId == id) { ms.moveMenuToCategory(mv, id); ms.moveCatFor = null }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold) { ms.moveCatFor = null }
    }

    val pk: MMenu? = ms.pickCatFor
    if (pk != null) MSheet({ ms.pickCatFor = null }) {
        MModalTitle("หมวดหมู่")
        val opts: List<Pair<String, String>> = listOf("" to "ไม่มีหมวดหมู่") + ms.categories.map { it.id to it.name }
        for ((id, nm) in opts) CatChoice(nm, pk.categoryId == id) {
            pk.categoryId = id; pk.category = ms.catName(id); ms.markDirty(pk.id); ms.pickCatFor = null
        }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold) { ms.pickCatFor = null }
    }

    val am: String? = ms.addModeCat
    if (am != null) MSheet({ ms.addModeCat = null }) {
        MModalTitle("เพิ่มเมนู: " + ms.catName(am).ifEmpty { "ไม่มีหมวดหมู่" })
        MBtn("เพิ่มเมนูเดียว", null, Color.White, FontWeight.ExtraBold) { ms.addModeCat = null; ms.openNewMenu(am) }
        MBtn("เพิ่มเมนูจำนวนมาก", C.PrimaryLight, C.Primary, FontWeight.ExtraBold, top = 10.dp) { ms.addModeCat = null; ms.openBulk(am) }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { ms.addModeCat = null }
    }

    val gt: MMenu? = ms.gtypeFor
    if (gt != null) MSheet({ ms.gtypeFor = null }) {
        MModalTitle("เลือกประเภทกลุ่มตัวเลือก")
        TypeChoice("เมนูเพิ่มเติมธรรมดา", "ให้ลูกค้าติ๊กเลือกตัวเลือกที่ตั้งไว้ (เลือกได้หลายอันหรือกำหนดจำนวนสูงสุดได้)") { ms.gtypeFor = null; ms.addGroup(gt, "normal") }
        TypeChoice("เมนูแบบตั้งราคา", "ลูกค้าเลือกราคาเองในช่วงที่ร้านกำหนด (ปรับทีละขั้น)") { ms.gtypeFor = null; ms.addGroup(gt, "price") }
        TypeChoice("เมนูแบบตั้งตามกิโลกรัม", "ลูกค้าใส่จำนวนกิโล/ขีด ระบบคำนวณราคาให้อัตโนมัติ") { ms.gtypeFor = null; ms.addGroup(gt, "kg") }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 2.dp) { ms.gtypeFor = null }
    }

    val st: Triple<MMenu, Int, Int>? = ms.sgtypeFor
    if (st != null) MSheet({ ms.sgtypeFor = null }) {
        MModalTitle("เลือกประเภทกลุ่มตัวเลือกย่อย")
        TypeChoice("เมนูเพิ่มเติมธรรมดา", "ให้ลูกค้าติ๊กเลือกตัวเลือกที่ตั้งไว้") { ms.sgtypeFor = null; ms.addSubGroup(st.first, st.second, st.third, "normal") }
        TypeChoice("เมนูแบบตั้งราคา", "ลูกค้าเลือกราคาเองในช่วงที่ร้านกำหนด (ปรับทีละขั้น)") { ms.sgtypeFor = null; ms.addSubGroup(st.first, st.second, st.third, "price") }
        TypeChoice("เมนูแบบตั้งตามกิโลกรัม", "ลูกค้าใส่จำนวนกิโล/ขีด ระบบคำนวณราคาให้อัตโนมัติ") { ms.sgtypeFor = null; ms.addSubGroup(st.first, st.second, st.third, "kg") }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 2.dp) { ms.sgtypeFor = null }
    }

    val cf: MMenu? = ms.copyFor
    if (cf != null) MSheet({ ms.copyFor = null }) {
        MModalTitle("คัดลอกกลุ่มตัวเลือก")
        FLabel("เลือกกลุ่มตัวเลือกที่จะคัดลอก (จากเมนูนี้)")
        ListBox(180.dp, 14.dp) {
            if (cf.groups.isEmpty()) Text("เมนูนี้ยังไม่มีกลุ่มตัวเลือก", fontSize = 13.sp, color = SSub, modifier = Modifier.fillMaxWidth().padding(14.dp))
            for ((gi, g) in cf.groups.withIndex()) {
                CheckRow(ms.copyGroups.contains(gi), { c -> if (c) { if (!ms.copyGroups.contains(gi)) ms.copyGroups.add(gi) } else ms.copyGroups.remove(gi) }) {
                    Text(g.title.ifEmpty { "(ยังไม่ตั้งชื่อ)" }, fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
                    GBadge(if (g.type == "price") "ตั้งราคา" else if (g.type == "kg") "กิโลกรัม" else "ธรรมดา")
                }
            }
        }
        FLabel("เลือกเมนูปลายทางที่จะนำไปใช้")
        val others: List<MMenu> = ms.otherMenus(cf.id)
        ListBox(220.dp, 0.dp) {
            if (others.isEmpty()) Text("ยังไม่มีเมนูอื่นในร้าน", fontSize = 13.sp, color = SSub, modifier = Modifier.fillMaxWidth().padding(14.dp))
            for (o in others) {
                CheckRow(ms.copyMenus.contains(o.id), { c -> if (c) { if (!ms.copyMenus.contains(o.id)) ms.copyMenus.add(o.id) } else ms.copyMenus.remove(o.id) }) {
                    Text(o.name.ifEmpty { "(ยังไม่ตั้งชื่อ)" } + (if (o.category.isNotEmpty()) " · " + o.category else ""), fontSize = 13.sp, color = C.Text)
                }
            }
        }
        MBtn("ตกลง", C.Primary, Color.White, FontWeight.Bold, top = 12.dp) { ms.confirmCopy() }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { ms.copyFor = null }
    }

    val af: MMenu? = ms.applyFor
    if (af != null) MSheet({ ms.applyFor = null }) {
        MModalTitle("ใช้เวลาขายนี้กับหลายเมนู")
        FLabel("เวลาที่จะนำไปใช้")
        Text(af.schedOpen + " - " + af.schedClose + " น.", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(bottom = 14.dp))
        FLabel("เลือกเมนูปลายทางที่จะใช้เวลานี้")
        val others: List<MMenu> = ms.otherMenus(af.id)
        ListBox(260.dp, 0.dp) {
            if (others.isEmpty()) Text("ยังไม่มีเมนูอื่นในร้าน", fontSize = 13.sp, color = SSub, modifier = Modifier.fillMaxWidth().padding(14.dp))
            for (o in others) {
                CheckRow(ms.applyMenus.contains(o.id), { c -> if (c) { if (!ms.applyMenus.contains(o.id)) ms.applyMenus.add(o.id) } else ms.applyMenus.remove(o.id) }) {
                    Text(o.name.ifEmpty { "(ยังไม่ตั้งชื่อ)" } + (if (o.category.isNotEmpty()) " · " + o.category else ""), fontSize = 13.sp, color = C.Text)
                }
            }
        }
        MBtn("ตกลง", C.Primary, Color.White, FontWeight.Bold, top = 12.dp) { ms.confirmApplySchedule() }
        MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { ms.applyFor = null }
    }

    if (ms.bulkVisible) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.92f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                Row(
                    Modifier.fillMaxWidth().drawBehind { val w: Float = 2.dp.toPx(); drawLine(Color(0xFFF5F6F8), Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("เพิ่มเมนูจำนวนมาก: " + ms.catName(ms.bulkCatId).ifEmpty { "ไม่มีหมวดหมู่" }, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).clickable { ms.addBulkRow() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("+ แถว", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val items: List<MMenu> = ms.bulkIds.mapNotNull { ms.menuById(it) }
                    if (items.isEmpty()) EmptyMenu("กด \"+ แถว\" เพื่อเริ่มเพิ่มเมนู")
                    for ((i, m) in items.withIndex()) key(m.id) { MenuCard(ms, m, i, items.size, true) }
                }
                Row(
                    Modifier.fillMaxWidth().drawBehind { drawLine(Color(0xFFF5F6F8), Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Gray).clickable(enabled = !ms.bulkSaving) { ms.cancelBulk() }, contentAlignment = Alignment.Center) {
                        Text("ยกเลิก", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
                    }
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Primary)
                            .clickable(enabled = !ms.bulkSaving) { scope.launch { ms.saveBulk() } },
                        contentAlignment = Alignment.Center
                    ) { Text(if (ms.bulkSaving) "กำลังบันทึก..." else "ยืนยัน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun CatChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp).clip(shape).background(if (selected) C.PrimaryLight else Color.White)
            .border(2.dp, if (selected) C.Primary else SBorder, shape).clickable { onClick() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) { Text(name + (if (selected) " ✓" else ""), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun TypeChoice(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(Color.White).border(2.dp, SBorder, shape).clickable { onClick() }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Text(sub, fontSize = 12.sp, color = SSub)
    }
}

@Composable
private fun ListBox(maxH: Dp, bottom: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = bottom).heightIn(max = maxH).clip(shape).border(1.5.dp, SBorder, shape)
            .verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)
    ) { content() }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked, { onChange(it) }, colors = CheckboxDefaults.colors(checkedColor = C.Primary))
        content()
    }
}
