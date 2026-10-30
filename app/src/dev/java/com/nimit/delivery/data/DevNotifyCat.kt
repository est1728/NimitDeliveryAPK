package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

// แผนส่งทั้งหมวด: tokens[i] = ผู้รับของข้อความ items[i] (หาไว้ล่วงหน้าเพื่อแสดงจำนวนก่อนยืนยัน)
class DevCatPlan(val items: List<DevBroadcast>, val tokens: List<List<String>>, val notes: List<String>)

/**
 * หมวดหมู่ของข้อความที่ตั้งไว้ (เหมือนหมวดเมนูของร้าน): รายชื่อหมวดเก็บที่ appConfig/devBroadcastCategories {list:[...]}
 * ข้อความแต่ละรายการมีฟิลด์ category (ว่าง = ไม่มีหมวด)
 */
object DevNotifyCat {
    private fun db() = FirebaseFirestore.getInstance()
    private fun listRef() = db().collection("appConfig").document("devBroadcastCategories")

    suspend fun load(): List<String> {
        val d = listRef().get().await()
        return (d.get("list") as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } } ?: emptyList()
    }

    private fun check(cur: List<String>, name: String, except: String?): String? {
        val n = name.trim()
        if (n.isEmpty()) return "กรุณาใส่ชื่อหมวดหมู่"
        if (n.length > 30) return "ชื่อหมวดหมู่ยาวเกิน 30 ตัวอักษร"
        if (cur.any { it == n && it != except }) return "มีหมวดหมู่ชื่อนี้อยู่แล้ว"
        return null
    }

    private suspend fun commit(block: (WriteBatch) -> Unit): String? {
        return try {
            val b = db().batch()
            block(b)
            b.commit().await()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            devWriteError(e)
        }
    }

    private fun listData(list: List<String>): Map<String, Any> =
        mapOf("list" to list, "updatedAt" to FieldValue.serverTimestamp())

    suspend fun add(cur: List<String>, name: String): String? {
        val bad = check(cur, name, null)
        if (bad != null) return bad
        return commit { b -> b.set(listRef(), listData(cur + name.trim()), SetOptions.merge()) }
    }

    suspend fun rename(cur: List<String>, old: String, name: String, docs: List<DevBroadcast>): String? {
        val bad = check(cur, name, old)
        if (bad != null) return bad
        val n = name.trim()
        if (n == old) return null
        return commit { b ->
            b.set(listRef(), listData(cur.map { if (it == old) n else it }), SetOptions.merge())
            docs.filter { it.category == old }.forEach { b.update(db().collection("devBroadcasts").document(it.id), "category", n) }
        }
    }

    /** ลบหมวด: ข้อความในหมวดไม่ถูกลบ แต่กลับไปเป็นไม่มีหมวด */
    suspend fun delete(cur: List<String>, old: String, docs: List<DevBroadcast>): String? {
        return commit { b ->
            b.set(listRef(), listData(cur.filter { it != old }), SetOptions.merge())
            docs.filter { it.category == old }.forEach { b.update(db().collection("devBroadcasts").document(it.id), "category", "") }
        }
    }

    suspend fun setCategory(id: String, cat: String): String? =
        commit { b -> b.update(db().collection("devBroadcasts").document(id), "category", cat) }

    suspend fun plan(items: List<DevBroadcast>): DevCatPlan {
        val toks = ArrayList<List<String>>()
        val notes = ArrayList<String>()
        for (b in items) {
            val r = DevNotify.resolve(b.role, b.scope, b.zoneId, b.person)
            if (r.second != null) {
                toks.add(emptyList())
                notes.add(b.title + ": " + r.second)
            } else {
                toks.add(r.first)
            }
        }
        return DevCatPlan(items, toks, notes)
    }

    /** ส่งทุกข้อความที่เลือก ไปยังผู้รับของแต่ละข้อความ */
    suspend fun sendAll(p: DevCatPlan): String {
        val sb = StringBuilder()
        for (i in p.items.indices) {
            val b = p.items[i]
            val t = p.tokens[i]
            if (t.isEmpty()) {
                sb.append(b.title).append(": ไม่มีผู้รับ\n")
                continue
            }
            val r = DevNotify.send(t, b.title, b.body)
            DevNotify.log(b.role, b.scope, b.zoneId, b.person, b.title, t.size, r.first)
            DevNotify.markSent(b.id, r.first, t.size)
            sb.append(b.title).append(": ").append(if (r.first) "ส่งถึง " else "ส่งไม่สำเร็จ ").append(t.size).append(" เครื่อง\n")
        }
        return sb.toString().trim()
    }

    /**
     * สุ่มคนละหนึ่งข้อความ: แต่ละเครื่องที่เป็นผู้รับของข้อความใดข้อความหนึ่งได้ข้อความเดียว
     * (สุ่มจากข้อความที่ผู้รับของมันรวมเครื่องนั้นอยู่)
     */
    suspend fun sendRandom(p: DevCatPlan): String {
        val owners = LinkedHashMap<String, MutableList<Int>>()
        for (i in p.tokens.indices) {
            for (t in p.tokens[i]) owners.getOrPut(t) { ArrayList() }.add(i)
        }
        val groups = HashMap<Int, MutableList<String>>()
        for ((t, idx) in owners) {
            val pick = idx[Random.nextInt(idx.size)]
            groups.getOrPut(pick) { ArrayList() }.add(t)
        }
        val sb = StringBuilder("สุ่มคนละ 1 ข้อความ รวม " + owners.size + " เครื่อง\n")
        for (i in groups.keys.sorted()) {
            val b = p.items[i]
            val t = groups[i] ?: continue
            val r = DevNotify.send(t, b.title, b.body)
            DevNotify.log(b.role, b.scope, b.zoneId, b.person, b.title, t.size, r.first)
            DevNotify.markSent(b.id, r.first, t.size)
            sb.append(b.title).append(": ").append(if (r.first) "ส่งถึง " else "ส่งไม่สำเร็จ ").append(t.size).append(" เครื่อง\n")
        }
        return sb.toString().trim()
    }
}
