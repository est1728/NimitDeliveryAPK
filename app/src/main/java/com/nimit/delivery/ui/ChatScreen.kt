package com.nimit.delivery.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

// ───────── แชท: พอร์ตหน้าตาและพฤติกรรมจาก chat.html (ใช้ร่วมทุก role: customer | rider | admin) ─────────
private const val CH_WEB_BASE = "https://nimitdelivery.vercel.app"
private val CH_P = Color(0xFF0C4AA6)
private val CH_PD = Color(0xFF083570)
private val CH_G = Color(0xFFF5F6F8)
private val CH_T = Color(0xFF0F172A)
private val CH_S = Color(0xFF64748B)
private val CH_RED = Color(0xFFDC2626)
private val CH_BG = Color(0xFFE8ECF0)
private val CH_MON = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

private const val IC_BACK = "M19 12H5M12 5l-7 7 7 7"
private const val IC_CAMERA = "M12 15.2c-1.77 0-3.2-1.43-3.2-3.2S10.23 8.8 12 8.8s3.2 1.43 3.2 3.2-1.43 3.2-3.2 3.2zM20 4h-3.17L15 2H9L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z"
private const val IC_IMAGE = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"
private const val IC_MIC = "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1-9c0-.55.45-1 1-1s1 .45 1 1v6c0 .55-.45 1-1 1s-1-.45-1-1V5zm6.91 6c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43 5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z"
private const val IC_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"
private const val IC_CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
private const val IC_PLAY = "M8 5v14l11-7z"
private const val IC_PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
private const val IC_CHAT = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"

private class ChatMsg(val id: String, val sender: String, val senderName: String, val type: String, val content: String, val ms: Long, val wave: List<Int> = emptyList())

private fun roleLabel(r: String) = when (r) { "customer" -> "ลูกค้า"; "rider" -> "ไรเดอร์"; "admin" -> "แอดมิน"; else -> r }
private fun dateStr(ms: Long): String { val c = Calendar.getInstance().apply { timeInMillis = ms }; return "%d %s %02d".format(c.get(Calendar.DAY_OF_MONTH), CH_MON[c.get(Calendar.MONTH)], (c.get(Calendar.YEAR) + 543) % 100) }
private fun timeStr(ms: Long): String { val c = Calendar.getInstance().apply { timeInMillis = ms }; return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }
private fun fmtSec(s: Int) = "%d:%02d".format(s / 60, s % 60)

/** ย่อรูปเหลือ ≤600px แล้วเข้ารหัสเป็น data URL (เหมือนที่เว็บทำ) */
private fun imageToDataUrl(ctx: Context, uri: Uri): String? = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1; while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1200) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
    val sc = minOf(1f, 600f / bmp.width, 600f / bmp.height)
    val out = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
    val bos = ByteArrayOutputStream(); out.compress(Bitmap.CompressFormat.JPEG, 70, bos)
    "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
} catch (_: Exception) { null }

private fun dataUrlToFile(ctx: Context, key: String, dataUrl: String): File? {
    val head = dataUrl.substringBefore(",").lowercase()
    val ext = when { "webm" in head -> "webm"; "ogg" in head -> "ogg"; "mpeg" in head || "mp3" in head -> "mp3"; "wav" in head -> "wav"; else -> "m4a" }
    val f = File(File(ctx.cacheDir, "chat_photos").apply { mkdirs() }, "aud_${key.hashCode()}.$ext")
    return try { if (!f.exists()) f.writeBytes(Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)); f } catch (_: Exception) { null }
}

/** เล่นในแอปไม่ได้ (เครื่องไม่รองรับรูปแบบเสียงนี้) -> ให้ผู้ใช้เลือกเปิดด้วยแอปเล่นเสียง/เบราว์เซอร์ในเครื่อง */
private fun openAudioExternally(ctx: Context, f: File) {
    try {
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".chatphotos", f)
        val mime = when (f.extension) { "webm" -> "audio/webm"; "ogg" -> "audio/ogg"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; else -> "audio/mp4" }
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri("", u)
        ctx.startActivity(Intent.createChooser(view, "เปิดเสียงด้วย...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { Toast.makeText(ctx, "เครื่องนี้เล่นเสียงนี้ไม่ได้", Toast.LENGTH_LONG).show() }
}

private class ChatPlayer(val ctx: Context) {
    private var mp: MediaPlayer? = null
    var playingId by mutableStateOf<String?>(null)
    var progress by mutableFloatStateOf(0f)
    var posSec by mutableIntStateOf(0)
    fun play(id: String, f: File) {
        if (playingId == id) { stop(); return }
        stop()
        try { mp = MediaPlayer().apply { setDataSource(f.path); setOnCompletionListener { stop() }; prepare(); start() }; playingId = id } catch (_: Exception) { stop(); openAudioExternally(ctx, f) }
    }
    fun playData(id: String, dataUrl: String) { if (playingId == id) { stop(); return }; dataUrlToFile(ctx, id, dataUrl)?.let { play(id, it) } }
    fun tick() { mp?.let { if (it.isPlaying && it.duration > 0) { progress = it.currentPosition.toFloat() / it.duration; posSec = it.currentPosition / 1000 } } }
    fun stop() { try { mp?.release() } catch (_: Exception) {}; mp = null; playingId = null; progress = 0f; posSec = 0 }
}

/** ระดับเสียง 0.05–1 จากแอมพลิจูดดิบ (สเกลเดซิเบล 50 dB) — เสียงพูดปกติจะได้ราว 0.6–0.85 เงียบจะต่ำ */
private fun ampLevel(a: Int): Float {
    if (a <= 0) return 0.05f
    val db = 20f * kotlin.math.log10(a / 32767f)
    return ((db + 50f) / 50f).coerceIn(0.05f, 1f)
}

/** ย่อ/ขยายรายการระดับเสียงให้เหลือ n ค่า (เฉลี่ยเป็นช่วง) */
private fun resampleLevels(src: List<Float>, n: Int): List<Float> {
    if (src.isEmpty()) return List(n) { 0.05f }
    return List(n) { i ->
        val a = (i * src.size / n).coerceAtMost(src.size - 1); val b = (((i + 1) * src.size) / n).coerceIn(a + 1, src.size)
        src.subList(a, b).average().toFloat()
    }
}

private class ChatRec(val ctx: Context) {
    private var mr: MediaRecorder? = null; private var file: File? = null; private var t0 = 0L
    fun start(): Boolean = try {
        val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION") val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1); r.setAudioSamplingRate(16000); r.setAudioEncodingBitRate(32000); r.setOutputFile(f.path); r.prepare(); r.start()
        mr = r; file = f; t0 = System.currentTimeMillis(); true
    } catch (_: Exception) { false }
    fun rawAmp(): Int = try { mr?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
    fun stop(): Pair<File, Int>? {
        val r = mr ?: return null; mr = null
        try { r.stop() } catch (_: Exception) { try { r.release() } catch (_: Exception) {}; return null }
        r.release(); val secs = ((System.currentTimeMillis() - t0) / 1000).toInt()
        return if (secs < 1) null else file!! to secs   // เหมือนเว็บ: สั้นกว่า 1 วินาทีทิ้ง
    }
    fun cancel() { try { mr?.stop() } catch (_: Exception) {}; try { mr?.release() } catch (_: Exception) {}; mr = null; file?.delete() }
}

@Composable
fun ChatScreen(orderId: String, role: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Firebase.firestore }
    val myName = roleLabel(role)
    var title by remember { mutableStateOf("แชท") }
    var sub by remember { mutableStateOf(roleLabel(role)) }
    var msgs by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var otherRead by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var text by remember { mutableStateOf("") }
    var pendingImg by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<Triple<File, Int, List<Int>>?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSec by remember { mutableIntStateOf(0) }
    var amps by remember { mutableStateOf<List<Float>>(emptyList()) }
    val waveAll = remember { mutableListOf<Float>() }
    var viewImg by remember { mutableStateOf<String?>(null) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val player = remember { ChatPlayer(ctx) }
    val rec = remember { ChatRec(ctx) }
    val listState = rememberLazyListState()
    val maxBubble = ((LocalConfiguration.current.screenWidthDp - 28) * 0.78f).dp

    DisposableEffect(Unit) { onDispose { player.stop(); rec.cancel() } }
    LaunchedEffect(player.playingId) { while (player.playingId != null) { player.tick(); delay(100) } }
    LaunchedEffect(recording) { amps = emptyList(); waveAll.clear(); var prev = 0.05f; while (recording) { delay(60); val lv = maxOf(ampLevel(rec.rawAmp()), prev * 0.7f); prev = lv; waveAll.add(lv); amps = (amps + lv).takeLast(96) } }
    LaunchedEffect(recording) { recSec = 0; var t = 0; while (recording) { delay(1000); t++; recSec = t } }

    fun markRead() { if (orderId.isNotEmpty()) db.collection("chats").document(orderId).set(mapOf("unread_$role" to 0, "${role}ReadAt" to FieldValue.serverTimestamp()), SetOptions.merge()) }

    DisposableEffect(orderId) {
        if (orderId.isEmpty() || orderId == "undefined" || orderId == "unknown") { loaded = true; return@DisposableEffect onDispose { } }
        scope.launch {
            try { db.collection("orders").document(orderId).get().await().data?.let { o ->
                title = "แชท " + ((o["orderId"] as? String)?.takeIf { it.isNotEmpty() } ?: orderId)
                val c = o["customer"] as? Map<*, *>
                sub = ((o["shopName"] as? String).orEmpty()) + " · " + ((c?.get("name") as? String)?.takeIf { it.isNotEmpty() } ?: (c?.get("phone") as? String).orEmpty())
            } } catch (_: Exception) {}
        }
        markRead()
        val r1 = db.collection("chats").document(orderId).addSnapshotListener { snap, _ ->
            val d = snap?.data ?: return@addSnapshotListener
            otherRead = listOf("customer", "rider", "admin").filter { it != role }.mapNotNull { r -> (d["${r}ReadAt"] as? Timestamp)?.toDate()?.time?.let { r to it } }.toMap()
        }
        val r2 = db.collection("chats").document(orderId).collection("messages").orderBy("timestamp").limit(200).addSnapshotListener { snap, _ ->
            msgs = snap?.documents?.map { d ->
                ChatMsg(d.id, d.getString("sender").orEmpty(), d.getString("senderName").orEmpty(), d.getString("type") ?: "text", d.getString("content").orEmpty(),
                    d.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: System.currentTimeMillis(),
                    (d.get("waveform") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList())
            } ?: emptyList()
            loaded = true; markRead()
        }
        onDispose { r1.remove(); r2.remove() }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1) }

    // แจ้งเตือน: ลูกค้าส่ง -> แอดมิน+ไรเดอร์ / แอดมินหรือไรเดอร์ส่ง -> ลูกค้าเจ้าของออเดอร์ (เหมือนเว็บ)
    fun notifyOther(preview: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val o = com.google.android.gms.tasks.Tasks.await(db.collection("orders").document(orderId).get()).data ?: return@launch
                val title2 = "💬 ข้อความใหม่: $myName"
                val body = JSONObject().put("title", title2).put("body", preview)
                if (role == "customer") {
                    val tokens = mutableListOf<String>()
                    com.google.android.gms.tasks.Tasks.await(db.collection("fcmTokens").whereEqualTo("role", "admin").get()).documents.forEach { d -> d.getString("token")?.takeIf { it.isNotEmpty() }?.let { tokens += it } }
                    (o["riderId"] as? String)?.takeIf { it.isNotEmpty() }?.let { rid -> com.google.android.gms.tasks.Tasks.await(db.collection("fcmTokens").document(rid + "_rider").get()).getString("token")?.takeIf { it.isNotEmpty() }?.let { tokens += it } }
                    if (tokens.isEmpty()) return@launch
                    body.put("tokens", JSONArray(tokens)).put("url", "/chat.html?orderId=$orderId&role=rider")
                } else {
                    val phone = (o["customer"] as? Map<*, *>)?.get("phone") as? String ?: return@launch
                    val tk = com.google.android.gms.tasks.Tasks.await(db.collection("fcmTokens").document(phone + "_customer").get()).getString("token")?.takeIf { it.isNotEmpty() } ?: return@launch
                    body.put("token", tk).put("url", "/chat.html?orderId=$orderId&role=customer").put("orderDocId", orderId).put("route", "track/$orderId")
                }
                val c = URL("$CH_WEB_BASE/api/send-order-notification").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }; c.responseCode; c.disconnect()
            } catch (_: Exception) {}
        }
    }

    suspend fun sendMessage(type: String, content: String, extra: Map<String, Any> = emptyMap()) {
        if (orderId.isEmpty() || orderId == "undefined" || orderId == "unknown") { Toast.makeText(ctx, "ไม่พบรหัสออเดอร์ กรุณาเข้าจากหน้าออเดอร์", Toast.LENGTH_SHORT).show(); return }
        try {
            db.collection("chats").document(orderId).collection("messages").add(mapOf("sender" to role, "senderName" to myName, "type" to type, "content" to content, "timestamp" to FieldValue.serverTimestamp(), "readBy" to listOf(role)) + extra).await()
            val upd = mutableMapOf<String, Any>("lastMessage" to (if (type == "text") content else "[$type]"), "lastTime" to FieldValue.serverTimestamp())
            listOf("customer", "rider", "admin").forEach { r -> if (r != role) upd["unread_$r"] = FieldValue.increment(1) }
            db.collection("chats").document(orderId).set(upd, SetOptions.merge()).await()
            notifyOther(if (type == "text") content else "ส่ง" + (if (type == "image") "รูปภาพ" else "เสียง"))
        } catch (e: Exception) { Toast.makeText(ctx, "ส่งไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    suspend fun sendPending() {
        val img = pendingImg; val au = pendingAudio
        if (img != null) { pendingImg = null; sendMessage("image", img) }
        else if (au != null) {
            pendingAudio = null; player.stop()
            val b64 = withContext(Dispatchers.IO) { "data:audio/mp4;base64," + Base64.encodeToString(au.first.readBytes(), Base64.NO_WRAP) }
            if (b64.length > 900000) Toast.makeText(ctx, "ไฟล์เสียงใหญ่เกินไป", Toast.LENGTH_SHORT).show() else sendMessage("audio", b64, mapOf("waveform" to au.third))
        }
    }

    fun sendAll() {
        scope.launch {
            sendPending()
            val t = text.trim()
            if (t.isNotEmpty()) { text = ""; sendMessage("text", t) }
        }
    }

    fun takeImage(uri: Uri) {
        scope.launch {
            val b = withContext(Dispatchers.IO) { imageToDataUrl(ctx, uri) }
            if (b == null) Toast.makeText(ctx, "เปิดรูปไม่ได้", Toast.LENGTH_SHORT).show()
            else if (b.length > 900000) Toast.makeText(ctx, "รูปใหญ่เกินไป กรุณาเลือกรูปขนาดเล็กกว่านี้", Toast.LENGTH_LONG).show()
            else { pendingImg = b; pendingAudio = null }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) takeImage(uri) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val u = camUri; if (ok && u != null) takeImage(u) }
    fun openCamera() {
        try {
            val dir = File(ctx.cacheDir, "chat_photos").apply { mkdirs() }
            val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".chatphotos", File(dir, "cam_${System.currentTimeMillis()}.jpg"))
            camUri = u; takePhoto.launch(u)
        } catch (_: Exception) { Toast.makeText(ctx, "เปิดกล้องไม่ได้ (ยังไม่ได้ตั้งค่ากล้องในแอป)", Toast.LENGTH_LONG).show() }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (rec.start()) recording = true else Toast.makeText(ctx, "ไม่สามารถเข้าถึงไมค์ได้", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(ctx, "ไม่สามารถเข้าถึงไมค์ได้", Toast.LENGTH_LONG).show()
    }
    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { if (rec.start()) recording = true else Toast.makeText(ctx, "ไม่สามารถเข้าถึงไมค์ได้", Toast.LENGTH_SHORT).show() }
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().background(CH_BG)) {
        // ── chat-bar ──
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CH_P, CH_PD))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onBack() }, contentAlignment = Alignment.Center) { PathIcon(IC_BACK, Color.White, 18.dp, stroke = true) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // ── messages ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loaded && msgs.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PathIcon(IC_CHAT, Color(0xFFCBD5E1), 48.dp)
                Text("ยังไม่มีข้อความ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CH_S)
                Text("เริ่มการสนทนา", fontSize = 13.sp, color = CH_S)
            } else LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs.size, key = { msgs[it].id }) { i ->
                    val m = msgs[i]; val me = m.sender == role
                    val ds = dateStr(m.ms)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (i == 0 || dateStr(msgs[i - 1].ms) != ds) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
                            Text(ds, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CH_S, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp))
                            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
                            if (!me) Text(m.senderName.ifEmpty { roleLabel(m.sender) }, fontSize = 11.sp, color = CH_S, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 3.dp))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (me) {
                                    val read = otherRead.values.any { it >= m.ms }
                                    Text(if (read) "อ่านแล้ว" else "ส่งแล้ว", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp, color = if (read) Color(0xFF2563EB) else Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 2.dp))
                                }
                                val admin = m.sender == "admin" && !me
                                val shape = if (me) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                                val bg = if (me) CH_P else if (admin) Color(0xFF7C3AED) else Color.White   // ตรงตาม CSS เดิม: ข้อความของแอดมินพื้นม่วง
                                val fg = if (me) Color.White else CH_T
                                Box(Modifier.widthIn(max = maxBubble).then(if (!me && !admin) Modifier.shadow(2.dp, shape) else Modifier).clip(shape).background(bg).padding(horizontal = 13.dp, vertical = 10.dp)) {
                                    when (m.type) {
                                        "image" -> ChatImg(m.content, Modifier.clickable { viewImg = m.content })
                                        "audio" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                                            val playing = player.playingId == m.id
                                            Box(Modifier.size(32.dp).clip(CircleShape).background(if (me) Color.White.copy(alpha = 0.3f) else CH_P).clickable { player.playData(m.id, m.content) }, contentAlignment = Alignment.Center) { PathIcon(if (playing) IC_PAUSE else IC_PLAY, Color.White, 14.dp) }
                                            if (m.wave.isNotEmpty()) WaveBars(m.wave.map { it / 100f }, if (playing) player.progress else 0f, if (me) Color.White else CH_P, if (me) Color.White.copy(alpha = 0.35f) else Color(0xFFCBD5E1), Modifier.width(120.dp).height(28.dp))
                                            else Box(Modifier.width(100.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (me) Color.White.copy(alpha = 0.3f) else Color(0xFFE2E8F0))) {
                                                Box(Modifier.fillMaxHeight().fillMaxWidth(if (playing) player.progress else 0f).background(if (me) Color.White else CH_P))
                                            }
                                            Text(fmtSec(if (playing) player.posSec else 0), fontSize = 11.sp, color = if (me) Color.White.copy(alpha = 0.8f) else CH_S, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 32.dp))
                                        }
                                        else -> Text(m.content, fontSize = 14.sp, lineHeight = 21.sp, color = fg)
                                    }
                                }
                            }
                            Text(timeStr(m.ms), fontSize = 10.sp, color = CH_S, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 3.dp))
                        }
                    }
                }
            }
        }
        // ── record panel ──
        if (recording) Row(Modifier.fillMaxWidth().background(Color(0xFFF1F5F9)).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(fmtSec(recSec), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = CH_RED, modifier = Modifier.widthIn(min = 60.dp))
            WaveBars(amps, 1f, Color(0xFFEF4444), Color(0xFFEF4444), Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEE2E2)).padding(horizontal = 6.dp), live = true)
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE2E8F0)).clickable { rec.cancel(); recording = false }, contentAlignment = Alignment.Center) { PathIcon(IC_CLOSE, CH_S, 16.dp) }
            Box(Modifier.size(52.dp).shadow(6.dp, CircleShape, ambientColor = CH_RED.copy(alpha = 0.3f), spotColor = CH_RED.copy(alpha = 0.3f)).clip(CircleShape).background(CH_RED).clickable {
                recording = false
                rec.stop()?.let { (f, secs) -> pendingAudio = Triple(f, secs, resampleLevels(waveAll.toList(), 40).map { (it * 100).toInt().coerceIn(0, 100) }); pendingImg = null }
            }, contentAlignment = Alignment.Center) { Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)) }
        }
        // ── preview bar ──
        if (pendingImg != null || pendingAudio != null) Row(Modifier.fillMaxWidth().background(Color(0xFFF1F5F9)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val img = pendingImg; val au = pendingAudio
            if (img != null) {
                ChatImg(img, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp)), thumb = true)
                Text("พร้อมส่งรูปภาพ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CH_P, modifier = Modifier.weight(1f))
            } else if (au != null) {
                val playing = player.playingId == "preview"
                Row(Modifier.weight(1f).shadow(1.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(CH_P).clickable { player.play("preview", au.first) }, contentAlignment = Alignment.Center) { PathIcon(if (playing) IC_PAUSE else IC_PLAY, Color.White, 14.dp) }
                    WaveBars(au.third.map { it / 100f }, if (playing) player.progress else 0f, CH_P, Color(0xFFCBD5E1), Modifier.weight(1f).height(28.dp))
                    Text(fmtSec(if (playing) player.posSec else au.second), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CH_S, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 34.dp))
                }
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFE2E8F0)).clickable { player.stop(); pendingImg = null; pendingAudio = null }, contentAlignment = Alignment.Center) { PathIcon(IC_CLOSE, CH_S, 14.dp) }
            Box(Modifier.height(38.dp).clip(RoundedCornerShape(20.dp)).background(CH_P).clickable { scope.launch { sendPending() } }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text("ส่ง", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        // ── input-area ──
        Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { openCamera() }, contentAlignment = Alignment.Center) { PathIcon(IC_CAMERA, CH_S, 20.dp) }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { pickImage.launch("image/*") }, contentAlignment = Alignment.Center) { PathIcon(IC_IMAGE, CH_S, 20.dp) }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !recording) { startRec() }, contentAlignment = Alignment.Center) { PathIcon(IC_MIC, CH_S, 20.dp) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 42.dp, max = 120.dp).clip(RoundedCornerShape(22.dp)).background(CH_G).padding(horizontal = 14.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("พิมพ์ข้อความ...", fontSize = 15.sp, color = Color(0xFFB0B8C1))
                    BasicTextField(text, { text = it }, textStyle = TextStyle(fontSize = 15.sp, color = CH_T, lineHeight = 21.sp), modifier = Modifier.fillMaxWidth(), maxLines = 5)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(CH_P).clickable { sendAll() }, contentAlignment = Alignment.Center) { PathIcon(IC_SEND, Color.White, 20.dp) }
            }
        }
    }

    viewImg?.let { src ->
        Dialog(onDismissRequest = { viewImg = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { viewImg = null }, contentAlignment = Alignment.Center) {
                ChatImg(src, Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(12.dp)), full = true)
                Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 18.sp) }
            }
        }
    }
}

/** รูปในแชท: bubble = สูงสุด 200x200 รักษาสัดส่วน, thumb = ครอปเต็มกรอบ, full = เต็มความกว้าง */
@Composable
private fun ChatImg(src: String, modifier: Modifier, thumb: Boolean = false, full: Boolean = false) {
    if (src.startsWith("http")) {
        AsyncImage(src, null, contentScale = if (thumb) ContentScale.Crop else ContentScale.Fit, modifier = if (thumb || full) modifier else modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).clip(RoundedCornerShape(10.dp)))
        return
    }
    val bmp by produceState<Bitmap?>(null, src) {
        value = withContext(Dispatchers.Default) { try { Base64.decode(src.substringAfter(","), Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (_: Exception) { null } }
    }
    val b = bmp
    if (b == null) { Box(modifier.then(if (thumb || full) Modifier else Modifier.size(120.dp)).background(Color(0xFFE2E8F0))); return }
    val ratio = b.width.toFloat() / b.height
    when {
        thumb -> androidx.compose.foundation.Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier)
        full -> androidx.compose.foundation.Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = modifier.aspectRatio(ratio))
        else -> {
            val w = if (ratio >= 1f) 200.dp else (200 * ratio).dp
            androidx.compose.foundation.Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = modifier.width(w).aspectRatio(ratio).clip(RoundedCornerShape(10.dp)))
        }
    }
}


/** แท่งคลื่นเสียงแบบ Instagram/WhatsApp: live = วิ่งจากขวาไปซ้ายตามเสียงสด, ไม่ใช่ live = คลื่นที่บันทึกไว้ + ไฮไลต์ตามความคืบหน้าการเล่น */
@Composable
private fun WaveBars(levels: List<Float>, progress: Float, on: Color, off: Color, modifier: Modifier, live: Boolean = false) {
    Canvas(modifier) {
        val bw = 3.dp.toPx(); val gp = 2.dp.toPx(); val step = bw + gp
        val n = (size.width / step).toInt().coerceAtLeast(1)
        val vals = if (live) List(maxOf(0, n - levels.size)) { 0.05f } + levels.takeLast(n) else resampleLevels(levels, n)
        val x0 = (size.width - n * step + gp) / 2
        for (i in 0 until n) {
            val v = vals[i].coerceIn(0.05f, 1f)
            val h = maxOf(4.dp.toPx(), v * size.height)
            val color = if (live) on.copy(alpha = 0.45f + 0.55f * v) else if ((i + 0.5f) / n <= progress) on else off
            drawRoundRect(color, Offset(x0 + i * step, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}
