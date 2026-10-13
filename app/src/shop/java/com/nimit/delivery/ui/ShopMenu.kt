package com.nimit.delivery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nimit.delivery.data.shopNum
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// ---------- โมเดลเมนูในหน่วยความจำ (ทุกช่องเป็น state เพื่อให้หน้าจออัปเดตตามที่พิมพ์) ----------
class MCat(val id: String, var name: String)

class MSubOpt(name: String = "", price: String = "0") {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
}

class MSubGroup(val type: String) {
    var title by mutableStateOf("")
    var required by mutableStateOf(false)
    var maxSelect by mutableStateOf("")
    var priceMin by mutableStateOf("0")
    var priceMax by mutableStateOf("0")
    var priceStep by mutableStateOf("5")
    var kgRate by mutableStateOf("0")
    val options = mutableStateListOf<MSubOpt>()
}

class MOption(name: String = "", price: String = "0") {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
    val subGroups = mutableStateListOf<MSubGroup>()
}

class MGroup(val type: String) {
    var title by mutableStateOf("")
    var required by mutableStateOf(false)
    var maxSelect by mutableStateOf("")
    var priceMin by mutableStateOf("0")
    var priceMax by mutableStateOf("0")
    var priceStep by mutableStateOf("5")
    var kgRate by mutableStateOf("0")
    val options = mutableStateListOf<MOption>()
}

class MMenu(var id: String) {
    var name by mutableStateOf("")
    var price by mutableStateOf("")
    var categoryId by mutableStateOf("")
    var category by mutableStateOf("")
    var gp by mutableStateOf("0")
    var desc by mutableStateOf("")
    var imgUrl by mutableStateOf("")
    var available by mutableStateOf(true)
    var order by mutableIntStateOf(0)
    var schedEnabled by mutableStateOf(false)
    var schedOpen by mutableStateOf("")
    var schedClose by mutableStateOf("")
    val groups = mutableStateListOf<MGroup>()
}

// ---------- ตัวช่วยแปลงค่า ----------
private fun dn(a: Any?): Double = (a as? Number)?.toDouble() ?: 0.0
private fun ns(v: Double, blankZero: Boolean = false): String = if (blankZero && v == 0.0) "" else shopNum(v)
internal fun pd(s: String): Double = s.trim().toDoubleOrNull() ?: 0.0
private fun pn(d: Double): Any = if (d == Math.rint(d) && abs(d) < 1e15) d.toLong() else d
private fun stepOr5(s: String): Double { val v: Double = pd(s); return if (v == 0.0) 5.0 else v }
private fun mapOf2(a: Any?): Map<String, Any?> {
    val m = a as? Map<*, *> ?: return emptyMap()
    val r = HashMap<String, Any?>()
    for ((k, v) in m) r[k.toString()] = v
    return r
}

private fun parseSub(m: Map<String, Any?>): MSubGroup {
    val type: String = m["type"]?.toString() ?: "normal"
    val sg = MSubGroup(if (type == "price" || type == "kg") type else "normal")
    sg.title = m["title"]?.toString() ?: ""
    sg.required = m["required"] == true
    if (sg.type == "price") {
        sg.priceMin = ns(dn(m["priceMin"])); sg.priceMax = ns(dn(m["priceMax"]))
        sg.priceStep = ns(if (dn(m["priceStep"]) == 0.0) 5.0 else dn(m["priceStep"]))
    } else if (sg.type == "kg") {
        sg.kgRate = ns(dn(m["kgRate"]))
    } else {
        // รองรับข้อมูลเก่า: ไม่มี maxSelect → ดูจาก multiSelect (true = ไม่จำกัด, อื่นๆ = 1)
        val mx: Int = if (m["maxSelect"] != null) dn(m["maxSelect"]).toInt() else if (m["multiSelect"] == true) 0 else 1
        sg.maxSelect = if (mx == 0) "" else mx.toString()
        val l = m["options"] as? List<*>
        if (l != null) for (x in l) {
            val o = mapOf2(x)
            sg.options.add(MSubOpt(o["name"]?.toString() ?: "", ns(dn(o["price"]))))
        }
    }
    return sg
}

private fun parseGroup(m: Map<String, Any?>): MGroup {
    val type: String = m["type"]?.toString() ?: "normal"
    val g = MGroup(if (type == "price" || type == "kg") type else "normal")
    g.title = m["title"]?.toString() ?: ""
    g.required = m["required"] == true
    if (g.type == "price") {
        g.priceMin = ns(dn(m["priceMin"])); g.priceMax = ns(dn(m["priceMax"]))
        g.priceStep = ns(if (dn(m["priceStep"]) == 0.0) 5.0 else dn(m["priceStep"]))
    } else if (g.type == "kg") {
        g.kgRate = ns(dn(m["kgRate"]))
    } else {
        val mx: Int = dn(m["maxSelect"]).toInt()
        g.maxSelect = if (mx == 0) "" else mx.toString()
        val l = m["options"] as? List<*>
        if (l != null) for (x in l) {
            val o = mapOf2(x)
            val op = MOption(o["name"]?.toString() ?: "", ns(dn(o["price"])))
            val sgs = o["subGroups"] as? List<*>
            if (sgs != null) for (s in sgs) op.subGroups.add(parseSub(mapOf2(s)))
            g.options.add(op)
        }
    }
    return g
}

fun shopParseMenu(id: String, d: Map<String, Any?>): MMenu {
    val m = MMenu(id)
    m.name = d["name"]?.toString() ?: ""
    m.price = ns(dn(d["price"]), true)
    m.categoryId = d["categoryId"]?.toString() ?: ""
    m.category = d["category"]?.toString() ?: ""
    m.gp = ns(dn(d["gp"]))
    m.desc = d["desc"]?.toString() ?: ""
    m.imgUrl = d["imgUrl"]?.toString() ?: ""
    m.available = d["available"] != false
    m.order = dn(d["order"]).toInt()
    m.schedEnabled = d["schedEnabled"] == true
    m.schedOpen = d["schedOpen"]?.toString() ?: ""
    m.schedClose = d["schedClose"]?.toString() ?: ""
    val gs = d["optionGroups"] as? List<*>
    if (gs != null) for (x in gs) m.groups.add(parseGroup(mapOf2(x)))
    return m
}

private fun saveSub(sg: MSubGroup): Map<String, Any?> {
    val r = LinkedHashMap<String, Any?>()
    r["type"] = sg.type; r["title"] = sg.title; r["required"] = sg.required
    if (sg.type == "price") {
        r["priceMin"] = pn(pd(sg.priceMin)); r["priceMax"] = pn(pd(sg.priceMax)); r["priceStep"] = pn(stepOr5(sg.priceStep))
    } else if (sg.type == "kg") {
        r["kgRate"] = pn(pd(sg.kgRate))
    } else {
        r["maxSelect"] = sg.maxSelect.trim().toIntOrNull() ?: 0
        r["options"] = sg.options.filter { it.name.isNotEmpty() }.map { o ->
            val x = LinkedHashMap<String, Any?>(); x["name"] = o.name; x["price"] = pn(pd(o.price)); x
        }
    }
    return r
}

private fun saveGroups(m: MMenu): List<Map<String, Any?>> {
    return m.groups.filter { it.title.isNotEmpty() }.map { g ->
        val r = LinkedHashMap<String, Any?>()
        r["type"] = g.type; r["title"] = g.title
        if (g.type == "price") {
            r["priceMin"] = pn(pd(g.priceMin)); r["priceMax"] = pn(pd(g.priceMax)); r["priceStep"] = pn(stepOr5(g.priceStep))
        } else if (g.type == "kg") {
            r["required"] = g.required; r["kgRate"] = pn(pd(g.kgRate))
        } else {
            r["required"] = g.required
            r["maxSelect"] = g.maxSelect.trim().toIntOrNull() ?: 0
            r["options"] = g.options.filter { it.name.isNotEmpty() }.map { o ->
                val x = LinkedHashMap<String, Any?>()
                x["name"] = o.name; x["price"] = pn(pd(o.price))
                x["subGroups"] = o.subGroups.filter { it.title.isNotEmpty() }.map { saveSub(it) }
                x
            }
        }
        r
    }
}

fun shopCopySub(s: MSubGroup): MSubGroup {
    val n = MSubGroup(s.type)
    n.title = s.title; n.required = s.required; n.maxSelect = s.maxSelect
    n.priceMin = s.priceMin; n.priceMax = s.priceMax; n.priceStep = s.priceStep; n.kgRate = s.kgRate
    for (o in s.options) n.options.add(MSubOpt(o.name, o.price))
    return n
}

fun shopCopyGroup(g: MGroup): MGroup {
    val n = MGroup(g.type)
    n.title = g.title; n.required = g.required; n.maxSelect = g.maxSelect
    n.priceMin = g.priceMin; n.priceMax = g.priceMax; n.priceStep = g.priceStep; n.kgRate = g.kgRate
    for (o in g.options) {
        val no = MOption(o.name, o.price)
        for (s in o.subGroups) no.subGroups.add(shopCopySub(s))
        n.options.add(no)
    }
    return n
}

/** ป๊อบอัพข้อความ/ยืนยัน/ถามชื่อ (แทน alert/confirm/prompt ในเว็บ) */
class MDialog(val kind: String, val title: String, val text: String, val onOk: (String) -> Unit)

/** สถานะและตรรกะของส่วนหมวดหมู่/เมนู (พอร์ตจาก shop-manage.html) */
class ShopMenuState(val shopId: String) {
    val categories = mutableStateListOf<MCat>()
    val menus = mutableStateListOf<MMenu>()
    var openMenuId by mutableStateOf<String?>(null)
    var openCatId by mutableStateOf<String?>(null)
    var shopType by mutableStateOf("normal")
    var loadError by mutableStateOf<String?>(null)
    val expanded = mutableStateListOf<String>()

    private val dirtyIds = HashSet<String>()
    private var dirtyVersion by mutableIntStateOf(0)
    val hasDirty: Boolean get() { dirtyVersion; return dirtyIds.isNotEmpty() }

    // ป๊อบอัพ/แผ่นล่างต่างๆ
    var dialog by mutableStateOf<MDialog?>(null)
    var moveCatFor by mutableStateOf<MMenu?>(null)
    var pickCatFor by mutableStateOf<MMenu?>(null)
    var addModeCat by mutableStateOf<String?>(null)
    var gtypeFor by mutableStateOf<MMenu?>(null)
    var sgtypeFor by mutableStateOf<Triple<MMenu, Int, Int>?>(null)
    var copyFor by mutableStateOf<MMenu?>(null)
    val copyGroups = mutableStateListOf<Int>()
    val copyMenus = mutableStateListOf<String>()
    var applyFor by mutableStateOf<MMenu?>(null)
    val applyMenus = mutableStateListOf<String>()
    var requestImage: (String) -> Unit = { }
    var toast: (String) -> Unit = { }

    // บันทึกกลุ่มจำนวนมาก
    var bulkVisible by mutableStateOf(false)
    var bulkCatId by mutableStateOf("")
    val bulkIds = mutableStateListOf<String>()
    var bulkOpenId by mutableStateOf<String?>(null)
    var bulkSaving by mutableStateOf(false)
    private var savedOpenMenuId: String? = null

    // GP (settings/pricing)
    private var gpPercent = 0.0
    private var gpSubPercent = 0.0
    private var gpTiers: List<Map<String, Any?>> = emptyList()
    private var gpSubTiers: List<Map<String, Any?>> = emptyList()

    private var reg: ListenerRegistration? = null
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun loadGp() {
        try {
            val s = db.collection("settings").document("pricing").get().await()
            val d: Map<String, Any?>? = s.data
            if (d != null) {
                gpPercent = dn(d["gpPercent"]); gpSubPercent = dn(d["gpSubPercent"])
                gpTiers = (d["gpTiers"] as? List<*>)?.map { mapOf2(it) } ?: emptyList()
                gpSubTiers = (d["gpSubTiers"] as? List<*>)?.map { mapOf2(it) } ?: emptyList()
            }
        } catch (e: Exception) { }
    }

    private fun tierGp(base: Double, tiers: List<Map<String, Any?>>, fallback: Double): Double {
        if (tiers.isNotEmpty()) {
            val sorted = tiers.sortedBy { if (it["min"] is Number) dn(it["min"]) else 0.0 }
            for (i in sorted.indices) {
                val t = sorted[i]
                val mn: Double = if (t["min"] is Number) dn(t["min"]) else 0.0
                val mx: Double = if (t["max"] is Number) dn(t["max"]) else Double.POSITIVE_INFINITY
                val inRange: Boolean = if (i == 0) (base >= mn && base <= mx) else (base > mn && base <= mx)
                if (inRange) return dn(t["gp"])
            }
        }
        return fallback
    }

    /** ราคาที่ลูกค้าเห็นจริง (รวม GP) ใช้แสดงตัวอย่างในฟอร์มเท่านั้น */
    fun previewCustomerPrice(base: Double, menuGp: Double): Long {
        if (shopType == "proxy") return ceil(base).toLong()
        return ceil(base * (1 + (tierGp(base, gpTiers, gpPercent) + menuGp) / 100)).toLong()
    }

    fun previewSubPrice(optPrice: Double): Long {
        if (shopType == "proxy") return ceil(optPrice).toLong()
        return ceil(optPrice * (1 + tierGp(optPrice, gpSubTiers, gpSubPercent) / 100)).toLong()
    }

    // ---------- สถานะแก้ไขค้าง ----------
    /** ปิดหน้าต่างบนสุดที่เปิดอยู่ คืน true ถ้ามีอะไรถูกปิด */
    fun handleBack(): Boolean {
        when {
            dialog != null -> dialog = null
            sgtypeFor != null -> sgtypeFor = null
            gtypeFor != null -> gtypeFor = null
            addModeCat != null -> addModeCat = null
            pickCatFor != null -> pickCatFor = null
            moveCatFor != null -> moveCatFor = null
            copyFor != null -> copyFor = null
            applyFor != null -> applyFor = null
            bulkVisible -> cancelBulk()
            else -> return false
        }
        return true
    }

    fun markDirty(id: String) {
        if (id.isEmpty() || id.startsWith("_bulk_")) return
        dirtyIds.add(id); dirtyVersion++
    }
    fun clearDirty() { dirtyIds.clear(); dirtyVersion++ }

    fun menuById(id: String?): MMenu? = if (id == null) null else menus.firstOrNull { it.id == id }
    fun catName(id: String): String = categories.firstOrNull { it.id == id }?.name ?: ""
    fun itemsOf(catId: String): List<MMenu> = menus.filter { it.categoryId == catId && !it.id.startsWith("_bulk_") }.sortedBy { it.order }
    fun uncategorized(): List<MMenu> = menus.filter { !it.id.startsWith("_bulk_") && (it.categoryId.isEmpty() || categories.none { c -> c.id == it.categoryId }) }.sortedBy { it.order }

    // ---------- โหลดเรียลไทม์ ----------
    fun startListen() {
        reg?.remove()
        reg = db.collection("shops").document(shopId).collection("menus").addSnapshotListener { snap, err ->
            if (err != null) { loadError = err.message ?: "ผิดพลาด"; return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            loadError = null
            val remote = ArrayList<MMenu>()
            for (doc in snap.documents) {
                val local: MMenu? = menus.firstOrNull { it.id == doc.id }
                // เมนูที่กำลังแก้ไขค้าง/เปิดอยู่ ต้องคงค่าที่พิมพ์ไว้ ไม่ให้ข้อมูลสดมาทับ
                if (local != null && (dirtyIds.contains(doc.id) || openMenuId == doc.id)) remote.add(local)
                else remote.add(shopParseMenu(doc.id, doc.data ?: emptyMap()))
            }
            val temp: List<MMenu> = menus.filter { it.id.startsWith("_new_") || it.id.startsWith("_bulk_") }
            menus.clear(); menus.addAll(remote); menus.addAll(temp)
        }
    }
    fun stopListen() { reg?.remove(); reg = null }

    // ---------- หมวดหมู่ ----------
    private fun saveCategories() {
        val upd = HashMap<String, Any?>()
        upd["categories"] = categories.map { c -> val x = LinkedHashMap<String, Any?>(); x["id"] = c.id; x["name"] = c.name; x }
        upd["updatedAt"] = FieldValue.serverTimestamp()
        try { db.collection("shops").document(shopId).update(upd) } catch (e: Exception) { }
    }
    fun addCategory(name: String) {
        if (name.trim().isEmpty()) return
        categories.add(MCat("cat_" + System.currentTimeMillis(), name.trim()))
        saveCategories()
    }
    fun renameCategory(i: Int, name: String) {
        val c = categories.getOrNull(i) ?: return
        if (name.trim().isEmpty()) return
        val nn: String = name.trim()
        categories[i] = MCat(c.id, nn)
        saveCategories()
        // ชื่อหมวดที่เก็บซ้ำในเมนู ต้องตามชื่อใหม่ด้วย
        for (m in menus) {
            if (m.categoryId == c.id && !m.id.startsWith("_new_") && !m.id.startsWith("_bulk_")) {
                m.category = nn
                db.collection("shops").document(shopId).collection("menus").document(m.id).update("category", nn)
            }
        }
    }
    fun deleteCategory(i: Int) {
        if (i < 0 || i >= categories.size) return
        categories.removeAt(i)
        saveCategories()
    }
    fun moveCategory(i: Int, dir: Int) {
        val j: Int = i + dir
        if (i < 0 || j < 0 || j >= categories.size) return
        val t = categories[i]; categories[i] = categories[j]; categories[j] = t
        saveCategories()
    }

    // ---------- เมนู ----------
    fun toggleCat(id: String) { openCatId = if (openCatId == id) null else id }
    fun toggleMenu(id: String) { openMenuId = if (openMenuId == id) null else id }

    fun toggleAvailable(m: MMenu) {
        val cur: Boolean = m.available
        m.available = !cur
        if (!m.id.startsWith("_new_")) {
            val upd = HashMap<String, Any?>()
            upd["available"] = m.available; upd["updatedAt"] = FieldValue.serverTimestamp()
            try { db.collection("shops").document(shopId).collection("menus").document(m.id).update(upd).addOnFailureListener { m.available = cur } } catch (e: Exception) { m.available = cur }
        }
    }

    fun moveMenuOrder(m: MMenu, dir: Int) {
        val group: MutableList<MMenu> = menus.filter { it.categoryId == m.categoryId && !it.id.startsWith("_bulk_") }.sortedBy { it.order }.toMutableList()
        val idx: Int = group.indexOfFirst { it.id == m.id }
        val j: Int = idx + dir
        if (idx < 0 || j < 0 || j >= group.size) return
        val tmpM: MMenu = group[idx]; group[idx] = group[j]; group[j] = tmpM
        // ปรับลำดับใหม่ทั้งกลุ่ม (กันเคส order ซ้ำ/เป็น 0 ทั้งหมดแล้วสลับไม่ขยับ)
        group.forEachIndexed { k, x ->
            if (x.order != k) {
                x.order = k
                if (!x.id.startsWith("_new_")) {
                    db.collection("shops").document(shopId).collection("menus").document(x.id).update("order", k)
                }
            }
        }
    }

    fun moveMenuToCategory(m: MMenu, catId: String) {
        val maxOrder: Int = menus.filter { it.categoryId == catId }.fold(-1) { a, x -> max(a, x.order) }
        m.categoryId = catId; m.category = catName(catId); m.order = maxOrder + 1
        if (!m.id.startsWith("_new_")) {
            val upd = HashMap<String, Any?>()
            upd["categoryId"] = catId; upd["category"] = m.category; upd["order"] = m.order; upd["updatedAt"] = FieldValue.serverTimestamp()
            try { db.collection("shops").document(shopId).collection("menus").document(m.id).update(upd) } catch (e: Exception) { }
        }
    }

    suspend fun deleteMenu(m: MMenu) {
        if (!m.id.startsWith("_new_")) db.collection("shops").document(shopId).collection("menus").document(m.id).delete().await()
        menus.removeAll { it.id == m.id }
        if (openMenuId == m.id) openMenuId = null
        dirtyIds.remove(m.id); dirtyVersion++
    }

    fun openNewMenu(catId: String) {
        val maxOrder: Int = menus.filter { it.categoryId == catId }.fold(-1) { a, x -> max(a, x.order) }
        val m = MMenu("_new_" + System.currentTimeMillis())
        m.categoryId = catId; m.category = catName(catId); m.order = maxOrder + 1
        menus.add(0, m)
        openMenuId = m.id; openCatId = catId
        markDirty(m.id)
    }

    // ---------- กลุ่มตัวเลือก ----------
    fun addGroup(m: MMenu, type: String) {
        val g = MGroup(type)
        if (type == "price") { g.priceMin = "10"; g.priceMax = "50"; g.priceStep = "5" }
        else if (type == "normal") g.options.add(MOption())
        m.groups.add(g)
        markDirty(m.id)
    }
    fun addSubGroup(m: MMenu, gi: Int, oi: Int, type: String) {
        val o: MOption = m.groups.getOrNull(gi)?.options?.getOrNull(oi) ?: return
        val sg = MSubGroup(type)
        if (type == "price") { sg.priceMin = "10"; sg.priceMax = "50"; sg.priceStep = "5" }
        else if (type == "normal") { sg.maxSelect = "1"; sg.options.add(MSubOpt()) }
        o.subGroups.add(sg)
        val key: String = m.id + "|" + gi + "-" + oi
        if (!expanded.contains(key)) expanded.add(key)
        markDirty(m.id)
    }

    // ---------- บันทึกจริง ----------
    fun buildSaveData(m: MMenu): HashMap<String, Any?> {
        val d = HashMap<String, Any?>()
        d["name"] = m.name; d["price"] = pn(pd(m.price)); d["category"] = m.category; d["categoryId"] = m.categoryId
        d["available"] = m.available; d["order"] = m.order
        d["gp"] = pn(pd(m.gp)); d["gpIsMain"] = shopType == "proxy"; d["desc"] = m.desc; d["imgUrl"] = m.imgUrl
        d["schedEnabled"] = m.schedEnabled; d["schedOpen"] = m.schedOpen; d["schedClose"] = m.schedClose
        d["optionGroups"] = saveGroups(m)
        d["updatedAt"] = FieldValue.serverTimestamp()
        return d
    }

    /** ตรวจก่อนบันทึก (ชื่อเมนูต้องไม่ว่าง) แล้วหยุดฟังข้อมูลสดระหว่างบันทึก */
    fun prepareSave() {
        val nameless: String? = dirtyIds.firstOrNull { id -> menuById(id)?.name?.isEmpty() == true }
        if (nameless != null) throw Exception("กรุณากรอกชื่อเมนูให้ครบก่อนบันทึก")
        stopListen()
    }

    suspend fun saveMenus() {
        for (id in dirtyIds.toList()) {
            val m: MMenu = menuById(id) ?: continue
            val data: HashMap<String, Any?> = buildSaveData(m)
            if (id.startsWith("_new_")) {
                data["createdAt"] = FieldValue.serverTimestamp()
                val ref = db.collection("shops").document(shopId).collection("menus").add(data).await()
                m.id = ref.id
            } else {
                db.collection("shops").document(shopId).collection("menus").document(id).update(data).await()
            }
        }
    }

    fun finishSave() {
        clearDirty()
        openMenuId = null
    }

    // ---------- เพิ่มเมนูจำนวนมาก ----------
    fun openBulk(catId: String) {
        bulkCatId = catId
        savedOpenMenuId = openMenuId
        bulkIds.clear(); bulkOpenId = null
        bulkVisible = true
        addBulkRow()
    }
    fun addBulkRow() {
        val catGroup: List<MMenu> = menus.filter { it.categoryId == bulkCatId && !it.id.startsWith("_bulk_") }
        val bulkGroup: List<MMenu> = bulkIds.mapNotNull { menuById(it) }
        val maxOrder: Int = (catGroup + bulkGroup).fold(-1) { a, x -> max(a, x.order) }
        val m = MMenu("_bulk_" + System.currentTimeMillis() + "_" + (Math.random() * 100000).toInt())
        m.categoryId = bulkCatId; m.category = catName(bulkCatId); m.order = maxOrder + 1
        menus.add(m); bulkIds.add(m.id)
        bulkOpenId = m.id
    }
    fun removeBulkRow(id: String) {
        if (bulkIds.size <= 1) return
        bulkIds.remove(id)
        menus.removeAll { it.id == id }
        if (bulkOpenId == id) bulkOpenId = null
    }
    fun cancelBulk() {
        menus.removeAll { bulkIds.contains(it.id) }
        bulkIds.clear(); bulkOpenId = null
        bulkVisible = false
        openMenuId = savedOpenMenuId
    }
    suspend fun saveBulk() {
        val items: List<MMenu> = bulkIds.mapNotNull { menuById(it) }
        val valid: List<MMenu> = items.filter { it.name.trim().isNotEmpty() }
        if (valid.isEmpty()) { toast("กรุณากรอกชื่อเมนูอย่างน้อย 1 รายการ"); return }
        bulkSaving = true
        val failed = ArrayList<String>()
        for (m in valid) {
            val d: HashMap<String, Any?> = buildSaveData(m)
            d["name"] = m.name.trim(); d["available"] = true
            d.remove("schedEnabled"); d.remove("schedOpen"); d.remove("schedClose")
            d["createdAt"] = FieldValue.serverTimestamp()
            try { db.collection("shops").document(shopId).collection("menus").add(d).await() } catch (e: Exception) { failed.add(m.name) }
        }
        menus.removeAll { bulkIds.contains(it.id) }
        bulkIds.clear(); bulkOpenId = null
        bulkSaving = false
        bulkVisible = false
        openMenuId = savedOpenMenuId
        openCatId = bulkCatId
        if (failed.isNotEmpty()) dialog = MDialog("alert", "", "บันทึกไม่สำเร็จ " + failed.size + " รายการ: " + failed.joinToString(", ") + "\nกรุณาลองใหม่") { }
    }

    // ---------- คัดลอกกลุ่ม / ใช้เวลาขายกับหลายเมนู ----------
    fun otherMenus(excludeId: String): List<MMenu> =
        menus.filter { !it.id.startsWith("_new_") && !it.id.startsWith("_bulk_") && it.id != excludeId }
            .sortedWith(compareBy<MMenu>({ it.category }, { it.name }))

    fun confirmCopy() {
        val src: MMenu = copyFor ?: return
        if (copyGroups.isEmpty()) { toast("กรุณาเลือกอย่างน้อย 1 กลุ่มตัวเลือกที่จะคัดลอก"); return }
        if (copyMenus.isEmpty()) { toast("กรุณาเลือกอย่างน้อย 1 เมนูปลายทางที่จะนำไปใช้"); return }
        val toCopy: List<MGroup> = copyGroups.sorted().mapNotNull { src.groups.getOrNull(it) }
        val targets: List<String> = copyMenus.toList()
        for (id in targets) {
            val t: MMenu = menuById(id) ?: continue
            for (g in toCopy) t.groups.add(shopCopyGroup(g))
            markDirty(id)
        }
        copyFor = null
        // เปิดเมนูปลายทางอันแรกให้เห็นผลทันที
        val first: String = targets[0]
        val fm: MMenu? = menuById(first)
        if (fm != null) { openCatId = fm.categoryId; openMenuId = first }
        dialog = MDialog("alert", "", "เพิ่ม " + toCopy.size + " กลุ่มตัวเลือกให้ " + targets.size + " เมนูแล้ว (เปิดเมนูแรกให้ดูผลลัพธ์แล้ว)\nอย่าลืมกดปุ่ม \"บันทึก\" ด้านบนเพื่อบันทึกจริงด้วยนะครับ") { }
    }

    fun confirmApplySchedule() {
        val src: MMenu = applyFor ?: return
        if (applyMenus.isEmpty()) { toast("กรุณาเลือกอย่างน้อย 1 เมนูปลายทาง"); return }
        val n: Int = applyMenus.size
        for (id in applyMenus.toList()) {
            val t: MMenu = menuById(id) ?: continue
            t.schedEnabled = true; t.schedOpen = src.schedOpen; t.schedClose = src.schedClose
            markDirty(id)
        }
        applyFor = null
        dialog = MDialog("alert", "", "ใช้เวลาขาย " + src.schedOpen + "-" + src.schedClose + " น. กับ " + n + " เมนูแล้ว\nอย่าลืมกดปุ่ม \"บันทึก\" ด้านบนเพื่อบันทึกจริงด้วยนะครับ") { }
    }
}
