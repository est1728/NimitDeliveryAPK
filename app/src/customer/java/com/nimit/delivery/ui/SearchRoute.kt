package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.searchRoute(session: Session, nav: NavHostController) {
    composable("search") {
        SearchScreen(session, onBack = { nav.popBackStack() }, onOpenShop = { nav.navigate("menu/$it") })
    }
}
