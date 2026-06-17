package co.electriccoin.zcash.ui.screen.connectkeepkey.height

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.BlockHeightView
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import org.koin.androidx.compose.koinViewModel

@Composable
fun KeepKeyWBHScreen() {
    val vm = koinViewModel<KeepKeyHeightVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        BlockHeightView(it)
    }
}
