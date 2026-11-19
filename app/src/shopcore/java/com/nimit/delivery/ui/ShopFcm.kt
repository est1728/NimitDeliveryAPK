package com.nimit.delivery.ui

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.nimit.delivery.data.ShopSession

/**
 * โทเคน FCM ของร้าน: เอกสาร fcmTokens/{shopId}_shop
 *  - tokens: array ของโทเคนทุกเครื่องที่ล็อกอินร้านนี้อยู่ (เซิร์ฟเวอร์ส่งให้ทุกตัว)
 *  - token : โทเคนล่าสุด เก็บไว้ให้โค้ดเซิร์ฟเวอร์รุ่นเก่าอ่านได้
 */
object ShopFcm {
    private const val TAG = "ShopFcm"
    private fun ref(shopId: String) = FirebaseFirestore.getInstance().collection("fcmTokens").document(shopId + "_shop")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nimit_shop", Context.MODE_PRIVATE)

    fun save(ctx: Context, shopId: String, token: String) {
        if (shopId.isEmpty() || token.isEmpty()) return
        val p = prefs(ctx)
        val old: String = p.getString("fcmToken", "") ?: ""
        val m = HashMap<String, Any?>()
        m["token"] = token; m["tokens"] = FieldValue.arrayUnion(token)
        m["role"] = "shop"; m["platform"] = "android"; m["updatedAt"] = FieldValue.serverTimestamp()
        ref(shopId).set(m, SetOptions.merge())
            .addOnSuccessListener {
                p.edit().putString("fcmToken", token).apply()
                // โทเคนเดิมของเครื่องนี้ (ถูกหมุนเวียน) เอาออก ไม่ให้ค้างเป็นโทเคนตาย
                if (old.isNotEmpty() && old != token) ref(shopId).update("tokens", FieldValue.arrayRemove(old))
            }
            .addOnFailureListener { e -> Log.w(TAG, "save token failed: " + e.message) }
    }

    fun register(ctx: Context, shopId: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { t: String? -> if (t != null) save(ctx, shopId, t) }
                .addOnFailureListener { e -> Log.w(TAG, "get token failed: " + e.message) }
        } catch (e: Exception) { Log.w(TAG, "register failed: " + e.message) }
    }

    /** ออกจากระบบ: เอาโทเคนของเครื่องนี้ออกจาก tokens (เครื่องอื่นของร้านยังรับต่อได้) */
    fun unregister(ctx: Context, shopId: String) {
        if (shopId.isEmpty()) return
        val p = prefs(ctx)
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { cur: String? ->
                val mine: String = cur ?: (p.getString("fcmToken", "") ?: "")
                if (mine.isEmpty()) return@addOnSuccessListener
                val r = ref(shopId)
                r.update("tokens", FieldValue.arrayRemove(mine)).addOnCompleteListener {
                    r.get().addOnSuccessListener { d ->
                        if (d.exists() && d.getString("token") == mine) {
                            val rest: List<*> = (d.get("tokens") as? List<*>) ?: emptyList<Any>()
                            val next: String = rest.firstOrNull { it is String && it.isNotEmpty() } as? String ?: ""
                            if (next.isNotEmpty()) r.update("token", next) else r.update("token", FieldValue.delete())
                        }
                    }
                }
                p.edit().remove("fcmToken").apply()
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
        ShopFcm.save(this, id, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (ShopSession(this).shopId.isNullOrEmpty()) return
        if (message.data["type"] != "new_order") return
        val oid: String = message.data["orderId"] ?: ""
        // ข้อความทดสอบ (test = "1" หรือ orderId ขึ้นต้นด้วย TEST) ดังโดยไม่ต้องมีออเดอร์จริง
        val isTest: Boolean = message.data["test"] == "1" || oid.startsWith("TEST", ignoreCase = true)
        if (isTest) ShopAlertService.start(this, ring = false, testId = "test_" + oid.ifEmpty { System.currentTimeMillis().toString() },
            title = message.data["title"] ?: "", body = message.data["body"] ?: "")
        else ShopAlertService.start(this, ring = true)
    }
}
