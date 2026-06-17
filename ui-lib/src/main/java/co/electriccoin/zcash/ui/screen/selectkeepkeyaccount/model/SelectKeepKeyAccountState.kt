package co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.model

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.CheckboxListItemState
import co.electriccoin.zcash.ui.design.util.StringResource

data class SelectKeepKeyAccountState(
    val onBack: () -> Unit,
    val title: StringResource,
    val subtitle: StringResource,
    val items: List<CheckboxListItemState>,
    val positiveButton: ButtonState,
)
