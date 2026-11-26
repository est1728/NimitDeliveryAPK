package com.nimit.delivery.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

/**
 * addSnapshotListener ของ Firestore จะ "ตายถาวร" เมื่อเกิด error (โควตาเต็ม / เน็ตหลุดนาน / สิทธิ์ชั่วคราว)
 * แล้วไม่ส่งข้อมูลใหม่อีกเลยจนกว่าจะเปิดแอพใหม่ → ไม่มีเสียงแจ้งเตือนออเดอร์.
 * ตัวห่อนี้สมัครฟังใหม่เองอัตโนมัติ (หน่วง 3 วิ เพิ่มขึ้นทีละ 3 วิ สูงสุด 60 วิ) จนกว่าจะ remove()
 */
private class RetryHandle(
    private val subscribe: (onOk: () -> Unit, onFail: (Exception) -> Unit) -> ListenerRegistration,
    private val onError: ((Exception) -> Unit)?
) : ListenerRegistration {
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var removed = false
    private var reg: ListenerRegistration? = null
    private var tries = 0
    private val again = Runnable { if (!removed) open() }

    private fun open() {
        reg?.remove()
        reg = subscribe({ tries = 0 }, { e -> fail(e) })
    }

    private fun fail(e: Exception) {
        if (removed) return
        onError?.invoke(e)
        reg?.remove(); reg = null
        tries++
        h.removeCallbacks(again)
        h.postDelayed(again, minOf(60_000L, 3_000L * tries))
    }

    fun begin(): RetryHandle { open(); return this }

    override fun remove() {
        removed = true
        h.removeCallbacks(again)
        reg?.remove(); reg = null
    }
}

fun Query.listenRetry(onError: ((Exception) -> Unit)? = null, onSnap: (QuerySnapshot) -> Unit): ListenerRegistration =
    RetryHandle({ ok, fail ->
        addSnapshotListener { s, e ->
            if (e != null) fail(e) else if (s != null) { ok(); onSnap(s) }
        }
    }, onError).begin()

fun DocumentReference.listenRetry(onError: ((Exception) -> Unit)? = null, onSnap: (DocumentSnapshot) -> Unit): ListenerRegistration =
    RetryHandle({ ok, fail ->
        addSnapshotListener { s, e ->
            if (e != null) fail(e) else if (s != null) { ok(); onSnap(s) }
        }
    }, onError).begin()
