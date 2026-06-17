package co.electriccoin.zcash.ui.screen.connectkeepkey.connect

import co.electriccoin.zcash.ui.design.util.StringResource

data class KeepKeyConnectState(
    val isLoading: Boolean,
    val errorMessage: StringResource?,
    val onBackClick: () -> Unit,
    val onConnectClick: () -> Unit,
)
