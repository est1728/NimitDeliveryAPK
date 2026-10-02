package com.nimit.delivery.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nimit.delivery.data.Session

fun NavGraphBuilder.chatRoute(session: Session, nav: NavHostController) {
    composable("chat") {
        ChatScreen(session.chatOrderId ?: "", "customer", { nav.popBackStack() })
    }
}
