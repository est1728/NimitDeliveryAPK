package com.nimit.delivery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nimit.delivery.data.Ord
import com.nimit.delivery.data.RiderInfo
import com.nimit.delivery.data.RiderSession
import com.nimit.delivery.data.RiderStore

private class StoreHolder {
    var store: RiderStore? by mutableStateOf<RiderStore?>(null)
}

@Composable
fun RiderNav() {
    val ctx = LocalContext.current
    val session = remember { RiderSession(ctx) }
    val nav = rememberNavController()
    val holder: StoreHolder = remember {
        val h = StoreHolder()
        val id: String? = session.riderId
        if (!id.isNullOrEmpty()) h.store = RiderStore(id)
        h
    }
    val start: String = remember { if (session.riderId.isNullOrEmpty()) "login" else "home" }
    val cur: RiderStore? = holder.store

    DisposableEffect(cur) {
        val s: RiderStore? = cur
        if (s != null) {
            s.onNewJob = { o: Ord ->
                val txt: String = "งานใหม่มาแล้ว! " + o.orderId + " · " + o.s("shopName")
                s.banner.value = txt
                RiderSound.alert(ctx, "งานใหม่มาแล้ว!", o.orderId + " · " + o.s("shopName") + " · ฿" + Math.round(o.n("grandTotal")))
            }
            s.start()
        }
        onDispose { s?.stop() }
    }
    if (cur != null) LocationSharing(cur.riderId)

    UpdatePrompt(appName = "Nimit Rider", tagPrefix = "rider-build-")

    NavHost(nav, startDestination = start) {
        composable("login") {
            RiderLoginScreen(onLoggedIn = { r: RiderInfo ->
                session.riderId = r.id
                session.riderName = r.name
                session.riderPhone = r.phone
                holder.store = RiderStore(r.id)
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("home") {
            val s: RiderStore = holder.store ?: return@composable
            RiderHome(
                store = s,
                onOpenOrder = { id: String, hist: Boolean -> nav.navigate("order/" + id + "/" + hist) },
                onOpenChat = { id: String -> nav.navigate("chat/" + id) },
                onStatement = { nav.navigate("statement") },
                onWallet = { nav.navigate("wallet") },
                onShift = { nav.navigate("shift") },
                onAccount = { nav.navigate("account") },
                onReviews = { nav.navigate("reviews/rider/" + s.riderId) },
                onLogout = {
                    session.logout()
                    holder.store = null
                    nav.navigate("login") { popUpTo(0) }
                }
            )
        }
        composable("order/{id}/{hist}") { e: NavBackStackEntry ->
            val s: RiderStore = holder.store ?: return@composable
            val id: String = e.arguments?.getString("id") ?: ""
            val hist: Boolean = e.arguments?.getString("hist") == "true"
            RiderOrderScreen(s, id, hist, onBack = { nav.popBackStack() }, onChat = { nav.navigate("chat/" + id) })
        }
        composable("chat/{orderId}") { e: NavBackStackEntry ->
            ChatScreen(e.arguments?.getString("orderId") ?: "", "rider", { nav.popBackStack() })
        }
        composable("statement") {
            val s: RiderStore = holder.store ?: return@composable
            RiderStatementScreen(s, { nav.popBackStack() })
        }
        composable("wallet") {
            val s: RiderStore = holder.store ?: return@composable
            RiderWalletScreen(s, { nav.popBackStack() })
        }
        composable("shift") {
            val s: RiderStore = holder.store ?: return@composable
            RiderShiftScreen(s, { nav.popBackStack() })
        }
        composable("account") {
            val s: RiderStore = holder.store ?: return@composable
            RiderAccountScreen(s, { nav.popBackStack() })
        }
        reviewsRoute(nav)
    }
}
