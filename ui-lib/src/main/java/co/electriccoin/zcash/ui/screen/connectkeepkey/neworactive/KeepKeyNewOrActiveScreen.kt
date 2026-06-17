package co.electriccoin.zcash.ui.screen.connectkeepkey.neworactive

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import org.koin.androidx.compose.koinViewModel

@Composable
fun KeepKeyNewOrActiveScreen() {
    val vm = koinViewModel<KeepKeyNewOrActiveVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        KeepKeyNewOrActiveView(it)
    }
}
