package com.nimit.delivery.ui

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/** หน้ารีวิว ใช้ร่วมทุกแอป: reviews/{type}/{id}  (type = shop | rider) */
fun NavGraphBuilder.reviewsRoute(nav: NavHostController) {
    composable("reviews/{type}/{id}") { e: NavBackStackEntry ->
        ReviewsScreen(e.arguments?.getString("type") ?: "shop", e.arguments?.getString("id") ?: "", "", { nav.popBackStack() })
    }
}
