package com.nimit.delivery.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.Session
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private data class Addr(
    val name: String, val phone: String, val address: String, val note: String,
    val lat: Double?, val lng: Double?
)

private fun parseAddrs(s: String?): List<Addr> {
    val out = ArrayList<Addr>()
    try {
        val arr = JSONArray(s ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Addr(
                o.optString("name"), o.optString("phone"), o.optString("address"), o.optString("note"),
                if (o.isNull("lat") || !o.has("lat")) null else o.optDouble("lat"),
                if (o.isNull("lng") || !o.has("lng")) null else o.optDouble("lng")
            ))
        }
    } catch (_: Exception) { }
    return out
}

private fun addrsToJson(l: List<Addr>): String {
    val arr = JSONArray()
    l.forEach { a ->
        arr.put(JSONObject().put("name", a.name).put("phone", a.phone).put("address", a.address).put("note", a.note)
            .put("lat", a.lat ?: JSONObject.NULL).put("lng", a.lng ?: JSONObject.NULL))
    }
    return arr.toString()
}

private fun pathIcon(d: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
        pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)
    ).build()

private val PinIcon: ImageVector by lazy { pathIcon("M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5S10.62 6.5 12 6.5s2.5 1.12 2.5 2.5S13.38 11.5 12 11.5z") }
private val TrashIcon: ImageVector by lazy { pathIcon("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z") }
private val PlusIcon: ImageVector by lazy { pathIcon("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }

private val addrSat = object : OnlineTileSourceBase(
    "EsriSat", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private val Line = Color(0xFFEEF0F4)

@Composable
private fun AddrHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
    }
}

@Composable
fun AddressScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Addr>().also { it.addAll(parseAddrs(session.addresses)) } }
    var defIdx by remember { mutableIntStateOf(session.defaultAddress?.toIntOrNull() ?: 0) }
    var loading by remember { mutableStateOf(items.isEmpty() && !session.customerPhone.isNullOrEmpty()) }
    var showForm by remember { mutableStateOf(items.isEmpty() && !loading) }
    var editIdx by remember { mutableIntStateOf(-1) }
    var delIdx by remember { mutableIntStateOf(-1) }

    // ลูกค้าเก่าที่เข้าหน้านี้ตรงๆ แต่ยังไม่มีที่อยู่ในเครื่อง → ดึงจาก Firestore ก่อนเปิดฟอร์มเปล่า
    LaunchedEffect(Unit) {
        if (!loading) return@LaunchedEffect
        val phone = session.customerPhone.orEmpty()
        try {
            val d = Firebase.firestore.collection("customers").document(phone).get().await()
            if (d.exists() && d.getBoolean("registered") == true && !d.getString("address").isNullOrEmpty()) {
                items.add(Addr(d.getString("name").orEmpty(), phone, d.getString("address").orEmpty(), "", d.getDouble("lat"), d.getDouble("lng")))
                defIdx = 0
                session.addresses = addrsToJson(items); session.defaultAddress = "0"
            }
        } catch (_: Exception) { }
        loading = false
        showForm = items.isEmpty()
    }

    fun persist() {
        session.addresses = addrsToJson(items)
        session.defaultAddress = defIdx.toString()
        val def = items.getOrNull(defIdx) ?: items.firstOrNull() ?: return
        val phone = session.customerPhone.orEmpty()
        if (phone.isEmpty()) return
        try {
            val cd = try { JSONObject(session.customerData ?: "{}") } catch (_: Exception) { JSONObject() }
            cd.put("name", def.name).put("phone", phone).put("address", def.address)
                .put("lat", def.lat ?: JSONObject.NULL).put("lng", def.lng ?: JSONObject.NULL)
            session.customerData = cd.toString()
        } catch (_: Exception) { }
        scope.launch {
            try {
                Firebase.firestore.collection("customers").document(phone).set(
                    mapOf("phone" to phone, "name" to def.name, "address" to def.address, "lat" to def.lat, "lng" to def.lng,
                        "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()
                ).await()
            } catch (_: Exception) { }
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(C.Gray), contentAlignment = Alignment.Center) { Text("กำลังโหลด...", color = C.Subtext, fontSize = 14.sp) }
        return
    }

    if (showForm) {
        BackHandler(enabled = items.isNotEmpty()) { showForm = false }
        AddressForm(
            existing = items.getOrNull(editIdx), isNew = editIdx < 0, defaultPhone = session.customerPhone.orEmpty(),
            canBack = items.isNotEmpty(), onBack = { showForm = false },
            onSave = { a ->
                if (a.phone.isNotEmpty()) session.customerPhone = a.phone
                if (editIdx >= 0) items[editIdx] = a else { items.add(a); defIdx = items.size - 1 }
                persist()
                showForm = false
            }
        )
        return
    }

    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().background(C.Gray)) {
        Column(Modifier.fillMaxSize()) {
            AddrHeader("ที่อยู่จัดส่ง", true, onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp)) {
                if (items.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(PinIcon, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("ยังไม่มีที่อยู่", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Text)
                        Spacer(Modifier.height(6.dp))
                        Text("กด + เพื่อเพิ่มที่อยู่แรก", fontSize = 14.sp, color = C.Subtext)
                    }
                }
                items.forEachIndexed { i, a ->
                    val isDef = i == defIdx
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).shadow(2.dp, shape).clip(shape).background(Color.White)
                            .then(if (isDef) Modifier.border(2.dp, C.Primary, shape) else Modifier)
                            .clickable { editIdx = i; showForm = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(if (isDef) C.Primary else C.PrimaryLight)
                                .clickable { defIdx = i; persist() },
                            contentAlignment = Alignment.Center
                        ) { Icon(PinIcon, null, tint = if (isDef) Color.White else C.Primary, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.name.ifEmpty { "ไม่มีชื่อ" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (isDef) Text("ที่อยู่หลัก", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary,
                                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Text(a.address.ifEmpty { "-" }, fontSize = 12.sp, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            if (!isDef) Text("ตั้งเป็นที่อยู่หลัก", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                                modifier = Modifier.padding(top = 4.dp).clickable { defIdx = i; persist() })
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEE2E2)).clickable { delIdx = i },
                            contentAlignment = Alignment.Center
                        ) { Icon(TrashIcon, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .drawBehind {
                            drawRoundRect(Color(0xFFC7D7F0), cornerRadius = CornerRadius(16.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                        }
                        .clickable { editIdx = -1; showForm = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(PlusIcon, null, tint = C.Primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("เพิ่มที่อยู่ใหม่", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                }
            }
        }
        if (items.isNotEmpty()) {
            val fab = RoundedCornerShape(16.dp)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp).navigationBarsPadding()
                    .height(56.dp).shadow(10.dp, fab, ambientColor = C.Primary.copy(alpha = 0.35f), spotColor = C.Primary.copy(alpha = 0.35f))
                    .clip(fab).background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).clickable { onNext() },
                contentAlignment = Alignment.Center
            ) { Text("เริ่มสั่งอาหาร →", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }

    if (delIdx >= 0) AlertDialog(
        onDismissRequest = { delIdx = -1 },
        title = { Text("ลบที่อยู่นี้?") },
        confirmButton = {
            TextButton(onClick = {
                val i = delIdx; delIdx = -1
                if (i in items.indices) {
                    items.removeAt(i)
                    if (defIdx >= items.size) defIdx = maxOf(0, items.size - 1)
                    persist()
                }
            }) { Text("ลบ", color = Color(0xFFDC2626)) }
        },
        dismissButton = { TextButton(onClick = { delIdx = -1 }) { Text("ยกเลิก") } }
    )
}

/** สร้างแผนที่ osmdroid (ใช้ทั้งแผนที่เล็กที่ดูอย่างเดียว และแผนที่ใหญ่ที่ปักหมุด) */
@SuppressLint("ClickableViewAccessibility")
internal fun makeAddrMap(ctx: android.content.Context, interactive: Boolean, la: Double, ln: Double, zoom: Double): MapView {
    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm", 0))
    Configuration.getInstance().userAgentValue = ctx.packageName
    Configuration.getInstance().apply {
        osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
        osmdroidTileCache = java.io.File(ctx.cacheDir, "osmdroid/tiles")
        tileDownloadThreads = 6.toShort()
        tileFileSystemThreads = 4.toShort()
        expirationOverrideDuration = 7L * 24 * 3600 * 1000
    }
    return MapView(ctx).apply {
        setMultiTouchControls(interactive)
        setTileSource(addrSat)
        isTilesScaledToDpi = true
        controller.setZoom(zoom)
        controller.setCenter(GeoPoint(la, ln))
        if (interactive) {
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        } else {
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { _, _ -> true }
        }
    }
}

/** แผนที่ใหญ่เต็มจอ: ปักหมุด/ลากหมุด/ใช้ตำแหน่งปัจจุบัน/สลับดาวเทียม แล้วกดยืนยันเพื่อกลับ — ปุ่มเกี่ยวกับแผนที่อยู่ที่นี่ทั้งหมด */
@SuppressLint("MissingPermission")
@Composable
internal fun BigMapPicker(startLat: Double, startLng: Double, autoLocate: Boolean, onConfirm: (Double, Double) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by remember { mutableDoubleStateOf(startLat) }
    var lng by remember { mutableDoubleStateOf(startLng) }
    var sat by remember { mutableStateOf(true) }
    var locating by remember { mutableStateOf(false) }
    val map = remember { makeAddrMap(ctx, true, startLat, startLng, if (autoLocate) 15.0 else 17.0) }
    val marker = remember {
        Marker(map).apply {
            position = GeoPoint(startLat, startLng)
            isDraggable = true
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
    fun moveTo(la: Double, ln: Double, zoom: Boolean) {
        lat = la; lng = ln
        marker.position = GeoPoint(la, ln)
        if (zoom) { map.controller.setZoom(17.0); map.controller.animateTo(GeoPoint(la, ln)) }
        map.invalidate()
    }
    LaunchedEffect(Unit) {
        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDrag(m: Marker) {}
            override fun onMarkerDragStart(m: Marker) {}
            override fun onMarkerDragEnd(m: Marker) { lat = m.position.latitude; lng = m.position.longitude }
        })
        map.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { moveTo(p.latitude, p.longitude, false); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        map.overlays.add(marker)
        map.invalidate()
    }
    LaunchedEffect(sat) {
        val want = if (sat) addrSat else TileSourceFactory.MAPNIK
        if (map.tileProvider.tileSource.name() != want.name()) map.setTileSource(want)
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }

    fun fetchLocation(silent: Boolean) {
        scope.launch {
            locating = true
            try {
                val client = LocationServices.getFusedLocationProviderClient(ctx)
                val last = try { client.lastLocation.await() } catch (_: Exception) { null }
                val loc = if (last != null && System.currentTimeMillis() - last.time < 120_000) last
                    else withTimeoutOrNull(10_000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }
                if (loc != null) moveTo(loc.latitude, loc.longitude, true)
                else if (!silent) Toast.makeText(ctx, "ค้นหาตำแหน่งนานเกินไป ลองใหม่อีกครั้ง หรือปักหมุดเองบนแผนที่แทนได้ครับ", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                if (!silent) Toast.makeText(ctx, "ไม่สามารถเข้าถึง GPS ได้ ลองปักหมุดเองบนแผนที่แทนได้ครับ", Toast.LENGTH_LONG).show()
            }
            locating = false
        }
    }
    var silentAsk by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetchLocation(silentAsk)
        else if (!silentAsk) Toast.makeText(ctx, "ไม่ได้รับสิทธิ์ตำแหน่ง — เปิดได้ที่ตั้งค่าเครื่อง หรือปักหมุดเองบนแผนที่", Toast.LENGTH_LONG).show()
    }
    fun locate(silent: Boolean) {
        silentAsk = silent
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation(silent)
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // ปักหมุดอัตโนมัติตอนเปิดแผนที่ใหญ่ครั้งแรกของที่อยู่ใหม่
    LaunchedEffect(Unit) { if (autoLocate) locate(true) }

    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(C.Gray)) {
        AddrHeader("ปักหมุดตำแหน่ง", true, onClose)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            Text(
                if (sat) "🗺 แผนที่ถนน" else "🛰 ดาวเทียม", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .shadow(4.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .clickable { sat = !sat }.padding(horizontal = 12.dp, vertical = 9.dp)
            )
            Text(
                if (locating) "กำลังหาตำแหน่ง..." else "📍 ใช้ตำแหน่งปัจจุบัน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable(enabled = !locating) { locate(false) }.padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
        Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
            Text("แตะแผนที่ หรือกดหมุดค้างแล้วลากเพื่อย้ายหมุด", fontSize = 12.sp, color = C.Subtext, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            val sh = RoundedCornerShape(14.dp)
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(sh).background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark))).clickable { onConfirm(lat, lng) },
                contentAlignment = Alignment.Center
            ) { Text("ยืนยันตำแหน่งนี้", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}

@Composable
private fun AddressForm(existing: Addr?, isNew: Boolean, defaultPhone: String, canBack: Boolean, onBack: () -> Unit, onSave: (Addr) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: defaultPhone) }
    var addr by remember { mutableStateOf(existing?.address ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var lat by remember { mutableDoubleStateOf(existing?.lat ?: 13.7563) }
    var lng by remember { mutableDoubleStateOf(existing?.lng ?: 100.5018) }
    var phoneFocused by remember { mutableStateOf(false) }
    var bigOpen by remember { mutableStateOf(false) }
    // ที่อยู่ใหม่ที่ยังไม่เคยปักหมุด → เปิดแผนที่ใหญ่ครั้งแรกแล้วหาตำแหน่งให้เอง
    var pinned by remember { mutableStateOf(existing?.lat != null) }

    // แผนที่เล็ก: ดูอย่างเดียว แตะแล้วเปิดแผนที่ใหญ่
    val pv = remember { makeAddrMap(ctx, false, existing?.lat ?: 13.7563, existing?.lng ?: 100.5018, 16.0) }
    val pvMarker = remember {
        Marker(pv).apply {
            position = GeoPoint(existing?.lat ?: 13.7563, existing?.lng ?: 100.5018)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
    LaunchedEffect(Unit) { pv.overlays.add(pvMarker) }
    LaunchedEffect(lat, lng) {
        pvMarker.position = GeoPoint(lat, lng)
        pv.controller.setCenter(GeoPoint(lat, lng))
        pv.invalidate()
    }
    DisposableEffect(Unit) { pv.onResume(); onDispose { pv.onPause(); pv.onDetach() } }

    fun checkPhone() {
        if (!isNew) return
        val p = phone.trim()
        if (!Regex("^0[0-9]{9}$").matches(p)) return
        scope.launch {
            try {
                val d = Firebase.firestore.collection("customers").document(p).get().await()
                if (d.exists() && d.getBoolean("registered") == true) {
                    d.getString("name")?.takeIf { it.isNotEmpty() }?.let { name = it }
                    d.getString("address")?.takeIf { it.isNotEmpty() }?.let { addr = it }
                    val la = d.getDouble("lat"); val ln = d.getDouble("lng")
                    if (la != null && ln != null) { lat = la; lng = ln; pinned = true }
                    Toast.makeText(ctx, "พบบัญชีเดิมของเบอร์นี้ ดึงข้อมูลที่เคยบันทึกไว้มาให้แล้ว", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) { }
        }
    }

    if (bigOpen) {
        BigMapPicker(
            startLat = lat, startLng = lng, autoLocate = !pinned,
            onConfirm = { la, ln -> lat = la; lng = ln; pinned = true; bigOpen = false },
            onClose = { bigOpen = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        AddrHeader(if (isNew) "เพิ่มที่อยู่จัดส่ง" else "แก้ไขที่อยู่", canBack, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val card = RoundedCornerShape(18.dp)
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).shadow(2.dp, card).clip(card).background(Color.White).padding(18.dp)) {
                FLabel("ชื่อ-นามสกุล"); NimitInput(name, { name = it }, "ชื่อ-นามสกุลผู้รับ", height = 50.dp)
                Spacer(Modifier.height(14.dp))
                FLabel("เบอร์โทร")
                NimitInput(phone, { phone = it }, "08X-XXX-XXXX", height = 50.dp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.onFocusChanged { s ->
                        if (phoneFocused && !s.isFocused) checkPhone()
                        phoneFocused = s.isFocused
                    })
                Spacer(Modifier.height(14.dp))
                FLabel("ที่อยู่จัดส่ง"); NimitInput(addr, { addr = it }, "บ้านเลขที่ ซอย ถนน แขวง เขต", height = 50.dp)
                Spacer(Modifier.height(14.dp))
                FLabel("หมายเหตุ (ไม่บังคับ)"); NimitInput(note, { note = it }, "เช่น ตึก A ชั้น 3 ห้อง 305", height = 50.dp)
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).shadow(2.dp, card).clip(card).background(Color.White).padding(18.dp)) {
                FLabel("ตำแหน่งบนแผนที่")
                val mapShape = RoundedCornerShape(13.dp)
                Box(Modifier.fillMaxWidth().height(160.dp).clip(mapShape).border(2.dp, Line, mapShape)) {
                    AndroidView(factory = { pv }, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)).clickable { bigOpen = true }, contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(PinIcon, null, tint = C.Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("แก้ไขที่อยู่พิกัด", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                        }
                    }
                }
            }
            val sh = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp).height(54.dp)
                    .shadow(8.dp, sh, ambientColor = C.Primary.copy(alpha = 0.25f), spotColor = C.Primary.copy(alpha = 0.25f))
                    .clip(sh).background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                    .clickable {
                        val n = name.trim(); val a = addr.trim(); val p = phone.trim()
                        when {
                            n.isEmpty() || a.isEmpty() -> Toast.makeText(ctx, "กรุณากรอกชื่อและที่อยู่", Toast.LENGTH_SHORT).show()
                            p.isEmpty() -> Toast.makeText(ctx, "กรุณากรอกเบอร์โทรศัพท์ครับ ใช้สำหรับให้ร้าน/ไรเดอร์ติดต่อกลับ", Toast.LENGTH_LONG).show()
                            else -> onSave(Addr(n, p, a, note.trim(), lat, lng))
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("บันทึกที่อยู่", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}

@Composable private fun FLabel(t: String) {
    Text(t.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, letterSpacing = 0.4.sp, modifier = Modifier.padding(bottom = 6.dp))
}
