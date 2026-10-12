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
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nimit.delivery.MainActivity
import com.nimit.delivery.R
import com.nimit.delivery.data.ShopOrd
import com.nimit.delivery.data.ShopSession
import com.nimit.delivery.data.ShopStore
import com.nimit.delivery.data.shopNum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** เสียงเตือนออเดอร์ใหม่: สังเคราะห์เสียงระฆังเอง เล่นช่องสัญญาณ "นาฬิกาปลุก" (ดังแม้ปิดเสียงเรียกเข้า) ความดังตามที่ตั้งในแอพ ไม่ขึ้นกับปุ่มเร่งเสียงเครื่อง */
object ShopAlert {
    private const val SR = 22050
    private val main = Handler(Looper.getMainLooper())
    private var loop: AudioTrack? = null
    private var test: AudioTrack? = null

    /** ออเดอร์ใหม่ที่ยังไม่ได้เปิดดูรายละเอียด (ยังมีรายการในนี้ = เสียงยังดังวน) */
    val unacked: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

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

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("nimit_shop", Context.MODE_PRIVATE)
    fun volume(ctx: Context): Int = prefs(ctx).getInt("alertVol", 80)
    fun setVolume(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt("alertVol", v.coerceIn(0, 100)).apply()
        val v2: Float = v.coerceIn(0, 100) / 100f
        try { loop?.setVolume(v2) } catch (e: Exception) { }
        try { mpLoop?.setVolume(v2, v2) } catch (e: Exception) { }
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

    private fun startSynth(ctx: Context) {
        if (loop != null) return
        try {
            boost(ctx)
            val t: AudioTrack = track(volume(ctx))
            t.setLoopPoints(0, pcm.size, -1)
            t.play()
            loop = t
        } catch (e: Exception) { loop = null }
    }

    private fun stopSynth() {
        val t: AudioTrack? = loop
        loop = null
        if (t != null) { try { t.stop() } catch (e: Exception) { }; try { t.release() } catch (e: Exception) { } }
    }

    /** เล่นทดลอง 1 รอบด้วยความดังที่ตั้งไว้ */
    private fun testSynth(ctx: Context) {
        try {
            val old: AudioTrack? = test
            test = null
            if (old != null) { try { old.stop() } catch (e: Exception) { }; try { old.release() } catch (e: Exception) { } }
            boost(ctx)
            val t: AudioTrack = track(volume(ctx))
            t.play()
            test = t
            main.postDelayed({
                if (test === t) { test = null; try { t.stop() } catch (e: Exception) { }; try { t.release() } catch (e: Exception) { } }
            }, (pcm.size * 1000L / SR) + 200)
        } catch (e: Exception) { }
    }

    // ---------- เสียงจากเครื่อง (เลือกได้จากรายการเสียงของมือถือ) ----------
    private var looping = false
    private var mpLoop: MediaPlayer? = null
    private var mpTest: MediaPlayer? = null
    private var nextRun: Runnable? = null
    const val NIMIT = "nimit"

    /** "" หรือ "nimit" = เสียง Nimit (ไฟล์ในแอพ ค่าเริ่มต้น), "system" = เสียงแจ้งเตือนเริ่มต้นของเครื่อง, อื่นๆ = uri ที่เลือก */
    fun soundPref(ctx: Context): String = prefs(ctx).getString("alertUri", "") ?: ""
    fun setSoundPref(ctx: Context, v: String) { prefs(ctx).edit().putString("alertUri", v).apply() }
    fun isNimitSound(ctx: Context): Boolean { val v: String = soundPref(ctx); return v.isEmpty() || v == NIMIT }
    private fun realUri(ctx: Context): Uri? {
        val v: String = soundPref(ctx)
        if (v.isEmpty() || v == NIMIT) return Uri.parse("android.resource://" + ctx.applicationContext.packageName + "/" + R.raw.nimit_order_chime)
        return if (v == "system") RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(v)
    }
    fun soundTitle(ctx: Context): String {
        val v: String = soundPref(ctx)
        if (v.isEmpty() || v == NIMIT) return "เสียง Nimit (ค่าเริ่มต้น)"
        if (v == "system") return "เสียงแจ้งเตือนเริ่มต้นของเครื่อง"
        return try { RingtoneManager.getRingtone(ctx.applicationContext, Uri.parse(v))?.getTitle(ctx) ?: "เสียงที่เลือก" } catch (e: Exception) { "เสียงที่เลือก" }
    }

    private fun player(ctx: Context, u: Uri): MediaPlayer? {
        return try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            p.setDataSource(ctx.applicationContext, u)
            val v: Float = volume(ctx) / 100f
            p.setVolume(v, v)
            p.prepare()
            p
        } catch (e: Exception) { null }
    }

    private fun release(p: MediaPlayer?) { if (p != null) { try { p.stop() } catch (e: Exception) { }; try { p.release() } catch (e: Exception) { } } }

    private fun playNext(ctx: Context, u: Uri) {
        if (!looping) return
        val p: MediaPlayer? = player(ctx, u)
        if (p == null) { startSynth(ctx); return }   // เปิดไฟล์เสียงไม่ได้ ใช้เสียงมาตรฐานแทน
        mpLoop = p
        p.setOnCompletionListener { m: MediaPlayer ->
            if (mpLoop === m) mpLoop = null
            release(m)
            val r = Runnable { playNext(ctx, u) }
            nextRun = r
            main.postDelayed(r, if (u.scheme == "android.resource") 700L else 1200L)   // เว้นช่วงสั้นๆ ระหว่างรอบ เสียงไม่ซ้อนกัน
        }
        p.start()
    }

    /** เริ่มเสียงเตือนวน (ถ้าเริ่มอยู่แล้วไม่เริ่มซ้ำ จึงไม่มีเสียงซ้อนกัน) */
    fun startLoop(ctx: Context) {
        if (looping) return
        looping = true
        boost(ctx)
        val u: Uri? = realUri(ctx)
        if (u == null) startSynth(ctx) else playNext(ctx.applicationContext, u)
    }

    fun stopLoop() {
        looping = false
        val r: Runnable? = nextRun
        if (r != null) main.removeCallbacks(r)
        nextRun = null
        val p: MediaPlayer? = mpLoop
        mpLoop = null
        release(p)
        stopSynth()
    }

    /** เล่นทดลอง 1 รอบด้วยเสียงและความดังที่ตั้งไว้ */
    fun test(ctx: Context) {
        val old: MediaPlayer? = mpTest
        mpTest = null
        release(old)
        val u: Uri? = realUri(ctx)
        if (u == null) { testSynth(ctx); return }
        boost(ctx)
        val p: MediaPlayer? = player(ctx, u)
        if (p == null) { testSynth(ctx); return }
        mpTest = p
        p.setOnCompletionListener { m: MediaPlayer -> if (mpTest === m) mpTest = null; release(m) }
        p.start()
        main.postDelayed({ if (mpTest === p) { mpTest = null; release(p) } }, 6000)
    }

    // เข้าหน้ารายละเอียดออเดอร์ = รับทราบ (เสียงหยุด) ถ้ามีออเดอร์ใหม่เข้าขณะอยู่หน้านี้ เสียงจะดังจนกว่าจะย้อนกลับ
    fun detailOpened() { unacked.clear() }
    fun detailClosed() { unacked.clear() }
}

/** บริการเบื้องหน้า: ฟังออเดอร์ของร้านตลอดแม้ปิดหน้าจอ/ออกจากแอพ แล้วเตือนด้วยเสียงวนซ้ำ + แจ้งเตือนข้อความ */
class ShopAlertService : Service() {
    private var store: ShopStore? = null
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        val n = NotificationCompat.Builder(this, "shop_service")
            .setSmallIcon(R.drawable.ic_shop_notify).setContentTitle("Nimit Shop").setContentText("กำลังรอรับออเดอร์ใหม่")
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(this, 1)).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7001, n)
        } catch (e: Exception) { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id: String = ShopSession(this).shopId ?: ""
        if (id.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        if (store?.shopId != id) {
            store?.stop()
            scope?.cancel()
            ShopAlert.unacked.clear()
            val s = ShopStore(id)
            store = s
            s.onNewOrder = { o: ShopOrd ->
                if (o.status == "pending") {
                    ShopAlert.unacked.add(o.id)
                    notifyOrder(o)
                }
            }
            s.start()
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope = sc
            sc.launch {
                while (isActive) {
                    val pend: Set<String> = s.orders.value.filter { it.status == "pending" }.map { it.id }.toSet()
                    ShopAlert.unacked.retainAll(pend)
                    if (ShopAlert.unacked.isEmpty()) ShopAlert.stopLoop() else ShopAlert.startLoop(this@ShopAlertService)
                    delay(700)
                }
            }
        }
        return START_STICKY
    }

    private fun notifyOrder(o: ShopOrd) {
        val n = NotificationCompat.Builder(this, "shop_order_alert")
            .setSmallIcon(R.drawable.ic_shop_notify).setColor(0xFF0C4AA6.toInt())
            .setContentTitle("🔔 ออเดอร์ใหม่!").setContentText(o.orderId + " · ฿" + shopNum(o.n("grandTotal")))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 400, 200, 400)).setAutoCancel(true)
            .setContentIntent(openApp(this, 2)).build()
        try { NotificationManagerCompat.from(this).notify(o.id.hashCode(), n) } catch (e: SecurityException) { }
    }

    override fun onDestroy() {
        store?.stop(); store = null
        scope?.cancel(); scope = null
        ShopAlert.stopLoop()
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
            nm.createNotificationChannel(NotificationChannel("shop_service", "สถานะการรับออเดอร์", NotificationManager.IMPORTANCE_MIN))
            val ch = NotificationChannel("shop_order_alert", "ออเดอร์ใหม่", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)   // เสียงดังมาจากตัวเตือนวนซ้ำของแอพ ไม่ใช่เสียงแจ้งเตือนของระบบ
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }

        fun start(ctx: Context) {
            try { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, ShopAlertService::class.java)) } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ShopAlertService::class.java)) } catch (e: Exception) { }
        }
    }
}

private const val P_SPK = "M3 9v6h4l5 5V4L7 9H3z"
private const val P_WAVE1 = "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"
private const val P_WAVE2 = "M14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"

/** แถบเลื่อนปรับความดังเสียงแจ้งเตือนออเดอร์ (ใน sidebar) พร้อมเสียงทดลองตอนปล่อยนิ้ว */
@Composable
fun ShopVolumeControl() {
    val ctx = LocalContext.current
    var vol by remember { mutableFloatStateOf(ShopAlert.volume(ctx).toFloat()) }
    var playing by remember { mutableStateOf(false) }
    val w1: Float by animateFloatAsState(if (vol > 0f) 1f else 0.18f, tween(180), label = "w1")
    val w2: Float by animateFloatAsState(if (vol > 55f) 1f else 0.18f, tween(180), label = "w2")
    val pulse: Float by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.22f, infiniteRepeatable(tween(320), RepeatMode.Reverse), label = "pl")
    LaunchedEffect(playing) { if (playing) { kotlinx.coroutines.delay(1900); playing = false } }
    fun runTest() { ShopAlert.test(ctx); playing = true }
    var soundName by remember { mutableStateOf(ShopAlert.soundTitle(ctx)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d: Intent? = r.data
        if (r.resultCode == android.app.Activity.RESULT_OK && d != null) {
            val u: Uri? = if (Build.VERSION.SDK_INT >= 33) d.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else @Suppress("DEPRECATION") d.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ShopAlert.setSoundPref(ctx, if (u == null) ShopAlert.NIMIT else if (u == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)) "system" else u.toString())
            soundName = ShopAlert.soundTitle(ctx)
            runTest()
        }
    }

    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).scale(if (playing) pulse else 1f), contentAlignment = Alignment.Center) {
                PathIcon(P_SPK, C.Subtext, 24.dp)
                Box(Modifier.size(24.dp).background(Color.Transparent)) { PathIcon(P_WAVE1, C.Primary.copy(alpha = w1), 24.dp) }
                Box(Modifier.size(24.dp).background(Color.Transparent)) { PathIcon(P_WAVE2, C.Primary.copy(alpha = w2), 24.dp) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("เสียงแจ้งเตือนออเดอร์", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Text("ไม่ขึ้นกับปุ่มเร่งเสียงของเครื่อง", fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(top = 2.dp))
            }
            Text(vol.toInt().toString() + "%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
        }
        Slider(
            value = vol, onValueChange = { vol = it; ShopAlert.setVolume(ctx, it.toInt()) },
            onValueChangeFinished = { runTest() }, valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = C.Primary, activeTrackColor = C.Primary, inactiveTrackColor = Color(0xFFE2E8F0))
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight).clickable { runTest() }, contentAlignment = Alignment.Center) {
                Text(if (playing) "กำลังเล่น..." else "ทดลองเสียง", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
            }
            Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Primary).clickable {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "เลือกเสียงเตือนออเดอร์")
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                val cur: String = ShopAlert.soundPref(ctx)
                if (cur.isNotEmpty() && cur != ShopAlert.NIMIT && cur != "system") i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur))
                picker.launch(i)
            }, contentAlignment = Alignment.Center) {
                Text("เลือกเสียง", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("เสียงที่ใช้: " + soundName, fontSize = 12.sp, color = C.Subtext, maxLines = 1, modifier = Modifier.weight(1f))
            if (!ShopAlert.isNimitSound(ctx)) Text("ใช้เสียง Nimit", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.clickable { ShopAlert.setSoundPref(ctx, ShopAlert.NIMIT); soundName = ShopAlert.soundTitle(ctx); runTest() }.padding(start = 8.dp))
        }
    }
}
