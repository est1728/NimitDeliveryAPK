#!/bin/bash
# 1) โหมดแก้ไขออเดอร์ (เหมือน order-create.html?editOrderId=...): โหลดร้าน/ลูกค้า/รายการเดิมมาแก้ แล้วบันทึกทับ (ไม่แตะสถานะ/ไรเดอร์)
#    เปิดจากหน้ารายละเอียดออเดอร์ > ปุ่ม "แก้ไขออเดอร์" > "แก้ไขรายการเมนูในออเดอร์"
# 2) รายชื่อลูกค้า: ถ้าอ่านคอลเลกชัน customers ไม่ได้ (PERMISSION_DENIED) ให้ดึงรายชื่อจากออเดอร์ล่าสุดแทน
# 3) แก้ลูกค้าในหน้ารายละเอียด: บันทึกออเดอร์สำเร็จแม้เขียน customers ไม่ได้  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
CD=$J/data/AdminCreateOrder.kt
CU=$J/ui/AdminCreateOrder.kt
DT=$J/ui/AdminOrderDetail.kt
NV=$J/ui/AdminNav.kt
ED=$J/data/AdminOrderEdit.kt
for f in $CD $CU $DT $NV $ED; do [ -f $f ] || die "ไม่เจอ $f (ต้องรัน admin-create-v1.sh และ admin-detail-v2.sh ก่อน)"; done

# ================= ข้อมูล (AdminCreateOrder.kt) =================
if grep -q 'suspend fun loadForEdit' $CD; then
  echo "data: แก้แล้ว ข้าม"
else
  grep -q '^    suspend fun customers(): List<AdminCust> {$' $CD || die "data: ไม่เจอจุดยึด customers()"
  [ "$(tail -n 1 $CD)" = "}" ] || die "data: บรรทัดสุดท้ายของไฟล์ไม่ใช่ }"
  # (ก) customers(): fallback จากออเดอร์ล่าสุด
  LN=$(grep -n '^    suspend fun customers(): List<AdminCust> {$' $CD | head -1 | cut -d: -f1)
  [ "$(sed -n "$((LN+5))p" $CD)" = "    }" ] || die "data: รูปแบบ customers() ไม่ตรงที่คาด"
  cat > .admin-edit-a.tmp <<'KT_EOF'
    /** รายชื่อลูกค้า: อ่านจาก customers ถ้าไม่ได้สิทธิ์ ใช้ข้อมูลลูกค้าจากออเดอร์ล่าสุด 500 ใบแทน */
    suspend fun customers(): List<AdminCust> {
        try {
            val snap = db.collection("customers").get().await()
            return snap.documents.map {
                AdminCust(it.id, it.getString("name") ?: "", it.getString("address") ?: "", (it.get("lat") as? Number)?.toDouble(), (it.get("lng") as? Number)?.toDouble())
            }
        } catch (e: Exception) {
            val snap = db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).limit(500).get().await()
            val seen = LinkedHashMap<String, AdminCust>()
            for (d in snap.documents) {
                val cm = d.get("customer") as? Map<*, *> ?: continue
                val ph: String = cm["phone"]?.toString() ?: ""
                if (ph.isEmpty() || seen.containsKey(ph)) continue
                seen[ph] = AdminCust(ph, cm["name"]?.toString() ?: "", cm["address"]?.toString() ?: "", (cm["lat"] as? Number)?.toDouble(), (cm["lng"] as? Number)?.toDouble())
            }
            return seen.values.toList()
        }
    }
KT_EOF
  head -n $((LN-1)) $CD > .admin-edit-new.tmp
  cat .admin-edit-a.tmp >> .admin-edit-new.tmp
  tail -n +$((LN+6)) $CD >> .admin-edit-new.tmp
  mv .admin-edit-new.tmp $CD
  grep -q '^import com.google.firebase.firestore.Query$' $CD || sed -i 's|^import com.google.firebase.firestore.FirebaseFirestore$|import com.google.firebase.firestore.FirebaseFirestore\nimport com.google.firebase.firestore.Query|' $CD

  # (ข) loadForEdit / update ต่อท้าย object แล้วปิดด้วย } และเพิ่มคลาสผลลัพธ์
  head -n -1 $CD > .admin-edit-new.tmp
  cat >> .admin-edit-new.tmp <<'KT_EOF'

    /** โหลดออเดอร์เดิมมาแก้: ร้าน/ลูกค้า/รายการ ใช้ข้อมูลที่เก็บไว้ในออเดอร์เอง (ประเภทร้านอ่านจาก shops) */
    suspend fun loadForEdit(orderDocId: String): AdminLoadedOrder {
        val snap = db.collection("orders").document(orderDocId).get().await()
        if (!snap.exists()) throw AdminBizException("ไม่พบออเดอร์นี้ อาจถูกลบไปแล้ว")
        val m: Map<String, Any> = snap.data ?: emptyMap()
        val sid: String = str(m, "shopId")
        var shopType: String = str(m, "shopType").ifEmpty { "normal" }
        try {
            val ss = db.collection("shops").document(sid).get().await()
            if (ss.exists()) shopType = ss.getString("shopType") ?: "normal"
        } catch (e: Exception) { }
        val shop = AdminShopRef(sid, str(m, "shopName"), str(m, "shopPhone"), str(m, "shopAddress"), (m["shopLat"] as? Number)?.toDouble(), (m["shopLng"] as? Number)?.toDouble(), shopType, true)
        val cm = m["customer"] as? Map<*, *>
        val cus = AdminCust(
            cm?.get("phone")?.toString() ?: "", cm?.get("name")?.toString() ?: "", cm?.get("address")?.toString() ?: "",
            (cm?.get("lat") as? Number)?.toDouble(), (cm?.get("lng") as? Number)?.toDouble()
        )
        val lines = ArrayList<AdminCartLine>()
        val raw = m["items"] as? List<*> ?: emptyList<Any?>()
        for (i in raw.indices) {
            val im = raw[i] as? Map<*, *> ?: continue
            val opts = ArrayList<AdminChosen>()
            val ol = im["options"] as? List<*> ?: emptyList<Any?>()
            for (o in ol) {
                val om = o as? Map<*, *> ?: continue
                opts.add(AdminChosen(str(om, "groupTitle"), str(om, "name"), dbl(om["price"], 0.0)))
            }
            val price: Long = Math.round(dbl(im["price"], 0.0))
            val qty: Int = (im["qty"] as? Number)?.toInt() ?: 1
            lines.add(AdminCartLine("edit_" + i, AdminCartItem(str(im, "name"), dbl(im["basePrice"], 0.0), price, qty, str(im, "note"), opts, str(im, "category"))))
        }
        return AdminLoadedOrder(shop, cus, lines, str(m, "status"))
    }

    /** บันทึกการแก้ไขออเดอร์เดิม: เขียนทับเฉพาะ ร้าน/ลูกค้า/รายการ/ราคา ไม่แตะสถานะ ไรเดอร์ รหัส เวลาสร้าง */
    suspend fun update(orderDocId: String, shop: AdminShopRef, cus: AdminCust, items: List<AdminCartItem>, c: AdminOrderCalc) {
        val ref = db.collection("orders").document(orderDocId)
        val cur = ref.get().await()
        if (!cur.exists()) throw AdminBizException("ไม่พบออเดอร์นี้ อาจถูกลบไปแล้ว")
        val st: String = cur.getString("status") ?: "pending"
        if (st == "done" || st == "rejected") throw AdminBizException("ออเดอร์นี้จบงานแล้ว แก้ไขรายการไม่ได้")
        val itemMaps = ArrayList<Map<String, Any?>>()
        for (ci in items) {
            val opts = ArrayList<Map<String, Any?>>()
            for (o in ci.options) {
                val om = HashMap<String, Any?>()
                om["groupTitle"] = o.groupTitle
                om["name"] = o.name
                om["price"] = numAny(o.price)
                opts.add(om)
            }
            val im = HashMap<String, Any?>()
            im["name"] = ci.name
            im["price"] = ci.price
            im["basePrice"] = numAny(ci.basePrice)
            im["qty"] = ci.qty
            im["options"] = opts
            im["note"] = ci.note
            im["imgUrl"] = ""
            im["category"] = ci.category
            itemMaps.add(im)
        }
        val cm = HashMap<String, Any?>()
        cm["name"] = cus.name
        cm["phone"] = cus.phone
        cm["address"] = cus.address
        cm["lat"] = cus.lat
        cm["lng"] = cus.lng
        val data = HashMap<String, Any?>()
        data["shopId"] = shop.id
        data["shopName"] = shop.name
        data["shopType"] = shop.shopType
        data["shopPhone"] = shop.phone
        data["shopAddress"] = shop.address
        data["shopLat"] = shop.lat
        data["shopLng"] = shop.lng
        data["customer"] = cm
        data["items"] = itemMaps
        data["subtotal"] = c.subtotal
        data["baseCost"] = numAny(c.baseCost)
        data["deliveryFee"] = numAny(c.deliveryFee)
        data["grandTotal"] = numAny(c.grandTotal)
        data["totalDiff"] = numAny(c.totalDiff)
        data["walletCut"] = c.walletCut
        data["riderEarn"] = c.riderEarn
        data["paymentMethod"] = "cash"
        data["updatedAt"] = FieldValue.serverTimestamp()
        ref.update(data).await()
    }
}

class AdminLoadedOrder(val shop: AdminShopRef, val cus: AdminCust, val lines: List<AdminCartLine>, val status: String)
KT_EOF
  mv .admin-edit-new.tmp $CD
  rm -f .admin-edit-a.tmp
  grep -q 'suspend fun loadForEdit' $CD || die "data: เพิ่ม loadForEdit ไม่สำเร็จ"
  echo "data: แก้แล้ว"
fi

# ================= หน้าสร้าง/แก้ไข (AdminCreateOrder.kt ui) =================
if grep -q 'editOrderId' $CU; then
  echo "ui(create): แก้แล้ว ข้าม"
else
  grep -q 'fun AdminCreateOrderScreen(store: AdminOrdersStore, onBack: () -> Unit, onCreated: () -> Unit) {' $CU || die "ui: ไม่เจอจุดยึด signature"
  grep -q '    val previewId = remember { AdminCreateApi.randomOrderId() }' $CU || die "ui: ไม่เจอจุดยึด previewId"
  grep -q '    LaunchedEffect(Unit) { pricing = AdminCreateApi.pricing() }' $CU || die "ui: ไม่เจอจุดยึด pricing"
  grep -q 'Text("สร้างออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))' $CU || die "ui: ไม่เจอจุดยึดชื่อหน้า"
  grep -q 'CRow("สถานะ", "รอสร้าง", CYellow)' $CU || die "ui: ไม่เจอจุดยึดสถานะ"
  grep -q 'val oid = AdminCreateApi.create(previewId, s2, c2, items, k2)' $CU || die "ui: ไม่เจอจุดยึด create"
  grep -q '{ Text(if (busy) "กำลังสร้าง..." else "สร้างออเดอร์", fontSize = 17.sp' $CU || die "ui: ไม่เจอจุดยึดปุ่มสร้าง"

  sed -i 's|fun AdminCreateOrderScreen(store: AdminOrdersStore, onBack: () -> Unit, onCreated: () -> Unit) {|fun AdminCreateOrderScreen(store: AdminOrdersStore, onBack: () -> Unit, onCreated: () -> Unit, editOrderId: String? = null) {|' $CU
  sed -i 's|    val previewId = remember { AdminCreateApi.randomOrderId() }|    val previewId = remember { editOrderId ?: AdminCreateApi.randomOrderId() }|' $CU
  sed -i 's|Text("สร้างออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))|Text(if (editOrderId != null) "แก้ไขออเดอร์" else "สร้างออเดอร์", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))|' $CU
  sed -i 's|CRow("สถานะ", "รอสร้าง", CYellow)|CRow("สถานะ", if (editOrderId != null) (ADMIN_STATUS_TH[editStatus] ?: editStatus) else "รอสร้าง", CYellow)|' $CU
  sed -i 's|{ Text(if (busy) "กำลังสร้าง..." else "สร้างออเดอร์", fontSize = 17.sp|{ Text(if (busy) (if (editOrderId != null) "กำลังบันทึก..." else "กำลังสร้าง...") else (if (editOrderId != null) "บันทึกการแก้ไข" else "สร้างออเดอร์"), fontSize = 17.sp|' $CU
  sed -i 's|^import com.nimit.delivery.data.AdminCartItem$|import com.nimit.delivery.data.ADMIN_STATUS_TH\nimport com.nimit.delivery.data.AdminCartItem|' $CU

  # โหลดออเดอร์เดิม (โหมดแก้ไข)
  cat > .admin-edit-b.tmp <<'KT_EOF'
    var editStatus by remember { mutableStateOf("") }
    LaunchedEffect(editOrderId) {
        if (editOrderId != null) {
            try {
                val loaded = AdminCreateApi.loadForEdit(editOrderId)
                shop = loaded.shop
                cus = loaded.cus
                cart.clear()
                cart.addAll(loaded.lines)
                editStatus = loaded.status
            } catch (e: Exception) {
                cToast(ctx, "โหลดออเดอร์เดิมไม่สำเร็จ: " + (e.message ?: ""))
            }
        }
    }
KT_EOF
  LN=$(grep -n '^    LaunchedEffect(Unit) { pricing = AdminCreateApi.pricing() }$' $CU | head -1 | cut -d: -f1)
  head -n $LN $CU > .admin-edit-new.tmp
  cat .admin-edit-b.tmp >> .admin-edit-new.tmp
  tail -n +$((LN+1)) $CU >> .admin-edit-new.tmp
  mv .admin-edit-new.tmp $CU

  # บันทึก: สร้างใหม่ หรือ แก้ไขของเดิม
  cat > .admin-edit-c.tmp <<'KT_EOF'
                                        if (editOrderId != null) {
                                            AdminCreateApi.update(editOrderId, s2, c2, items, k2)
                                            cToast(ctx, "บันทึกการแก้ไขออเดอร์แล้ว")
                                        } else {
                                            val oid = AdminCreateApi.create(previewId, s2, c2, items, k2)
                                            cToast(ctx, "สร้างออเดอร์ " + oid + " แล้ว")
                                        }
KT_EOF
  LN=$(grep -n 'val oid = AdminCreateApi.create(previewId, s2, c2, items, k2)' $CU | head -1 | cut -d: -f1)
  sed -n "$((LN+1))p" $CU | grep -q 'cToast(ctx, "สร้างออเดอร์ " + oid + " แล้ว")' || die "ui: รูปแบบบรรทัดหลัง create ไม่ตรงที่คาด"
  head -n $((LN-1)) $CU > .admin-edit-new.tmp
  cat .admin-edit-c.tmp >> .admin-edit-new.tmp
  tail -n +$((LN+2)) $CU >> .admin-edit-new.tmp
  mv .admin-edit-new.tmp $CU
  rm -f .admin-edit-b.tmp .admin-edit-c.tmp
  grep -q 'AdminCreateApi.update(editOrderId' $CU || die "ui: แก้ปุ่มบันทึกไม่สำเร็จ"
  echo "ui(create): แก้แล้ว"
fi

# ================= หน้ารายละเอียด: ปุ่มเปิดหน้าแก้ไขรายการ =================
if grep -q 'onEditMenu' $DT; then
  echo "ui(detail): แก้แล้ว ข้าม"
else
  grep -q 'fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: () -> Unit) {' $DT || die "detail: ไม่เจอจุดยึด signature"
  grep -q '^    onSave: (List<String>) -> Unit$' $DT || die "detail: ไม่เจอจุดยึด DEditDialog"
  grep -q 'Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {' $DT || die "detail: ไม่เจอจุดยึดเนื้อหาหน้าต่างแก้ไข"
  grep -q 'listOf(Math.round(o.n("deliveryFee")).toString(), o.s("adminNote")), listOf(KeyboardType.Number, KeyboardType.Text),' $DT || die "detail: ไม่เจอจุดยึดหน้าต่างแก้ไขออเดอร์"
  sed -i 's|fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: () -> Unit) {|fun AdminOrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: () -> Unit, onEditMenu: () -> Unit = {}) {|' $DT
  sed -i 's|^    onSave: (List<String>) -> Unit$|    onSave: (List<String>) -> Unit,\n    extra: (@Composable () -> Unit)? = null|' $DT
  sed -i 's|^\( *\)Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {$|&\n\1    if (extra != null) extra()|' $DT
  cat > .admin-edit-d.tmp <<'KT_EOF'
            extra = {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(C.PrimaryLight)
                        .clickable { editKind = ""; onEditMenu() },
                    contentAlignment = Alignment.Center
                ) { Text("แก้ไขรายการเมนูในออเดอร์ ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Primary) }
            },
KT_EOF
  LN=$(grep -n 'listOf(Math.round(o.n("deliveryFee")).toString(), o.s("adminNote")), listOf(KeyboardType.Number, KeyboardType.Text),' $DT | head -1 | cut -d: -f1)
  head -n $LN $DT > .admin-edit-new.tmp
  cat .admin-edit-d.tmp >> .admin-edit-new.tmp
  tail -n +$((LN+1)) $DT >> .admin-edit-new.tmp
  mv .admin-edit-new.tmp $DT
  rm -f .admin-edit-d.tmp
  grep -q 'extra = {' $DT && grep -q 'if (extra != null) extra()' $DT || die "detail: แก้ไม่สำเร็จ"
  echo "ui(detail): แก้แล้ว"
fi

# ================= นำทางหลัก =================
if grep -q 'editOrderId = eid' $NV; then
  echo "nav: แก้แล้ว ข้าม"
else
  grep -q '^    var creating by remember { mutableStateOf(false) }$' $NV || die "nav: ไม่เจอจุดยึด creating"
  grep -q '^    val oid = openId$' $NV || die "nav: ไม่เจอจุดยึด oid"
  grep -q '^    } else if (cid != null) {$' $NV || die "nav: ไม่เจอจุดยึด cid"
  grep -q 'AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid })' $NV || die "nav: ไม่เจอจุดยึดการเรียกหน้ารายละเอียด"
  sed -i 's|^    var creating by remember { mutableStateOf(false) }$|    var creating by remember { mutableStateOf(false) }\n    var editId by remember { mutableStateOf<String?>(null) }|' $NV
  sed -i 's|^    val oid = openId$|    val oid = openId\n    val eid = editId|' $NV
  sed -i 's|^    } else if (cid != null) {$|    } else if (eid != null) {\n        BackHandler { editId = null }\n        Box(Modifier.fillMaxSize().statusBarsPadding()) {\n            AdminCreateOrderScreen(store, onBack = { editId = null }, onCreated = { editId = null }, editOrderId = eid)\n        }\n    } else if (cid != null) {|' $NV
  sed -i 's|AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid })|AdminOrderDetailScreen(oid, onBack = { openId = null }, onChat = { chatId = oid }, onEditMenu = { editId = oid })|' $NV
  grep -q 'editOrderId = eid' $NV && grep -q 'onEditMenu = { editId = oid }' $NV || die "nav: แก้ไม่สำเร็จ"
  echo "nav: แก้แล้ว"
fi

# ================= แก้ลูกค้าในรายละเอียด: ไม่ให้ล้มทั้งงานถ้าเขียน customers ไม่ได้ =================
if grep -q 'catch (e: Exception) { }' $ED && grep -q 'try { db.collection("customers")' $ED; then
  echo "edit: แก้แล้ว ข้าม"
else
  grep -q 'db.collection("customers").document(phone).set(c, SetOptions.merge()).await()' $ED || die "edit: ไม่เจอจุดยึด customers set"
  sed -i 's|db.collection("customers").document(phone).set(c, SetOptions.merge()).await()|try { db.collection("customers").document(phone).set(c, SetOptions.merge()).await() } catch (e: Exception) { }|' $ED
  echo "edit: แก้แล้ว"
fi

rm -f .admin-edit-*.tmp
echo "---- ผลตรวจ ----"
for f in $CD $CU $DT $NV $ED; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  p1=$(tr -cd '(' < "$f" | wc -c); p2=$(tr -cd ')' < "$f" | wc -c)
  echo "$f  {} $o/$c  () $p1/$p2"
done
echo "เสร็จ"
