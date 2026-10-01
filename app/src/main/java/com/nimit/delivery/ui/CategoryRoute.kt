package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.categoryRoute(session: Session, nav: NavHostController) {
    composable("category/{id}") { entry ->
        CategoryScreen(
            session, entry.arguments?.getString("id").orEmpty(),
            onBack = { nav.popBackStack() },
            onOpenShop = { nav.navigate("menu/$it") },
            onCheckout = { nav.navigate("checkout") },
            onLogin = { nav.navigate("login") }
        )
    }
}
