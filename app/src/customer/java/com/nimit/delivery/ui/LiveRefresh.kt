package com.nimit.delivery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import android.os.Handler
import android.os.Looper

/** collection / document path helper: "shops" หรือ "settings/appBanner" */
fun liveSrc(path: String): Any {
    val db = Firebase.firestore
    return if (path.split("/").size % 2 == 0) db.document(path) else db.collection(path)
}

/**
 * ฟังการเปลี่ยนแปลงของ Firestore แบบเรียลไทม์ แล้วสั่งโหลดข้อมูลใหม่เงียบๆ (ไม่ต้องปัดรีเฟรช)
 * - ข้ามเหตุการณ์แรกของแต่ละแหล่ง (เพราะหน้านั้นโหลดข้อมูลเริ่มต้นเองอยู่แล้ว)
 * - รวมการเปลี่ยนแปลงถี่ๆ ให้เหลือครั้งเดียว (debounce)
 * - เลิกฟังอัตโนมัติเมื่อออกจากหน้า
 * sources รับ CollectionReference / Query / DocumentReference หรือ path (String)
 */
@Composable
fun LiveRefresh(vararg sources: Any?, debounceMs: Long = 700L, onChange: () -> Unit) {
    val cb = rememberUpdatedState(onChange)
    // Query/DocumentReference มี equals/hashCode ตามเนื้อหา → ไม่ resubscribe ทุกครั้งที่ recompose
    val keys = sources.map { it?.hashCode() }
    DisposableEffect(keys) {
        val h = Handler(Looper.getMainLooper())
        val fire = Runnable { cb.value() }
        val regs = ArrayList<ListenerRegistration>()
        sources.filterNotNull().forEach { raw ->
            val src = if (raw is String) liveSrc(raw) else raw
            var first = true
            val listener = com.google.firebase.firestore.EventListener<Any> { _, err ->
                if (err != null) return@EventListener
                if (first) { first = false; return@EventListener }
                h.removeCallbacks(fire); h.postDelayed(fire, debounceMs)
            }
            when (src) {
                is DocumentReference -> regs.add(src.addSnapshotListener { s, e -> listener.onEvent(s, e) })
                is Query -> regs.add(src.addSnapshotListener { s, e -> listener.onEvent(s, e) })
            }
        }
        onDispose { regs.forEach { it.remove() }; h.removeCallbacks(fire) }
    }
}
