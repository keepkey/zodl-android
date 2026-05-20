package co.electriccoin.zcash.ui.screen.connectkeepkey.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportException
import co.electriccoin.zcash.ui.common.usecase.ConnectKeepKeyUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeepKeyConnectVM(
    private val navigationRouter: NavigationRouter,
    private val connectKeepKey: ConnectKeepKeyUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(createIdleState())
    val state: StateFlow<KeepKeyConnectState> = _state.asStateFlow()

    private fun createIdleState() =
        KeepKeyConnectState(
            isLoading = false,
            errorMessage = null,
            onBackClick = ::onBack,
            onConnectClick = ::onConnect,
        )

    private fun onBack() = navigationRouter.back()

    private fun onConnect() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { connectKeepKey() }
                .onFailure { e ->
                    val msg = when (e) {
                        is KeepKeyTransportException -> stringRes(e.message ?: "Connection failed")
                        else -> stringRes(e.message ?: "Unknown error")
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            // On success, ConnectKeepKeyUseCase navigates forward — no state update needed here.
        }
    }
}
