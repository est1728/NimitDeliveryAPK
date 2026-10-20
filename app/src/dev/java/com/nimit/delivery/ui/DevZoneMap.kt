package com.nimit.delivery.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import kotlin.math.hypot
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

// คำสั่งให้แผนที่ซูมไปที่ชุดจุด (สร้างอินสแตนซ์ใหม่ทุกครั้งที่ต้องการสั่ง)
private class DevCam(val pts: List<DevPt>)

private val devEsriSat = object : OnlineTileSourceBase(
    "EsriSat", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

/**
 * วาดรูปเขตที่กำลังแก้ + จุดยอดเอง และจัดการการลาก: แตะที่จุดแล้วลากได้ทันที (ไม่ต้องกดค้าง)
 * ลากจุดใดจะไม่เลื่อนแผนที่ (overlay นี้รับเหตุการณ์ก่อน) แตะที่ว่างตามปกติ = เพิ่มจุด (MapEventsOverlay)
 */
private class DevEditOverlay(
    private val pts: MutableList<DevPt>,
    private val onMove: (Int, Double, Double) -> Unit,
    density: Float
) : Overlay() {
    private val hit = 34f * density
    private val dotR = 11f * density
    private var drag = -1
    private val tmp = Point()
    private val fill = Paint().apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(70, 234, 88, 12)
        isAntiAlias = true
    }
    private val line = Paint().apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.argb(255, 234, 88, 12)
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val dot = Paint().apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(255, 234, 88, 12)
        isAntiAlias = true
    }
    private val ring = Paint().apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 3f * density
        isAntiAlias = true
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || pts.isEmpty()) return
        val proj = osmv.projection
        val xs = FloatArray(pts.size)
        val ys = FloatArray(pts.size)
        for (i in pts.indices) {
            proj.toPixels(GeoPoint(pts[i].lat, pts[i].lng), tmp)
            xs[i] = tmp.x.toFloat()
            ys[i] = tmp.y.toFloat()
        }
        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(xs[0], ys[0])
            for (i in 1 until pts.size) path.lineTo(xs[i], ys[i])
            if (pts.size >= 3) {
                path.close()
                c.drawPath(path, fill)
            }
            c.drawPath(path, line)
        }
        for (i in pts.indices) {
            c.drawCircle(xs[i], ys[i], dotR, dot)
            c.drawCircle(xs[i], ys[i], dotR, ring)
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val proj = mapView.projection
                var best = -1
                var bestD = hit
                for (i in pts.indices) {
                    proj.toPixels(GeoPoint(pts[i].lat, pts[i].lng), tmp)
                    val d = hypot((tmp.x - event.x).toDouble(), (tmp.y - event.y).toDouble()).toFloat()
                    if (d <= bestD) {
                        bestD = d
                        best = i
                    }
                }
                drag = best
                return best >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag < 0) return false
                if (event.pointerCount > 1) return true
                val g = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                pts[drag] = DevPt(g.latitude, g.longitude)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drag < 0) return false
                val i = drag
                drag = -1
                val p = pts[i]
                onMove(i, p.lat, p.lng)
                return true
            }
        }
        return false
    }
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
    // แตะที่ว่าง = เพิ่มจุด (อยู่เหนือรูปเขตอื่นเพื่อไม่ให้รูปเขตดักการแตะ)
    v.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
            onAdd(p.latitude, p.longitude)
            return true
        }

        override fun longPressHelper(p: GeoPoint): Boolean = false
    }))
    // รูปเขตที่กำลังแก้ + จุดที่ลากได้ทันที (บนสุด รับการสัมผัสก่อน)
    v.overlays.add(DevEditOverlay(pts.toMutableList(), onMove, v.context.resources.displayMetrics.density))
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
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
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
    var initial by remember { mutableStateOf<List<DevPt>>(emptyList()) }
    var confirmExit by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<List<DevPt>>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val m = DevZoneGeoRepo.loadAll()
            others = m.filterKeys { it != f.id }.filterValues { it.size >= 3 }
            val mine = m[f.id] ?: emptyList()
            pts.clear()
            pts.addAll(mine)
            initial = mine
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
    val dirty = loaded && snap != initial
    val nameOf = all.associate { it.id to it.name }
    val overlapNames = remember(snap, others) {
        others.filter { DevGeo.overlaps(snap, it.value) }.keys.map { nameOf[it] ?: "เขตอื่น" }
    }

    // บันทึกสถานะก่อนแก้ทุกครั้ง เพื่อกด "ย้อนกลับ" ได้
    fun push() {
        history.add(pts.toList())
        if (history.size > 60) history.removeAt(0)
    }

    fun undo() {
        if (history.isEmpty()) return
        val prev = history.removeAt(history.size - 1)
        pts.clear()
        pts.addAll(prev)
    }

    fun requestClose() {
        if (busy) return
        if (dirty) confirmExit = true else onDismiss()
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
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevMapChip("ปิด", Color.White.copy(alpha = 0.2f), Color.White) { requestClose() }
                Text(
                    "กำหนดเขต: " + f.name + (if (dirty) " *" else ""), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                DevMapChip(if (busy) "กำลังทำงาน..." else "บันทึก", if (dirty) Color(0xFFFFEDD5) else Color.White, DevC.P) { if (!busy) doSave() }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                DevZoneMapView(
                    pts = snap, others = others.values.toList(), sat = sat, cam = cam,
                    onAdd = { la, lo ->
                        push()
                        pts.add(DevPt(la, lo))
                    },
                    onMove = { i, la, lo ->
                        // แตะที่จุดเฉยๆ (ไม่ได้ลาก) ไม่นับเป็นการแก้ไข
                        if (i in pts.indices && (pts[i].lat != la || pts[i].lng != lo)) {
                            push()
                            pts[i] = DevPt(la, lo)
                        }
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds()
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
                Modifier.fillMaxWidth().zIndex(2f).background(Color.White).heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp)
            ) {
                Text(
                    "จุดทั้งหมด " + snap.size + " · แตะแผนที่เพื่อเพิ่มจุด แตะที่จุดสีส้มแล้วลากได้ทันที (เส้นสีเทา = เขตอื่น)",
                    fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp)
                )
                if (overlapNames.isNotEmpty()) {
                    Text(
                        "ทับซ้อนกับ: " + overlapNames.joinToString(", ") + " (ร้านที่อยู่ในพื้นที่ทับซ้อนผูกให้ขึ้นทั้งสองเขตได้)",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DevC.O, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    DevMapChip(
                        "ย้อนกลับ (" + history.size + ")",
                        if (history.isEmpty()) Color(0xFFF1F5F9) else DevC.PL,
                        if (history.isEmpty()) Color(0xFF94A3B8) else DevC.P
                    ) { undo() }
                    DevMapChip(if (busy) "กำลังทำงาน..." else "บันทึก", if (dirty) DevC.Gn else DevC.GnL, if (dirty) Color.White else DevC.Gn) { if (!busy) doSave() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    DevMapChip("ลบจุดล่าสุด", DevC.PL, DevC.P) {
                        if (pts.isNotEmpty()) {
                            push()
                            pts.removeAt(pts.size - 1)
                        }
                    }
                    DevMapChip("ล้างทั้งหมด", DevC.RL, DevC.R) {
                        if (pts.isNotEmpty()) {
                            push()
                            pts.clear()
                        }
                    }
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
                            push()
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
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                containerColor = Color.White,
                title = { Text("ยังไม่ได้บันทึก", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
                text = { Text("มีการแก้ไขรูปเขตที่ยังไม่ได้บันทึก จะออกโดยไม่บันทึกหรือไม่", fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmExit = false
                        doSave()
                    }) { Text("บันทึกแล้วออก", color = DevC.P, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { confirmExit = false }) { Text("อยู่ต่อ", color = DevC.S) }
                        TextButton(onClick = {
                            confirmExit = false
                            onDismiss()
                        }) { Text("ออกโดยไม่บันทึก", color = DevC.R, fontWeight = FontWeight.Bold) }
                    }
                }
            )
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
