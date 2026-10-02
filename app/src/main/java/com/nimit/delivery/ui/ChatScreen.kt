package com.nimit.delivery.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class ChatMsg(val id: String, val sender: String, val senderName: String, val type: String, val content: String, val time: Date?)

private val CHAT_ROLES: List<String> = listOf("customer", "rider", "admin")

private fun chatRoleLabel(r: String): String = when (r) {
    "admin" -> "แอดมิน"
    "rider" -> "ไรเดอร์"
    else -> "ลูกค้า"
}

private fun chatDay(d: Date): String = SimpleDateFormat("d MMM yy", Locale("th", "TH")).format(d)
private fun chatTime(d: Date): String = SimpleDateFormat("HH:mm", Locale("th", "TH")).format(d)

private fun chatDecodeImg(s: String): Bitmap? {
    return try {
        if (!s.startsWith("data:")) return null
        val bytes: ByteArray = Base64.decode(s.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

private fun chatEncodeImg(ctx: Context, uri: Uri): String? {
    return try {
        val opt = BitmapFactory.Options()
        opt.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
        var sample = 1
        while (opt.outWidth / sample > 1200 || opt.outHeight / sample > 1200) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        val bmp: Bitmap = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val big: Int = if (bmp.width > bmp.height) bmp.width else bmp.height
        val sc: Float = if (big > 600) 600f / big.toFloat() else 1f
        val w: Int = (bmp.width * sc).toInt().coerceAtLeast(1)
        val h: Int = (bmp.height * sc).toInt().coerceAtLeast(1)
        val out: Bitmap = Bitmap.createScaledBitmap(bmp, w, h, true)
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 70, bos)
        val s: String = "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        if (s.length > 900000) null else s
    } catch (e: Exception) {
        null
    }
}

private class ChatPlayer {
    var playingId: String? by mutableStateOf<String?>(null)
    private var mp: MediaPlayer? = null

    fun stop() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        playingId = null
    }

    fun playFile(f: File, id: String) {
        if (playingId == id) { stop(); return }
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            mp = p
            playingId = id
        } catch (e: Exception) {
            stop()
        }
    }

    fun playData(ctx: Context, id: String, dataUri: String) {
        if (playingId == id) { stop(); return }
        try {
            val f = File(ctx.cacheDir, "chat_play_" + Math.abs(id.hashCode()) + ".m4a")
            f.writeBytes(Base64.decode(dataUri.substringAfter(","), Base64.DEFAULT))
            playFile(f, id)
        } catch (e: Exception) {
            stop()
        }
    }
}

private fun chatMarkRead(db: FirebaseFirestore, orderId: String, role: String) {
    val m = HashMap<String, Any>()
    m["unread_" + role] = 0
    m[role + "ReadAt"] = FieldValue.serverTimestamp()
    db.collection("chats").document(orderId).set(m, SetOptions.merge())
}

private fun chatPostJson(urlStr: String, body: JSONObject) {
    try {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        c.responseCode
        c.disconnect()
    } catch (e: Exception) { }
}

private suspend fun chatNotify(db: FirebaseFirestore, orderId: String, role: String, preview: String) {
    try {
        val o: DocumentSnapshot = db.collection("orders").document(orderId).get().await()
        if (!o.exists()) return
        val title: String = "💬 ข้อความใหม่: " + chatRoleLabel(role)
        val api = "https://nimitdelivery.vercel.app/api/send-order-notification"
        if (role == "customer") {
            val tokens = JSONArray()
            val admins = db.collection("fcmTokens").whereEqualTo("role", "admin").get().await()
            for (d in admins.documents) { val t: String? = d.getString("token"); if (!t.isNullOrEmpty()) tokens.put(t) }
            val rid: String = o.getString("riderId") ?: ""
            if (rid.isNotEmpty()) {
                val rs: DocumentSnapshot = db.collection("fcmTokens").document(rid + "_rider").get().await()
                val t: String? = rs.getString("token")
                if (!t.isNullOrEmpty()) tokens.put(t)
            }
            if (tokens.length() == 0) return
            val j = JSONObject()
            j.put("tokens", tokens); j.put("title", title); j.put("body", preview)
            j.put("url", "/chat.html?orderId=" + orderId + "&role=rider")
            withContext(Dispatchers.IO) { chatPostJson(api, j) }
        } else {
            val cust = o.get("customer") as? Map<*, *>
            val phone: String = cust?.get("phone")?.toString() ?: ""
            if (phone.isEmpty()) return
            val cs: DocumentSnapshot = db.collection("fcmTokens").document(phone + "_customer").get().await()
            val t: String = cs.getString("token") ?: return
            val j = JSONObject()
            j.put("token", t); j.put("title", title); j.put("body", preview)
            j.put("url", "/chat.html?orderId=" + orderId + "&role=customer")
            withContext(Dispatchers.IO) { chatPostJson(api, j) }
        }
    } catch (e: Exception) { }
}

@Composable
fun ChatScreen(orderId: String, role: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db: FirebaseFirestore = remember { FirebaseFirestore.getInstance() }
    var msgs by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
    var readAt by remember { mutableStateOf<Map<String, Date>>(emptyMap()) }
    var title by remember { mutableStateOf("แชท") }
    var sub by remember { mutableStateOf(chatRoleLabel(role)) }
    var text by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<File?>(null) }
    var pendingSecs by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableStateOf(0) }
    var viewImg by remember { mutableStateOf<Bitmap?>(null) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val recorder = remember { arrayOfNulls<MediaRecorder>(1) }
    val recFile = remember { arrayOfNulls<File>(1) }
    val player = remember { ChatPlayer() }
    val listState = rememberLazyListState()

    fun toast(s: String) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() }

    DisposableEffect(orderId) {
        val r1 = db.collection("chats").document(orderId).addSnapshotListener { s, _ ->
            val d: Map<String, Any>? = s?.data
            if (d != null) {
                val m = HashMap<String, Date>()
                for (r in CHAT_ROLES) {
                    if (r != role) {
                        val t: Timestamp? = d[r + "ReadAt"] as? Timestamp
                        if (t != null) m[r] = t.toDate()
                    }
                }
                readAt = m
            }
        }
        val r2 = db.collection("chats").document(orderId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING).limit(200)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    msgs = snap.documents.map { x: DocumentSnapshot ->
                        ChatMsg(x.id, x.getString("sender") ?: "", x.getString("senderName") ?: "", x.getString("type") ?: "text", x.getString("content") ?: "", x.getTimestamp("timestamp")?.toDate())
                    }
                    chatMarkRead(db, orderId, role)
                }
            }
        onDispose { r1.remove(); r2.remove(); player.stop() }
    }

    LaunchedEffect(orderId) {
        chatMarkRead(db, orderId, role)
        try {
            val o: DocumentSnapshot = db.collection("orders").document(orderId).get().await()
            if (o.exists()) {
                title = "แชท " + (o.getString("orderId") ?: orderId)
                val cust = o.get("customer") as? Map<*, *>
                val cn: String = cust?.get("name")?.toString() ?: cust?.get("phone")?.toString() ?: ""
                sub = (o.getString("shopName") ?: "") + " · " + cn
            }
        } catch (e: Exception) { }
    }

    LaunchedEffect(recording) {
        while (recording) { delay(1000L); recSecs += 1 }
    }

    val rows: List<Any> = remember(msgs) {
        val l = ArrayList<Any>()
        var last = ""
        for (m in msgs) {
            val ds: String = chatDay(m.time ?: Date())
            if (ds != last) { last = ds; l.add(ds) }
            l.add(m)
        }
        l
    }
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.scrollToItem(rows.size - 1) }

    fun send(type: String, content: String) {
        if (orderId.isEmpty()) { toast("ไม่พบรหัสออเดอร์"); return }
        scope.launch {
            try {
                val m = HashMap<String, Any>()
                m["sender"] = role
                m["senderName"] = chatRoleLabel(role)
                m["type"] = type
                m["content"] = content
                m["timestamp"] = FieldValue.serverTimestamp()
                m["readBy"] = listOf(role)
                db.collection("chats").document(orderId).collection("messages").add(m).await()
                val meta = HashMap<String, Any>()
                meta["lastMessage"] = if (type == "text") content else "[" + type + "]"
                meta["lastTime"] = FieldValue.serverTimestamp()
                for (r in CHAT_ROLES) { if (r != role) meta["unread_" + r] = FieldValue.increment(1L) }
                db.collection("chats").document(orderId).set(meta, SetOptions.merge()).await()
                chatNotify(db, orderId, role, if (type == "text") content else if (type == "image") "ส่งรูปภาพ" else "ส่งเสียง")
            } catch (e: Exception) {
                toast("ส่งไม่สำเร็จ")
            }
        }
    }

    fun sendAll() {
        val img: String? = pendingImage
        val au: File? = pendingAudio
        if (img != null) {
            pendingImage = null
            send("image", img)
        } else if (au != null) {
            pendingAudio = null
            player.stop()
            scope.launch {
                val b64: String = withContext(Dispatchers.IO) { Base64.encodeToString(au.readBytes(), Base64.NO_WRAP) }
                val uri: String = "data:audio/mp4;base64," + b64
                if (uri.length > 900000) toast("ไฟล์เสียงใหญ่เกินไป") else send("audio", uri)
            }
        }
        val t: String = text.trim()
        if (t.isNotEmpty()) { text = ""; send("text", t) }
    }

    fun startRec() {
        try {
            val f = File(ctx.cacheDir, "rec_" + System.currentTimeMillis() + ".m4a")
            val r: MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder[0] = r
            recFile[0] = f
            recSecs = 0
            recording = true
        } catch (e: Exception) {
            toast("เปิดไมค์ไม่ได้")
        }
    }

    fun stopRec(keep: Boolean) {
        val r: MediaRecorder? = recorder[0]
        val f: File? = recFile[0]
        recording = false
        recorder[0] = null
        var ok = false
        if (r != null) {
            try { r.stop(); ok = true } catch (e: Exception) { }
            try { r.release() } catch (e: Exception) { }
        }
        if (keep && ok && f != null && recSecs >= 1) {
            pendingAudio = f
            pendingSecs = recSecs
        } else {
            f?.delete()
        }
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok: Boolean ->
        if (ok) startRec() else toast("ต้องอนุญาตการใช้ไมค์ก่อน")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val s: String? = chatEncodeImg(ctx, uri)
            if (s == null) toast("รูปใหญ่เกินไปหรืออ่านไม่ได้") else pendingImage = s
        }
    }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val u: Uri? = camUri
        if (ok && u != null) {
            val s: String? = chatEncodeImg(ctx, u)
            if (s == null) toast("รูปใหญ่เกินไปหรืออ่านไม่ได้") else pendingImage = s
        }
        if (u != null) { try { ctx.contentResolver.delete(u, null, null) } catch (e: Exception) { } }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF1F5F9))) {
        TopBar(title, onBack)
        Text(sub, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.fillMaxWidth().background(Color.White).padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (rows.isEmpty()) {
                item { Text("ยังไม่มีข้อความ เริ่มการสนทนาได้เลย", color = C.Subtext, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            }
            items(rows) { row: Any ->
                if (row is String) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(row, fontSize = 11.sp, color = C.Subtext, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFE2E8F0)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                } else if (row is ChatMsg) {
                    ChatRow(row, role, readAt, player, ctx) { b: Bitmap -> viewImg = b }
                }
            }
        }
        val pi: String? = pendingImage
        val pa: File? = pendingAudio
        if (pi != null || pa != null) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pi != null) {
                    val b: Bitmap? = remember(pi) { chatDecodeImg(pi) }
                    if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text("พร้อมส่งรูปภาพ", fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
                } else if (pa != null) {
                    Text(if (player.playingId == "preview") "⏸" else "▶", fontSize = 22.sp, modifier = Modifier.clickable { player.playFile(pa, "preview") }.padding(6.dp))
                    Text("ข้อความเสียง " + (pendingSecs / 60) + ":" + (if (pendingSecs % 60 < 10) "0" else "") + (pendingSecs % 60), fontSize = 13.sp, color = C.Text, modifier = Modifier.weight(1f))
                }
                Text("✕", fontSize = 18.sp, color = C.Subtext, modifier = Modifier.clickable { pendingImage = null; pendingAudio = null; player.stop() }.padding(10.dp))
                Text("ส่ง", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Primary).clickable { sendAll() }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        if (recording) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFFEE2E2)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                Spacer(Modifier.width(8.dp))
                Text("กำลังอัดเสียง " + (recSecs / 60) + ":" + (if (recSecs % 60 < 10) "0" else "") + (recSecs % 60), fontSize = 14.sp, color = C.Text, modifier = Modifier.weight(1f))
                Text("ยกเลิก", fontSize = 13.sp, color = C.Subtext, modifier = Modifier.clickable { stopRec(false) }.padding(10.dp))
                Text("หยุด", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFEF4444)).clickable { stopRec(true) }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📷", fontSize = 22.sp, modifier = Modifier.clickable {
                    try {
                        val v = ContentValues()
                        v.put(MediaStore.Images.Media.DISPLAY_NAME, "chat_" + System.currentTimeMillis() + ".jpg")
                        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        val u: Uri? = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                        if (u != null) { camUri = u; takePic.launch(u) }
                    } catch (e: Exception) { toast("เปิดกล้องไม่ได้") }
                }.padding(6.dp))
                Text("🖼", fontSize = 22.sp, modifier = Modifier.clickable { pickImage.launch("image/*") }.padding(6.dp))
                Text("🎤", fontSize = 22.sp, modifier = Modifier.clickable {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                    else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                }.padding(6.dp))
                NimitInput(value = text, onChange = { s: String -> text = s }, placeholder = "พิมพ์ข้อความ...", height = 44.dp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.Primary).clickable { sendAll() }, contentAlignment = Alignment.Center) {
                    Text("➤", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }

    val vi: Bitmap? = viewImg
    if (vi != null) {
        Dialog(onDismissRequest = { viewImg = null }) {
            Image(bitmap = vi.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().clickable { viewImg = null })
        }
    }
}

@Composable
private fun ChatRow(m: ChatMsg, role: String, readAt: Map<String, Date>, player: ChatPlayer, ctx: Context, onImage: (Bitmap) -> Unit) {
    val isMe: Boolean = m.sender == role
    val ts: Date = m.time ?: Date()
    val bg: Color = if (isMe) C.Primary else if (m.sender == "admin") Color(0xFFFEF3C7) else Color.White
    val fg: Color = if (isMe) Color.White else C.Text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            if (!isMe) Text(if (m.senderName.isNotEmpty()) m.senderName else chatRoleLabel(m.sender), fontSize = 11.sp, color = C.Subtext)
            Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(10.dp)) {
                if (m.type == "image") {
                    val b: Bitmap? = remember(m.id) { chatDecodeImg(m.content) }
                    if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).clickable { onImage(b) })
                    else Text("[รูปภาพ]", color = fg, fontSize = 14.sp)
                } else if (m.type == "audio") {
                    val playing: Boolean = player.playingId == m.id
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { player.playData(ctx, m.id, m.content) }) {
                        Text(if (playing) "⏸" else "▶", color = fg, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("ข้อความเสียง", color = fg, fontSize = 14.sp)
                    }
                } else {
                    Text(m.content, color = fg, fontSize = 15.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMe) {
                    var anyRead = false
                    for (r in CHAT_ROLES) { if (r != role) { val t: Date? = readAt[r]; if (t != null && !t.before(ts)) anyRead = true } }
                    Text(if (anyRead) "อ่านแล้ว" else "ส่งแล้ว", fontSize = 10.sp, color = if (anyRead) Color(0xFF16A34A) else C.Subtext)
                    Spacer(Modifier.width(6.dp))
                }
                Text(chatTime(ts), fontSize = 10.sp, color = C.Subtext)
            }
        }
    }
}
