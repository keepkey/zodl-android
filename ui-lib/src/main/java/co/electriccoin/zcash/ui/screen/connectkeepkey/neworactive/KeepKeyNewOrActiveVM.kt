package co.electriccoin.zcash.ui.screen.connectkeepkey.neworactive

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
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeepkey.date.KeepKeyDateArgs
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.SelectKeepKeyAccountArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class KeepKeyNewOrActiveVM(
    private val getKeepKeyOrchardFVK: GetKeepKeyOrchardFVKUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val connectLce = mutableLce<Unit>()

    val state: StateFlow<LceState<KeepKeyNewOrActiveState>> =
        connectLce.state
            .map { lce ->
                KeepKeyNewOrActiveState(
                    subtitle = stringRes(R.string.keepkey_new_or_active_subtitle),
                    message = stringRes(R.string.keepkey_new_or_active_message),
                    newDevice =
                        ButtonState(
                            text = stringRes(R.string.keepkey_new_device_button),
                            isLoading = lce.loading,
                            onClick = ::onNewDeviceClick,
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                        ),
                    activeDevice =
                        ButtonState(
                            text = stringRes(R.string.keepkey_active_device_button),
                            isEnabled = !lce.loading,
                            onClick = ::onActiveDeviceClick,
                        ),
                    onBack = ::onBack,
                )
            }.withLce(connectLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onNewDeviceClick() =
        connectLce.execute {
            val fvkData = getKeepKeyOrchardFVK()
            navigationRouter.forward(
                SelectKeepKeyAccountArgs(
                    ufvk = fvkData.ufvk,
                    seedFingerprintHex = fvkData.seedFingerprint.toHex(),
                    unifiedAddress = fvkData.unifiedAddress,
                    birthday = -1L,
                )
            )
        }

    private fun onActiveDeviceClick() =
        connectLce.guardLoading {
            navigationRouter.forward(KeepKeyDateArgs)
        }

    private fun onBack() =
        connectLce.guardLoading {
            navigationRouter.back()
        }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
