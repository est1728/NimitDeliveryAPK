package com.nimit.delivery.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class DevAuditItem(
    val id: String,
    val action: String,
    val by: String,
    val atMillis: Long,
    val names: List<String>,
    val groupId: String,
    val zoneIds: List<String>,
    val details: List<String>
)

private val devAuditFields = listOf(
    "name" to "ชื่อเขต",
    "province" to "พื้นที่",
    "phone" to "เบอร์โทร",
    "monthlyFee" to "ค่าแฟรนไชส์/เดือน",
    "status" to "สถานะ"
)

private fun devAuditValue(k: String, v: Any?): String {
    val s = v?.toString() ?: "-"
    if (k == "status") return if (s == "active") "ใช้งาน" else if (s == "suspended") "ระงับ" else s
    return if (s.isEmpty()) "-" else s
}

// สร้างบรรทัด "ชื่อฟิลด์: ก่อน → หลัง" สำหรับเพิ่ม/แก้ไขเขต (ไม่มีรหัสจริงในประวัติ)
private fun devAuditDetails(action: String, before: Map<String, Any?>, after: Map<String, Any?>, codeChanged: Boolean): List<String> {
    if (action != "create_zone" && action != "edit_zone") return emptyList()
    val out = ArrayList<String>()
    devAuditFields.forEach { (k, label) ->
        if (after.containsKey(k)) out.add(label + ": " + devAuditValue(k, before[k]) + " → " + devAuditValue(k, after[k]))
    }
    if (codeChanged) out.add("รหัสเข้าใช้แอปแอดมิน: ตั้งใหม่")
    return out
}

// อ่านประวัติการเปลี่ยนแปลงเขต (franchiseAudit) อ่านอย่างเดียว เรียงใหม่สุดก่อน
object DevAuditRepo {
    suspend fun recent(limit: Long = 100): List<DevAuditItem> {
        val snap = FirebaseFirestore.getInstance().collection("franchiseAudit")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snap.documents.map { d ->
            val action = d.getString("action") ?: ""
            val before = (d.get("before") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            val after = (d.get("after") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            DevAuditItem(
                id = d.id,
                action = action,
                by = d.getString("by") ?: "",
                atMillis = d.getTimestamp("at")?.toDate()?.time ?: 0L,
                names = (d.get("zoneNames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                groupId = d.getString("groupId") ?: "",
                zoneIds = (d.get("zoneIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                details = devAuditDetails(action, before, after, d.getBoolean("codeChanged") == true)
            )
        }
    }
}
