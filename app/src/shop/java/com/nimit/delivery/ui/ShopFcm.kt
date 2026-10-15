package com.nimit.delivery.ui

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.nimit.delivery.data.ShopSession

/** โทเคน FCM ของร้าน: เอกสาร fcmTokens/{shopId}_shop (เครื่องเดียวต่อร้าน เครื่องล่าสุดที่ล็อกอินจะเป็นเจ้าของ) */
object ShopFcm {
    private const val TAG = "ShopFcm"

    fun save(shopId: String, token: String) {
        if (shopId.isEmpty() || token.isEmpty()) return
        val m = HashMap<String, Any?>()
        m["token"] = token; m["role"] = "shop"; m["platform"] = "android"; m["updatedAt"] = FieldValue.serverTimestamp()
        FirebaseFirestore.getInstance().collection("fcmTokens").document(shopId + "_shop").set(m)
            .addOnFailureListener { e -> Log.w(TAG, "save token failed: " + e.message) }
    }

    fun register(shopId: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { t: String? -> if (t != null) save(shopId, t) }
                .addOnFailureListener { e -> Log.w(TAG, "get token failed: " + e.message) }
        } catch (e: Exception) { Log.w(TAG, "register failed: " + e.message) }
    }

    /** ออกจากระบบ: ลบโทเคนออก (เฉพาะถ้าเป็นของเครื่องนี้) เพื่อไม่ให้เครื่องนี้ได้รับออเดอร์ของร้านอีก */
    fun unregister(shopId: String) {
        if (shopId.isEmpty()) return
        try {
            val ref = FirebaseFirestore.getInstance().collection("fcmTokens").document(shopId + "_shop")
            FirebaseMessaging.getInstance().token.addOnSuccessListener { mine: String? ->
                ref.get().addOnSuccessListener { d ->
                    if (d.exists() && d.getString("token") == mine) ref.delete()
                }
            }
        } catch (e: Exception) { Log.w(TAG, "unregister failed: " + e.message) }
    }
}

/**
 * รับข้อความ FCM แบบ data-only: { type: "new_order", orderId, title, body }
 * ปลุกบริการเบื้องหน้าให้ฟังออเดอร์และดังเสียงวนซ้ำ (ถ้าบริการทำงานอยู่แล้ว ตัวฟัง Firestore จะดังเองและกันซ้ำด้วยรหัสออเดอร์)
 * หมายเหตุ: ถ้าเซิร์ฟเวอร์ส่ง notification block มาด้วย ระบบจะแสดงเองตอนแอปปิดและไม่เรียกฟังก์ชันนี้
 */
class ShopMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val id: String = ShopSession(this).shopId ?: return
        ShopFcm.save(id, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (ShopSession(this).shopId.isNullOrEmpty()) return
        if (message.data["type"] == "new_order") ShopAlertService.start(this, ring = true)
    }
}
