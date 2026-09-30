package com.nimit.delivery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nimit.delivery.data.Session
import org.json.JSONObject

// หน้าที่ยังไม่ได้แปลง (ทยอยเปลี่ยนเป็นหน้าจริงทีละหน้า)
private val pendingRoutes = listOf(
    "address", "search", "coupon", "checkout", "chat", "review", "reorder", "notifications",
    "track/{orderId}", "reviews/{type}/{id}", "category/{id}"
)

@Composable
fun NimitNav() {
    val ctx = LocalContext.current
    val session = remember { Session(ctx) }
    val nav = rememberNavController()

    fun toShops() {
        val r = session.loginReturnRoute
        session.loginReturnRoute = null
        nav.navigate(r ?: "shops") { popUpTo("login") { inclusive = true } }
    }

    // เคยล็อกอิน+ลงทะเบียนแล้ว -> เข้าหน้าช้อปเลย
    val startRoute = remember {
        val ok = try {
            !session.customerPhone.isNullOrEmpty() && JSONObject(session.customerData ?: "{}").optBoolean("registered", false)
        } catch (_: Exception) { false }
        if (ok) "shops" else "login"
    }

    UpdatePrompt()

    NavHost(
        nav, startDestination = startRoute,
        enterTransition = { slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } },
        exitTransition = { slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } },
        popEnterTransition = { slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } },
        popExitTransition = { slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } }
    ) {
        composable("login") {
            LoginScreen(
                session,
                onNeedOtp = { nav.navigate("otp") },
                onLoggedIn = { toShops() },
                onNewCustomer = { nav.navigate("register") }
            )
        }
        composable("otp") {
            OtpScreen(session, onBack = { nav.popBackStack() }) { registered ->
                if (registered) toShops()
                else nav.navigate("register") { popUpTo("otp") { inclusive = true } }
            }
        }
        composable("register") {
            RegisterScreen(session, onBack = { nav.popBackStack() }, onDone = { toShops() })
        }
        composable("shops") {
            ShopsScreen(
                session,
                onOpenShop = { id, name -> session.selectedShopId = id; session.selectedShopName = name; nav.navigate("menu/$id") },
                onNavigate = { nav.navigate(it) },
                onLogout = { session.logout(); nav.navigate("login") { popUpTo(0) } }
            )
        }
        menuRoute(session, nav)
        cartRoute(session, nav)
        pendingRoutes.forEach { r ->
            composable(r) { Pending(r) { nav.popBackStack() } }
        }
    }
}

@Composable
private fun Pending(name: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("หน้า $name กำลังแปลงเป็น Native")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("กลับ") }
    }
}
