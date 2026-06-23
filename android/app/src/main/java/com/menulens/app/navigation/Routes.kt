package com.menulens.app.navigation

sealed class Route(val value: String) {
    data object Scan : Route("scan")
    data object Processing : Route("processing")
    data object Results : Route("results")
    data object Detail : Route("detail/{itemId}") {
        fun create(itemId: String): String = "detail/$itemId"
    }
    data object ShowToStaff : Route("show_to_staff/{itemId}") {
        fun create(itemId: String): String = "show_to_staff/$itemId"
    }
    data object Paywall : Route("paywall")
}
