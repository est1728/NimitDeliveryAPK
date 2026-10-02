package com.nimit.delivery.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object LoginFlow {
    /** เช็คลูกค้าใน Firestore (collection customers) -> true = ลงทะเบียนแล้ว, false = ต้องไปหน้า register */
    suspend fun route(session: Session, p: String): Boolean {
        session.newCustomer = null
        try {
            val snap = Firebase.firestore.collection("customers").document(p).get().await()
            val d = if (snap.exists()) snap.data else null
            if (d != null && d["registered"] == true) {
                session.customerData = d.toJson().toString()
                try {
                    val arr = JSONArray(session.savedAccounts ?: "[]")
                    val entry = JSONObject().put("phone", p)
                        .put("name", d["name"]?.toString() ?: "")
                        .put("address", d["address"]?.toString() ?: "")
                        .put("lat", d["lat"] ?: JSONObject.NULL).put("lng", d["lng"] ?: JSONObject.NULL)
                    var idx = -1
                    for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("phone") == p) idx = i
                    if (idx >= 0) arr.put(idx, entry) else arr.put(entry)
                    while (arr.length() > 3) arr.remove(0)
                    session.savedAccounts = arr.toString()
                } catch (_: Exception) {}
                val addr = d["address"]?.toString().orEmpty()
                if (addr.isNotEmpty()) {
                    val a = JSONObject().put("name", d["name"]?.toString() ?: "").put("phone", p)
                        .put("address", addr)
                        .put("lat", d["lat"] ?: JSONObject.NULL).put("lng", d["lng"] ?: JSONObject.NULL)
                    session.addresses = JSONArray().put(a).toString()
                    session.defaultAddress = "0"
                }
                return true
            }
            try {
                Firebase.firestore.collection("customers").document(p).set(
                    mapOf("phone" to p, "createdAt" to java.time.Instant.now().toString()), SetOptions.merge()
                ).await()
            } catch (_: Exception) {}
        } catch (_: Exception) {}
        session.newCustomer = "1"; session.addrBack = "shops"
        return false
    }
}
