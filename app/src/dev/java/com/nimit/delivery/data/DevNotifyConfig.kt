package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// รายการเหตุการณ์แจ้งเตือนที่มีอยู่จริงในระบบ (จากโค้ดแอปและ rider.html เดิม) vars = ตัวแปรที่ผู้ส่งแนบได้
data class DevEventDef(val id: String, val group: String, val label: String, val vars: String)

// title/body ว่าง = ใช้ข้อความเดิมของแอป/ผู้ส่ง
data class DevEventCfg(val enabled: Boolean, val title: String, val body: String)

/**
 * ตั้งค่าข้อความแจ้งเตือนจากแอป dev เก็บที่ appConfig/notifications:
 *   { events: { "<รหัสเหตุการณ์>": { enabled, title, body } }, updatedAt, updatedBy }
 * ตัวแปรในข้อความเขียนเป็น {orderId} {shopName} ฯลฯ ผู้อ่านค่า (เซิร์ฟเวอร์/แอป) เป็นคนแทนที่
 * ทุกครั้งที่บันทึกจะเขียนประวัติลง devConfigLog (เพิ่มอย่างเดียว) ในครั้งเดียวกัน
 */
object DevNotifyConfig {
    val groups = listOf(
        "rider" to "ไรเดอร์ (ในแอปไรเดอร์)",
        "status" to "ลูกค้า (สถานะจากไรเดอร์)",
        "order" to "ออเดอร์ใหม่",
        "shop" to "ร้านค้า",
        "chat" to "แชท"
    )

    val events = listOf(
        DevEventDef("rider_new_job", "rider", "งานใหม่มาแล้ว (แบนเนอร์+เสียงในแอปไรเดอร์)", "{orderId} {shopName}"),
        DevEventDef("rider_accepted", "rider", "รับงานแล้ว", "{orderId}"),
        DevEventDef("rider_assigned", "rider", "ถูกมอบหมายงาน", "{orderId}"),
        DevEventDef("rider_reassigned", "rider", "งานถูกถอด/โอน (แบนเนอร์สีแดง)", "{orderId}"),
        DevEventDef("status_accepted", "status", "ไรเดอร์รับงานแล้ว", "{orderId} {riderName} {riderPhone}"),
        DevEventDef("status_picking", "status", "ไรเดอร์กำลังไปร้าน", "{orderId} {riderName}"),
        DevEventDef("status_arrived", "status", "ไรเดอร์ถึงร้านแล้ว", "{orderId} {riderName}"),
        DevEventDef("status_delivering", "status", "กำลังนำอาหารมาส่ง", "{orderId} {riderName}"),
        DevEventDef("status_done", "status", "จัดส่งสำเร็จแล้ว", "{orderId}"),
        DevEventDef("order_new", "order", "มีออเดอร์ใหม่ (ถึงร้าน/แอดมิน/ไรเดอร์)", "{orderId} {shopName} {total}"),
        DevEventDef("shop_new_order", "shop", "ออเดอร์ใหม่ในแอปร้านค้า", "{orderId} {total}"),
        DevEventDef("chat_message", "chat", "ข้อความแชทใหม่", "{orderId}")
    )

    private fun cfgOf(m: Map<String, Any?>): DevEventCfg = DevEventCfg(
        enabled = (m["enabled"] as? Boolean) ?: true,
        title = (m["title"] as? String) ?: "",
        body = (m["body"] as? String) ?: ""
    )

    suspend fun load(): Map<String, DevEventCfg> {
        val d = FirebaseFirestore.getInstance().collection("appConfig").document("notifications").get().await()
        val ev = d.get("events") as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, DevEventCfg>()
        for ((k, v) in ev) {
            val m = (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: continue
            out[k.toString()] = cfgOf(m)
        }
        return out
    }

    /** คืนข้อความผิดพลาด หรือ null ถ้าสำเร็จ */
    suspend fun save(def: DevEventDef, cfg: DevEventCfg, before: DevEventCfg?): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        return try {
            val db = FirebaseFirestore.getInstance()
            val b = db.batch()
            b.set(
                db.collection("appConfig").document("notifications"),
                mapOf(
                    "events" to mapOf(
                        def.id to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                    ),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to email
                ),
                SetOptions.merge()
            )
            b.set(
                db.collection("devConfigLog").document(),
                mapOf(
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "event" to def.id,
                    "label" to def.label,
                    "before" to mapOf(
                        "enabled" to (before?.enabled ?: true),
                        "title" to (before?.title ?: ""),
                        "body" to (before?.body ?: "")
                    ),
                    "after" to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }
}
