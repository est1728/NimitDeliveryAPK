package com.nimit.delivery.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.*
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/* ===== ค่าหน้าแรกจาก settings/homeBg (ตั้งที่ developer) — ให้ตรงกับเว็บ ===== */

private fun num(v: Any?, d: Float, lo: Float, hi: Float): Float =
    ((v as? Number)?.toFloat() ?: (v as? String)?.toFloatOrNull() ?: d).coerceIn(lo, hi)

private fun hexColor(v: Any?): Color? {
    val s = v as? String ?: return null
    if (!Regex("^#[0-9a-fA-F]{6}$").matches(s)) return null
    return try { Color(android.graphics.Color.parseColor(s)) } catch (_: Exception) { null }
}

private fun okUrl(u: String) = u.startsWith("https://") || u.startsWith("data:image/")

/* ฟอนต์ไทยชุดเดียวกับเว็บ (homebg.js FONTS) — โหลดจาก Google Fonts ครั้งแรกแล้วเก็บในเครื่อง */
private val HFONTS: Map<String, List<Int>?> = mapOf(
    "Prompt" to listOf(500, 800), "Kanit" to listOf(500, 800), "Sarabun" to listOf(500, 800), "Mitr" to listOf(500, 700), "Pridi" to listOf(500, 700),
    "Chonburi" to null, "Itim" to null, "Bai Jamjuree" to listOf(500, 700), "Sriracha" to null, "K2D" to listOf(500, 800), "Mali" to listOf(500, 700),
    "Krub" to listOf(500, 700), "Taviraj" to listOf(500, 800), "Pattaya" to null, "Athiti" to listOf(500, 700), "Trirong" to listOf(500, 800),
    "IBM Plex Sans Thai" to listOf(500, 700)
)

object HFonts {
    val loaded = mutableStateMapOf<String, FontFamily>()
    private val busy = HashSet<String>()
    suspend fun ensure(ctx: Context, name: String) {
        if (name in loaded || !HFONTS.containsKey(name)) return
        synchronized(busy) { if (!busy.add(name)) return }
        val fam = withContext(Dispatchers.IO) { try { fetch(ctx, name) } catch (_: Exception) { null } }
        if (fam != null) loaded[name] = fam
        synchronized(busy) { busy.remove(name) }
    }
    private fun fetch(ctx: Context, name: String): FontFamily? {
        val ws = HFONTS[name] ?: listOf(400)
        val dir = File(ctx.cacheDir, "hbfonts").apply { mkdirs() }
        fun file(w: Int) = File(dir, name.replace(" ", "_") + "_" + w + ".ttf")
        if (ws.all { file(it).let { f -> f.exists() && f.length() > 0 } }) return FontFamily(ws.map { Font(file(it), FontWeight(it)) })
        val q = "family=" + name.replace(" ", "+") + (if (HFONTS[name] != null) ":wght@" + ws.joinToString(";") else "")
        val ua = "Mozilla/5.0 (Linux; U; Android 2.2; en-us) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"
        val css = URL("https://fonts.googleapis.com/css2?$q&display=swap").openConnection().apply { setRequestProperty("User-Agent", ua); connectTimeout = 8000; readTimeout = 8000 }
            .getInputStream().bufferedReader().readText()
        val fonts = LinkedHashMap<Int, Font>()
        for (b in css.split("@font-face").drop(1)) {
            val w = Regex("font-weight:\\s*(\\d+)").find(b)?.groupValues?.get(1)?.toIntOrNull() ?: 400
            val u = Regex("url\\((https[^)]+)\\)").find(b)?.groupValues?.get(1) ?: continue
            if (w in fonts) continue
            val f = file(w)
            if (!f.exists() || f.length() == 0L) URL(u).openConnection().apply { connectTimeout = 8000; readTimeout = 15000 }.getInputStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            fonts[w] = Font(f, FontWeight(w))
        }
        return if (fonts.isEmpty()) null else FontFamily(fonts.values.toList())
    }
}

class HLayer(
    val isText: Boolean, val x: Float, val y: Float, val rot: Float, val op: Float, val vis: Boolean,
    val text: String = "", val color: Color = Color.White, val size: Float = 8f, val bold: Boolean = false, val sh: Float = 0f,
    val url: String = "", val cover: Boolean = false, val ar: Float = 2f, val w: Float = 100f,
    val font: String = "Prompt", val sw: Float = 0f, val sc: Color = Color(0xFF0B2F86), val dp: Float = 0f, val dc: Color = Color(0xFF0B2F86)
)

class HBgSlide(val id: String, val bg: Color?, val bg2: Color?, val layers: List<HLayer>, val link: String)

class HSlide(val key: String, val bg: HBgSlide?, val ad: Doc?)

class HBtn(val o: Float, val s: Float, val x: Float, val y: Float)

class HomeCfg(private val c: Doc?) {
    private val ui: Map<*, *> = (c?.get("ui") as? Map<*, *>) ?: emptyMap<Any, Any>()
    val heroH = num(c?.get("heroH"), 59f, 35f, 100f)
    val lift = num(c?.get("lift"), 0f, 0f, 80f)
    val shT = num(c?.get("shT"), 100f, 0f, 100f) / 100f
    val shB = num(c?.get("shB"), 100f, 0f, 100f) / 100f
    val secs = num(c?.get("secs"), 5f, 3f, 15f)
    val adCap = c?.get("adCap") == true
    val on = c?.get("on") != false
    private val hideHero: Set<String> = (c?.get("hide") as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
    val hide: Set<String> = (ui["hide"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
    val mt: Map<String, Float> = (ui["mt"] as? Map<*, *>)?.entries?.associate { it.key.toString() to num(it.value, 0f, -30f, 80f) } ?: emptyMap()
    val zoom: Map<String, Float> = (ui["zoom"] as? Map<*, *>)?.entries?.associate { it.key.toString() to num(it.value, 1f, 0.7f, 1.3f) } ?: emptyMap()
    private val pop: Map<*, *> = (ui["pop"] as? Map<*, *>) ?: emptyMap<Any, Any>()
    private val btn: Map<*, *> = (ui["btn"] as? Map<*, *>) ?: emptyMap<Any, Any>()
    fun p(k: String, d: Float, lo: Float, hi: Float) = num(pop[k], d, lo, hi)
    private fun b(k: String): HBtn { val o = (btn[k] as? Map<*, *>) ?: emptyMap<Any, Any>(); return HBtn(num(o["o"], 55f, 0f, 100f) / 100f, num(o["s"], 100f, 60f, 160f) / 100f, num(o["x"], 0f, -40f, 40f), num(o["y"], 0f, -40f, 40f)) }
    val pin = b("pin"); val menu = b("menu")

    fun sectionOrder(): List<String> {
        val def = listOf("slot", "svc", "rw", "cat", "coupon", "flash", "pop", "bnr", "flt", "shops")
        val ord = (ui["order"] as? List<*>)?.map { it.toString() }?.filter { it in def }?.distinct() ?: emptyList()
        return ord + def.filter { it !in ord }
    }

    private fun parseLayers(src: Doc): List<HLayer> {
        var ls = (src["layers"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
        val legacy = src["imgUrl"] as? String
        if (ls.isEmpty() && legacy != null && okUrl(legacy)) ls = listOf(mapOf("t" to "img", "url" to legacy, "ar" to 2, "x" to 50, "y" to 50, "w" to 100))
        val out = ArrayList<HLayer>()
        ls.take(20).forEach { l ->
            val x = num(l["x"], 50f, -50f, 150f); val y = num(l["y"], 50f, -50f, 150f)
            val rot = num(l["rot"], 0f, -180f, 180f); val op = num(l["op"], 100f, 0f, 100f); val vis = l["vis"] != false
            if (l["t"] == "txt") {
                val t = (l["text"] as? String).orEmpty().take(80)
                if (t.isNotEmpty()) out.add(HLayer(true, x, y, rot, op, vis, text = t, color = hexColor(l["color"]) ?: Color.White, size = num(l["size"], 8f, 2f, 40f),
                    bold = num(l["weight"], 500f, 0f, 1000f) >= 700f, sh = num(l["sh"], 40f, 0f, 100f),
                    font = (l["font"] as? String)?.takeIf { HFONTS.containsKey(it) } ?: "Prompt", sw = num(l["sw"], 0f, 0f, 3f), sc = hexColor(l["sc"]) ?: Color(0xFF0B2F86),
                    dp = num(l["dp"], 0f, 0f, 4f), dc = hexColor(l["dc"]) ?: Color(0xFF0B2F86)))
            } else {
                val u = (l["url"] as? String).orEmpty()
                if (okUrl(u)) out.add(HLayer(false, x, y, rot, op, vis, url = u, cover = l["cover"] == true, ar = num(l["ar"], 2f, 0.1f, 10f), w = num(l["w"], 100f, 5f, 400f)))
            }
        }
        return out
    }

    private fun linkOf(o: Map<*, *>?): String {
        if (o == null) return ""
        val u = when (o["linkType"]) { "url" -> (o["linkUrl"] as? String).orEmpty().trim(); "page" -> (o["page"] as? String).orEmpty().trim(); else -> "" }
        return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u) || Regex("^[A-Za-z0-9_\\-]+\\.html([?#][A-Za-z0-9_\\-.=&%#]*)?$").matches(u)) u else ""
    }

    private fun bgSlide(id: String, src: Doc): HBgSlide? {
        if (src["on"] == false || c?.get("on") == false) return null
        val layers = parseLayers(src); val bg = hexColor(src["bg"])
        if (layers.isEmpty() && bg == null) return null
        return HBgSlide(id, bg, hexColor(src["bg2"]), layers, linkOf(src))
    }

    /** ลำดับสไลด์ = เหมือน heroSlides() ในเว็บ (พื้นหลัง + พื้นหลังเพิ่ม + โฆษณาร้าน) */
    fun slides(ads: List<Pair<String, Doc>>): List<HSlide> {
        val bgs = ArrayList<Pair<String, Doc>>()
        if (c != null) bgs.add("bg" to c)
        (c?.get("extra") as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { e ->
            val id = e["id"] as? String ?: return@forEach
            @Suppress("UNCHECKED_CAST") val d = HashMap(e as Map<String, Any?>)
            d["on"] = c?.get("on"); d["heroH"] = c?.get("heroH"); bgs.add(id to d)
        }
        val ids = bgs.map { it.first } + ads.map { it.first }
        val ord = (c?.get("order") as? List<*>)?.map { it.toString() }?.filter { it in ids } ?: emptyList()
        val all = bgs.map { it.first }.filter { it !in ord } + ord + ads.map { it.first }.filter { it !in ord }
        val out = ArrayList<HSlide>()
        all.forEach { id ->
            if (id in hideHero) return@forEach
            val g = bgs.firstOrNull { it.first == id }
            if (g != null) { bgSlide(id, g.second)?.let { out.add(HSlide(id, it, null)) }; return@forEach }
            val a = ads.firstOrNull { it.first == id }?.second ?: return@forEach
            val img = listOf("imgUrl", "imageUrl", "image", "img", "photo", "url", "banner").map { a.str(it) }.firstOrNull { it.isNotEmpty() }
            if (img != null || (a.str("type") == "video" && a.str("videoUrl").isNotEmpty())) out.add(HSlide(id, null, a))
        }
        return out
    }

    fun adImg(a: Doc): String = listOf("imgUrl", "imageUrl", "image", "img", "photo", "url", "banner").map { a.str(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

/* ===== ตัวช่วยจัดวาง ===== */

/** เลื่อนส่วนขึ้น/ลง (เหมือน margin-top) ค่าลบ = ดึงขึ้นทับส่วนบน */
fun Modifier.topShift(dp: Float): Modifier = if (dp == 0f) this else layout { m, c ->
    val p = m.measure(c); val s = (dp * density).roundToInt()
    layout(p.width, max(0, p.height + s)) { p.place(0, s) }
}

/** ขยาย/ย่อทั้งส่วน (เหมือน CSS zoom) */
@Composable
fun ZoomBox(z: Float, content: @Composable () -> Unit) {
    if (z == 1f) { content(); return }
    Layout(content = { Box { content() } }) { ms, c ->
        val inner = (c.maxWidth / z).roundToInt()
        val p = ms.first().measure(Constraints(maxWidth = inner))
        layout(c.maxWidth, (p.height * z).roundToInt()) {
            p.placeWithLayer(0, 0) { scaleX = z; scaleY = z; transformOrigin = TransformOrigin(0f, 0f) }
        }
    }
}

@Composable
fun HSec(cfg: HomeCfg, id: String, content: @Composable () -> Unit) {
    Box(Modifier.topShift(cfg.mt[id] ?: 0f)) { ZoomBox(cfg.zoom[id] ?: 1f) { Column { content() } } }
}

private val Navy = Color(0xFF071B3D)

/* ===== ฮีโร่: พื้นหลังแอพ + โฆษณาร้าน เลื่อนวน ===== */

@Composable
private fun HLayers(layers: List<HLayer>, arFrame: Float) {
    val fctx = LocalContext.current
    LaunchedEffect(layers) { layers.filter { it.isText }.map { it.font }.distinct().forEach { HFonts.ensure(fctx, it) } }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        layers.filter { it.vis }.forEach { l ->
            val place = Modifier.layout { m, _ ->
                val p = m.measure(Constraints())
                layout(wPx.roundToInt(), hPx.roundToInt()) { p.place((l.x / 100f * wPx - p.width / 2f).roundToInt(), (l.y / 100f * hPx - p.height / 2f).roundToInt()) }
            }.graphicsLayer { rotationZ = l.rot; alpha = l.op / 100f }
            if (l.isText) {
                val fs = with(LocalDensity.current) { (wPx * l.size / 100f).toSp() }
                HText(l, wPx, fs, HFonts.loaded[l.font], place)
            } else {
                val wp = if (l.cover) max(100f, 100f * l.ar / arFrame) else l.w
                AdImage(l.url, place.width(with(LocalDensity.current) { (wPx * wp / 100f).toDp() }).aspectRatio(l.ar), ContentScale.FillBounds)
            }
        }
    }
}

@Composable
private fun HText(l: HLayer, wPx: Float, fs: TextUnit, ff: FontFamily?, place: Modifier) {
    val base = TextStyle(fontFamily = ff, fontSize = fs, lineHeight = fs * 1.1f, fontWeight = if (l.bold) FontWeight.ExtraBold else FontWeight.Medium)
    val r = l.sw / 100f * wPx
    Box(place) {
        // ความลึก (เงาแข็งเฉียงลงขวา)
        if (l.dp > 0f) {
            val n = max(2, (l.dp * 6f).roundToInt()); val d0 = r * 0.7f
            for (i in n downTo 1) {
                val d = (d0 + l.dp / 100f * wPx * i / n).roundToInt()
                Text(l.text, color = l.dc, style = base, maxLines = 1, softWrap = false, modifier = Modifier.offset { IntOffset(d, d) })
            }
        }
        // ขอบตัวอักษร
        if (r > 0f) Text(l.text, color = l.sc, maxLines = 1, softWrap = false, style = base.copy(drawStyle = Stroke(width = r * 2f, join = StrokeJoin.Round)))
        Text(l.text, color = l.color, maxLines = 1, softWrap = false,
            style = base.copy(shadow = if (l.sh > 0f) Shadow(Navy.copy(alpha = l.sh / 130f), Offset(0f, 3f), l.sh / 25f * wPx / 100f) else null))
    }
}

@Composable
private fun HeroVideo(url: String, modifier: Modifier = Modifier) {
    AndroidView(factory = { ctx ->
        android.widget.VideoView(ctx).apply {
            setVideoURI(android.net.Uri.parse(url))
            setOnPreparedListener { mp ->
                mp.isLooping = true; mp.setVolume(0f, 0f)
                val vw = mp.videoWidth.toFloat(); val vh = mp.videoHeight.toFloat()
                if (vw > 0 && vh > 0 && width > 0 && height > 0) {
                    val vr = vw / vh; val br = width.toFloat() / height
                    val sc = if (vr > br) vr / br else br / vr
                    scaleX = sc; scaleY = sc
                }
                start()
            }
            setOnErrorListener { _, _, _ -> true }
        }
    }, modifier = modifier, onRelease = { it.stopPlayback() })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    cfg: HomeCfg, slides: List<HSlide>, loginText: String, canAdvance: () -> Boolean,
    onChip: () -> Unit, onMenu: () -> Unit, onLink: (String) -> Unit, onAd: (Doc) -> Unit
) {
    val n = slides.size
    key(slides.map { it.key }, cfg.adCap) {
        val pager = rememberPagerState(initialPage = if (n > 1) 5000 * n else 0, pageCount = { if (n > 1) 10000 * n else 1 })
        var hold by remember { mutableLongStateOf(0L) }
        LaunchedEffect(n, cfg.secs) {
            while (n > 1) {
                delay((cfg.secs * 1000).toLong())
                if (System.currentTimeMillis() > hold && !pager.isScrollInProgress && canAdvance()) pager.animateScrollToPage(pager.currentPage + 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(100f / cfg.heroH).clipToBounds().background(Brush.linearGradient(listOf(N.B900, N.B700)))) {
            val w = maxWidth
            HorizontalPager(pager, Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false); hold = System.currentTimeMillis() + 10_000 }
            }) { page ->
                val sl = slides[page % n]
                Box(Modifier.fillMaxSize()) {
                    val bg = sl.bg
                    if (bg != null) {
                        val br = if (bg.bg != null) (if (bg.bg2 != null) Brush.linearGradient(listOf(bg.bg, bg.bg2)) else Brush.linearGradient(listOf(bg.bg, bg.bg))) else Brush.linearGradient(listOf(N.B900, N.B700))
                        Box(Modifier.fillMaxSize().background(br).then(if (bg.link.isNotEmpty()) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onLink(bg.link) } else Modifier)) {
                            HLayers(bg.layers, 100f / cfg.heroH)
                        }
                    } else {
                        val a = sl.ad!!
                        Box(Modifier.fillMaxSize().background(Color(0xFF0B2F86)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onAd(a) }) {
                            if (cfg.adImg(a).isNotEmpty()) AdImage(cfg.adImg(a), Modifier.fillMaxSize())
                            if (a.str("type") == "video" && a.str("videoUrl").isNotEmpty()) HeroVideo(a.str("videoUrl"), Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f).background(Brush.verticalGradient(listOf(Color.Transparent, Navy.copy(alpha = 0.72f * cfg.shB)))))
                            val nm = a.str("shopName"); val tx = a.str("caption")
                            if (cfg.adCap && (nm.isNotEmpty() || tx.isNotEmpty())) Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = w * 86f / 941f + 14.dp)) {
                                if (nm.isNotEmpty()) Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(50)).padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PathIcon("M4 4h16l1 5a3 3 0 0 1-5.5 1.6 3 3 0 0 1-5 0A3 3 0 0 1 3 9l1-5zm1 8.4V20h5v-5h4v5h5v-7.6a4.9 4.9 0 0 1-1 .1 4.9 4.9 0 0 1-3.5-1.4 4.9 4.9 0 0 1-3.5 1.4 4.9 4.9 0 0 1-3.5-1.4A4.9 4.9 0 0 1 6 12.5a4.9 4.9 0 0 1-1-.1z", Color.White, 15.dp)
                                    Text(nm, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (tx.isNotEmpty()) Text(tx, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
                                    style = TextStyle(shadow = Shadow(Navy.copy(alpha = 0.5f), Offset(0f, 2f), 6f)))
                            }
                        }
                    }
                }
            }
            // เงาด้านบน (ให้ปุ่มอ่านออก)
            Box(Modifier.fillMaxWidth().height(84.dp).background(Brush.verticalGradient(listOf(Navy.copy(alpha = 0.34f * cfg.shT), Color.Transparent))))
            // จุดบอกหน้า
            if (n > 1) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = w * 46f / 941f + cfg.lift.dp), horizontalArrangement = Arrangement.Center) {
                val cur = pager.currentPage % n
                for (i in 0 until n) Box(Modifier.padding(horizontal = 2.5.dp).size(if (i == cur) 16.dp else 5.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == cur) 1f else 0.55f)))
            }
            // ปุ่มปักหมุด (ซ้ายบน)
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 17.dp).widthIn(max = w - 84.dp)
                    .graphicsLayer {
                        translationX = cfg.pin.x * density; translationY = cfg.pin.y * density
                        scaleX = cfg.pin.s; scaleY = cfg.pin.s; transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .clip(RoundedCornerShape(50)).background(Navy.copy(alpha = cfg.pin.o)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onChip() }.padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PathIcon(P.PIN, Color.White, 13.dp, stroke = true)
                Text(loginText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                PathIcon(P.TRI, Color.White, 12.dp, modifier = Modifier.graphicsLayer { rotationZ = 90f })
            }
            // ปุ่มเมนู (ขวาบน)
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 14.dp)
                    .graphicsLayer {
                        translationX = cfg.menu.x * density; translationY = cfg.menu.y * density
                        scaleX = cfg.menu.s; scaleY = cfg.menu.s; transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .size(40.dp).clip(CircleShape).background(Navy.copy(alpha = cfg.menu.o)).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) { PathIcon(P.MENU, Color.White, 20.dp) }
        }
    }
}

/* ===== การ์ดร้านแนะนำ (ปรับขนาด/มุม/หัวใจ/ป้ายจาก developer) ===== */

@Composable
fun PopSection(
    cfg: HomeCfg, shops: List<Shop>, favs: Set<String>, onPick: (Shop) -> Unit, onFav: (String) -> Unit, onAll: () -> Unit
) {
    val nn = cfg.p("n", 3f, 2f, 4f).roundToInt()
    val ir = cfg.p("h", 55f, 30f, 90f) / 100f
    val rad = cfg.p("r", 7f, 0f, 20f); val gap = cfg.p("g", 7f, 0f, 16f); val ppad = cfg.p("px", 8f, 0f, 20f)
    val fs = cfg.p("fs", 100f, 80f, 130f) / 100f
    val hs = cfg.p("hs", 26f, 16f, 44f); val hx = cfg.p("hx", 0f, -30f, 30f); val hy = cfg.p("hy", 0f, -30f, 30f)
    val bs = cfg.p("bs", 100f, 60f, 180f) / 100f; val bx = cfg.p("bx", 0f, -30f, 30f); val by = cfg.p("by", 0f, -30f, 30f)
    val fixedW = cfg.p("w", 0f, 0f, 260f)
    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
        .border(1.5.dp, Color(0xFFDCE8FC), RoundedCornerShape(10.dp)).padding(top = 9.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(N.B600), contentAlignment = Alignment.Center) { PathIcon(P.STAR, Color.White, 13.dp) }
                Text("ร้านแนะนำ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = N.B900)
            }
            Text("ดูทั้งหมด ›", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = N.B600, modifier = Modifier.clickable { onAll() })
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cw = if (fixedW >= 60f) fixedW.dp else (maxWidth - (2 * ppad).dp - (gap * (nn - 1)).dp) / nn
            LazyRow(contentPadding = PaddingValues(start = ppad.dp, end = ppad.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                items(shops, key = { it.id }) { s ->
                    val fav = s.id in favs
                    val shape = RoundedCornerShape(rad.dp)
                    Column(Modifier.width(cw).shadow(4.dp, shape).clip(shape).background(Color.White).clickable { onPick(s) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f / ir).background(N.B50)) {
                            val pic = s.bannerUrl.ifEmpty { s.avatarUrl }
                            if (pic.isNotEmpty()) AdImage(pic, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PathIcon(P.RESTAURANT, N.B500.copy(alpha = 0.35f), 30.dp) }
                            Box(Modifier.align(Alignment.TopEnd).padding(top = (6f + hy).dp, end = (6f - hx).dp).size(hs.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.88f)).clickable { onFav(s.id) }, contentAlignment = Alignment.Center) {
                                PathIcon(P.HEART, if (fav) N.Red500 else N.Ink700, (hs * 0.58f).dp, stroke = !fav)
                            }
                        }
                        Box(Modifier.fillMaxWidth().topShift(-10f).background(Color.White, RoundedCornerShape(topStart = (rad + 1).dp, topEnd = (rad + 1).dp))) {
                            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    PathIcon(P.STAR, Color(0xFFF5B301), (11 * fs).dp)
                                    val dist = s.dist?.let { if (it < 1) "${(it * 1000).roundToInt()} ม." else "%.1f กม.".format(it) } ?: "${s.deliveryTime} นาที"
                                    Text("%.1f · %s".format(s.rating, dist), fontSize = (10 * fs).sp, lineHeight = (12 * fs).sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(s.name, fontSize = (11 * fs).sp, lineHeight = (13.2f * fs).sp, fontWeight = FontWeight.SemiBold, color = N.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.desc.ifEmpty { " " }, fontSize = (9 * fs).sp, lineHeight = (11.7f * fs).sp, color = N.Ink500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val bl = s.badge()?.let { BADGE_LABEL[it] } ?: "แนะนำ"
                            Text(bl, fontSize = (8.5f * bs).sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-(6f - bx)).dp, y = (-11f + by).dp).widthIn(max = cw * 0.8f)
                                    .clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF5B94FF), Color(0xFF2F6FE4))))
                                    .padding(horizontal = (7 * bs).dp, vertical = (2 * bs).dp))
                        }
                    }
                }
            }
        }
    }
}
