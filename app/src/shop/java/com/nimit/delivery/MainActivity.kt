package com.nimit.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.nimit.delivery.ui.C
import com.nimit.delivery.ui.ShopNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Android 15+ (target 35/36) บังคับวาดเต็มจอใต้แถบสถานะ/แถบนำทาง จึงตั้งให้เหมือนกันทุกเวอร์ชัน
        // แล้วเว้นขอบให้เองที่ราก: แถบสถานะสีน้ำเงินเหมือนเดิม, แถบล่างและคีย์บอร์ดดันเนื้อหาขึ้น
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.isAppearanceLightStatusBars = false
        ic.isAppearanceLightNavigationBars = true
        setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(C.Primary))
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { ShopNav() }
            }
        }
    }
}
