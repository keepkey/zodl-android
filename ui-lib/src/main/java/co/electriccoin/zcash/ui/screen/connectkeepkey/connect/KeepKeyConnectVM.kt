package co.electriccoin.zcash.ui.screen.connectkeepkey.connect

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectkeepkey.neworactive.KeepKeyNewOrActiveArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeepKeyConnectVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<KeepKeyConnectState> =
        MutableStateFlow(
            KeepKeyConnectState(
                isLoading = false,
                errorMessage = null,
                onBackClick = ::onBack,
                onConnectClick = ::onConnect,
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onConnect() = navigationRouter.forward(KeepKeyNewOrActiveArgs)
}
