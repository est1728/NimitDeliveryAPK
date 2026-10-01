package com.nimit.delivery.data

import android.content.Context
import org.json.JSONObject

/** แทน localStorage/sessionStorage ของเว็บ (เก็บเฉพาะแคชฝั่งเครื่อง ข้อมูลหลักอยู่ที่ Firestore) */
class Session(ctx: Context) {
    private val p = ctx.getSharedPreferences("nimit", Context.MODE_PRIVATE)

    private fun get(k: String): String? = p.getString(k, null)
    private fun set(k: String, v: String?) {
        p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }

    var customerPhone: String? get() = get("customerPhone"); set(v) = set("customerPhone", v)
    var customerData: String? get() = get("customerData"); set(v) = set("customerData", v)
    var addresses: String? get() = get("addresses"); set(v) = set("addresses", v)
    var defaultAddress: String? get() = get("defaultAddress"); set(v) = set("defaultAddress", v)
    var savedAccounts: String? get() = get("savedAccounts"); set(v) = set("savedAccounts", v)
    var loginReturnRoute: String? get() = get("loginReturnRoute"); set(v) = set("loginReturnRoute", v)
    var newCustomer: String? get() = get("newCustomer"); set(v) = set("newCustomer", v)
    var verifiedPhone: String? get() = get("verifiedPhone"); set(v) = set("verifiedPhone", v)
    var addrBack: String? get() = get("addrBack"); set(v) = set("addrBack", v)

    var cart: String? get() = get("cart"); set(v) = set("cart", v)
    var favoriteShops: String? get() = get("favoriteShops"); set(v) = set("favoriteShops", v)
    var notifLastRead: String? get() = get("notifLastRead"); set(v) = set("notifLastRead", v)
    var chatOrderId: String? get() = get("chatOrderId"); set(v) = set("chatOrderId", v)
    var selectedShopId: String? get() = get("selectedShopId"); set(v) = set("selectedShopId", v)
    var selectedShopName: String? get() = get("selectedShopName"); set(v) = set("selectedShopName", v)

    fun logout() { customerData = null; customerPhone = null }

    fun clearCustomerCache() {
        customerData = null; addresses = null; defaultAddress = null
    }

    fun clearSaved() {
        customerPhone = null; customerData = null; addresses = null
    }
}

fun Map<String, Any?>.toJson(): JSONObject {
    val o = JSONObject()
    for ((k, v) in this) {
        when (v) {
            null -> {}
            is String, is Number, is Boolean -> o.put(k, v)
            else -> o.put(k, v.toString())
        }
    }
    return o
}
