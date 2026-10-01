package com.nimit.delivery.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

data class RiderInfo(val id: String, val name: String, val phone: String)

/** จำไรเดอร์ที่ล็อกอินไว้ในเครื่อง */
class RiderSession(ctx: Context) {
    private val p = ctx.getSharedPreferences("nimit_rider", Context.MODE_PRIVATE)

    private fun get(k: String): String? = p.getString(k, null)
    private fun set(k: String, v: String?) {
        p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }

    var riderId: String? get() = get("riderId"); set(v) = set("riderId", v)
    var riderName: String? get() = get("riderName"); set(v) = set("riderName", v)
    var riderPhone: String? get() = get("riderPhone"); set(v) = set("riderPhone", v)

    fun logout() { riderId = null; riderName = null; riderPhone = null }
}

object RiderRepo {
    fun normPhone(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("66")) "0" + d.substring(2) else d
    }

    /** หาไรเดอร์จากเบอร์ที่แอดมินลงไว้ในคอลเลกชัน riders (ฟิลด์ phone) คืน null ถ้าไม่พบ */
    suspend fun findByPhone(input: String): RiderInfo? {
        val p = normPhone(input)
        if (p.length < 9) return null
        val col = FirebaseFirestore.getInstance().collection("riders")
        val q: QuerySnapshot = col.whereEqualTo("phone", p).get().await()
        var found: DocumentSnapshot? = q.documents.firstOrNull()
        if (found == null) {
            val all: QuerySnapshot = col.get().await()
            found = all.documents.firstOrNull { normPhone(it.get("phone")?.toString() ?: "") == p }
        }
        val d: DocumentSnapshot = found ?: return null
        return RiderInfo(d.id, d.getString("name") ?: "ไรเดอร์", d.get("phone")?.toString() ?: p)
    }
}
