package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.notificationsRoute(session: Session, nav: NavHostController) {
    composable("notifications") {
        NotificationsScreen(session, onBack = { nav.popBackStack() }, onOpenOrder = { nav.navigate("track/$it") })
    }
}
