package com.nimit.delivery.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.nimit.delivery.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
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

private val GpsIcon: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
        pathData = PathParser().parsePathString(
            "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 17.93V18a1 1 0 0 0-2 0v1.93A8 8 0 0 1 4.07 13H6a1 1 0 0 0 0-2H4.07A8 8 0 0 1 11 4.07V6a1 1 0 0 0 2 0V4.07A8 8 0 0 1 19.93 11H18a1 1 0 0 0 0 2h1.93A8 8 0 0 1 13 19.93zM12 9a3 3 0 1 0 3 3 3 3 0 0 0-3-3z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}

private val esriSat = object : OnlineTileSourceBase(
    "EsriSat", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
@Composable
fun RegisterScreen(session: Session, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val phone = session.customerPhone.orEmpty()

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var sat by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val map = remember {
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
            setTileSource(esriSat)
            isTilesScaledToDpi = true
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(13.7563, 100.5018))
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marker = remember { Marker(map) }

    fun place(lat: Double, lng: Double) {
        pin = lat to lng
        marker.position = GeoPoint(lat, lng)
        if (!map.overlays.contains(marker)) map.overlays.add(marker)
        map.controller.setZoom(16.0)
        map.controller.animateTo(GeoPoint(lat, lng))
        map.invalidate()
    }

    LaunchedEffect(Unit) {
        map.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { place(p.latitude, p.longitude); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
    }
    LaunchedEffect(sat) {
        val want = if (sat) esriSat else TileSourceFactory.MAPNIK
        if (map.tileProvider.tileSource.name() != want.name()) map.setTileSource(want)
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }

    fun fetchLocation() {
        scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(ctx)
                val last = try { client.lastLocation.await() } catch (_: Exception) { null }
                val loc = if (last != null && System.currentTimeMillis() - last.time < 120_000) last
                    else withTimeoutOrNull(10_000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }
                if (loc != null) place(loc.latitude, loc.longitude)
                else Toast.makeText(ctx, "ไม่พบตำแหน่ง ลองปักหมุดเองบนแผนที่ได้เลย", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "ไม่สามารถเข้าถึง GPS ได้ ลองปักหมุดเองบนแผนที่แทนได้ครับ", Toast.LENGTH_LONG).show()
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetchLocation()
        else Toast.makeText(ctx, "ไม่ได้รับสิทธิ์ตำแหน่ง — เปิดได้ที่ตั้งค่าเครื่อง หรือปักหมุดเองบนแผนที่", Toast.LENGTH_LONG).show()
    }
    fun locate() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation()
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(Unit) { locate() }

    // บันทึกความคืบหน้าอัตโนมัติ (เหมือนเว็บ)
    LaunchedEffect(name, address, pin) {
        delay(1000)
        if (phone.isEmpty()) return@LaunchedEffect
        val partial = mutableMapOf<String, Any>("phone" to phone)
        if (name.isNotBlank()) partial["name"] = name.trim()
        if (address.isNotBlank()) partial["address"] = address.trim()
        pin?.let { partial["lat"] = it.first; partial["lng"] = it.second }
        if (partial.size <= 1) return@LaunchedEffect
        try { Firebase.firestore.collection("customers").document(phone).set(partial, SetOptions.merge()).await() } catch (_: Exception) {}
    }

    fun save() {
        val n = name.trim(); val a = address.trim(); val pp = pin ?: return
        saving = true
        scope.launch {
            val data = mapOf(
                "phone" to phone, "name" to n, "address" to a, "lat" to pp.first, "lng" to pp.second,
                "createdAt" to java.time.Instant.now().toString(), "registered" to true
            )
            try {
                Firebase.firestore.collection("customers").document(phone).set(data).await()
                session.customerData = JSONObject(data).toString()
                session.addresses = JSONArray().put(
                    JSONObject().put("name", n).put("phone", phone).put("address", a).put("lat", pp.first).put("lng", pp.second)
                ).toString()
                session.defaultAddress = "0"
                session.newCustomer = null
                saving = false
                onDone()
            } catch (e: Exception) {
                saving = false
                Toast.makeText(ctx, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("ข้อมูลจัดส่ง", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                SectionTitle("ข้อมูลส่วนตัว", top = 0)
                Label("ชื่อ-นามสกุล")
                NimitInput(name, { name = it }, "เช่น คุณนิด")
                Spacer(Modifier.height(14.dp))
                Label("รายละเอียดที่อยู่")
                NimitInput(address, { address = it }, "บ้านเลขที่ / หมู่ / ซอย / จุดสังเกต", height = 90.dp, singleLine = false)

                SectionTitle("ตำแหน่งจัดส่ง", top = 20)
                val mapShape = RoundedCornerShape(14.dp)
                Box(Modifier.fillMaxWidth().height(260.dp).clip(mapShape).border(2.dp, Color(0xFFE8E8E8), mapShape)) {
                    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp)
                            .shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { locate() },
                        contentAlignment = Alignment.Center
                    ) { Icon(GpsIcon, null, tint = C.Primary, modifier = Modifier.size(22.dp)) }
                    Text(
                        if (sat) "แผนที่ถนน" else "ดาวเทียม", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)
                            .clickable { sat = !sat }.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Text("แตะแผนที่เพื่อปักหมุด หรือกดปุ่มใช้ตำแหน่งปัจจุบัน", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 8.dp))
                pin?.let {
                    Text(
                        "%.5f, %.5f".format(it.first, it.second), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(C.PrimaryLight).padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Box(Modifier.fillMaxWidth().background(Color.White).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 26.dp)) {
                GradientButton(if (saving) "กำลังบันทึก..." else "บันทึกข้อมูล", enabled = name.isNotBlank() && address.isNotBlank() && pin != null && !saving) { save() }
            }
        }
    }
}

@Composable private fun SectionTitle(t: String, top: Int) {
    Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Subtext, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = top.dp, bottom = 10.dp))
}
@Composable private fun Label(t: String) {
    Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.padding(bottom = 6.dp))
}
