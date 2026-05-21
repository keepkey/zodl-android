package co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.viewmodel

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.Lce
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ImportKeepKeyAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.SelectKeepKeyAccountArgs
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.model.SelectKeepKeyAccountState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class SelectKeepKeyAccountViewModel(
    private val args: SelectKeepKeyAccountArgs,
    private val importKeepKeyAccount: ImportKeepKeyAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val importLce = mutableLce<Unit>()

    val state: StateFlow<LceState<SelectKeepKeyAccountState>> =
        importLce.state
            .map { lce -> createState(lce) }
            .withLce(importLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(lce: Lce<Unit>) =
        SelectKeepKeyAccountState(
            onBack = ::onBack,
            title = stringRes(R.string.select_keepkey_account_title),
            subtitle = stringRes(R.string.select_keepkey_account_subtitle),
            items =
                listOf(
                    ZashiExpandedCheckboxListItemState(
                        title = stringRes(R.string.select_keepkey_account_default),
                        subtitle = stringResByAddress(args.unifiedAddress),
                        icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keepkey,
                        isSelected = true,
                        info = null,
                        onClick = {},
                    )
                ),
            positiveButton =
                ButtonState(
                    text = stringRes(R.string.select_keepkey_account_positive),
                    isLoading = lce.loading,
                    onClick = ::onConfirmClick,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                ),
        )

    private fun onConfirmClick() =
        importLce.execute {
            val birthday = if (args.birthday >= 0L) BlockHeight.new(args.birthday) else null
            importKeepKeyAccount(
                ufvk = args.ufvk,
                seedFingerprint = args.seedFingerprintHex.fromHex(),
                birthday = birthday,
            )
        }

    private fun onBack() = importLce.guardLoading { navigationRouter.back() }

    @Suppress("MagicNumber")
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
