package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.couponRoute(session: Session, nav: NavHostController) {
    composable("coupon") {
        CouponScreen(
            session,
            onBack = { nav.popBackStack() },
            onHome = { nav.popBackStack("shops", false) },
            onHistory = { nav.navigate("reorder") },
            onLogin = { nav.navigate("login") }
        )
    }
}
