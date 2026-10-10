package com.nimit.delivery.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** คำสั่งให้แผนที่เลื่อนไปตำแหน่งใหม่ (สร้างอินสแตนซ์ใหม่ทุกครั้งที่ต้องการสั่ง) */
class ShopMapMove(val lat: Double, val lng: Double)

private val shopEsriSat = object : OnlineTileSourceBase(
    "EsriSat", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

/** แผนที่ปักหมุด (osmdroid ตัวเดียวกับแอพลูกค้า): ดาวเทียม/แผนที่ถนน แตะเพื่อปักหมุด กดค้างที่หมุดแล้วลากเพื่อขยับ */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun ShopLocMap(lat: Double, lng: Double, move: ShopMapMove?, onPick: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var sat by remember { mutableStateOf(true) }
    val pick = rememberUpdatedState(onPick)

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
            setTileSource(shopEsriSat)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(lat, lng))
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marker: Marker = remember {
        Marker(map).apply {
            position = GeoPoint(lat, lng)
            isDraggable = true
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(m: Marker) { }
                override fun onMarkerDragEnd(m: Marker) { pick.value(m.position.latitude, m.position.longitude) }
                override fun onMarkerDragStart(m: Marker) { }
            })
        }
    }
    remember {
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                marker.position = GeoPoint(p.latitude, p.longitude)
                map.invalidate()
                pick.value(p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        map.overlays.add(marker)
        true
    }

    LaunchedEffect(move) {
        if (move != null) {
            marker.position = GeoPoint(move.lat, move.lng)
            map.controller.setZoom(16.0)
            map.controller.animateTo(GeoPoint(move.lat, move.lng))
            map.invalidate()
        }
    }
    LaunchedEffect(sat) {
        val want: OnlineTileSourceBase = if (sat) shopEsriSat else TileSourceFactory.MAPNIK
        if (map.tileProvider.tileSource.name() != want.name()) map.setTileSource(want)
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }

    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        Text(
            if (sat) "แผนที่ถนน" else "ดาวเทียม", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .clickable { sat = !sat }.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
