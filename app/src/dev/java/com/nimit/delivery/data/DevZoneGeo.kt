package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// จุดของรูปหลายเหลี่ยมเขต เก็บเป็น [{lat,lng},...] ตรงกับข้อมูลเดิม (franchises.zone)
data class DevPt(val lat: Double, val lng: Double)

// ผลค้นหาขอบเขตจากชื่อ (ring = เส้นรอบนอกที่ลดจุดแล้ว)
data class DevPlace(val name: String, val kind: String, val ring: List<DevPt>)

object DevGeo {
    private const val UA = "NimitDeveloper/1.0 (zone editor)"

    /**
     * ค้นหาขอบเขตอำเภอ/ตำบลจากชื่อ ผ่าน OpenStreetMap Nominatim (ข้อมูล © OpenStreetMap contributors)
     * ตามนโยบายของ Nominatim: ระบุ User-Agent, เรียกเมื่อกดค้นหาเท่านั้น (ไม่ค้นระหว่างพิมพ์)
     */
    suspend fun searchBoundary(q: String): List<DevPlace> = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&polygon_geojson=1&polygon_threshold=0.0003" +
            "&limit=6&countrycodes=th&accept-language=th&q=" + URLEncoder.encode(q.trim(), "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "th")
            val code = c.responseCode
            if (code != 200) throw java.io.IOException("HTTP " + code)
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(txt)
            val out = ArrayList<DevPlace>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val g = o.optJSONObject("geojson") ?: continue
                val type = g.optString("type")
                if (type != "Polygon" && type != "MultiPolygon") continue
                val ring = biggestRing(g) ?: continue
                val simple = simplify(ring)
                if (simple.size < 3) continue
                out.add(DevPlace(o.optString("display_name"), o.optString("type"), simple))
            }
            out
        } finally {
            c.disconnect()
        }
    }

    private fun ringOf(a: JSONArray): List<DevPt> {
        val out = ArrayList<DevPt>()
        for (i in 0 until a.length()) {
            val p = a.getJSONArray(i)
            out.add(DevPt(p.getDouble(1), p.getDouble(0)))
        }
        if (out.size > 1 && out[0] == out[out.size - 1]) out.removeAt(out.size - 1)
        return out
    }

    private fun biggestRing(g: JSONObject): List<DevPt>? {
        val coords = g.optJSONArray("coordinates") ?: return null
        if (g.optString("type") == "Polygon") {
            val outer = coords.optJSONArray(0) ?: return null
            return ringOf(outer)
        }
        var best: List<DevPt>? = null
        for (i in 0 until coords.length()) {
            val poly = coords.optJSONArray(i) ?: continue
            val outer = poly.optJSONArray(0) ?: continue
            val r = ringOf(outer)
            if (best == null || r.size > (best?.size ?: 0)) best = r
        }
        return best
    }

    /** ลดจำนวนจุด (Ramer–Douglas–Peucker) ให้ไม่เกินราว 300 จุด เพื่อให้เอกสารใน Firestore เล็กและแก้จุดได้จริง */
    fun simplify(p: List<DevPt>): List<DevPt> {
        var eps = 0.0002
        var cur = rdp(p, eps)
        while (cur.size > 300 && eps < 0.05) {
            eps *= 1.6
            cur = rdp(p, eps)
        }
        return cur
    }

    private fun dist(pt: DevPt, a: DevPt, b: DevPt): Double {
        val dx = b.lng - a.lng
        val dy = b.lat - a.lat
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return Math.hypot(pt.lng - a.lng, pt.lat - a.lat)
        var t = ((pt.lng - a.lng) * dx + (pt.lat - a.lat) * dy) / len2
        if (t < 0.0) t = 0.0
        if (t > 1.0) t = 1.0
        return Math.hypot(pt.lng - (a.lng + t * dx), pt.lat - (a.lat + t * dy))
    }

    private fun rdp(p: List<DevPt>, eps: Double): List<DevPt> {
        if (p.size < 4) return p
        val keep = BooleanArray(p.size)
        keep[0] = true
        keep[p.size - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, p.size - 1))
        while (stack.isNotEmpty()) {
            val seg = stack.removeLast()
            val a = seg[0]
            val b = seg[1]
            var maxD = 0.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = dist(p[i], p[a], p[b])
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            if (idx != -1 && maxD > eps) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx))
                stack.addLast(intArrayOf(idx, b))
            }
        }
        return p.filterIndexed { i, _ -> keep[i] }
    }

    private fun inside(pt: DevPt, poly: List<DevPt>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.lat > pt.lat) != (b.lat > pt.lat) &&
                pt.lng < (b.lng - a.lng) * (pt.lat - a.lat) / (b.lat - a.lat) + a.lng
            ) c = !c
            j = i
        }
        return c
    }

    private fun cross(o: DevPt, a: DevPt, b: DevPt): Double =
        (a.lng - o.lng) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lng - o.lng)

    private fun segInter(p1: DevPt, p2: DevPt, p3: DevPt, p4: DevPt): Boolean {
        val d1 = cross(p3, p4, p1)
        val d2 = cross(p3, p4, p2)
        val d3 = cross(p1, p2, p3)
        val d4 = cross(p1, p2, p4)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** รูปสองรูปทับซ้อนกันหรือไม่ (จุดยอดอยู่ในอีกรูป หรือเส้นขอบตัดกัน) */
    fun overlaps(a: List<DevPt>, b: List<DevPt>): Boolean {
        if (a.size < 3 || b.size < 3) return false
        if (a.any { inside(it, b) } || b.any { inside(it, a) }) return true
        for (i in a.indices) {
            val a1 = a[i]
            val a2 = a[(i + 1) % a.size]
            for (j in b.indices) {
                if (segInter(a1, a2, b[j], b[(j + 1) % b.size])) return true
            }
        }
        return false
    }
}

object DevZoneGeoRepo {
    private fun parse(d: DocumentSnapshot): List<DevPt> {
        val raw = d.get("zone") as? List<*> ?: return emptyList()
        val out = ArrayList<DevPt>()
        for (m in raw) {
            val e = (m as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: continue
            val la = (e["lat"] as? Number)?.toDouble()
            val lo = (e["lng"] as? Number)?.toDouble()
            if (la != null && lo != null) out.add(DevPt(la, lo))
        }
        return out
    }

    /** รูปเขตของทุกแฟรนไชส์ (id -> จุด) */
    suspend fun loadAll(): Map<String, List<DevPt>> {
        val s = FirebaseFirestore.getInstance().collection("franchises").get().await()
        return s.documents.associate { it.id to parse(it) }
    }

    /**
     * บันทึกรูปเขต: franchises.zone + zoneStatus:"approved" (ผู้พัฒนากำหนดเองจึงไม่มีขั้นอนุมัติ)
     * + สำเนา franchiseZones/{id} (แอปลูกค้าใช้เช็คเขต) + ประวัติ — เขียนพร้อมกันในครั้งเดียว
     */
    suspend fun saveZone(f: DevFranchise, pts: List<DevPt>): String? {
        if (pts.size < 3) return "ต้องมีอย่างน้อย 3 จุด"
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        val list = pts.map { mapOf("lat" to it.lat, "lng" to it.lng) }
        return try {
            val db = FirebaseFirestore.getInstance()
            val b = db.batch()
            b.update(db.collection("franchises").document(f.id), mapOf("zone" to list, "zoneStatus" to "approved"))
            b.set(
                db.collection("franchiseZones").document(f.id),
                mapOf("franchiseId" to f.id, "name" to f.name, "zone" to list),
                SetOptions.merge()
            )
            b.set(
                db.collection("franchiseAudit").document(),
                mapOf(
                    "action" to "set_zone",
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "groupId" to f.groupId,
                    "zoneIds" to listOf(f.id),
                    "zoneNames" to listOf(f.name),
                    "before" to mapOf("zone" to (f.zonePoints.toString() + " จุด")),
                    "after" to mapOf("zone" to (pts.size.toString() + " จุด"))
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }
}
