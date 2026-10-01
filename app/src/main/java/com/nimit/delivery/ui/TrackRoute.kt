package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.trackRoute(session: Session, nav: NavHostController) {
    composable("track/{orderId}") { entry ->
        TrackScreen(
            session, entry.arguments?.getString("orderId").orEmpty(),
            onBack = { nav.popBackStack() },
            onChat = { id -> session.chatOrderId = id; nav.navigate("chat") }
        )
    }
}
