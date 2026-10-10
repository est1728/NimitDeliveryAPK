package com.nimit.delivery.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ShopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

private val MBorder = Color(0xFFEEF0F4)
private val MFieldBg = Color(0xFFFAFBFC)
private val MGrad = Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))
private const val M_IMG_PH = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z"
private const val M_LOC = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 17.93V18a1 1 0 0 0-2 0v1.93A8 8 0 0 1 4.07 13H6a1 1 0 0 0 0-2H4.07A8 8 0 0 1 11 4.07V6a1 1 0 0 0 2 0V4.07A8 8 0 0 1 19.93 11H18a1 1 0 0 0 0 2h1.93A8 8 0 0 1 13 19.93zM12 9a3 3 0 1 0 3 3 3 3 0 0 0-3-3z"
private val M_DAYS = listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")

private class ClosedDate(val type: String, val day: Int, val date: String)
private class SaveResult(val icon: String, val title: String, val msg: String)

/** หน้าแก้ไขข้อมูลร้าน (พอร์ตจากส่วนข้อมูลร้านใน shop-manage.html) */
@Composable
fun ShopManageScreen(store: ShopStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenW: Int = LocalConfiguration.current.screenWidthDp

    var inited by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("จัดการร้านค้า") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var deliveryTime by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(13.7563) }
    var lng by remember { mutableStateOf(100.5018) }
    var isOpen by remember { mutableStateOf(false) }
    var openTouched by remember { mutableStateOf(false) }
    var bannerUrl by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var autoSchedule by remember { mutableStateOf(false) }
    var schOpen by remember { mutableStateOf("") }
    var schClose by remember { mutableStateOf("") }
    var everyDay by remember { mutableStateOf(true) }
    val openDays = remember { mutableStateListOf(0, 1, 2, 3, 4, 5, 6) }
    val closedDates = remember { mutableStateListOf<ClosedDate>() }
    var mapMove by remember { mutableStateOf<ShopMapMove?>(null) }

    var showBanner by remember { mutableStateOf(false) }
    var showAvatar by remember { mutableStateOf(false) }
    var bannerInput by remember { mutableStateOf("") }
    var avatarInput by remember { mutableStateOf("") }
    var showUnsaved by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SaveResult?>(null) }
    var showCd by remember { mutableStateOf(false) }
    var cdType by remember { mutableStateOf("") }
    var cdDay by remember { mutableStateOf("") }
    var cdDate by remember { mutableStateOf("") }
    var cropShape by remember { mutableStateOf("") }
    var cropBmp by remember { mutableStateOf<Bitmap?>(null) }

    // โหลดข้อมูลร้านครั้งเดียวตอนเข้าหน้า (เหมือน getDoc ในเว็บ)
    LaunchedEffect(store.shopId) {
        run {
            // ดึงเอกสารร้านสดๆ จากเซิร์ฟเวอร์ (เหมือน getDoc ในเว็บ) แล้วค่อยถอยไปใช้ข้อมูลที่ฟังอยู่
            var s: Map<String, Any> = store.shop.value
            try {
                val ref = FirebaseFirestore.getInstance().collection("shops").document(store.shopId)
                val snap = try { ref.get(com.google.firebase.firestore.Source.SERVER).await() } catch (e: Exception) { ref.get().await() }
                val d: Map<String, Any>? = snap.data
                if (d != null) s = d
            } catch (e: Exception) { }
            if (s.isEmpty()) return@LaunchedEffect
            title = s["name"]?.toString().takeIf { !it.isNullOrEmpty() } ?: "จัดการร้านค้า"
            name = s["name"]?.toString() ?: ""
            desc = s["desc"]?.toString() ?: ""
            phone = s["phone"]?.toString() ?: ""
            deliveryTime = s["deliveryTime"]?.toString() ?: ""
            address = s["address"]?.toString() ?: ""
            val la: Double = (s["lat"] as? Number)?.toDouble() ?: 0.0
            val ln: Double = (s["lng"] as? Number)?.toDouble() ?: 0.0
            lat = if (la != 0.0) la else 13.7563
            lng = if (ln != 0.0) ln else 100.5018
            isOpen = s["isOpen"] == true
            bannerUrl = s["bannerUrl"]?.toString() ?: ""
            avatarUrl = s["avatarUrl"]?.toString() ?: ""
            autoSchedule = s["autoSchedule"] == true
            schOpen = s["scheduleOpen"]?.toString() ?: ""
            schClose = s["scheduleClose"]?.toString() ?: ""
            val od: List<Int> = (s["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
            openDays.clear()
            openDays.addAll(if (od.isEmpty()) listOf(0, 1, 2, 3, 4, 5, 6) else od)
            everyDay = openDays.size == 7
            closedDates.clear()
            val cd = s["closedDates"] as? List<*>
            if (cd != null) for (x in cd) {
                val m = x as? Map<*, *> ?: continue
                closedDates.add(ClosedDate(m["type"]?.toString() ?: "date", (m["day"] as? Number)?.toInt() ?: 0, m["date"]?.toString() ?: ""))
            }
            inited = true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val b: Bitmap? = withContext(Dispatchers.IO) { shopDecodeImage(ctx, uri) }
            if (b == null) shopToast(ctx, "เปิดรูปไม่สำเร็จ") else cropBmp = b
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m: Map<String, Boolean> ->
        if (m.values.any { it }) shopFindMe(ctx) { a, b -> lat = a; lng = b; mapMove = ShopMapMove(a, b); dirty = true }
        else shopToast(ctx, "ไม่ได้รับสิทธิ์เข้าถึงตำแหน่ง ลองปักหมุดเองบนแผนที่แทนได้ครับ")
    }

    suspend fun saveAll() {
        val upd = HashMap<String, Any?>()
        upd["name"] = name.trim(); upd["desc"] = desc.trim(); upd["phone"] = phone.trim()
        upd["deliveryTime"] = deliveryTime.trim(); upd["address"] = address.trim()
        upd["lat"] = lat; upd["lng"] = lng
        upd["isOpen"] = isOpen; upd["bannerUrl"] = bannerUrl; upd["avatarUrl"] = avatarUrl
        if (openTouched) { upd["closeType"] = null; upd["closeUntil"] = null; upd["closeReason"] = "" }
        upd["autoSchedule"] = autoSchedule; upd["scheduleOpen"] = schOpen; upd["scheduleClose"] = schClose
        upd["openDays"] = openDays.toList()
        upd["closedDates"] = closedDates.map { c ->
            val m = HashMap<String, Any>()
            m["type"] = c.type
            if (c.type == "monthly") m["day"] = c.day else m["date"] = c.date
            m
        }
        upd["updatedAt"] = FieldValue.serverTimestamp()
        FirebaseFirestore.getInstance().collection("shops").document(store.shopId).update(upd).await()
        dirty = false; openTouched = false
    }

    fun attemptBack() { if (dirty) showUnsaved = true else onBack() }

    BackHandler {
        when {
            cropBmp != null -> cropBmp = null
            saving -> { }
            result != null -> result = null
            showConfirm -> showConfirm = false
            showCd -> showCd = false
            showBanner -> showBanner = false
            showAvatar -> showAvatar = false
            showUnsaved -> showUnsaved = false
            else -> attemptBack()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Gray)) {
        Column(Modifier.fillMaxSize()) {
            // TOPBAR
            Row(
                Modifier.fillMaxWidth().background(MGrad).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { attemptBack() }, contentAlignment = Alignment.Center) {
                    PathIcon("M19 12H5M12 5l-7 7 7 7", Color.White, 18.dp, stroke = true)
                }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).clickable { showConfirm = true }.padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("บันทึก", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // HEADER (แบนเนอร์ + โปรไฟล์ + ชื่อ + สวิตช์เปิดร้าน)
                Box(Modifier.fillMaxWidth().background(Color.White)) {
                Column(Modifier.fillMaxWidth()) {
                    val bannerSrc = remember { MutableInteractionSource() }
                    val bannerPressed by bannerSrc.collectIsPressedAsState()
                    Box(
                        Modifier.fillMaxWidth().height(130.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFFDDE8FA), Color(0xFFC2D4F5))))
                            .clickable(interactionSource = bannerSrc, indication = null) { bannerInput = bannerUrl; showBanner = true }
                    ) {
                        if (bannerUrl.isNotEmpty()) AsyncImage(bannerUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (bannerPressed) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PathIcon(ShopP.EDIT, Color.White, 16.dp)
                                Text("เปลี่ยนแบนเนอร์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name.ifEmpty { "ชื่อร้าน" }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isOpen) "เปิดอยู่" else "ปิดอยู่", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Subtext)
                            ShopSwitch(isOpen, ShopGreen, Color(0xFFCBD5E1)) { isOpen = !isOpen; openTouched = true; dirty = true }
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 104.dp).size(52.dp).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                        .background(Color.White).padding(3.dp).clip(RoundedCornerShape(13.dp)).background(C.PrimaryLight)
                        .clickable { avatarInput = avatarUrl; showAvatar = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotEmpty()) AsyncImage(avatarUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else PathIcon(ShopP.SHOP, C.Primary, 24.dp)
                }
                }

                // ข้อมูลร้านค้า
                MSection {
                    MTitle("ข้อมูลร้านค้า")
                    MField("ชื่อร้าน") { MInput(name, { name = it; dirty = true }, "เช่น ร้านส้มตำ") }
                    MField("คำอธิบาย") { MInput(desc, { desc = it; dirty = true }, "อาหารอีสาน อร่อยมาก") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            MLabel("เบอร์โทร")
                            MInput(phone, { phone = it; dirty = true }, "08X-XXX-XXXX", keyboardType = KeyboardType.Phone)
                        }
                        Column(Modifier.weight(1f)) {
                            MLabel("เวลาส่ง")
                            MInput(deliveryTime, { deliveryTime = it; dirty = true }, "20-30 นาที")
                        }
                    }
                    Column(Modifier.fillMaxWidth()) {
                        MLabel("ที่อยู่ร้าน")
                        MInput(address, { address = it; dirty = true }, "เยื้องการไฟฟ้า...")
                    }
                }

                // ปักหมุด
                MSection {
                    MTitle("ปักหมุดตำแหน่งร้าน")
                    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Color(0xFFE8EEF5), RoundedCornerShape(14.dp))) {
                        if (inited) ShopLocMap(lat, lng, mapMove, { a, b -> lat = a; lng = b; dirty = true }, Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp).size(40.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
                                .clickable { locPerm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            contentAlignment = Alignment.Center
                        ) { PathIcon(M_LOC, C.Primary, 20.dp) }
                    }
                    Text("แตะแผนที่เพื่อปักหมุด หรือลากหมุดเพื่อขยับตำแหน่งร้าน", fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 8.dp))
                }

                // เวลาเปิด-ปิดอัตโนมัติ
                MSection {
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("เวลาเปิด-ปิดร้านอัตโนมัติ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                        ShopSwitch(autoSchedule, ShopGreen, Color(0xFFCBD5E1)) { autoSchedule = !autoSchedule; dirty = true }
                    }
                    if (autoSchedule) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                MLabel("เวลาเปิด")
                                MPick(schOpen, "--:--") { shopPickTime(ctx, schOpen) { schOpen = it; dirty = true } }
                            }
                            Column(Modifier.weight(1f)) {
                                MLabel("เวลาปิด")
                                MPick(schClose, "--:--") { shopPickTime(ctx, schClose) { schClose = it; dirty = true } }
                            }
                        }
                        Text("วันที่เปิด", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
                        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShopSwitch(everyDay, ShopGreen, Color(0xFFCBD5E1), 40.dp, 22.dp) {
                                everyDay = !everyDay
                                if (everyDay) { openDays.clear(); openDays.addAll(listOf(0, 1, 2, 3, 4, 5, 6)) }
                                dirty = true
                            }
                            Text("เปิดทุกวัน", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                        }
                        if (!everyDay) {
                            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in 0..6) {
                                    val on: Boolean = openDays.contains(i)
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(if (on) C.Primary else Color.White)
                                            .border(2.dp, if (on) C.Primary else MBorder, CircleShape)
                                            .clickable {
                                                if (openDays.contains(i)) openDays.remove(i) else openDays.add(i)
                                                dirty = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) { Text(M_DAYS[i], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext) }
                                }
                            }
                        }
                        Text(
                            "ระบบจะเปิด-ปิดร้านให้อัตโนมัติตามเวลาและวันนี้ โดยไม่ต้องกดสวิตช์เปิด/ปิดเอง (ปุ่มด้านบนยังกดปิดฉุกเฉินเองได้เสมอ แต่ถึงรอบเปิดถัดไประบบจะเปิดให้อัตโนมัติ)",
                            fontSize = 11.sp, color = C.Subtext, lineHeight = 17.6.sp
                        )
                    } else {
                        Text("ปิดอยู่ — ร้านจะเปิด/ปิดตามที่กดสวิตช์ด้านบนเองเท่านั้น", fontSize = 11.sp, color = C.Subtext)
                    }
                }

                // วันปิดพิเศษ
                MSection {
                    MTitle("วันปิดพิเศษ")
                    Text("ร้านจะปิดทั้งวันในวันที่กำหนดไว้นี้เสมอ ไม่ว่าจะตั้งเวลาเปิด-ปิดร้านแบบไหนไว้ก็ตาม", fontSize = 11.sp, color = C.Subtext, lineHeight = 17.6.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (closedDates.isEmpty()) Text("ยังไม่มีวันปิดพิเศษ", fontSize = 12.sp, color = C.Subtext)
                        for ((i, c) in closedDates.withIndex()) {
                            Row(
                                Modifier.fillMaxWidth().border(1.5.dp, MBorder, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val label: String = if (c.type == "monthly") "🔁 ทุกวันที่ " + c.day else "📅 " + shopFmtClosedDate(c.date)
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
                                Text("ลบ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShopRed, modifier = Modifier.clickable { closedDates.removeAt(i); dirty = true })
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(C.PrimaryLight)
                            .drawBehind {
                                drawRoundRect(Color(0xFFC7D7F5), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
                            }
                            .clickable { cdType = ""; cdDay = ""; cdDate = ""; showCd = true },
                        contentAlignment = Alignment.Center
                    ) { Text("+ เพิ่มวันปิด", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
                }
                Spacer(Modifier.height(100.dp))
            }
        }

        // ---------- MODALS ----------
        if (showUnsaved) MSheet(null) {
            MModalTitle("ยังไม่ได้บันทึกการเปลี่ยนแปลง")
            Text("มีข้อมูลที่แก้ไขแล้วแต่ยังไม่ได้บันทึก ต้องการทำอะไรต่อ?", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 16.dp))
            MBtn("ออกเลย", ShopRedLight, ShopRed, FontWeight.Bold) { dirty = false; showUnsaved = false; onBack() }
            MBtn("แก้ไขต่อ", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { showUnsaved = false }
            MBtn("บันทึก", null, Color.White, FontWeight.ExtraBold, top = 8.dp) {
                scope.launch {
                    try { saveAll(); showUnsaved = false; onBack() } catch (e: Exception) { shopToast(ctx, "บันทึกไม่สำเร็จ: " + (e.message ?: "")) }
                }
            }
        }
        if (showConfirm) MSheet({ showConfirm = false }) {
            MModalTitle("ยืนยันบันทึกการเปลี่ยนแปลง")
            Text("ต้องการบันทึกข้อมูลร้าน/เมนูที่แก้ไขไว้ทั้งหมดใช่มั้ยครับ", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 14.dp))
            MBtn("ยืนยันบันทึก", C.Primary, Color.White, FontWeight.Bold) {
                showConfirm = false
                saving = true
                scope.launch {
                    try {
                        saveAll()
                        title = name.ifEmpty { "จัดการร้านค้า" }
                        result = SaveResult("✅", "บันทึกสำเร็จ", "บันทึกข้อมูลร้าน/เมนูเรียบร้อยแล้วครับ")
                    } catch (e: Exception) {
                        result = SaveResult("⚠️", "เกิดข้อผิดพลาด", e.message ?: "")
                    }
                    saving = false
                }
            }
            MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { showConfirm = false }
        }
        if (saving) MSheet(null) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(40.dp), color = C.Primary, trackColor = MBorder, strokeWidth = 4.dp)
            }
            Text("กำลังบันทึก...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        val res: SaveResult? = result
        if (res != null) MSheet({ result = null }) {
            Text(res.icon, fontSize = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            Text(res.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
            Text(res.msg, fontSize = 13.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
            MBtn("ตกลง", C.Primary, Color.White, FontWeight.Bold) { result = null }
        }
        if (showBanner) MSheet(null) {
            MModalTitle("รูปแบนเนอร์")
            MPreview(bannerInput, false) { cropShape = "banner"; picker.launch("image/*") }
            MBtn("เลือกรูปจากเครื่อง", C.PrimaryLight, C.Primary, FontWeight.ExtraBold) { cropShape = "banner"; picker.launch("image/*") }
            Text("หรือวาง URL รูปแบนเนอร์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            MInput(bannerInput, { bannerInput = it }, "https://...", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(12.dp))
            MBtn("บันทึก", null, Color.White, FontWeight.ExtraBold) { bannerUrl = bannerInput.trim(); showBanner = false; dirty = true }
            MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { showBanner = false }
        }
        if (showAvatar) MSheet(null) {
            MModalTitle("รูปโปรไฟล์ร้าน")
            MPreview(avatarInput, true) { cropShape = "circle"; picker.launch("image/*") }
            MBtn("เลือกรูปจากเครื่อง", C.PrimaryLight, C.Primary, FontWeight.ExtraBold) { cropShape = "circle"; picker.launch("image/*") }
            Text("หรือวาง URL รูปโปรไฟล์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            MInput(avatarInput, { avatarInput = it }, "https://...", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(12.dp))
            MBtn("บันทึก", null, Color.White, FontWeight.ExtraBold) { avatarUrl = avatarInput.trim(); showAvatar = false; dirty = true }
            MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { showAvatar = false }
        }
        if (showCd) MSheet({ showCd = false }) {
            MModalTitle("เพิ่มวันปิด")
            if (cdType.isEmpty()) {
                Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CdChoice("🔁 ปิดซ้ำทุกเดือน", "เลือกวันที่ (เช่น 1, 16) จะปิดวันนั้นทุกเดือน") { cdType = "monthly" }
                    CdChoice("📅 เจาะจงวันที่", "เลือกวันที่จริงจากปฏิทิน ปิดแค่ครั้งเดียว") { cdType = "date" }
                }
            } else {
                Text("‹ กลับ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary, modifier = Modifier.clickable { cdType = "" }.padding(bottom = 10.dp))
                if (cdType == "monthly") {
                    MLabel("วันที่ (1-31) ของทุกเดือน")
                    MInput(cdDay, { cdDay = it.filter { c -> c.isDigit() } }, "เช่น 1", keyboardType = KeyboardType.Number)
                } else {
                    MLabel("เลือกวันที่")
                    MPick(cdDate, "เลือกวันที่") { shopPickDate(ctx, cdDate) { cdDate = it } }
                }
                MBtn("เพิ่ม", C.Primary, Color.White, FontWeight.Bold, top = 14.dp) {
                    if (cdType == "monthly") {
                        val d: Int = cdDay.toIntOrNull() ?: 0
                        if (d < 1 || d > 31) { shopToast(ctx, "กรุณาใส่วันที่ 1-31"); return@MBtn }
                        if (closedDates.any { it.type == "monthly" && it.day == d }) { shopToast(ctx, "มีวันนี้อยู่แล้ว"); return@MBtn }
                        closedDates.add(ClosedDate("monthly", d, ""))
                    } else {
                        if (cdDate.isEmpty()) { shopToast(ctx, "กรุณาเลือกวันที่"); return@MBtn }
                        if (closedDates.any { it.type == "date" && it.date == cdDate }) { shopToast(ctx, "มีวันที่นี้อยู่แล้ว"); return@MBtn }
                        closedDates.add(ClosedDate("date", 0, cdDate))
                    }
                    val sorted: List<ClosedDate> = closedDates.sortedWith(Comparator { a, b ->
                        if (a.type != b.type) (if (a.type == "monthly") -1 else 1)
                        else if (a.type == "monthly") a.day - b.day else a.date.compareTo(b.date)
                    })
                    closedDates.clear(); closedDates.addAll(sorted)
                    dirty = true
                    showCd = false
                }
            }
            MBtn("ยกเลิก", C.Gray, C.Text, FontWeight.Bold, top = 8.dp) { showCd = false }
        }

        val bmp: Bitmap? = cropBmp
        if (bmp != null) {
            ShopCropOverlay(
                bitmap = bmp, shape = cropShape,
                ratio = if (cropShape == "banner") screenW / 130f else 1f,
                onCancel = { cropBmp = null },
                onDone = { url: String ->
                    if (cropShape == "banner") bannerInput = url else avatarInput = url
                    dirty = true
                    cropBmp = null
                }
            )
        }
    }
}

// ---------- ค้นหาตำแหน่งปัจจุบัน ----------
private fun shopFindMe(ctx: android.content.Context, onFound: (Double, Double) -> Unit) {
    var settled = false
    val h = android.os.Handler(android.os.Looper.getMainLooper())
    val timeout = Runnable {
        if (!settled) { settled = true; shopToast(ctx, "ค้นหาตำแหน่งนานเกินไป ลองใหม่อีกครั้ง หรือปักหมุดเองบนแผนที่แทนได้ครับ") }
    }
    h.postDelayed(timeout, 8000)
    try {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (settled) return@addOnSuccessListener
                settled = true; h.removeCallbacks(timeout)
                if (loc != null) onFound(loc.latitude, loc.longitude)
                else shopToast(ctx, "ไม่สามารถเข้าถึง GPS ได้ ลองปักหมุดเองบนแผนที่แทนได้ครับ")
            }
            .addOnFailureListener {
                if (settled) return@addOnFailureListener
                settled = true; h.removeCallbacks(timeout)
                shopToast(ctx, "ไม่สามารถเข้าถึง GPS ได้ ลองปักหมุดเองบนแผนที่แทนได้ครับ")
            }
    } catch (e: SecurityException) {
        settled = true; h.removeCallbacks(timeout)
        shopToast(ctx, "ไม่สามารถเข้าถึง GPS ได้ ลองปักหมุดเองบนแผนที่แทนได้ครับ")
    }
}

private fun shopPickTime(ctx: android.content.Context, cur: String, onPick: (String) -> Unit) {
    val p: List<String> = cur.split(":")
    val h: Int = p.getOrNull(0)?.toIntOrNull() ?: 8
    val m: Int = p.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(ctx, { _, hh, mm -> onPick(String.format(java.util.Locale.US, "%02d:%02d", hh, mm)) }, h, m, true).show()
}

private fun shopPickDate(ctx: android.content.Context, cur: String, onPick: (String) -> Unit) {
    val p: List<String> = cur.split("-")
    val now = Calendar.getInstance()
    val y: Int = p.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
    val m: Int = (p.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)) - 1
    val d: Int = p.getOrNull(2)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
    DatePickerDialog(ctx, { _, yy, mm, dd -> onPick(String.format(java.util.Locale.US, "%04d-%02d-%02d", yy, mm + 1, dd)) }, y, m, d).show()
}

private fun shopFmtClosedDate(s: String): String {
    if (s.isEmpty()) return ""
    val p: List<String> = s.split("-")
    if (p.size < 3) return s
    return (p[2].toIntOrNull() ?: 0).toString() + "/" + (p[1].toIntOrNull() ?: 0) + "/" + p[0]
}

// ---------- ชิ้นส่วน UI ตามสไตล์ใน HTML ----------
@Composable
private fun MSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White).padding(16.dp), content = content)
}

@Composable
private fun MTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
private fun MLabel(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun MField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { MLabel(label); content() }
}

@Composable
private fun MInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, color = C.Text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().height(46.dp).clip(shape).background(if (focused) Color.White else MFieldBg)
            .border(2.dp, if (focused) C.Primary else MBorder, shape).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = Color(0xFF9CA3AF))
                inner()
            }
        }
    )
}

@Composable
private fun MPick(value: String, placeholder: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(shape).background(MFieldBg).border(2.dp, MBorder, shape).clickable { onClick() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = Color(0xFF9CA3AF)) else Text(value, fontSize = 15.sp, color = C.Text)
    }
}

@Composable
private fun CdChoice(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(2.dp, MBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(sub, fontSize = 11.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MPreview(url: String, square: Boolean, onClick: () -> Unit) {
    val m: Modifier = if (square) Modifier.padding(bottom = 10.dp).size(90.dp).clip(RoundedCornerShape(20.dp))
    else Modifier.padding(bottom = 10.dp).fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(m.background(C.Gray).clickable { onClick() }, contentAlignment = Alignment.Center) {
            if (url.trim().isNotEmpty()) AsyncImage(url.trim(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else PathIcon(M_IMG_PH, Color(0xFFCBD5E1), 30.dp)
        }
    }
}

@Composable
private fun MModalTitle(t: String) {
    Text(t, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 14.dp))
}

/** ปุ่มในป๊อบอัพ: bg == null คือปุ่มไล่สีหลัก (modal-save-btn) */
@Composable
private fun MBtn(text: String, bg: Color?, fg: Color, weight: FontWeight, top: Dp = 0.dp, onClick: () -> Unit) {
    val base = Modifier.fillMaxWidth().padding(top = top).height(50.dp).clip(RoundedCornerShape(12.dp))
    Box(
        (if (bg == null) base.background(MGrad) else base.background(bg)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = weight, color = fg) }
}

/** ป๊อบอัพแบบแผ่นเลื่อนขึ้นจากด้านล่าง (modal-overlay + modal) onDismiss = null คือแตะข้างนอกแล้วไม่ปิด */
@Composable
private fun MSheet(onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val maxH: Dp = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(indication = null, interactionSource = none) { onDismiss?.invoke() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxH).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color.White)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 36.dp)
        ) {
            Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE2E8F0)))
            content()
        }
    }
}
