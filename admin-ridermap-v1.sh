#!/bin/bash
# แผนที่ตำแหน่งไรเดอร์แบบเรียลไทม์ในหน้า "ไรเดอร์" (ถอดจาก riderTrackMap ใน admin.html)
# กรอบน้ำเงิน "ตำแหน่งไรเดอร์แบบเรียลไทม์" + ป้าย "N คนออนไลน์" + แผนที่สูง 260 อยู่เหนือรายการไรเดอร์
# หมุดสีน้ำเงินเฉพาะไรเดอร์ที่ออนไลน์ (ตำแหน่งอัปเดตไม่เกิน 3 นาที) แตะหมุดเห็นชื่อ ปรับกรอบให้เห็นทุกคนครั้งแรกที่มีคนออนไลน์
# ใช้ osmdroid (มีในโปรเจกต์อยู่แล้ว) พื้นแผนที่ OpenStreetMap เหมือนเว็บ  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
R=$J/ui/AdminRiders.kt
[ -f $R ] || die "ไม่เจอ $R (ต้องรัน admin-lists-v2.sh ก่อน)"
grep -q 'private fun RiderCard' $R || die "AdminRiders.kt ไม่ใช่เวอร์ชันที่คาด"
if grep -q 'RiderMapCard' $R; then
  echo "แก้แล้ว ข้าม"
else
  L=$(grep -n 'Text(onlineCount.toString() + " คนออนไลน์"' $R | head -1 | cut -d: -f1)
  [ -n "$L" ] || die "ไม่เจอป้ายออนไลน์ที่หัวหน้า"
  sed -n "$((L+2))p" $R | grep -q 'Spacer(Modifier.width(10.dp))' || die "รูปแบบหัวหน้าไม่ตรงที่คาด"
  grep -q '^            if (list == null || list.isEmpty()) {$' $R || die "ไม่เจอจุดยึดรายการ"
  grep -q '^import com.nimit.delivery.data.adminErrorText$' $R || die "ไม่เจอจุดยึด import"
  # 1) เอาป้ายออนไลน์ออกจากหัวหน้า (ย้ายไปอยู่ในกรอบแผนที่ตามเว็บ)
  head -n $((L-2)) $R > .admin-map.tmp
  tail -n +$((L+3)) $R >> .admin-map.tmp
  mv .admin-map.tmp $R
  # 2) แทรกการ์ดแผนที่เหนือรายการ
  sed -i 's|^            if (list == null \|\| list.isEmpty()) {$|            item { RiderMapCard(list ?: emptyList(), now) }\n            if (list == null \|\| list.isEmpty()) {|' $R
  # 3) import
  sed -i 's|^import com.nimit.delivery.data.adminErrorText$|import com.nimit.delivery.data.adminErrorText\nimport android.graphics.Bitmap\nimport android.graphics.Canvas\nimport android.graphics.Paint\nimport android.graphics.drawable.BitmapDrawable\nimport android.view.MotionEvent\nimport androidx.compose.ui.platform.LocalLifecycleOwner\nimport androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.foundation.layout.height\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver\nimport org.osmdroid.config.Configuration\nimport org.osmdroid.tileprovider.tilesource.TileSourceFactory\nimport org.osmdroid.util.BoundingBox\nimport org.osmdroid.util.GeoPoint\nimport org.osmdroid.views.MapView\nimport org.osmdroid.views.overlay.Marker|' $R
  cat >> $R <<'KT_EOF'

private fun riderPin(ctx: Context): BitmapDrawable {
    val d: Float = ctx.resources.displayMetrics.density
    val size: Int = (38 * d).toInt()
    val bmp: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = 0xFF0C4AA6.toInt()
    c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * d, p)
    p.color = 0xFFFFFFFF.toInt()
    p.textSize = 18 * d
    p.textAlign = Paint.Align.CENTER
    c.drawText("\uD83D\uDEF5", size / 2f, size / 2f + 6 * d, p)
    return BitmapDrawable(ctx.resources, bmp)
}

/** กรอบแผนที่ตำแหน่งไรเดอร์แบบเรียลไทม์ (เหมือนเว็บ): หมุดเฉพาะคนที่ออนไลน์ */
@Composable
private fun RiderMapCard(riders: List<AdminRiderFull>, now: Long) {
    val ctx = LocalContext.current
    val online: List<AdminRiderFull> = riders.filter { it.online(now) }
    val fitted = remember { booleanArrayOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView: MapView = remember {
        val conf = Configuration.getInstance()
        conf.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        conf.userAgentValue = ctx.packageName
        val base = java.io.File(ctx.cacheDir, "osmdroid")
        conf.osmdroidBasePath = base
        conf.osmdroidTileCache = java.io.File(base, "tiles")
        val mv = MapView(ctx)
        mv.setTileSource(TileSourceFactory.MAPNIK)
        mv.setMultiTouchControls(true)
        mv.controller.setZoom(12.0)
        mv.controller.setCenter(GeoPoint(13.7563, 100.5018))
        // ลากแผนที่ไม่ให้รายการเลื่อนตาม
        mv.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
            else if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) v.parent?.requestDisallowInterceptTouchEvent(false)
            false
        }
        mv
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) mapView.onResume()
            else if (e == Lifecycle.Event.ON_PAUSE) mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(C.Primary).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ตำแหน่งไรเดอร์แบบเรียลไทม์", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(online.size.toString() + " คนออนไลน์", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        AndroidView(
            factory = { mapView },
            update = { mv ->
                mv.overlays.removeAll { it is Marker }
                val pts = ArrayList<GeoPoint>()
                for (r in online) {
                    val lat: Double = (r.d["currentLat"] as? Number)?.toDouble() ?: continue
                    val lng: Double = (r.d["currentLng"] as? Number)?.toDouble() ?: continue
                    val gp = GeoPoint(lat, lng)
                    pts.add(gp)
                    val m = Marker(mv)
                    m.position = gp
                    m.title = if (r.name.isEmpty()) "ไรเดอร์" else r.name
                    m.icon = riderPin(mv.context)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mv.overlays.add(m)
                }
                if (pts.isNotEmpty() && !fitted[0]) {
                    fitted[0] = true
                    if (pts.size == 1) {
                        mv.controller.setZoom(15.0)
                        mv.controller.setCenter(pts[0])
                    } else {
                        mv.post { mv.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 80) }
                    }
                }
                mv.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )
    }
}
KT_EOF
  grep -q 'RiderMapCard(list' $R && grep -q 'org.osmdroid.views.MapView' $R || die "แก้ไม่สำเร็จ"
  echo "แก้แล้ว"
fi
echo "---- ผลตรวจ ----"
o=$(tr -cd '{' < $R | wc -c); c=$(tr -cd '}' < $R | wc -c); p1=$(tr -cd '(' < $R | wc -c); p2=$(tr -cd ')' < $R | wc -c)
echo "$R {} $o/$c () $p1/$p2"
grep -c 'คนออนไลน์' $R | sed 's/^/ข้อความคนออนไลน์ (ควรเหลือ 1): /'
grep -n 'import androidx.compose.foundation.layout.height' $R | head -3
echo "เสร็จ"
