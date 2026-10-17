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
    val groupId: String
)

// อ่านประวัติการเปลี่ยนแปลงเขต (franchiseAudit) อ่านอย่างเดียว เรียงใหม่สุดก่อน
object DevAuditRepo {
    suspend fun recent(limit: Long = 100): List<DevAuditItem> {
        val snap = FirebaseFirestore.getInstance().collection("franchiseAudit")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snap.documents.map { d ->
            DevAuditItem(
                id = d.id,
                action = d.getString("action") ?: "",
                by = d.getString("by") ?: "",
                atMillis = d.getTimestamp("at")?.toDate()?.time ?: 0L,
                names = (d.get("zoneNames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                groupId = d.getString("groupId") ?: ""
            )
        }
    }
}
