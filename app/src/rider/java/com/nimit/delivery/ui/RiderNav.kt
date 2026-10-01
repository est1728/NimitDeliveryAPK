package com.nimit.delivery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nimit.delivery.data.RiderInfo
import com.nimit.delivery.data.RiderSession

@Composable
fun RiderNav() {
    val ctx = LocalContext.current
    val session = remember { RiderSession(ctx) }
    val nav = rememberNavController()
    val start: String = remember { if (session.riderId.isNullOrEmpty()) "login" else "home" }

    NavHost(nav, startDestination = start) {
        composable("login") {
            RiderLoginScreen(onLoggedIn = { r: RiderInfo ->
                session.riderId = r.id
                session.riderName = r.name
                session.riderPhone = r.phone
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("home") {
            RiderHomeScreen(session, onLogout = {
                session.logout()
                nav.navigate("login") { popUpTo(0) }
            })
        }
    }
}
