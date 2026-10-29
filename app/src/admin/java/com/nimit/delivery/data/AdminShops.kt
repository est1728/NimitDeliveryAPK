package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import kotlinx.coroutines.tasks.await

class AdminShop(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    val name: String get() = s("name")
    val isOpen: Boolean get() = d["isOpen"] != false
    val isProxy: Boolean get() = s("shopType") == "proxy"
    val slug: String get() = if (s("slug").isEmpty()) id else s("slug")

    /** เขตของร้าน: franchiseIds (หลายเขต) หรือ franchiseId (ฟิลด์เก่า เขตเดียว) */
    fun zones(): List<String> {
        val a = (d["franchiseIds"] as? List<*>)?.mapNotNull { it as? String }
        if (a != null && a.isNotEmpty()) return a
        val one = s("franchiseId")
        return if (one.isNotEmpty()) listOf(one) else emptyList()
    }
}

object AdminShopsApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun listen(onChange: (List<AdminShop>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("shops").addSnapshotListener { snap, e ->
            if (e != null) onError(e.message ?: "โหลดร้านค้าไม่ได้")
            if (snap != null) onChange(snap.documents.map { AdminShop(it.id, it.data ?: emptyMap()) })
        }
    }

    suspend fun open(id: String) {
        val m = HashMap<String, Any?>()
        m["isOpen"] = true
        m["closeReason"] = ""
        m["closeType"] = null
        m["closeUntil"] = null
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
        log(id, "open", "", null)
    }

    suspend fun close(id: String, reason: String, type: String, until: Long?) {
        val m = HashMap<String, Any?>()
        m["isOpen"] = false
        m["closeReason"] = reason
        m["closeType"] = type
        m["closeUntil"] = until
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
        log(id, "close", reason, type)
    }

    private suspend fun log(id: String, action: String, reason: String, closeType: String?) {
        val l = HashMap<String, Any?>()
        l["action"] = action
        l["reason"] = reason
        if (closeType != null) l["closeType"] = closeType
        l["at"] = FieldValue.serverTimestamp()
        l["by"] = "Admin"
        try { db.collection("shops").document(id).collection("statusLogs").add(l).await() } catch (e: Exception) { }
    }

    suspend fun setType(id: String, type: String) {
        val m = HashMap<String, Any>()
        m["shopType"] = type
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("shops").document(id).update(m).await()
    }

    /** เวลาเปิดร้านรอบถัดไปตามเวลา/วันที่ตั้งอัตโนมัติ (ใช้ตอนปิดยาว) ไม่ได้ตั้งไว้ = null (ปิดค้างจนกดเปิดเอง) */
    fun nextOpenTime(s: AdminShop): Long? {
        if (s.d["autoSchedule"] != true) return null
        val so = s.s("scheduleOpen")
        if (so.isEmpty() || s.s("scheduleClose").isEmpty()) return null
        val raw = (s.d["openDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val days: List<Int> = if (raw.isEmpty()) (0..6).toList() else raw
        val parts = so.split(":")
        val oh: Int = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val om: Int = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now: Long = System.currentTimeMillis()
        for (add in 0..8) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, add)
            c.set(Calendar.HOUR_OF_DAY, oh)
            c.set(Calendar.MINUTE, om)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) continue
            if (days.contains(c.get(Calendar.DAY_OF_WEEK) - 1)) return c.timeInMillis
        }
        return null
    }
}
