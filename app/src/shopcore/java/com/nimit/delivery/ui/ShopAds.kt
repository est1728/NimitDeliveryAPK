package com.nimit.delivery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nimit.delivery.data.ShopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private class AdItem(val id: String, val imgUrl: String, val caption: String, val createdMs: Long)

private const val P_AD_TRASH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
private const val P_AD_CHECK = "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"

/** โฆษณา: เพิ่มโฆษณาใหม่ (รูป 2:1 + แคปชั่น) และรายการโฆษณาของร้าน (ลบได้) — คอลเลกชัน ads เดียวกับเว็บ */
@Composable
fun ShopAdsScreen(store: ShopStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val shopName: String = store.shop.value["name"]?.toString() ?: ""
    var imgUrl by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var ads by remember { mutableStateOf<List<AdItem>>(emptyList()) }
    var cropBmp by remember { mutableStateOf<Bitmap?>(null) }
    var confirmPublish by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val screenW: Int = LocalConfiguration.current.screenWidthDp

    DisposableEffect(store.shopId) {
        val reg = db.collection("ads").whereEqualTo("shopId", store.shopId).addSnapshotListener { snap, _ ->
            if (snap != null) {
                ads = snap.documents.map {
                    AdItem(it.id, it.getString("imgUrl") ?: "", it.getString("caption") ?: "", it.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                }.sortedByDescending { it.createdMs }
            }
        }
        onDispose { reg.remove() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val b: Bitmap? = withContext(Dispatchers.IO) { shopDecodeImage(ctx, uri) }
            if (b == null) shopToast(ctx, "เปิดรูปไม่สำเร็จ") else cropBmp = b
        }
    }

    BackHandler {
        when {
            cropBmp != null -> cropBmp = null
            confirmPublish -> confirmPublish = false
            deleteId != null -> deleteId = null
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Gray)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().shadow(2.dp).background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    PathIcon("M19 12H5M12 5l-7 7 7 7", C.Text, 18.dp, stroke = true)
                }
                Spacer(Modifier.width(12.dp))
                Text("โฆษณา", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp)) {
                // การ์ดเพิ่มโฆษณาใหม่
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                    Text("เพิ่มโฆษณาใหม่", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Primary, modifier = Modifier.padding(bottom = 10.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(C.Gray))
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(C.Gray)
                            .border(BorderStroke(2.dp, Color(0xFFD1D9E8)), RoundedCornerShape(14.dp))
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imgUrl.isNotEmpty()) AsyncImage(imgUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PathIcon(ShopP.IMAGE, Color(0xFFB8CCEE), 28.dp)
                            Text("แตะเพื่อเพิ่มรูปโฆษณา", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8CCEE))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = caption, onValueChange = { caption = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = C.Text),
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).border(2.dp, Color(0xFFEEF0F4), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                if (caption.isEmpty()) Text("แคปชั่น เช่น โปรโมชั่นพิเศษ! ลด 20%", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)))
                            .clickable {
                                if (imgUrl.isEmpty() && caption.trim().isEmpty()) shopToast(ctx, "กรุณาเพิ่มรูปหรือแคปชั่นก่อน") else confirmPublish = true
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("เผยแพร่โฆษณา", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
                // รายการโฆษณา
                if (ads.isNotEmpty()) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Text("โฆษณาของคุณ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = C.Subtext, modifier = Modifier.padding(bottom = 10.dp))
                        for (ad in ads) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).shadow(1.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                                if (ad.imgUrl.isNotEmpty()) AsyncImage(ad.imgUrl, null, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ad.caption.ifEmpty { "ไม่มีแคปชั่น" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
                                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(ShopRedLight).clickable { deleteId = ad.id }, contentAlignment = Alignment.Center) {
                                        PathIcon(P_AD_TRASH, ShopRed, 15.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confirmPublish) AdConfirm(
            green = true, title = "ยืนยันเผยแพร่โฆษณา", sub = "คุณต้องการเผยแพร่โฆษณานี้ใช่หรือไม่?", okText = "ยืนยัน",
            onCancel = { confirmPublish = false },
            onOk = {
                confirmPublish = false
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val d = HashMap<String, Any>()
                            d["shopId"] = store.shopId; d["shopName"] = shopName; d["imgUrl"] = imgUrl
                            d["caption"] = caption.trim(); d["active"] = true; d["createdAt"] = FieldValue.serverTimestamp()
                            db.collection("ads").add(d).await()
                            imgUrl = ""; caption = ""
                            shopToast(ctx, "เผยแพร่โฆษณาสำเร็จ")
                        } catch (e: Exception) { shopToast(ctx, "เกิดข้อผิดพลาด") }
                        busy = false
                    }
                }
            }
        )
        val did: String? = deleteId
        if (did != null) AdConfirm(
            green = false, title = "ลบโฆษณานี้?", sub = "โฆษณาจะถูกลบออกจากแอพลูกค้าทันที", okText = "ลบ",
            onCancel = { deleteId = null },
            onOk = {
                deleteId = null
                scope.launch {
                    try { db.collection("ads").document(did).delete().await(); shopToast(ctx, "ลบโฆษณาแล้ว") } catch (e: Exception) { shopToast(ctx, "ลบไม่สำเร็จ") }
                }
            }
        )

        val bmp: Bitmap? = cropBmp
        if (bmp != null) {
            ShopCropOverlay(
                bitmap = bmp, shape = "ad", ratio = 2f,
                onCancel = { cropBmp = null },
                onDone = { url: String -> imgUrl = url; cropBmp = null }
            )
        }
    }
}

/** กล่องยืนยันตรงกลางจอ (confirm-box ของเว็บ) */
@Composable
private fun AdConfirm(green: Boolean, title: String, sub: String, okText: String, onCancel: () -> Unit, onOk: () -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(indication = null, interactionSource = none) { onCancel() }.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 28.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(if (green) ShopGreenLight else ShopRedLight), contentAlignment = Alignment.Center) {
                    PathIcon(if (green) P_AD_CHECK else P_AD_TRASH, if (green) ShopGreen else ShopRed, 36.dp)
                }
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
            Text(sub, fontSize = 14.sp, color = C.Subtext, textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F2F5)))
            Row(Modifier.fillMaxWidth().height(54.dp)) {
                Box(Modifier.weight(1f).fillMaxSize().clickable { onCancel() }, contentAlignment = Alignment.Center) {
                    Text("ยกเลิก", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Subtext)
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFF0F2F5)))
                Box(Modifier.weight(1f).fillMaxSize().clickable { onOk() }, contentAlignment = Alignment.Center) {
                    Text(okText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (green) C.Primary else ShopRed)
                }
            }
        }
    }
}
