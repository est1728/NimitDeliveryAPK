package com.nimit.delivery.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class DevPage(val key: String, val title: String, val desc: String)

// ลำดับ sidebar ตามที่ตกลงกัน
private val devMain = listOf(
    DevPage("zones", "เขตแฟรนไชส์", ""),
    DevPage("hub", "ศูนย์ข้อมูลเขต", "บอร์ดรวมทุกเขต: จำนวนเขต สถานะ และประวัติของแต่ละเขต กดเข้าดูรายเขตได้"),
    DevPage("fin", "การเงินรวม", "ตารางใหญ่ประวัติการเงินทุกเขต กรองตามเขตและช่วงวันที่"),
    DevPage("audit", "ประวัติการเปลี่ยนแปลง", "บันทึกทุกการเปลี่ยนเขต ผูกเขต และตั้งค่า (ใคร/ทำอะไร/ก่อน→หลัง/เวลา) แก้ไขหรือลบไม่ได้"),
    DevPage("notif", "แจ้งเตือน", "ทดสอบระบบ push ตามหน้า developer.html เดิม")
)
private val devTools = listOf(
    DevPage("deploy", "Deploy", "อัปโหลดไฟล์ขึ้น GitHub"),
    DevPage("diff", "Diff", "เทียบไฟล์ก่อน push"),
    DevPage("files", "ไฟล์", "รายการไฟล์ใน repo"),
    DevPage("ai", "AI", "แชท Gemini"),
    DevPage("deployhist", "ประวัติ Deploy", "ประวัติการ push")
)
private val devSettings = DevPage("set", "ตั้งค่า", "GitHub / Gemini / ราคา GP")

@Composable
fun DevNav() {
    val ctx = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("zones") }
    val all = devMain + devTools + devSettings
    val curPage = all.first { it.key == cur }

    @Composable
    fun item(p: DevPage) {
        NavigationDrawerItem(
            label = { Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            selected = cur == p.key,
            onClick = {
                cur = p.key
                scope.launch { drawer.close() }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = DevC.PL,
                selectedTextColor = DevC.P,
                unselectedTextColor = DevC.T
            )
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(290.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                        .padding(16.dp)
                ) {
                    Text("Nimit Developer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ศูนย์ผู้พัฒนา", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    devMain.forEach { item(it) }
                    Text(
                        "เครื่องมือ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DevC.S,
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp)
                    )
                    devTools.forEach { item(it) }
                    Box(Modifier.padding(top = 8.dp)) { item(devSettings) }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(DevC.G)) {
            // bar: ถอดจาก .bar ใน developer.html
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(DevC.P, DevC.PD)))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = .18f))
                        .clickable { scope.launch { drawer.open() } },
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { Box(Modifier.width(16.dp).height(2.dp).background(Color.White)) }
                    }
                }
                Text(
                    curPage.title, color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = .18f))
                        .clickable { (ctx as? Activity)?.finish() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("ออก", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp)
            ) {
                if (cur == "zones") DevZonesPage() else DevCard {
                    DevSTitle(curPage.title)
                    Text(curPage.desc, fontSize = 13.sp, color = DevC.S, lineHeight = 20.sp)
                    Text("ยังไม่เปิดใช้งานในเวอร์ชันนี้", fontSize = 11.sp, color = DevC.S, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DevZonesPage() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("เขตแฟรนไชส์ทั้งหมด", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DevC.T, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DevC.P)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ เพิ่มเขต", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    DevCard {
        Text("ยังไม่มีเขต", fontSize = 13.sp, color = DevC.S)
        Text("เพิ่มเขตหลังคุยกับผู้สมัครทางไลน์ กดการ์ดเพื่อดูตั้งค่า/หลังบ้าน และใช้ปุ่ม \"เชื่อมหลังบ้าน\" เพื่อผูกหลายเขตเข้าหลังบ้านเดียวกัน", fontSize = 11.sp, color = DevC.S, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
