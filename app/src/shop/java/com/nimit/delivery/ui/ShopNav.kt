package com.nimit.delivery.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nimit.delivery.data.ShopApi
import com.nimit.delivery.data.ShopInfo
import com.nimit.delivery.data.ShopOrd
import com.nimit.delivery.data.ShopSession
import com.nimit.delivery.data.ShopStore
import com.nimit.delivery.data.shopEffectiveOpen
import kotlinx.coroutines.delay

private class ShopStoreHolder {
    var store: ShopStore? by mutableStateOf<ShopStore?>(null)
}

@Composable
fun ShopNav() {
    val ctx = LocalContext.current
    val session = remember { ShopSession(ctx) }
    val nav = rememberNavController()
    val holder: ShopStoreHolder = remember {
        val h = ShopStoreHolder()
        val id: String? = session.shopId
        if (!id.isNullOrEmpty()) h.store = ShopStore(id)
        h
    }
    val start: String = remember { if (session.shopId.isNullOrEmpty()) "login" else "home" }
    val cur: ShopStore? = holder.store

    DisposableEffect(cur) {
        val s: ShopStore? = cur
        if (s != null) {
            s.onNewOrder = { o: ShopOrd ->
                s.banner.value = "🔔 ออเดอร์ใหม่! " + o.orderId + " · ฿" + com.nimit.delivery.data.shopNum(o.n("grandTotal"))
                ShopSound.alert(ctx)
            }
            s.start()
        }
        onDispose { s?.stop() }
    }

    // เช็คทุก 30 วิ ว่าถึงเวลาเปิดร้านเองอัตโนมัติหรือยัง (ปิดยาว/ปิดชั่วคราว/ตารางเวลา) เหมือน checkAutoReopen ในเว็บ
    LaunchedEffect(cur) {
        val s: ShopStore? = cur
        if (s != null) {
            while (true) {
                val shop: Map<String, Any> = s.shop.value
                if (s.shopLoaded.value && shop["isOpen"] == false && shopEffectiveOpen(shop)) {
                    try { ShopApi.openShop(s.shopId) } catch (e: Exception) { }
                }
                delay(30_000)
            }
        }
    }

    UpdatePrompt(appName = "Nimit Shop", tagPrefix = "shop-build-")

    // เปลี่ยนหน้า: เลื่อน + จาง ประมาณ 200ms
    NavHost(
        nav, startDestination = start,
        enterTransition = { slideInHorizontally(tween(200)) { it / 6 } + fadeIn(tween(200)) },
        exitTransition = { slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(160)) },
        popEnterTransition = { slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160)) }
    ) {
        composable("login") {
            ShopLoginScreen(onLoggedIn = { s: ShopInfo ->
                session.shopId = s.id
                session.shopName = s.name
                session.shopPhone = s.phone
                holder.store = ShopStore(s.id)
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("home") {
            val s: ShopStore = holder.store ?: return@composable
            ShopHome(
                store = s,
                onOpenOrder = { id: String -> nav.navigate("order/" + id) },
                onReviews = { nav.navigate("reviews/shop/" + s.shopId) },
                onLogout = {
                    session.logout()
                    holder.store = null
                    nav.navigate("login") { popUpTo(0) }
                }
            )
        }
        composable("order/{id}") { e: NavBackStackEntry ->
            val s: ShopStore = holder.store ?: return@composable
            ShopOrderDetail(s, e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
        reviewsRoute(nav)
    }
}
