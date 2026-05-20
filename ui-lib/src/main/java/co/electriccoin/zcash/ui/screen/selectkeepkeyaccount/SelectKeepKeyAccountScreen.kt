package co.electriccoin.zcash.ui.screen.selectkeepkeyaccount

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.view.SelectKeepKeyAccountView
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.viewmodel.SelectKeepKeyAccountViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SelectKeepKeyAccountScreen(args: SelectKeepKeyAccountArgs) {
    val viewModel = koinViewModel<SelectKeepKeyAccountViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        SelectKeepKeyAccountView(state = it)
    }
}
