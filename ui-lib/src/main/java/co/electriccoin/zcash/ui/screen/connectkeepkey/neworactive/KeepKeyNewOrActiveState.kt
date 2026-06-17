package co.electriccoin.zcash.ui.screen.connectkeepkey.neworactive

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class KeepKeyNewOrActiveState(
    val subtitle: StringResource,
    val message: StringResource,
    val newDevice: ButtonState,
    val activeDevice: ButtonState,
    val onBack: () -> Unit,
)
