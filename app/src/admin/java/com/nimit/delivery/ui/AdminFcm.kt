package com.nimit.delivery.ui

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nimit.delivery.data.AdminRepo

/**
 * โทเคน FCM ของแอดมิน: เอกสาร fcmTokens/{franchiseId}_admin  (role = admin, platform = android)
 *  - tokens: โทเคนทุกเครื่องที่ล็อกอินเขตนี้อยู่   - token: โทเคนล่าสุด (เซิร์ฟเวอร์รุ่นเก่าอ่านได้)
 */
object AdminFcm {
    private const val TAG = "AdminFcm"
    private fun ref(id: String) = FirebaseFirestore.getInstance().collection("fcmTokens").document(id + "_admin")
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("nimit_admin_alert", Context.MODE_PRIVATE)

    fun save(ctx: Context, franchiseId: String, token: String) {
        if (franchiseId.isEmpty() || token.isEmpty()) return
        val p = prefs(ctx)
        val old: String = p.getString("fcmToken", "") ?: ""
        val m = HashMap<String, Any?>()
        m["token"] = token
        m["tokens"] = FieldValue.arrayUnion(token)
        m["role"] = "admin"
        m["platform"] = "android"
        m["franchiseId"] = franchiseId
        m["updatedAt"] = FieldValue.serverTimestamp()
        ref(franchiseId).set(m, SetOptions.merge())
            .addOnSuccessListener {
                p.edit().putString("fcmToken", token).apply()
                if (old.isNotEmpty() && old != token) ref(franchiseId).update("tokens", FieldValue.arrayRemove(old))
            }
            .addOnFailureListener { e -> Log.w(TAG, "save token failed: " + e.message) }
    }

    fun register(ctx: Context, franchiseId: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { t: String? -> if (t != null) save(ctx, franchiseId, t) }
                .addOnFailureListener { e -> Log.w(TAG, "get token failed: " + e.message) }
        } catch (e: Exception) { Log.w(TAG, "register failed: " + e.message) }
    }

    /** ออกจากระบบ: เอาโทเคนของเครื่องนี้ออก (เครื่องอื่นของเขตนี้ยังรับต่อได้) */
    fun unregister(ctx: Context, franchiseId: String) {
        if (franchiseId.isEmpty()) return
        val p = prefs(ctx)
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { cur: String? ->
                val mine: String = cur ?: (p.getString("fcmToken", "") ?: "")
                if (mine.isEmpty()) return@addOnSuccessListener
                val r = ref(franchiseId)
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
 * รับข้อความ FCM แบบ data-only
 *  - { type: "new_order", orderId, title, body }  ปลุกบริการให้ฟังออเดอร์และดังเสียง
 *  - { type: "order_cancelled", orderId, title, body }  แจ้งเตือนทั่วไป (ไม่วนเสียง)
 *  - test = "1" หรือ orderId ขึ้นต้น TEST = ข้อความทดสอบ ดังโดยไม่ต้องมีออเดอร์จริง
 */
class AdminMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val s = AdminRepo.load(this) ?: return
        AdminFcm.save(this, s.franchiseId, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (AdminRepo.load(this) == null) return
        val type: String = message.data["type"] ?: ""
        val oid: String = message.data["orderId"] ?: ""
        if (type == "order_cancelled") {
            AdminAlertService.notifyPlain(this, ("cancel_" + oid).hashCode(), message.data["title"] ?: "❌ ออเดอร์ถูกยกเลิก", message.data["body"] ?: oid)
            return
        }
        if (type != "new_order") return
        val isTest: Boolean = message.data["test"] == "1" || oid.startsWith("TEST", ignoreCase = true)
        if (isTest) AdminAlertService.start(
            this, ring = false, testId = "test_" + oid.ifEmpty { System.currentTimeMillis().toString() },
            title = message.data["title"] ?: "", body = message.data["body"] ?: ""
        ) else AdminAlertService.start(this, ring = true)
    }
}
