package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.reorderRoute(session: Session, nav: NavHostController) {
    composable("reorder") {
        ReorderScreen(
            session,
            onBack = { nav.popBackStack() },
            onHome = { nav.popBackStack("shops", false) },
            onCoupon = { nav.navigate("coupon") },
            onLogin = { session.loginReturnRoute = "reorder"; nav.navigate("login") },
            onCart = { nav.navigate("cart") }
        )
    }
}
