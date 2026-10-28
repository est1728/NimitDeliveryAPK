package com.nimit.delivery.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nimit.delivery.MainActivity
import com.nimit.delivery.R
import com.nimit.delivery.data.AdminOrd
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminRepo
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** เสียงเตือนออเดอร์ใหม่: สังเคราะห์เสียงระฆังเอง เล่นช่องสัญญาณ "นาฬิกาปลุก" ดังแม้ปิดเสียงเครื่อง ความดังตั้งในแอป */
object AdminAlert {
    private const val SR = 22050
    private val main = Handler(Looper.getMainLooper())
    private var loop: AudioTrack? = null
    private var once: AudioTrack? = null
    private var test: AudioTrack? = null

    /** ออเดอร์ใหม่ที่ยังไม่ได้มอบหมายไรเดอร์ (ยังมีรายการในนี้ = เสียงยังต้องดัง) */
    val unacked: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    /** ออเดอร์ที่เสียงเตือนดังไปแล้ว (โหมดดังรอบเดียว) กันดังซ้ำออเดอร์เดิม */
    val rung: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    private val pcm: ShortArray by lazy { build() }

    private fun build(): ShortArray {
        val notes: List<Pair<Double, Double>> = listOf(988.0 to 0.22, 740.0 to 0.42, 0.0 to 0.10, 988.0 to 0.22, 740.0 to 0.42, 0.0 to 0.70)
        var total = 0
        for (n in notes) total += (n.second * SR).toInt()
        val out = ShortArray(total)
        var pos = 0
        for (n in notes) {
            val len: Int = (n.second * SR).toInt()
            for (i in 0 until len) {
                if (n.first > 0.0) {
                    val t: Double = i.toDouble() / SR
                    val env: Double = exp(-3.5 * t / n.second) * min(1.0, t / 0.004)
                    val v: Double = sin(2 * PI * n.first * t) * 0.62 + sin(2 * PI * n.first * 2 * t) * 0.24 + sin(2 * PI * n.first * 3 * t) * 0.10
                    out[pos + i] = (v * env * 0.9 * 32767).toInt().toShort()
                }
            }
            pos += len
        }
        return out
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE)

    /** true = เสียงวนซ้ำจนกว่าจะมอบหมายไรเดอร์, false (ค่าเริ่มต้น) = ดังรอบเดียวต่อออเดอร์ใหม่ */
    fun repeat(ctx: Context): Boolean = prefs(ctx).getBoolean("alertRepeat", false)
    fun setRepeat(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("alertRepeat", v).apply()
        if (!v) stopLoop()
    }

    fun volume(ctx: Context): Int = prefs(ctx).getInt("alertVol", 80)
    fun setVolume(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt("alertVol", v.coerceIn(0, 100)).apply()
        val v2: Float = v.coerceIn(0, 100) / 100f
        try { loop?.setVolume(v2) } catch (e: Exception) { }
    }

    private fun boost(ctx: Context) {
        try {
            val am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: Exception) { }
    }

    private fun track(vol: Int): AudioTrack {
        val t: AudioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setVolume(vol / 100f)
        return t
    }

    private fun free(t: AudioTrack?) {
        if (t != null) { try { t.stop() } catch (e: Exception) { }; try { t.release() } catch (e: Exception) { } }
    }

    /** เริ่มเสียงเตือนวน (ถ้าเริ่มอยู่แล้วไม่เริ่มซ้ำ จึงไม่มีเสียงซ้อนกัน) */
    fun startLoop(ctx: Context) {
        if (loop != null) return
        try {
            boost(ctx)
            val t: AudioTrack = track(volume(ctx))
            t.setLoopPoints(0, pcm.size, -1)
            t.play()
            loop = t
        } catch (e: Exception) { loop = null }
    }

    /** ดังรอบเดียว คืน true เมื่อเริ่มเล่น (ถ้ากำลังเล่นอยู่คืน false เพื่อให้ลองใหม่รอบถัดไป) */
    fun playOnce(ctx: Context): Boolean {
        if (loop != null || once != null) return false
        try {
            boost(ctx)
            val t: AudioTrack = track(volume(ctx))
            t.play()
            once = t
            main.postDelayed({
                if (once === t) { once = null; free(t) }
            }, (pcm.size * 1000L / SR) + 200)
        } catch (e: Exception) { once = null }
        return true
    }

    fun stopLoop() {
        val a: AudioTrack? = loop
        loop = null
        free(a)
        val b: AudioTrack? = once
        once = null
        free(b)
    }

    /** เล่นทดลอง 1 รอบด้วยความดังที่ตั้งไว้ */
    fun test(ctx: Context) {
        try {
            val old: AudioTrack? = test
            test = null
            free(old)
            boost(ctx)
            val t: AudioTrack = track(volume(ctx))
            t.play()
            test = t
            main.postDelayed({
                if (test === t) { test = null; free(t) }
            }, (pcm.size * 1000L / SR) + 200)
        } catch (e: Exception) { }
    }
}

/** บริการเบื้องหน้า: ฟังออเดอร์ pending ในเขตตลอดแม้ปิดหน้าจอ/ออกจากแอป แล้วเตือนด้วยเสียงและแจ้งเตือน */
class AdminAlertService : Service() {
    private var store: AdminOrdersStore? = null
    private var scope: CoroutineScope? = null
    private var startedFor: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        val n = NotificationCompat.Builder(this, "admin_service")
            .setSmallIcon(R.drawable.ic_admin_notify).setContentTitle("Nimit Admin").setContentText("กำลังรอรับออเดอร์ใหม่")
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(this, 1)).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7101, n)
        } catch (e: Exception) { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = AdminRepo.load(this)
        if (session == null) { stopSelf(); return START_NOT_STICKY }
        if (startedFor != session.franchiseId) {
            store?.stop()
            scope?.cancel()
            AdminAlert.unacked.clear()
            AdminAlert.rung.clear()
            startedFor = session.franchiseId
            val s = AdminOrdersStore(session)
            store = s
            // ถูกปลุกด้วย FCM: ออเดอร์ที่เข้าใน 15 นาทีล่าสุดต้องดังด้วย  ปกติ: เฉพาะออเดอร์ที่เข้าหลังบริการเริ่ม
            val ring: Boolean = intent?.getBooleanExtra("ring", false) == true
            val startAt: Long = System.currentTimeMillis()
            val window: Long = if (ring) 15 * 60_000L else 20_000L
            val known = HashSet<String>()
            s.start()
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope = sc
            sc.launch {
                while (isActive) {
                    val shops: Set<String> = s.shopIds.value
                    val pend: List<AdminOrd> = s.orders.value.filter { shops.contains(it.s("shopId")) && it.status == "pending" }
                    val ids: Set<String> = pend.map { it.id }.toSet()
                    for (o in pend) {
                        if (known.add(o.id)) {
                            val created: Long = o.created?.time ?: 0L
                            if (created >= startAt - window) {
                                AdminAlert.unacked.add(o.id)
                                notifyOrder(o)
                            }
                        }
                    }
                    AdminAlert.unacked.retainAll { it in ids || it.startsWith("test_") }
                    AdminAlert.rung.retainAll { it in AdminAlert.unacked }
                    if (AdminAlert.unacked.isEmpty()) AdminAlert.stopLoop()
                    else if (AdminAlert.repeat(this@AdminAlertService)) AdminAlert.startLoop(this@AdminAlertService)
                    else {
                        val fresh: List<String> = AdminAlert.unacked.toList().filter { it !in AdminAlert.rung }
                        if (fresh.isNotEmpty() && AdminAlert.playOnce(this@AdminAlertService)) AdminAlert.rung.addAll(fresh)
                    }
                    delay(700)
                }
            }
        }
        // ข้อความทดสอบ: ดังเหมือนออเดอร์จริงโดยไม่ต้องมีออเดอร์ใน Firestore แล้วหยุด
        val testId: String = intent?.getStringExtra("testId") ?: ""
        if (testId.isNotEmpty()) {
            AdminAlert.unacked.add(testId)
            val title: String = (intent?.getStringExtra("title") ?: "").ifEmpty { "🔔 ทดสอบแจ้งเตือน" }
            val body: String = (intent?.getStringExtra("body") ?: "").ifEmpty { "ข้อความทดสอบจากระบบผู้พัฒนา" }
            notifyPlain(this, testId.hashCode(), title, body)
            scope?.launch { delay(20_000); AdminAlert.unacked.remove(testId) }
        }
        return START_STICKY
    }

    private fun notifyOrder(o: AdminOrd) {
        val shop: String = o.s("shopName").ifEmpty { "ร้านค้า" }
        notifyPlain(this, o.id.hashCode(), "🔔 มีออเดอร์ใหม่เข้ามา", o.orderId + " จาก " + shop)
    }

    override fun onDestroy() {
        store?.stop(); store = null
        scope?.cancel(); scope = null
        AdminAlert.stopLoop()
        super.onDestroy()
    }

    companion object {
        fun openApp(ctx: Context, code: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("admin_service", "สถานะการรับออเดอร์", NotificationManager.IMPORTANCE_MIN))
            val ch = NotificationChannel("admin_order_alert", "ออเดอร์ใหม่/ยกเลิก", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)   // เสียงดังมาจากตัวเตือนของแอป ไม่ใช่เสียงแจ้งเตือนของระบบ
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }

        /** แจ้งเตือนทั่วไป (ความสำคัญสูง สั่น ไม่มีเสียงระบบ) */
        fun notifyPlain(ctx: Context, id: Int, title: String, body: String) {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, "admin_order_alert")
                .setSmallIcon(R.drawable.ic_admin_notify).setColor(0xFF0C4AA6.toInt())
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(longArrayOf(0, 400, 200, 400)).setAutoCancel(true)
                .setContentIntent(openApp(ctx, 2)).build()
            try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
        }

        /** ring = true: ถูกปลุกด้วยข้อความ FCM → ออเดอร์ pending ที่เพิ่งเข้า (ใน 15 นาที) ต้องดังด้วย */
        fun start(ctx: Context, ring: Boolean = false, testId: String = "", title: String = "", body: String = "") {
            try {
                val i = Intent(ctx, AdminAlertService::class.java).putExtra("ring", ring)
                if (testId.isNotEmpty()) i.putExtra("testId", testId).putExtra("title", title).putExtra("body", body)
                androidx.core.content.ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, AdminAlertService::class.java)) } catch (e: Exception) { }
        }
    }
}

/** หน้า "ตั้งค่า": ตัวตั้งค่าเสียงแจ้งเตือน */
@Composable
fun AdminSettingsPanel() {
    Column(Modifier.fillMaxSize().background(C.Gray).verticalScroll(rememberScrollState())) {
        Text("ตั้งค่า", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp))
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
            AdminVolumeControl()
        }
    }
}

/** แถบเลื่อนปรับความดังเสียงแจ้งเตือนออเดอร์ + เตือนซ้ำ + ทดลองเสียง */
@Composable
fun AdminVolumeControl() {
    val ctx = LocalContext.current
    var vol by remember { mutableFloatStateOf(AdminAlert.volume(ctx).toFloat()) }
    var playing by remember { mutableStateOf(false) }
    var rep by remember { mutableStateOf(AdminAlert.repeat(ctx)) }
    LaunchedEffect(playing) { if (playing) { delay(1900); playing = false } }
    fun runTest() { AdminAlert.test(ctx); playing = true }

    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("เสียงแจ้งเตือนออเดอร์", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Text("ไม่ขึ้นกับปุ่มเร่งเสียงของเครื่อง", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
            }
            Text(vol.toInt().toString() + "%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        }
        Slider(
            value = vol, onValueChange = { vol = it; AdminAlert.setVolume(ctx, it.toInt()) },
            onValueChangeFinished = { runTest() }, valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = C.Primary, activeTrackColor = C.Primary, inactiveTrackColor = Color(0xFFE2E8F0))
        )
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight).clickable { runTest() },
            contentAlignment = Alignment.Center
        ) { Text(if (playing) "กำลังเล่น..." else "ทดลองเสียง", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("เตือนซ้ำจนกว่าจะมอบหมายไรเดอร์", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Text(if (rep) "เสียงดังวนซ้ำ" else "เสียงดังรอบเดียวต่อออเดอร์ใหม่", fontSize = 12.sp, color = C.Subtext)
            }
            Switch(
                checked = rep, onCheckedChange = { v: Boolean -> rep = v; AdminAlert.setRepeat(ctx, v) },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Primary, checkedThumbColor = Color.White)
            )
        }
    }
}
