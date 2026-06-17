package co.electriccoin.zcash.ui.screen.connectkeepkey.connected

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun KeepKeyConnectedScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    BackHandler { /* consume back — user must use the button */ }
    KeepKeyConnectedView(
        state = KeepKeyConnectedState(onClose = { navigationRouter.backToRoot() })
    )
}

@Serializable
data object KeepKeyConnectedArgs
