#!/bin/bash
# หน้า "ประวัติ" ของแอปแอดมิน (ถอดจาก historyPage ใน admin.html): ออเดอร์ที่เสร็จสิ้น/ยกเลิก 200 ใบล่าสุด (เฉพาะร้านในเขต)
# มีปุ่ม "เรียกกลับ" (ใส่หมายเหตุ แล้วออเดอร์กลับเป็นรอรับ ล้างไรเดอร์)  กดการ์ดเปิดรายละเอียด  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
N=$J/ui/AdminNav.kt
[ -f $N ] || die "ไม่เจอ $N"
grep -q 'AdminSettingsPanel' $N || die "AdminNav.kt ยังไม่ผ่าน admin-notify-v1.sh"
[ -f $J/data/AdminOrders.kt ] || die "ไม่เจอ AdminOrders.kt"

# ---------- ข้อมูล ----------
cat > $J/data/AdminHistory.kt <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object AdminHistoryApi {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** ออเดอร์ล่าสุด 200 ใบ แล้วเก็บเฉพาะที่เสร็จสิ้น/ยกเลิก (เหมือนเว็บ) */
    suspend fun load(): List<AdminOrd> {
        val snap = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
        val out = ArrayList<AdminOrd>()
        for (d in snap.documents) {
            val st: String = d.getString("status") ?: ""
            if (st == "done" || st == "rejected") out.add(AdminOrd(d.id, d.data ?: emptyMap()))
        }
        return out
    }

    /** เรียกกลับ: ออเดอร์กลับเป็นรอรับ ล้างไรเดอร์และสถานะอ่าน บันทึกหมายเหตุและเวลาเรียกกลับ */
    suspend fun recall(orderDocId: String, note: String) {
        val m = HashMap<String, Any?>()
        m["status"] = "pending"
        m["riderId"] = null
        m["riderName"] = null
        m["riderPhone"] = null
        m["riderReadAt"] = null
        m["riderViewingAt"] = null
        m["adminNote"] = note
        m["recalledAt"] = FieldValue.serverTimestamp()
        m["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("orders").document(orderDocId).update(m).await()
    }
}
KT_EOF

# ---------- หน้าจอ ----------
cat > $J/ui/AdminHistory.kt <<'KT_EOF'
package com.nimit.delivery.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.nimit.delivery.data.ADMIN_STATUS_TH
import com.nimit.delivery.data.AdminHistoryApi
import com.nimit.delivery.data.AdminOrd
import com.nimit.delivery.data.AdminOrdersStore
import com.nimit.delivery.data.adminErrorText
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

private val HOrange = Color(0xFFEA580C)
private val HOrangeL = Color(0xFFFFF7ED)

@Composable
fun AdminHistoryPage(store: AdminOrdersStore, onOpen: (String) -> Unit, onRecalled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AdminOrd>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var recallFor by remember { mutableStateOf<AdminOrd?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            all = AdminHistoryApi.load()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "โหลดประวัติไม่ได้"
        }
    }
    val scopeIds: Set<String> = store.shopIds.value
    val list: List<AdminOrd>? = all?.filter { scopeIds.contains(it.s("shopId")) }

    Column(Modifier.fillMaxSize().background(C.Gray)) {
        Box(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("ประวัติออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list == null || list.isEmpty()) {
                item {
                    val msg: String = when {
                        error != null -> "โหลดประวัติไม่ได้: " + error
                        list == null -> "กำลังโหลด..."
                        else -> "ยังไม่มีประวัติ"
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(msg, fontSize = 13.sp, color = C.Subtext)
                    }
                }
            }
            if (list != null) {
                items(list, key = { it.id }) { o ->
                    HistoryCard(o, store.zoneLabel(o.s("shopId")), { onOpen(o.id) }, { recallFor = o; note = "" })
                }
            }
        }
    }

    val rf = recallFor
    if (rf != null) {
        AlertDialog(
            onDismissRequest = { recallFor = null },
            title = { Text("เรียกกลับออเดอร์") },
            text = {
                Column {
                    Text("#" + rf.orderId + " · " + rf.s("shopName"), fontSize = 13.sp, color = C.Subtext, modifier = Modifier.padding(bottom = 10.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        placeholder = { Text("หมายเหตุที่เรียกกลับ") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (note.trim().isEmpty()) {
                        Toast.makeText(ctx, "กรุณาใส่หมายเหตุ", Toast.LENGTH_SHORT).show()
                    } else {
                        val n = note.trim()
                        val id = rf.id
                        recallFor = null
                        busy = true
                        scope.launch {
                            try {
                                AdminHistoryApi.recall(id, n)
                                Toast.makeText(ctx, "เรียกกลับสำเร็จ", Toast.LENGTH_SHORT).show()
                                reload++
                                onRecalled()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "เรียกกลับไม่สำเร็จ: " + adminErrorText(e), Toast.LENGTH_SHORT).show()
                            }
                            busy = false
                        }
                    }
                }) { Text("ยืนยัน") }
            },
            dismissButton = { TextButton(onClick = { recallFor = null }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun HistoryCard(o: AdminOrd, zone: String, onClick: () -> Unit, onRecall: () -> Unit) {
    val st = o.status
    val pillBg: Color = if (st == "rejected") Color(0xFFFEE2E2) else Color(0xFFF1F5F9)
    val pillFg: Color = if (st == "rejected") Color(0xFFDC2626) else C.Subtext
    val time: String = o.created?.let { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")).format(it) } ?: "-"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("#" + o.orderId, fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.Primary, letterSpacing = 1.sp)
                if (zone.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(zone, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = C.Subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pillBg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(ADMIN_STATUS_TH[st] ?: st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pillFg)
            }
        }
        HorizontalDivider(color = Color(0xFFF5F6F8))
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.s("shopName").ifEmpty { "-" }, fontSize = 13.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("฿" + Math.round(o.n("grandTotal")), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary)
                }
            }
            Text(
                (o.cust("name").ifEmpty { o.cust("phone").ifEmpty { "-" } }) + " · " + o.qty() + " รายการ",
                fontSize = 13.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 13.sp, color = C.Text)
                val rn = o.s("riderName")
                if (rn.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(C.PrimaryLight).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(rn, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Primary)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(HOrangeL).clickable { onRecall() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("เรียกกลับ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HOrange)
                }
            }
        }
    }
}
KT_EOF
echo "AdminHistory.kt (data+ui): เขียนแล้ว"

# ---------- ต่อเข้าแท็บ "ประวัติ" ----------
if grep -q 'AdminHistoryPage' $N; then
  echo "AdminNav: ต่อแล้ว ข้าม"
else
  grep -q 'if (tab == 5) AdminSettingsPanel() else Text("หน้า" + AdminMenu\[tab\] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)' $N || die "AdminNav: ไม่เจอจุดยึดแท็บ"
  sed -i 's|if (tab == 5) AdminSettingsPanel() else Text("หน้า" + AdminMenu\[tab\] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)|if (tab == 5) AdminSettingsPanel() else if (tab == 1) AdminHistoryPage(store, onOpen = { openId = it }, onRecalled = { tab = 0 }) else Text("หน้า" + AdminMenu[tab] + "อยู่ระหว่างพัฒนา", fontSize = 13.sp, color = C.Subtext)|' $N
  grep -q 'AdminHistoryPage' $N || die "ต่อ AdminNav ไม่สำเร็จ"
  echo "AdminNav: ต่อแล้ว"
fi

echo "---- ผลตรวจ ----"
for f in $J/data/AdminHistory.kt $J/ui/AdminHistory.kt $N; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
echo "เสร็จ"
