package com.nimit.delivery.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** คำสั่งให้แผนที่เลื่อนไปตำแหน่งใหม่ (สร้างอินสแตนซ์ใหม่ทุกครั้งที่ต้องการสั่ง) */
class ShopMapMove(val lat: Double, val lng: Double)

private class ShopMapBridge(val onPick: (Double, Double) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface
    fun onPick(lat: Double, lng: Double) { main.post { onPick.invoke(lat, lng) } }
}

private const val SHOP_MAP_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{margin:0;padding:0;height:100%;width:100%} .leaflet-top,.leaflet-bottom{z-index:50 !important;}</style>
</head><body><div id="m"></div>
<script>
var map = L.map('m').setView([__LAT__, __LNG__], 15);
var satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {attribution:'Tiles &copy; Esri', maxZoom:19, maxNativeZoom:19});
var streetLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution:'&copy; OpenStreetMap', maxZoom:19});
satelliteLayer.addTo(map);
L.control.layers({'ดาวเทียม':satelliteLayer, 'แผนที่ถนน':streetLayer}, null, {position:'topright'}).addTo(map);
var marker = L.marker([__LAT__, __LNG__], {draggable:true}).addTo(map);
marker.on('dragend', function(e){ var p = e.target.getLatLng(); Android.onPick(p.lat, p.lng); });
map.on('click', function(e){ marker.setLatLng(e.latlng); Android.onPick(e.latlng.lat, e.latlng.lng); });
function setPos(lat, lng, z){ map.setView([lat, lng], z); marker.setLatLng([lat, lng]); }
setTimeout(function(){ map.invalidateSize(); }, 300);
setTimeout(function(){ map.invalidateSize(); }, 1000);
</script></body></html>"""

/** แผนที่ปักหมุด (Leaflet ใน WebView เหมือนในเว็บ: ดาวเทียม/แผนที่ถนน แตะเพื่อปักหมุด ลากหมุดได้) */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ShopLocMap(lat: Double, lng: Double, move: ShopMapMove?, onPick: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val initLat = remember { lat }
    val initLng = remember { lng }
    LaunchedEffect(move, web) {
        val w = web
        if (move != null && w != null) w.evaluateJavascript("setPos(" + move.lat + "," + move.lng + ",16)", null)
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(ShopMapBridge { a, b -> onPick(a, b) }, "Android")
                // ลากแผนที่ในหน้าที่เลื่อนได้ ต้องไม่ให้หน้าแย่งนิ้ว
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                val html: String = SHOP_MAP_HTML.replace("__LAT__", initLat.toString()).replace("__LNG__", initLng.toString())
                loadDataWithBaseURL("https://nimitdelivery.vercel.app/", html, "text/html", "utf-8", null)
                web = this
            }
        },
        onRelease = { it.destroy() }
    )
}
