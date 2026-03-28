package one.yufz.hmspush.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Router : NavKey {

    @Serializable
    data object Home : Router

    @Serializable
    data object Settings : Router

    @Serializable
    data object Icon : Router

    @Serializable
    data object FakeDevice : Router
}