package com.nimit.delivery.data

import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

val ADMIN_ACTIVE: List<String> = listOf("pending", "accepted", "picking", "arrived", "delivering")
val ADMIN_STATUS_TH: Map<String, String> = mapOf(
    "pending" to "รอรับ", "accepted" to "รับแล้ว", "picking" to "ไปร้าน", "arrived" to "ถึงร้าน",
    "delivering" to "กำลังส่ง", "done" to "เสร็จสิ้น", "rejected" to "ยกเลิก"
)

class AdminOrd(val id: String, val d: Map<String, Any>) {
    fun s(k: String): String = d[k]?.toString() ?: ""
    fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
    fun date(k: String): Date? = (d[k] as? Timestamp)?.toDate()
    fun cust(k: String): String {
        val m = d["customer"] as? Map<*, *> ?: return ""
        return m[k]?.toString() ?: ""
    }
    fun qty(): Int {
        val l = d["items"] as? List<*> ?: return 0
        var t = 0
        for (i in l) {
            val m = i as? Map<*, *> ?: continue
            t += (m["qty"] as? Number)?.toInt() ?: 0
        }
        return t
    }
    val status: String get() = s("status")
    val orderId: String get() = if (s("orderId").isEmpty()) id else s("orderId")
    val created: Date? get() = date("createdAt")
}

/*
 * สถานะสดของหน้าออเดอร์: ฟังออเดอร์ที่ยังไม่จบ (เหมือน listenOrders ใน admin.html)
 * และฟังร้านที่อยู่ในเขตของแอดมิน (shops.franchiseIds แบบใหม่ หรือ shops.franchiseId แบบเก่า เหมือนแอปผู้พัฒนา)
 * เขตของแอดมิน = เขตตัวเอง + เขตอื่นที่ผูกหลังบ้านร่วม (backendGroupId เดียวกัน)
 */
class AdminOrdersStore(private val session: AdminSession) {
    val orders = mutableStateOf<List<AdminOrd>>(emptyList())
    val shopIds = mutableStateOf<Set<String>>(emptySet())
    val shopZones = mutableStateOf<Map<String, List<String>>>(emptyMap())
    val zoneNames = mutableStateOf<Map<String, String>>(emptyMap())
    val ready = mutableStateOf(false)
    val zoneIds = mutableStateOf<List<String>>(emptyList())
    val error = mutableStateOf<String?>(null)

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val regs = ArrayList<ListenerRegistration>()
    private val chunkShops = HashMap<Int, Map<String, List<String>>>()
    private var job: Job? = null

    private fun zonesOf(d: DocumentSnapshot): List<String> {
        val a = (d.get("franchiseIds") as? List<*>)?.mapNotNull { it as? String }
        if (a != null && a.isNotEmpty()) return a
        val s = d.getString("franchiseId")
        return if (s != null) listOf(s) else emptyList()
    }

    private fun publish() {
        val m = HashMap<String, List<String>>()
        for (v in chunkShops.values) m.putAll(v)
        shopZones.value = m
        shopIds.value = m.keys.toSet()
    }

    /** ป้ายเขตข้างรหัสออเดอร์: แสดงเฉพาะเมื่อกลุ่มหลังบ้านมีหลายเขต; ร้านหลายเขตแสดงทุกชื่อ */
    fun zoneLabel(shopId: String): String {
        if (zoneIds.value.size <= 1) return ""
        val zs = shopZones.value[shopId] ?: return ""
        val names = ArrayList<String>()
        for (z in zs) {
            if (zoneIds.value.contains(z)) {
                val n = zoneNames.value[z] ?: ""
                if (n.isNotEmpty()) names.add(n)
            }
        }
        return names.joinToString(" / ")
    }

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                // เขตทั้งหมดในกลุ่มหลังบ้านเดียวกัน (ไม่ผูก = มีแค่เขตตัวเอง)
                val g = db.collection("franchises").whereEqualTo("backendGroupId", session.groupId).get().await()
                val ids = ArrayList<String>()
                val names = HashMap<String, String>()
                for (x in g.documents) {
                    ids.add(x.id)
                    names[x.id] = x.getString("name") ?: ""
                }
                if (!ids.contains(session.franchiseId)) {
                    ids.add(session.franchiseId)
                    names[session.franchiseId] = session.name
                }
                zoneNames.value = names
                zoneIds.value = ids.toList()
                var idx = 0
                for (chunk in ids.chunked(10)) {
                    val i = idx
                    idx++
                    regs.add(db.collection("shops").whereArrayContainsAny("franchiseIds", chunk).addSnapshotListener { snap, e ->
                        if (e != null) error.value = e.message
                        if (snap != null) {
                            chunkShops[i] = snap.documents.associate { it.id to zonesOf(it) }
                            publish()
                        }
                    })
                }
                // ร้านแบบฟิลด์เก่า: shops.franchiseId (เขตเดียว) ยังไม่ถูกย้ายไปเป็น franchiseIds
                var idx2 = 0
                for (chunk in ids.chunked(10)) {
                    val j = 1000 + idx2
                    idx2++
                    regs.add(db.collection("shops").whereIn("franchiseId", chunk).addSnapshotListener { snap, e ->
                        if (e != null) error.value = e.message
                        if (snap != null) {
                            chunkShops[j] = snap.documents.associate { it.id to zonesOf(it) }
                            publish()
                        }
                    })
                }
                regs.add(db.collection("orders").whereIn("status", ADMIN_ACTIVE).addSnapshotListener { snap, e ->
                    if (e != null) error.value = e.message
                    if (snap != null) {
                        orders.value = snap.documents
                            .map { x -> AdminOrd(x.id, x.data ?: emptyMap()) }
                            .sortedBy { it.created?.time ?: 0L }
                    }
                })
                ready.value = true
            } catch (e: Exception) {
                error.value = e.message ?: "โหลดข้อมูลไม่สำเร็จ"
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        for (r in regs) r.remove()
        regs.clear()
        chunkShops.clear()
    }
}
