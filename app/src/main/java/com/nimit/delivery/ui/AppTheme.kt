package com.nimit.delivery.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * ธีมเทศกาล (ใช้ร่วมกับเว็บ): อ่านจาก Firestore settings/theme { active, mode, start, end }
 * - mode "manual" = เปิดจนกว่าจะปิด / อย่างอื่น = ตั้งเวลา (start-end เป็น "yyyy-MM-dd" หรือ "yyyy-MM-ddTHH:mm")
 * - สีใน N (Icons.kt) อ่านจากที่นี่ จึงเปลี่ยนตามธีมอัตโนมัติ · รูปตกแต่งโหลดจากเว็บ (theme/<ธีม>/...)
 */
object AppTheme {
    private const val WEB = "https://nimitdelivery.vercel.app/"

    class Preset(val name: String, val colors: Map<String, Color>, val dir: String?, val sprites: List<String>)

    private val PRESETS: Map<String, Preset> = mapOf(
        "default" to Preset("ปกติ", emptyMap(), null, emptyList()),
        "halloween" to Preset(
            "ฮาโลวีน",
            mapOf(
                "B900" to Color(0xFF1A0B2E), "B800" to Color(0xFF2A1048), "B700" to Color(0xFF4B1F7C), "B600" to Color(0xFF6A2FB0),
                "B500" to Color(0xFF8A4FD6), "B100" to Color(0xFFEFE5FB), "B50" to Color(0xFFF8F3FE), "Bg" to Color(0xFFF8F4FC),
                "Amber600" to Color(0xFFE0620A)
            ),
            "theme/halloween/",
            listOf("web", "pumpkin", "bat", "peek", "sit", "cbg")
        )
    )

    var activeId: String by mutableStateOf("default")
        private set

    val active: Preset get() = PRESETS[activeId] ?: PRESETS.getValue("default")
    val isDefault: Boolean get() = activeId == "default"

    fun c(key: String, def: Color): Color = active.colors[key] ?: def

    private fun url(rel: String): String? = active.dir?.let { WEB + it + rel }
    fun sprite(name: String): String? = if (name in active.sprites) url("deco/$name.webp") else null
    fun mascot(name: String): String? = url("mascot/$name.webp")
    val headerUrl: String? get() = url("header.webp")

    private fun nm(v: String, end: Boolean): String = if (v.isEmpty()) "" else if (v.length <= 10) v + (if (end) "T23:59" else "T00:00") else v

    fun pick(active: String, mode: String, start: String, end: String): String {
        if (active.isEmpty() || active == "default" || !PRESETS.containsKey(active)) return "default"
        if (mode != "manual") {
            val cal = java.util.Calendar.getInstance()
            val now = "%04d-%02d-%02dT%02d:%02d".format(
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)
            )
            val s = nm(start, false); val e = nm(end, true)
            if (s.isNotEmpty() && now < s) return "default"
            if (e.isNotEmpty() && now > e) return "default"
        }
        return active
    }

    private fun applyCfg(o: JSONObject) {
        activeId = pick(o.optString("active"), o.optString("mode"), o.optString("start"), o.optString("end"))
    }

    /** ใช้ค่าที่เก็บไว้ครั้งก่อนทันทีตอนเปิดแอพ (กันหน้าจอกะพริบเป็นธีมปกติ) */
    fun init(ctx: Context) {
        try { applyCfg(JSONObject(ctx.getSharedPreferences("nimit", Context.MODE_PRIVATE).getString("appThemeCfg", "{}") ?: "{}")) } catch (_: Exception) {}
    }

    /** ดึงค่าล่าสุดจาก settings/theme */
    suspend fun sync(ctx: Context) {
        try {
            val d = Firebase.firestore.collection("settings").document("theme").get().await().data ?: emptyMap()
            val o = JSONObject()
            for (k in listOf("active", "mode", "start", "end")) o.put(k, (d[k] as? String).orEmpty())
            ctx.getSharedPreferences("nimit", Context.MODE_PRIVATE).edit().putString("appThemeCfg", o.toString()).apply()
            applyCfg(o)
        } catch (_: Exception) {}
    }
}
