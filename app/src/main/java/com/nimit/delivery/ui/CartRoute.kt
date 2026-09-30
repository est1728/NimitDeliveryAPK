package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.cartRoute(session: Session, nav: NavHostController) {
    composable("cart") {
        CartScreen(
            session,
            onBack = { nav.popBackStack() },
            onShop = { nav.navigate("menu/$it") },
            onCheckout = { nav.navigate("checkout") },
            onTrack = { nav.navigate("track/$it") },
            onShops = { nav.popBackStack("shops", false) }
        )
    }
}
