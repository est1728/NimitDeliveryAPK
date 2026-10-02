package com.nimit.delivery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class RevItem(val rating: Int, val comment: String, val orderId: String, val date: Date)

private fun revStars(n: Int): String {
    val k: Int = if (n < 0) 0 else if (n > 5) 5 else n
    return "★".repeat(k) + "☆".repeat(5 - k)
}

@Composable
fun ReviewsScreen(type: String, id: String, name: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<RevItem>>(emptyList()) }

    LaunchedEffect(type, id) {
        loading = true
        err = null
        try {
            val isShop: Boolean = type == "shop"
            val field: String = if (isShop) "shopId" else "riderId"
            val rf: String = if (isShop) "shopRating" else "riderRating"
            val cf: String = if (isShop) "shopComment" else "riderComment"
            val col = FirebaseFirestore.getInstance().collection("reviews")
            val snap: QuerySnapshot = (if (id.isNotEmpty()) col.whereEqualTo(field, id).get() else col.get()).await()
            val out = ArrayList<RevItem>()
            for (d in snap.documents) {
                val rating: Int = d.get(rf)?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                if (rating > 0) {
                    val ts: Timestamp? = d.getTimestamp("createdAt")
                    out.add(RevItem(rating, d.getString(cf) ?: "", d.getString("orderId") ?: d.id, ts?.toDate() ?: Date()))
                }
            }
            out.sortByDescending { it.date.time }
            list = out
        } catch (e: Exception) {
            err = e.message ?: "โหลดไม่ได้"
        }
        loading = false
    }

    val title: String = if (name.isNotEmpty()) name else if (type == "shop") "ร้านค้า" else "ไรเดอร์"
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F6F8))) {
        TopBar("รีวิว · " + title, onBack)
        val e: String? = err
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("กำลังโหลด...", color = C.Subtext) }
        } else if (e != null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("โหลดไม่ได้\n" + e, color = C.Subtext, fontSize = 13.sp) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ReviewSummary(list) }
                if (list.isEmpty()) {
                    item { Text("ยังไม่มีรีวิว", color = C.Subtext, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                }
                items(list) { r: RevItem ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(revStars(r.rating), color = Color(0xFFF59E0B), fontSize = 16.sp)
                            Text(SimpleDateFormat("d MMM yy", Locale("th", "TH")).format(r.date), color = C.Subtext, fontSize = 12.sp)
                        }
                        Text("ออเดอร์ " + r.orderId, color = C.Subtext, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        if (r.comment.isNotEmpty()) Text(r.comment, color = C.Text, fontSize = 14.sp)
                        else Text("ไม่มีความคิดเห็น", color = C.Subtext, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSummary(list: List<RevItem>) {
    val total: Int = list.size
    var sum = 0
    val dist = IntArray(5)
    for (r in list) { sum += r.rating; if (r.rating in 1..5) dist[r.rating - 1] += 1 }
    val avg: Double = if (total > 0) sum.toDouble() / total else 0.0
    val ringColor: Color = if (avg >= 4.0) Color(0xFF16A34A) else if (avg >= 3.0) Color(0xFFF59E0B) else Color(0xFFEF4444)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.2f", avg), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
            Text(revStars(Math.round(avg).toInt()), color = Color(0xFFF59E0B), fontSize = 14.sp)
            Text(total.toString() + " รีวิว", color = C.Subtext, fontSize = 12.sp)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 12f
                val inset = sw / 2f
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(color = Color(0xFFEEF1F7), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = sz, style = Stroke(width = sw))
                drawArc(color = ringColor, startAngle = -90f, sweepAngle = (avg / 5.0 * 360.0).toFloat(), useCenter = false, topLeft = Offset(inset, inset), size = sz, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            Text(Math.round(avg / 5.0 * 100.0).toString() + "%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            for (s in 5 downTo 1) {
                val cnt: Int = dist[s - 1]
                val frac: Float = if (total > 0) cnt.toFloat() / total.toFloat() else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.toString(), fontSize = 11.sp, color = C.Subtext, modifier = Modifier.width(12.dp))
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEF1F7))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color(0xFFF59E0B)))
                    }
                    Text(cnt.toString(), fontSize = 11.sp, color = C.Subtext, modifier = Modifier.width(24.dp).padding(start = 4.dp))
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
