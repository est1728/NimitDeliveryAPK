#!/bin/bash
# 1) เอาข้อความวินิจฉัยชั่วคราวออก  2) ป้ายชื่อเขตข้างรหัสออเดอร์ (แสดงเมื่อเขตผูกหลังบ้านร่วมกับเขตอื่น)
# เขตเดียว/ไม่ผูกหลังบ้าน = ไม่แสดงป้าย  ร้านที่อยู่หลายเขตแสดงทุกชื่อเขตคั่นด้วย /  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
D=$J/data/AdminOrders.kt
S=$J/ui/AdminOrdersScreen.kt
[ -f $D ] && [ -f $S ] || die "ไม่เจอไฟล์หน้าออเดอร์"
grep -q 'onOpen' $S || die "AdminOrdersScreen ยังไม่ผ่าน admin-detail-v1.sh"
grep -q 'ร้านแบบฟิลด์เก่า' $D || die "ต้องรัน admin-shops-v1.sh ก่อน"

# ---------- ข้อมูล: เก็บเขตของแต่ละร้าน + ชื่อเขต ----------
cat > $D <<'KT_EOF'
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
KT_EOF
echo "AdminOrders.kt: เขียนใหม่แล้ว"

# ---------- หน้ารายการ: เอาข้อความวินิจฉัยออก + ป้ายเขต ----------
if grep -q 'วินิจฉัย' $S; then
  sed -i '/scope.isEmpty() -> "\[วินิจฉัย\]/c\                        scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"' $S
  sed -i '/all.isEmpty() && store.orders.value.isNotEmpty() -> "\[วินิจฉัย\]/d' $S
  sed -i '/else -> "\[วินิจฉัย\]/c\                        else -> "ไม่มีออเดอร์"' $S
  grep -q 'วินิจฉัย' $S && die "ลบข้อความวินิจฉัยไม่หมด"
  echo "AdminOrdersScreen: เอาข้อความวินิจฉัยออกแล้ว"
else
  echo "AdminOrdersScreen: ไม่มีข้อความวินิจฉัย ข้าม"
fi

if grep -q 'zone: String' $S; then
  echo "AdminOrdersScreen: ป้ายเขตแก้แล้ว ข้าม"
else
  grep -q 'AdminOrderCard(o) { onOpen(o.id) }' $S || die "ไม่เจอจุดยึดเรียกการ์ด"
  grep -q 'private fun AdminOrderCard(o: AdminOrd, onClick: () -> Unit) {' $S || die "ไม่เจอจุดยึดการ์ด"
  LN=$(grep -n 'Text("#" + o.orderId' $S | head -1 | cut -d: -f1)
  [ -n "$LN" ] || die "ไม่เจอบรรทัดรหัสออเดอร์"
  sed -i 's|AdminOrderCard(o) { onOpen(o.id) }|AdminOrderCard(o, store.zoneLabel(o.s("shopId"))) { onOpen(o.id) }|' $S
  sed -i 's|private fun AdminOrderCard(o: AdminOrd, onClick: () -> Unit) {|private fun AdminOrderCard(o: AdminOrd, zone: String, onClick: () -> Unit) {|' $S
  cat > .admin-zone-block.tmp <<'KT_EOF'
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("#" + o.orderId, fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.Primary, letterSpacing = 1.sp)
                if (zone.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(zone, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
KT_EOF
  head -n $((LN-1)) $S > .admin-zone-new.tmp
  cat .admin-zone-block.tmp >> .admin-zone-new.tmp
  tail -n +$((LN+1)) $S >> .admin-zone-new.tmp
  mv .admin-zone-new.tmp $S
  rm -f .admin-zone-block.tmp
  grep -q 'zone: String' $S || die "แก้ป้ายเขตไม่สำเร็จ"
  echo "AdminOrdersScreen: เพิ่มป้ายเขตแล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $D $S; do o=$(tr -cd '{' < $f | wc -c); c=$(tr -cd '}' < $f | wc -c); p1=$(tr -cd '(' < $f | wc -c); p2=$(tr -cd ')' < $f | wc -c); echo "$f {} $o/$c () $p1/$p2"; done
grep -c 'วินิจฉัย' $S | sed 's/^/ข้อความวินิจฉัยที่เหลือ: /'
grep -n 'zoneLabel\|zone: String' $S | cut -c1-170
echo "เสร็จ"
