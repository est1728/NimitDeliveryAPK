#!/bin/bash
# หน้ารายละเอียดออเดอร์ของแอปแอดมิน: ข้อมูลร้าน/ลูกค้า/สินค้า/ราคา + เปลี่ยนสถานะ + โยนงาน/ถอดงาน + ยกเลิก + แชท
# รันซ้ำได้  ต้องรัน admin-orders-v1.sh มาก่อน
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
S=$J/ui/AdminOrdersScreen.kt
[ -f $S ] || die "ไม่เจอ $S (ต้องรัน admin-orders-v1.sh ก่อน)"
[ -f $J/data/AdminOrders.kt ] || die "ไม่เจอ AdminOrders.kt"

# ---------- แก้หน้ารายการให้กดการ์ดได้ ----------
if grep -q 'onOpen' $S; then
  echo "AdminOrdersScreen: แก้แล้ว ข้าม"
else
  grep -q 'fun AdminOrdersPage(s: AdminSession, store: AdminOrdersStore) {' $S || die "จุดยึด AdminOrdersPage ไม่เจอ"
  grep -q 'items(list, key = { it.id }) { o -> AdminOrderCard(o) }' $S || die "จุดยึด items ไม่เจอ"
  grep -q 'private fun AdminOrderCard(o: AdminOrd) {' $S || die "จุดยึด AdminOrderCard ไม่เจอ"
  grep -q 'Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {' $S || die "จุดยึดการ์ดไม่เจอ"
  sed -i 's/fun AdminOrdersPage(s: AdminSession, store: AdminOrdersStore) {/fun AdminOrdersPage(s: AdminSession, store: AdminOrdersStore, onOpen: (String) -> Unit) {/' $S
  sed -i 's/items(list, key = { it.id }) { o -> AdminOrderCard(o) }/items(list, key = { it.id }) { o -> AdminOrderCard(o) { onOpen(o.id) } }/' $S
  sed -i 's/private fun AdminOrderCard(o: AdminOrd) {/private fun AdminOrderCard(o: AdminOrd, onClick: () -> Unit) {/' $S
  sed -i 's/Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {/Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onClick() }) {/' $S
  grep -q 'onOpen' $S || die "แก้ AdminOrdersScreen ไม่สำเร็จ"
  echo "AdminOrdersScreen: แก้แล้ว"
fi

# ---------- การกระทำต่อ Firestore (ใช้กติกาเดียวกับแอปไรเดอร์: transaction + กันหักกระเป๋าซ้ำ) ----------
cat > $J/data/AdminOrderApi.kt <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await

class AdminRider(val id: String, val name: String, val phone: String)

class AdminBizException(msg: String) : Exception(msg)

fun adminErrorText(e: Throwable): String {
    var t: Throwable? = e
    while (t != null) {
        if (t is AdminBizException) return t.message ?: "เกิดข้อผิดพลาด"
        t = t.cause
    }
    return "เกิดข้อผิดพลาด"
}

object AdminOrderApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun num(n: Double): Any = if (n % 1.0 == 0.0) n.toLong() else n
    private fun inc(n: Double): FieldValue = if (n % 1.0 == 0.0) FieldValue.increment(n.toLong()) else FieldValue.increment(n)

    suspend fun riders(): List<AdminRider> {
        val snap = db.collection("riders").get().await()
        return snap.documents.map { AdminRider(it.id, it.getString("name") ?: "ไรเดอร์", it.get("phone")?.toString() ?: "") }
    }

    // สถิติลูกค้า: (สั่งสำเร็จ, ยกเลิกที่เป็นความผิดลูกค้า)
    suspend fun customerStats(phone: String): Pair<Int, Int>? {
        return try {
            val snap = db.collection("orders").whereEqualTo("customer.phone", phone).get().await()
            var done = 0
            var cancel = 0
            for (d in snap.documents) {
                val st = d.getString("status")
                if (st == "done") done++
                else if (st == "rejected" && d.getString("cancelFault") != "shop") cancel++
            }
            Pair(done, cancel)
        } catch (e: Exception) {
            null
        }
    }

    /** โยนงาน: ตั้งไรเดอร์ใหม่ pending -> accepted (สถานะอื่นที่กำลังทำอยู่คงเดิม) ไม่ให้โยนออเดอร์ที่จบแล้ว */
    suspend fun assign(orderDocId: String, r: AdminRider) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว")
            val upd = HashMap<String, Any?>()
            upd["riderId"] = r.id
            upd["riderName"] = r.name
            upd["riderPhone"] = r.phone
            upd["status"] = if (st == "pending") "accepted" else st
            upd["riderReadAt"] = null
            upd["riderViewingAt"] = null
            upd["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, upd)
            null
        }).await()
    }

    /** ถอดไรเดอร์: ล้างฟิลด์ไรเดอร์ แล้วกลับเป็น pending */
    suspend fun unassign(orderDocId: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว")
            val upd = HashMap<String, Any?>()
            upd["riderId"] = ""
            upd["riderName"] = ""
            upd["riderPhone"] = ""
            upd["status"] = "pending"
            upd["riderReadAt"] = null
            upd["riderViewingAt"] = null
            upd["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(ref, upd)
            null
        }).await()
    }

    /** เลื่อน/ย้อนสถานะแบบ compare-and-set */
    suspend fun advance(orderDocId: String, from: String, to: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val st: String? = tx.get(ref).getString("status")
            if (st == from) tx.update(ref, "status", to, "updatedAt", FieldValue.serverTimestamp())
            else if (st != to) throw AdminBizException("สถานะออเดอร์เปลี่ยนไปแล้ว")
            null
        }).await()
    }

    /** ส่งสำเร็จ: เปลี่ยนเป็น done + หักกระเป๋าไรเดอร์ + เพิ่มกระเป๋ากลาง ใน transaction เดียว (กันซ้ำด้วย walletSettled เหมือนแอปไรเดอร์) */
    suspend fun completeDelivery(orderDocId: String, riderId: String, riderName: String): Boolean {
        val oref = db.collection("orders").document(orderDocId)
        val central = db.collection("wallet").document("central")
        val txRef = db.collection("walletTx").document("settle_" + orderDocId)
        return db.runTransaction(Transaction.Function<Boolean> { tx ->
            val o: DocumentSnapshot = tx.get(oref)
            val st: String? = o.getString("status")
            val settled: Boolean = o.getBoolean("walletSettled") == true
            var fresh = false
            if (!o.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            if (st != "done" && !settled) {
                if (st != "delivering") throw AdminBizException("สถานะออเดอร์เปลี่ยนไปแล้ว")
                val cut: Double = (o.get("walletCut") as? Number)?.toDouble() ?: 0.0
                val rid: String = o.getString("riderId").let { if (it.isNullOrEmpty()) riderId else it }
                val rname: String = o.getString("riderName").let { if (it.isNullOrEmpty()) riderName else it }
                val oid: String = o.getString("orderId").let { if (it.isNullOrEmpty()) orderDocId else it }
                tx.update(oref, "status", "done", "updatedAt", FieldValue.serverTimestamp(), "walletSettled", true)
                if (cut > 0.0 && rid.isNotEmpty()) {
                    val entry = HashMap<String, Any>()
                    entry["riderId"] = rid
                    entry["riderName"] = rname
                    entry["amount"] = num(-cut)
                    entry["type"] = "หักจากออเดอร์"
                    entry["orderId"] = oid
                    entry["note"] = ""
                    entry["createdAt"] = FieldValue.serverTimestamp()
                    entry["by"] = "system"
                    tx.set(txRef, entry)
                    tx.update(db.collection("riders").document(rid), "wallet", inc(-cut))
                    val bal = HashMap<String, Any>()
                    bal["balance"] = inc(cut)
                    tx.set(central, bal, SetOptions.merge())
                }
                fresh = true
            }
            fresh
        }).await()
    }

    /** ยกเลิกออเดอร์: fault = "customer" (หักเครดิตลูกค้า) หรือ "shop" (ไม่หัก) */
    suspend fun cancel(orderDocId: String, note: String, fault: String) {
        val ref = db.collection("orders").document(orderDocId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val s: DocumentSnapshot = tx.get(ref)
            if (!s.exists()) throw AdminBizException("ไม่พบออเดอร์นี้")
            val st: String = s.getString("status") ?: "pending"
            if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้เสร็จสิ้นแล้ว")
            tx.update(ref, "status", "rejected", "cancelNote", note, "cancelFault", fault, "updatedAt", FieldValue.serverTimestamp())
            null
        }).await()
    }
}
KT_EOF

# ---------- หน้ารายละเอียด ----------
cat > $J/ui/AdminOrderDetail.kt <<'KT_EOF'
package com.nimit.delivery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ADMIN_STATUS_TH
import com.nimit.delivery.data.AdminOrd
import com.nimit.delivery.data.AdminOrderApi
import com.nimit.delivery.data.AdminRider
import com.nimit.delivery.data.adminErrorText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val DGreen = Color(0xFF16A34A)
private val DRed = Color(0xFFDC2626)
private val DRedL = Color(0xFFFEE2E2)
private val DLine = Color(0xFFF0F2F5)

private val DNext: Map<String, String> = mapOf(
    "pending" to "accepted", "accepted" to "picking", "picking" to "arrived", "arrived" to "delivering", "delivering" to "done"
)
private val DPrev: Map<String, String> = mapOf(
    "accepted" to "pending", "picking" to "accepted", "arrived" to "picking", "delivering" to "arrived"
)
private val DNextLabel: Map<String, String> = mapOf(
    "pending" to "รับงาน", "accepted" to "เริ่มงาน", "picking" to "ถึงร้านแล้ว", "arrived" to "ขับไปหาลูกค้า", "delivering" to "ส่งสำเร็จ"
)

private fun dPill(st: String): Pair<Color, Color> = when (st) {
    "pending" -> Pair(Color(0xFFFFF7ED), Color(0xFFEA580C))
    "accepted", "picking" -> Pair(C.PrimaryLight, C.Primary)
    "arrived" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
    "delivering" -> Pair(Color(0xFFDCFCE7), DGreen)
    "rejected" -> Pair(DRedL, DRed)
    else -> Pair(Color(0xFFF1F5F9), C.Subtext)
}

private fun dToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun dDial(ctx: Context, phone: String) {
    if (phone.isEmpty()) { dToast(ctx, "ไม่มีเบอร์โทร"); return }
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))) } catch (e: Exception) { dToast(ctx, "โทรไม่ได้") }
}

private fun dMap(ctx: Context, lat: Double?, lng: Double?, addr: String) {
    val q: String = if (lat != null && lng != null) lat.toString() + "," + lng.toString() else Uri.encode(addr)
    if (q.isEmpty()) { dToast(ctx, "ไม่มีพิกัด"); return }
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + q))) } catch (e: Exception) { dToast(ctx, "เปิดแผนที่ไม่ได้") }
}

private fun dMoney(n: Double): String = "฿" + Math.round(n)

private fun dNum(m: Map<*, *>?, k: String): Double? = (m?.get(k) as? Number)?.toDouble()

private fun dItems(o: AdminOrd): List<Map<*, *>> = (o.d["items"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

private fun dOpts(m: Map<*, *>): List<String> {
    val l = m["options"] as? List<*> ?: return emptyList()
    return l.mapNotNull { op ->
        when (op) {
            is String -> op
            is Map<*, *> -> {
                val n = op["name"]?.toString() ?: ""
                val p = (op["price"] as? Number)?.toDouble() ?: 0.0
                if (p > 0.0) n + " +฿" + Math.round(p) else n
            }
            else -> null
        }
    }
}

@Composable
fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<AdminOrd?>(null) }
    var chatUnread by remember { mutableStateOf(0) }
    var riderReadAt by remember { mutableStateOf<Date?>(null) }
    var stats by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirm by remember { mutableStateOf("") }
    var showAssign by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(orderId) {
        val db = FirebaseFirestore.getInstance()
        val r1 = db.collection("orders").document(orderId).addSnapshotListener { s, _ ->
            if (s != null && s.exists()) order = AdminOrd(s.id, s.data ?: emptyMap())
        }
        val r2 = db.collection("chats").document(orderId).addSnapshotListener { s, _ ->
            if (s != null && s.exists()) {
                chatUnread = (s.getLong("unread_admin") ?: 0L).toInt()
                riderReadAt = s.getTimestamp("riderReadAt")?.toDate()
            }
        }
        onDispose {
            r1.remove()
            r2.remove()
        }
    }

    val o: AdminOrd? = order
    val phone: String = o?.cust("phone") ?: ""
    LaunchedEffect(phone) {
        stats = null
        if (phone.isNotEmpty()) stats = AdminOrderApi.customerStats(phone)
    }

    if (o == null) {
        Box(Modifier.fillMaxSize().background(C.Gray), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = C.Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        return
    }

    val st: String = o.status
    val finished: Boolean = st == "done" || st == "rejected"
    val pill = dPill(st)

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        // แถบบน
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("#" + o.orderId, fontSize = 17.sp, fontWeight = FontWeight.Black, color = C.Primary, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pill.first).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(ADMIN_STATUS_TH[st] ?: st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pill.second)
            }
            if (!finished) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(DRedL).clickable { showCancel = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("ยกเลิก", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DRed) }
            }
        }
        HorizontalDivider(color = DLine)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DSection("รายละเอียดออเดอร์") {
                DRow("รหัส", o.orderId)
                DRow("จำนวน", o.qty().toString() + " รายการ")
                DRow("จากร้าน", o.s("shopName").ifEmpty { "-" })
                DRow("การจ่ายเงิน", if (o.s("paymentMethod").isEmpty() || o.s("paymentMethod") == "cash") "เงินสด" else o.s("paymentMethod"))
                DRow("เวลา", o.created?.let { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")).format(it) } ?: "-")
                DRow("สถานะ", ADMIN_STATUS_TH[st] ?: st)
                if (st == "rejected") {
                    val faultTxt = if (o.s("cancelFault") == "shop") "ไม่หักเครดิตลูกค้า" else "หักเครดิตลูกค้า"
                    DRow("เหตุยกเลิก", o.s("cancelNote").ifEmpty { "-" } + " (" + faultTxt + ")", if (o.s("cancelFault") == "shop") DGreen else DRed)
                }
                DRow("ไรเดอร์", o.s("riderName").ifEmpty { "ยังไม่มี" })
                val rr = riderReadAt
                DRow("อ่านออเดอร์", if (rr != null) "อ่านแล้ว เวลา " + SimpleDateFormat("HH:mm", Locale("th", "TH")).format(rr) else "ยังไม่อ่าน", if (rr != null) DGreen else C.Subtext)
            }

            DSection("ข้อมูลร้านค้า") {
                DRow("ชื่อ", o.s("shopName").ifEmpty { "-" })
                DRow("โทร", o.s("shopPhone").ifEmpty { "-" })
                DRow("ที่อยู่", o.s("shopAddress").ifEmpty { "-" }, C.Subtext)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAct("โทรหาร้าน", Modifier.weight(1f), false) { dDial(ctx, o.s("shopPhone")) }
                    DAct("นำทาง", Modifier.weight(1f), false) {
                        val lat = (o.d["shopLat"] as? Number)?.toDouble()
                        val lng = (o.d["shopLng"] as? Number)?.toDouble()
                        if (lat == null || lng == null) dToast(ctx, "ไม่มีพิกัดร้าน") else dMap(ctx, lat, lng, "")
                    }
                }
            }

            DSection("ข้อมูลลูกค้า") {
                val cm = o.d["customer"] as? Map<*, *>
                DRow("ชื่อ", o.cust("name").ifEmpty { "-" })
                DRow("โทร", o.cust("phone").ifEmpty { "-" })
                DRow("ที่จัดส่ง", o.cust("address").ifEmpty { "-" })
                DRow("สั่งสำเร็จ", stats?.let { it.first.toString() + " ครั้ง" } ?: "...", DGreen)
                DRow("ยกเลิก", stats?.let { it.second.toString() + " ครั้ง" } ?: "...", DRed)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAct("โทร", Modifier.weight(1f), false) { dDial(ctx, o.cust("phone")) }
                    DAct(if (chatUnread > 0) "แชท (" + chatUnread + ")" else "แชท", Modifier.weight(1f), true) { onChat() }
                    DAct("นำทาง", Modifier.weight(1f), false) { dMap(ctx, dNum(cm, "lat"), dNum(cm, "lng"), o.cust("address")) }
                }
            }

            DSection("สรุปคำสั่งซื้อ") {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("สินค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.weight(1f))
                    Text("จำนวน", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                    Text("ราคา", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                    Text("รวม", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Subtext, modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                }
                for (it in dItems(o)) {
                    val qty = (it["qty"] as? Number)?.toInt() ?: 0
                    val unit = dNum(it, "price") ?: dNum(it, "basePrice") ?: 0.0
                    HorizontalDivider(color = Color(0xFFF5F6F8))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(it["name"]?.toString() ?: "-", fontSize = 13.sp, color = C.Text)
                            val cat = it["category"]?.toString() ?: ""
                            if (cat.isNotEmpty()) Text("หมวด: " + cat, fontSize = 11.sp, color = Color(0xFF94A3B8))
                            for (op in dOpts(it)) Text("- " + op, fontSize = 12.sp, color = C.Subtext)
                            val note = it["note"]?.toString() ?: ""
                            if (note.isNotEmpty()) Text("หมายเหตุ: " + note, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DRed)
                        }
                        Text(qty.toString(), fontSize = 13.sp, color = C.Text, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                        Text(dMoney(unit), fontSize = 13.sp, color = C.Text, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                        Text(dMoney(unit * qty), fontSize = 13.sp, color = C.Text, modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                    }
                }
            }

            DSection("สรุปราคา") {
                DRow("เรียกเก็บลูกค้า", dMoney(o.n("grandTotal")))
                DRow("จ่ายร้าน (ต้นทุน)", dMoney(o.n("baseCost")))
                DRow("หักจากกระเป๋า", dMoney(o.n("walletCut")))
                DRow("ค่าส่ง", dMoney(o.n("deliveryFee")))
                DRow("รายได้รอบนี้", dMoney(o.n("riderEarn")), C.Primary)
            }
        }

        // แถบปุ่มล่าง
        HorizontalDivider(color = DLine)
        Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (DPrev.containsKey(st)) {
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Gray)
                        .clickable(enabled = !busy) { confirm = "prev" }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("ย้อน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext) }
            }
            val nextOn = !finished && !busy
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (!nextOn) Color(0xFFC5CFE8) else if (st == "delivering") DGreen else C.Primary)
                    .clickable(enabled = nextOn) { confirm = "next" },
                contentAlignment = Alignment.Center
            ) { Text(if (finished) "เสร็จสิ้น" else (DNextLabel[st] ?: "-"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            if (!finished) {
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.PrimaryLight)
                        .clickable(enabled = !busy) { showAssign = true }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("โยนงาน", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary) }
            }
        }
    }

    // ยืนยันเปลี่ยน/ย้อนสถานะ
    if (confirm.isNotEmpty()) {
        val isNext = confirm == "next"
        val target: String = (if (isNext) DNext[st] else DPrev[st]) ?: ""
        val label: String = if (isNext) (DNextLabel[st] ?: "-") else "ย้อนกลับ"
        AlertDialog(
            onDismissRequest = { confirm = "" },
            title = { Text(if (isNext) "เปลี่ยนสถานะ" else "ย้อนกลับสถานะ") },
            text = { Text(if (isNext) label + " ยืนยันหรือไม่" else "ย้อนกลับเป็น \"" + (ADMIN_STATUS_TH[target] ?: target) + "\" ยืนยันหรือไม่") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = ""
                    if (target.isNotEmpty()) {
                        busy = true
                        scope.launch {
                            try {
                                if (isNext && target == "done") {
                                    AdminOrderApi.completeDelivery(orderId, o.s("riderId"), o.s("riderName"))
                                    dToast(ctx, "ออเดอร์เสร็จสิ้น")
                                    onBack()
                                } else {
                                    AdminOrderApi.advance(orderId, st, target)
                                }
                            } catch (e: Exception) {
                                dToast(ctx, adminErrorText(e))
                            }
                            busy = false
                        }
                    }
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { confirm = "" }) { Text("ยกเลิก") } }
        )
    }

    if (showAssign) {
        AdminAssignDialog(
            orderLabel = "ออเดอร์: #" + o.orderId + " · " + o.s("shopName"),
            currentRiderId = o.s("riderId"),
            onDismiss = { showAssign = false },
            onConfirm = { r: AdminRider? ->
                showAssign = false
                busy = true
                scope.launch {
                    try {
                        if (r == null) {
                            AdminOrderApi.unassign(orderId)
                            dToast(ctx, "ถอดไรเดอร์แล้ว")
                        } else {
                            AdminOrderApi.assign(orderId, r)
                            dToast(ctx, "โยนงานให้ " + r.name + " แล้ว")
                        }
                    } catch (e: Exception) {
                        dToast(ctx, adminErrorText(e))
                    }
                    busy = false
                }
            }
        )
    }

    if (showCancel) {
        var fault by remember { mutableStateOf("customer") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("ยกเลิกออเดอร์") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DChip("ลูกค้า (หักเครดิต)", fault == "customer", Modifier.weight(1f)) { fault = "customer" }
                        DChip("ร้าน (ไม่หัก)", fault == "shop", Modifier.weight(1f)) { fault = "shop" }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        placeholder = { Text("เหตุผลที่ยกเลิก") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (note.trim().isEmpty()) {
                        dToast(ctx, "กรุณาใส่เหตุผล")
                    } else {
                        showCancel = false
                        busy = true
                        val n = note.trim()
                        val f = fault
                        scope.launch {
                            try {
                                AdminOrderApi.cancel(orderId, n, f)
                                dToast(ctx, if (f == "shop") "ยกเลิกแล้ว (ไม่หักเครดิตลูกค้า)" else "ยกเลิกออเดอร์แล้ว")
                                onBack()
                            } catch (e: Exception) {
                                dToast(ctx, adminErrorText(e))
                            }
                            busy = false
                        }
                    }
                }) { Text("ยืนยันยกเลิก", color = DRed) }
            },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun AdminAssignDialog(
    orderLabel: String,
    currentRiderId: String,
    onDismiss: () -> Unit,
    onConfirm: (AdminRider?) -> Unit
) {
    var riders by remember { mutableStateOf<List<AdminRider>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try { riders = AdminOrderApi.riders() } catch (e: Exception) { failed = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("โยนงานให้ไรเดอร์") },
        text = {
            Column {
                Text(orderLabel, fontSize = 12.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 8.dp))
                val list = riders
                if (failed) {
                    Text("โหลดไรเดอร์ไม่ได้", color = DRed)
                } else if (list == null) {
                    CircularProgressIndicator(color = C.Primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (currentRiderId.isNotEmpty()) {
                            DPick("ถอดไรเดอร์ออกจากงาน", "", sel == "__unassign__", true) { sel = "__unassign__" }
                        }
                        for (r in list) {
                            DPick(r.name + if (r.id == currentRiderId) " (คนปัจจุบัน)" else "", r.phone, sel == r.id, false) { sel = r.id }
                        }
                        if (list.isEmpty()) Text("ยังไม่มีไรเดอร์", color = C.Subtext, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sel.isNotEmpty(), onClick = {
                if (sel == "__unassign__") onConfirm(null) else onConfirm(riders?.firstOrNull { it.id == sel })
            }) { Text("ยืนยัน") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun DPick(title: String, sub: String, on: Boolean, danger: Boolean, onClick: () -> Unit) {
    val bg = if (on) (if (danger) DRedL else C.PrimaryLight) else Color(0xFFF8FAFC)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }.padding(12.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (danger) DRed else C.Text)
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Subtext)
    }
}

@Composable
private fun DChip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.Primary else C.Gray).clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else C.Subtext) }
}

@Composable
private fun DSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun DRow(label: String, value: String, color: Color = C.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = C.Subtext, modifier = Modifier.width(96.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DAct(text: String, modifier: Modifier, green: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (green) Color(0xFFDCFCE7) else C.PrimaryLight).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (green) DGreen else C.Primary) }
}
KT_EOF

# ---------- นำทางหลัก: รายการ -> รายละเอียด -> แชท ----------
cat > $J/ui/AdminNav.kt <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.AdminRepo
import com.nimit.delivery.data.AdminSession

// เริ่มต้นตัดสินจากเซสชันที่เก็บไว้ทันที (อ่านในเครื่อง ไม่มีหน้า "ตรวจสอบสิทธิ์")
// มีเซสชัน = เข้าหน้าแอดมินเลย / ไม่มี = หน้าล็อกอิน
@Composable
fun AdminNav() {
    val ctx = LocalContext.current
    var session by remember { mutableStateOf(AdminRepo.load(ctx)) }
    val s = session
    if (s == null) {
        AdminLoginScreen(onDone = { session = AdminRepo.load(ctx) })
    } else {
        AdminHome(s, onLogout = { AdminRepo.clear(ctx); session = null })
    }
    AdminUpdatePrompt()
}

private val AdminMenu: List<String> = listOf("ออเดอร์", "ประวัติ", "ร้านค้า", "ไรเดอร์", "ลูกค้า", "ตั้งค่า")

@Composable
fun AdminHome(s: AdminSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var chatId by remember { mutableStateOf<String?>(null) }
    val store = remember(s.franchiseId) { AdminOrdersStore(s) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    val cid = chatId
    val oid = openId
    if (cid != null) {
        BackHandler { chatId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) { ChatScreen(cid, "admin", { chatId = null }) }
    } else if (oid != null) {
        BackHandler { openId = null }
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid })
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Gray)) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                if (tab == 0) {
                    AdminOrdersPage(s, store, onOpen = { openId = it })
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)
                        }
                        if (tab == 5) {
                            Box(
                                Modifier.padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp)).background(C.Primary)
                                    .clickable { onLogout() }.padding(horizontal = 20.dp, vertical = 10.dp)
                            ) { Text("ออกจากระบบ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F2F5))
            Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                for (i in AdminMenu.indices) {
                    val on = tab == i
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) C.Primary else Color.Transparent))
                        Text(
                            AdminMenu[i], fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (on) C.Primary else C.Subtext, modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
KT_EOF

echo "---- ผลตรวจ ----"
for f in $J/data/AdminOrderApi.kt $J/ui/AdminOrderDetail.kt $J/ui/AdminNav.kt $S; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
grep -c 'onOpen' $S | sed 's/^/onOpen ใน AdminOrdersScreen: /'
echo "เสร็จ"
