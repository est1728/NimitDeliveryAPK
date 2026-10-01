package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.checkoutRoute(session: Session, nav: NavHostController) {
    composable("checkout") {
        CheckoutScreen(
            session,
            onBack = { nav.popBackStack() },
            onAddress = { nav.navigate("address") },
            onPlaced = { id -> nav.navigate("track/$id") { popUpTo("shops") } }
        )
    }
}
