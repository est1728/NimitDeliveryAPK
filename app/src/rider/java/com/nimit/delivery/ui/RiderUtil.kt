package com.nimit.delivery.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nimit.delivery.data.RiderApi
import java.util.Calendar
import java.util.Date

val TH_MONTHS: List<String> = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
val TH_MONTHS_FULL: List<String> = listOf("มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม")

fun sameDay(d: Date?, y: Int, m: Int, day: Int): Boolean {
    if (d == null) return false
    val c = Calendar.getInstance()
    c.time = d
    return c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == day
}

fun inMonth(d: Date?, y: Int, m: Int): Boolean {
    if (d == null) return false
    val c = Calendar.getInstance()
    c.time = d
    return c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
}

object RiderSound {
    /** เสียงเตือน + แจ้งเตือนในเครื่อง ตอนมีงานใหม่ (ใช้ได้ตอนแอปเปิดอยู่ ส่วน push ตอนปิดแอปทำในเฟส 5) */
    fun alert(ctx: Context, title: String, body: String) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 1500)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 2500L)
        } catch (e: Exception) { }
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
            ) return
            val launch: Intent? = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi: PendingIntent? = if (launch != null) PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) else null
            val n = NotificationCompat.Builder(ctx, "orders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000L).toInt(), n)
        } catch (e: Exception) { }
    }
}

private fun hasLocPerm(ctx: Context): Boolean {
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/** ขอสิทธิ์ตำแหน่ง/แจ้งเตือน แล้วส่งตำแหน่งไรเดอร์ขึ้น Firestore ทุก ~15 วินาที (เหมือนเว็บเดิม) */
@Composable
fun LocationSharing(riderId: String) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _: Map<String, Boolean> ->
        granted = hasLocPerm(ctx)
    }
    LaunchedEffect(Unit) {
        val req = ArrayList<String>()
        req.add(Manifest.permission.ACCESS_FINE_LOCATION)
        req.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) req.add("android.permission.POST_NOTIFICATIONS")
        launcher.launch(req.toTypedArray())
    }
    DisposableEffect(granted, riderId) {
        if (!granted) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val last = LongArray(1)
            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    val l = r.lastLocation ?: return
                    val now: Long = System.currentTimeMillis()
                    if (now - last[0] < 15000L) return
                    last[0] = now
                    RiderApi.writeLocation(riderId, l.latitude, l.longitude)
                }
            }
            try {
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
                client.requestLocationUpdates(req, cb, Looper.getMainLooper())
            } catch (e: SecurityException) { }
            onDispose { client.removeLocationUpdates(cb) }
        }
    }
}
