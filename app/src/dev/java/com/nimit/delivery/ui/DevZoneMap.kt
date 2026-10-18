package com.nimit.delivery.ui

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nimit.delivery.data.DevFranchise
import com.nimit.delivery.data.DevGeo
import com.nimit.delivery.data.DevPlace
import com.nimit.delivery.data.DevPt
import com.nimit.delivery.data.DevZoneGeoRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

// คำสั่งให้แผนที่ซูมไปที่ชุดจุด (สร้างอินสแตนซ์ใหม่ทุกครั้งที่ต้องการสั่ง)
private class DevCam(val pts: List<DevPt>)

private val devEsriSat = object : OnlineTileSourceBase(
    "EsriSat", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private fun devRebuild(
    v: MapView,
    pts: List<DevPt>,
    others: List<List<DevPt>>,
    onAdd: (Double, Double) -> Unit,
    onMove: (Int, Double, Double) -> Unit
) {
    v.overlays.clear()
    // เขตอื่น: เส้นสีเทาจาง (ไว้ดูการทับซ้อน)
    others.forEach { poly ->
        if (poly.size >= 3) {
            v.overlays.add(Polygon(v).apply {
                points = poly.map { GeoPoint(it.lat, it.lng) }
                fillPaint.color = android.graphics.Color.argb(40, 100, 116, 139)
                outlinePaint.color = android.graphics.Color.argb(190, 100, 116, 139)
                outlinePaint.strokeWidth = 3f
            })
        }
    }
    // เขตที่กำลังแก้: สีส้ม
    val geo = pts.map { GeoPoint(it.lat, it.lng) }
    if (geo.size >= 3) {
        v.overlays.add(Polygon(v).apply {
            points = geo
            fillPaint.color = android.graphics.Color.argb(70, 234, 88, 12)
            outlinePaint.color = android.graphics.Color.argb(255, 234, 88, 12)
            outlinePaint.strokeWidth = 5f
        })
    } else if (geo.size == 2) {
        v.overlays.add(Polyline(v).apply {
            setPoints(geo)
            outlinePaint.color = android.graphics.Color.argb(255, 234, 88, 12)
            outlinePaint.strokeWidth = 5f
        })
    }
    // แตะแผนที่ = เพิ่มจุด (อยู่เหนือรูปเขตเพื่อไม่ให้รูปเขตดักการแตะ แต่อยู่ใต้จุดเพื่อให้จุดรับการลาก)
    v.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
            onAdd(p.latitude, p.longitude)
            return true
        }

        override fun longPressHelper(p: GeoPoint): Boolean = false
    }))
    val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(android.graphics.Color.rgb(234, 88, 12))
        setStroke(4, android.graphics.Color.WHITE)
        setSize(40, 40)
    }
    geo.forEachIndexed { i, g ->
        v.overlays.add(Marker(v).apply {
            position = g
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = dot
            isDraggable = true
            setOnMarkerClickListener(Marker.OnMarkerClickListener { _, _ -> true })
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {}
                override fun onMarkerDragStart(marker: Marker?) {}
                override fun onMarkerDragEnd(marker: Marker?) {
                    val pos = marker?.position
                    if (pos != null) onMove(i, pos.latitude, pos.longitude)
                }
            })
        })
    }
    v.invalidate()
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun DevZoneMapView(
    pts: List<DevPt>,
    others: List<List<DevPt>>,
    sat: Boolean,
    cam: DevCam?,
    onAdd: (Double, Double) -> Unit,
    onMove: (Int, Double, Double) -> Unit,
    modifier: Modifier
) {
    val ctx = LocalContext.current
    val addCb = rememberUpdatedState(onAdd)
    val moveCb = rememberUpdatedState(onMove)
    val satHolder = remember { booleanArrayOf(true) }
    val map: MapView = remember {
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm", 0))
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().apply {
            osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
            osmdroidTileCache = java.io.File(ctx.cacheDir, "osmdroid/tiles")
            tileDownloadThreads = 6.toShort()
            tileFileSystemThreads = 4.toShort()
            expirationOverrideDuration = 7L * 24 * 3600 * 1000
        }
        MapView(ctx).apply {
            setMultiTouchControls(true)
            setTileSource(devEsriSat)
            isTilesScaledToDpi = true
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(15.0, 101.0))
        }
    }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(cam) {
        val c = cam
        if (c != null && c.pts.isNotEmpty()) {
            val geo = c.pts.map { GeoPoint(it.lat, it.lng) }
            if (geo.size == 1) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(geo[0])
            } else {
                val bb = BoundingBox.fromGeoPoints(geo)
                map.post { map.zoomToBoundingBox(bb, true, 80) }
            }
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { v ->
            if (satHolder[0] != sat) {
                satHolder[0] = sat
                v.setTileSource(if (sat) devEsriSat else TileSourceFactory.MAPNIK)
            }
            devRebuild(v, pts, others, addCb.value, moveCb.value)
        }
    )
}

// ตัวแก้ไขเขตบนแผนที่ (เต็มจอ): แตะเพิ่มจุด / กดค้างที่จุดแล้วลากเพื่อขยับ / ค้นหาเส้นอำเภอ-ตำบลมาเป็นจุดตั้งต้น
@Composable
fun DevZoneMapEditor(f: DevFranchise, all: List<DevFranchise>, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val pts = remember { mutableStateListOf<DevPt>() }
    var others by remember { mutableStateOf<Map<String, List<DevPt>>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var sat by remember { mutableStateOf(true) }
    var cam by remember { mutableStateOf<DevCam?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DevPlace>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val m = DevZoneGeoRepo.loadAll()
            others = m.filterKeys { it != f.id }.filterValues { it.size >= 3 }
            val mine = m[f.id] ?: emptyList()
            pts.clear()
            pts.addAll(mine)
            cam = DevCam(if (mine.isNotEmpty()) mine else others.values.flatten())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            msg = "โหลดรูปเขตเดิมไม่สำเร็จ (ยังวาดใหม่ได้)"
            msgErr = true
        }
        loaded = true
    }

    val snap = pts.toList()
    val nameOf = all.associate { it.id to it.name }
    val overlapNames = remember(snap, others) {
        others.filter { DevGeo.overlaps(snap, it.value) }.keys.map { nameOf[it] ?: "เขตอื่น" }
    }

    fun doSearch() {
        if (query.isBlank()) return
        busy = true
        msg = ""
        msgErr = false
        scope.launch {
            try {
                val r = DevGeo.searchBoundary(query)
                results = r
                if (r.isEmpty()) {
                    msg = "ไม่พบเส้นขอบเขตของชื่อนี้ (ลองเติมคำ เช่น อำเภอแม่จัน เชียงราย)"
                    msgErr = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msg = "ค้นหาไม่สำเร็จ (ตรวจสอบอินเทอร์เน็ตแล้วลองใหม่)"
                msgErr = true
            }
            busy = false
        }
    }

    fun doSave() {
        if (pts.size < 3) {
            msg = "ต้องมีอย่างน้อย 3 จุด"
            msgErr = true
            return
        }
        busy = true
        msg = ""
        msgErr = false
        scope.launch {
            val n = pts.size
            val r = DevZoneGeoRepo.saveZone(f, pts.toList())
            busy = false
            if (r == null) {
                onSaved("กำหนดเขต " + f.name + " บนแผนที่แล้ว (" + n + " จุด) และบันทึกประวัติแล้ว")
            } else {
                msg = r
                msgErr = true
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMapChip("ปิด", Color.White.copy(alpha = 0.2f), Color.White) { if (!busy) onDismiss() }
                Text(
                    "กำหนดเขต: " + f.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                DevMapChip(if (busy) "กำลังทำงาน..." else "บันทึก", Color.White, DevC.P) { if (!busy) doSave() }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DevZoneMapView(
                    pts = snap, others = others.values.toList(), sat = sat, cam = cam,
                    onAdd = { la, lo -> pts.add(DevPt(la, lo)) },
                    onMove = { i, la, lo -> if (i in pts.indices) pts[i] = DevPt(la, lo) },
                    modifier = Modifier.fillMaxSize()
                )
                // เครดิตแหล่งแผนที่ (ข้อกำหนดของ Esri / OpenStreetMap)
                Text(
                    if (sat) "Powered by Esri · Esri, Maxar, Earthstar Geographics, and the GIS User Community" else "© OpenStreetMap contributors",
                    fontSize = 9.5.sp, color = Color(0xFF334155), maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (!loaded) {
                    Text(
                        "กำลังโหลดเขต...", fontSize = 12.sp, color = DevC.S, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp)
            ) {
                Text(
                    "จุดทั้งหมด " + snap.size + " · แตะแผนที่เพื่อเพิ่มจุด กดค้างที่จุดสีส้มแล้วลากเพื่อขยับ (เส้นสีเทา = เขตอื่น)",
                    fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp)
                )
                if (overlapNames.isNotEmpty()) {
                    Text(
                        "ทับซ้อนกับ: " + overlapNames.joinToString(", ") + " (ร้านที่อยู่ในพื้นที่ทับซ้อนผูกให้ขึ้นทั้งสองเขตได้)",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DevC.O, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    DevMapChip("ลบจุดล่าสุด", DevC.PL, DevC.P) { if (pts.isNotEmpty()) pts.removeAt(pts.size - 1) }
                    DevMapChip("ล้างทั้งหมด", DevC.RL, DevC.R) { pts.clear() }
                    DevMapChip(if (sat) "แผนที่ถนน" else "ดาวเทียม", DevC.PL, DevC.P) { sat = !sat }
                }
                Text("ค้นหาเส้นอำเภอ/ตำบลมาเป็นจุดตั้งต้น (จะแทนที่จุดเดิม)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DevMapInput(query, { query = it }, "เช่น อำเภอแม่จัน เชียงราย") }
                    DevMapChip(if (busy) "..." else "ค้นหา", DevC.P, Color.White) { if (!busy) doSearch() }
                }
                results.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 12.sp, color = DevC.T, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(p.kind + " · " + p.ring.size + " จุด", fontSize = 10.5.sp, color = DevC.S)
                        }
                        DevMapChip("ใช้เส้นนี้", DevC.GnL, DevC.Gn) {
                            pts.clear()
                            pts.addAll(p.ring)
                            cam = DevCam(p.ring)
                            results = emptyList()
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    Text("ข้อมูลขอบเขต © OpenStreetMap contributors", fontSize = 10.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
                }
                if (msg.isNotEmpty()) {
                    Text(
                        msg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (msgErr) DevC.R else DevC.Gn, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DevMapChip(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun DevMapInput(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
