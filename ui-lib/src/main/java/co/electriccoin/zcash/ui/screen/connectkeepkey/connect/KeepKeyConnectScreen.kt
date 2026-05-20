package co.electriccoin.zcash.ui.screen.connectkeepkey.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectKeepKeyScreen() {
    val vm = koinViewModel<KeepKeyConnectVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    KeepKeyConnectView(state)
}

@Serializable
object ConnectKeepKeyArgs
