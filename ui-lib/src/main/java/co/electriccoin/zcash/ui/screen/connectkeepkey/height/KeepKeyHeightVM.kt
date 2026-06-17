package co.electriccoin.zcash.ui.screen.connectkeepkey.height

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetKeepKeyOrchardFVKUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.BlockHeightState
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.SelectKeepKeyAccountArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class KeepKeyHeightVM(
    private val getKeepKeyOrchardFVK: GetKeepKeyOrchardFVKUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())
    private val connectLce = mutableLce<Unit>()

    val state: StateFlow<LceState<BlockHeightState>> =
        combine(blockHeightText, connectLce.state) { text, lce ->
            val isHigherThanSaplingActivationHeight =
                text.amount
                    ?.let { it.toLong() >= VersionInfo.NETWORK.saplingActivationHeight.value }
                    ?: false
            val isValid = !text.innerTextFieldState.value.isEmpty() && isHigherThanSaplingActivationHeight

            BlockHeightState(
                title = null,
                logo = null,
                onBack = ::onBack,
                dialogButton =
                    IconButtonState(
                        icon = R.drawable.ic_help,
                        onClick = ::onInfoClick,
                    ),
                primaryButton =
                    ButtonState(
                        text = stringRes(R.string.keepkey_wbh_confirm_button),
                        onClick = { text.amount?.toLong()?.let { onConfirmClick(it) } },
                        isEnabled = isValid && !lce.loading,
                        isLoading = lce.loading,
                        hapticFeedbackType = HapticFeedbackType.Confirm,
                    ),
                secondaryButton = null,
                blockHeight = NumberTextFieldState(innerState = text, onValueChange = ::onValueChanged),
            )
        }.withLce(connectLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onConfirmClick(height: Long) {
        connectLce.execute {
            val fvkData = getKeepKeyOrchardFVK()
            navigationRouter.forward(
                SelectKeepKeyAccountArgs(
                    ufvk = fvkData.ufvk,
                    seedFingerprintHex = fvkData.seedFingerprint.toHex(),
                    unifiedAddress = fvkData.unifiedAddress,
                    birthday = height,
                )
            )
        }
    }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = connectLce.guardLoading { navigationRouter.back() }

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
