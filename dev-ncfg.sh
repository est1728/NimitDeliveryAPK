#!/bin/bash
# หน้า "ตั้งค่าแจ้งเตือน" ในแอป dev (ข้อความแจ้งเตือนทุกแอป เก็บที่ appConfig/notifications) รันซ้ำได้
set -e
D=app/src/dev/java/com/nimit/delivery
N="$D/ui/DevNav.kt"
[ -f "$N" ] && [ -f "$D/data/DevRepo.kt" ] || { echo "หยุด: ไม่เจอไฟล์ dev ชุดก่อนหน้า (รันใน ~/nimit-customer หรือเปล่า)"; exit 1; }
cat > "$D/data/DevNotifyConfig.kt" <<'KT_EOF'
package com.nimit.delivery.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// รายการเหตุการณ์แจ้งเตือนที่มีอยู่จริงในระบบ (จากโค้ดแอปและ rider.html เดิม) vars = ตัวแปรที่ผู้ส่งแนบได้
data class DevEventDef(val id: String, val group: String, val label: String, val vars: String)

// title/body ว่าง = ใช้ข้อความเดิมของแอป/ผู้ส่ง
data class DevEventCfg(val enabled: Boolean, val title: String, val body: String)

/**
 * ตั้งค่าข้อความแจ้งเตือนจากแอป dev เก็บที่ appConfig/notifications:
 *   { events: { "<รหัสเหตุการณ์>": { enabled, title, body } }, updatedAt, updatedBy }
 * ตัวแปรในข้อความเขียนเป็น {orderId} {shopName} ฯลฯ ผู้อ่านค่า (เซิร์ฟเวอร์/แอป) เป็นคนแทนที่
 * ทุกครั้งที่บันทึกจะเขียนประวัติลง devConfigLog (เพิ่มอย่างเดียว) ในครั้งเดียวกัน
 */
object DevNotifyConfig {
    val groups = listOf(
        "rider" to "ไรเดอร์ (ในแอปไรเดอร์)",
        "status" to "ลูกค้า (สถานะจากไรเดอร์)",
        "order" to "ออเดอร์ใหม่",
        "shop" to "ร้านค้า",
        "chat" to "แชท"
    )

    val events = listOf(
        DevEventDef("rider_new_job", "rider", "งานใหม่มาแล้ว (แบนเนอร์+เสียงในแอปไรเดอร์)", "{orderId} {shopName}"),
        DevEventDef("rider_accepted", "rider", "รับงานแล้ว", "{orderId}"),
        DevEventDef("rider_assigned", "rider", "ถูกมอบหมายงาน", "{orderId}"),
        DevEventDef("rider_reassigned", "rider", "งานถูกถอด/โอน (แบนเนอร์สีแดง)", "{orderId}"),
        DevEventDef("status_accepted", "status", "ไรเดอร์รับงานแล้ว", "{orderId} {riderName} {riderPhone}"),
        DevEventDef("status_picking", "status", "ไรเดอร์กำลังไปร้าน", "{orderId} {riderName}"),
        DevEventDef("status_arrived", "status", "ไรเดอร์ถึงร้านแล้ว", "{orderId} {riderName}"),
        DevEventDef("status_delivering", "status", "กำลังนำอาหารมาส่ง", "{orderId} {riderName}"),
        DevEventDef("status_done", "status", "จัดส่งสำเร็จแล้ว", "{orderId}"),
        DevEventDef("order_new", "order", "มีออเดอร์ใหม่ (ถึงร้าน/แอดมิน/ไรเดอร์)", "{orderId} {shopName} {total}"),
        DevEventDef("shop_new_order", "shop", "ออเดอร์ใหม่ในแอปร้านค้า", "{orderId} {total}"),
        DevEventDef("chat_message", "chat", "ข้อความแชทใหม่", "{orderId}")
    )

    private fun cfgOf(m: Map<String, Any?>): DevEventCfg = DevEventCfg(
        enabled = (m["enabled"] as? Boolean) ?: true,
        title = (m["title"] as? String) ?: "",
        body = (m["body"] as? String) ?: ""
    )

    suspend fun load(): Map<String, DevEventCfg> {
        val d = FirebaseFirestore.getInstance().collection("appConfig").document("notifications").get().await()
        val ev = d.get("events") as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, DevEventCfg>()
        for ((k, v) in ev) {
            val m = (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: continue
            out[k.toString()] = cfgOf(m)
        }
        return out
    }

    /** คืนข้อความผิดพลาด หรือ null ถ้าสำเร็จ */
    suspend fun save(def: DevEventDef, cfg: DevEventCfg, before: DevEventCfg?): String? {
        val email = DevRepo.currentEmail() ?: return "ยังไม่ได้เข้าสู่ระบบ"
        return try {
            val db = FirebaseFirestore.getInstance()
            val b = db.batch()
            b.set(
                db.collection("appConfig").document("notifications"),
                mapOf(
                    "events" to mapOf(
                        def.id to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                    ),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to email
                ),
                SetOptions.merge()
            )
            b.set(
                db.collection("devConfigLog").document(),
                mapOf(
                    "by" to email,
                    "at" to FieldValue.serverTimestamp(),
                    "event" to def.id,
                    "label" to def.label,
                    "before" to mapOf(
                        "enabled" to (before?.enabled ?: true),
                        "title" to (before?.title ?: ""),
                        "body" to (before?.body ?: "")
                    ),
                    "after" to mapOf("enabled" to cfg.enabled, "title" to cfg.title.trim(), "body" to cfg.body.trim())
                )
            )
            b.commit().await()
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            devWriteError(ex)
        }
    }
}
KT_EOF
cat > "$D/ui/DevNotifyConfig.kt" <<'KT_EOF'
package com.nimit.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimit.delivery.data.DevEventCfg
import com.nimit.delivery.data.DevEventDef
import com.nimit.delivery.data.DevNotifyConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ตั้งค่าข้อความแจ้งเตือนของทุกแอป (เปิด/ปิด หัวข้อ เนื้อหา) เว้นว่าง = ใช้ข้อความเดิมของแอป
@Composable
fun DevNotifyConfigScreen() {
    val cfgs = remember { mutableStateMapOf<String, DevEventCfg>() }
    var loaded by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf("rider") }

    LaunchedEffect(tick) {
        loaded = false
        err = false
        try {
            val m = DevNotifyConfig.load()
            cfgs.clear()
            cfgs.putAll(m)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = true
        }
        loaded = true
    }

    DevCard {
        Text("ตั้งค่าข้อความแจ้งเตือน", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DevC.P, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "เว้นหัวข้อ/เนื้อหาว่างไว้ = ใช้ข้อความเดิมของแอป ตัวแปรเช่น {orderId} จะถูกแทนที่โดยผู้ส่ง/เซิร์ฟเวอร์ " +
                "ค่าที่ตั้งจะมีผลเมื่อเซิร์ฟเวอร์หรือแอปนั้นถูกแก้ให้อ่านค่า (ยังไม่ได้เปิดใช้ในทุกแอป)",
            fontSize = 11.5.sp, color = DevC.S, lineHeight = 17.sp
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)
    ) {
        DevNotifyConfig.groups.forEach { (id, label) ->
            Text(
                label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (group == id) Color.White else DevC.P,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (group == id) DevC.P else DevC.PL)
                    .clickable { group = id }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
    if (err) {
        DevCard(Modifier.clickable { tick++ }) {
            Text("โหลดค่าที่ตั้งไว้ไม่สำเร็จ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.R)
            Text("แตะเพื่อลองใหม่ (ตรวจสอบอินเทอร์เน็ตและสิทธิ์อ่านข้อมูล)", fontSize = 11.5.sp, color = DevC.S, modifier = Modifier.padding(top = 4.dp))
        }
    } else if (!loaded) {
        DevCard { Text("กำลังโหลด...", fontSize = 13.sp, color = DevC.S) }
    } else {
        DevNotifyConfig.events.filter { it.group == group }.forEach { def ->
            DevEventCard(def, cfgs[def.id]) { saved -> cfgs[def.id] = saved }
        }
    }
}

@Composable
private fun DevEventCard(def: DevEventDef, cfg: DevEventCfg?, onSaved: (DevEventCfg) -> Unit) {
    var enabled by remember(cfg) { mutableStateOf(cfg?.enabled ?: true) }
    var title by remember(cfg) { mutableStateOf(cfg?.title ?: "") }
    var body by remember(cfg) { mutableStateOf(cfg?.body ?: "") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = enabled != (cfg?.enabled ?: true) || title.trim() != (cfg?.title ?: "") || body.trim() != (cfg?.body ?: "")

    DevCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(def.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DevC.T)
                Text("ตัวแปร: " + def.vars, fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(
                checked = enabled, onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DevC.Gn)
            )
        }
        Text("หัวข้อ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        DevCfgInput(title, { title = it }, "เว้นว่าง = ใช้ข้อความเดิม", 1)
        Text("เนื้อหา", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DevC.S, modifier = Modifier.padding(bottom = 6.dp))
        DevCfgInput(body, { body = it }, "เว้นว่าง = ใช้ข้อความเดิม", 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (busy) "กำลังบันทึก..." else "บันทึก", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                color = if (dirty) Color.White else Color(0xFF94A3B8),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (dirty) DevC.P else Color(0xFFF1F5F9))
                    .clickable(enabled = dirty && !busy) {
                        busy = true
                        msg = ""
                        val next = DevEventCfg(enabled, title.trim(), body.trim())
                        scope.launch {
                            val r = DevNotifyConfig.save(def, next, cfg)
                            busy = false
                            if (r == null) {
                                msg = "บันทึกแล้ว และบันทึกประวัติแล้ว"
                                msgErr = false
                                onSaved(next)
                            } else {
                                msg = r
                                msgErr = true
                            }
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
            if (msg.isNotEmpty()) {
                Text(msg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (msgErr) DevC.R else DevC.Gn, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DevCfgInput(value: String, onChange: (String) -> Unit, hint: String, lines: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = TextStyle(fontSize = 14.sp, color = DevC.T),
        cursorBrush = SolidColor(DevC.P),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = Color(0xFF94A3B8))
                inner()
            }
        }
    )
}
KT_EOF
if ! grep -q "DevNotifyConfigScreen" "$N"; then
  grep -q 'DevPage("notif", "แจ้งเตือน"' "$N" || { echo "หยุด: ไม่เจอรายการเมนู แจ้งเตือน ใน DevNav.kt (ส่งผล grep -n DevPage $N มาให้ดู)"; exit 1; }
  grep -q "DevAuditScreen() else DevCard {" "$N" || { echo "หยุด: ไม่เจอจุดยึดเส้นทางหน้าใน DevNav.kt (ส่งผล grep -n 'cur ==' $N มาให้ดู)"; exit 1; }
  sed -i 's/^\(    DevPage("notif", "แจ้งเตือน", "[^"]*")\)$/\1,\n    DevPage("notifcfg", "ตั้งค่าแจ้งเตือน", "ตั้งค่าข้อความแจ้งเตือนของทุกแอป")/' "$N"
  sed -i 's/DevAuditScreen() else DevCard {/DevAuditScreen() else if (cur == "notifcfg") DevNotifyConfigScreen() else DevCard {/' "$N"
fi
echo "== ตรวจผล =="
grep -n "notifcfg" "$N"
for f in data/DevNotifyConfig.kt ui/DevNotifyConfig.kt; do printf "%s  %s บรรทัด\n" "$f" "$(wc -l < "$D/$f")"; done
git status --short
