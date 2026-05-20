package co.electriccoin.zcash.ui.screen.signkeepkeytransaction

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class SignKeepKeyTransactionState(
    val title: StringResource,
    val subtitle: StringResource,
    val isLoading: Boolean,
    val errorMessage: StringResource?,
    val positiveButton: ButtonState,
    val negativeButton: ButtonState,
    val onBack: () -> Unit,
)
