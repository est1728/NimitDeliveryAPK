package com.nimit.delivery

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class NimitApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(200L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        // ใช้ Firebase project เดียวกับเว็บ (nimit-delivery) -> ข้อมูลชุดเดียวกัน
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey("AIzaSyCnBUk0ZKFcwMK0NyYkheux1xPt9bLYhr4")
                    .setApplicationId("1:233476256130:web:62ba8f64ad0bf2f92c9f9b")
                    .setProjectId("nimit-delivery")
                    .setStorageBucket("nimit-delivery.firebasestorage.app")
                    .setGcmSenderId("233476256130")
                    .build()
            )
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("orders", "สถานะออเดอร์", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
