package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.menuRoute(session: Session, nav: NavHostController) {
    composable("menu/{id}") { entry ->
        MenuScreen(
            session,
            entry.arguments?.getString("id").orEmpty(),
            onBack = { nav.popBackStack() },
            onCart = { nav.navigate("cart") },
            onCheckout = { nav.navigate("checkout") },
            onLogin = { nav.navigate("login") }
        )
    }
}
