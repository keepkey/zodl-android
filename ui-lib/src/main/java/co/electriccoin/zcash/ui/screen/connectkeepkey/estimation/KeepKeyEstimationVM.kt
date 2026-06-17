package co.electriccoin.zcash.ui.screen.connectkeepkey.estimation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetKeepKeyOrchardFVKUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.common.EstimatedBlockHeightState
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.SelectKeepKeyAccountArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class KeepKeyEstimationVM(
    private val args: KeepKeyEstimationArgs,
    private val getKeepKeyOrchardFVK: GetKeepKeyOrchardFVKUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val connectLce = mutableLce<Unit>()

    val state: StateFlow<LceState<EstimatedBlockHeightState>> =
        connectLce.state
            .map { lce -> createState(isLoading = lce.loading) }
            .withLce(connectLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(isLoading = false)))

    private fun createState(isLoading: Boolean) =
        EstimatedBlockHeightState(
            title = null,
            logo = null,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoClick,
                ),
            onBack = ::onBack,
            blockHeightText = stringResByNumber(args.blockHeight, 0),
            copyButton =
                ButtonState(
                    text = stringRes(R.string.wbh_copy),
                    icon = R.drawable.ic_copy,
                    onClick = {},
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.keepkey_first_transaction_estimation_confirm),
                    isLoading = isLoading,
                    onClick = ::onConfirmClick,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                ),
        )

    private fun onConfirmClick() =
        connectLce.execute {
            val fvkData = getKeepKeyOrchardFVK()
            navigationRouter.forward(
                SelectKeepKeyAccountArgs(
                    ufvk = fvkData.ufvk,
                    seedFingerprintHex = fvkData.seedFingerprint.toHex(),
                    unifiedAddress = fvkData.unifiedAddress,
                    birthday = args.blockHeight,
                )
            )
        }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = connectLce.guardLoading { navigationRouter.back() }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
