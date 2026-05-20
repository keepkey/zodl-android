package co.electriccoin.zcash.ui.screen.signkeepkeytransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.KeepKeyProposalRepository
import co.electriccoin.zcash.ui.common.usecase.CancelProposalFlowUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignKeepKeyTransactionVM(
    private val keepKeyProposalRepository: KeepKeyProposalRepository,
    private val navigationRouter: NavigationRouter,
    private val cancelProposalFlow: CancelProposalFlowUseCase,
) : ViewModel() {
    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<SignKeepKeyTransactionState> =
        combine(isLoading, errorMessage) { loading, error ->
            SignKeepKeyTransactionState(
                title = stringRes(R.string.keepkey_signing_title),
                subtitle = stringRes(R.string.keepkey_signing_subtitle),
                isLoading = loading,
                errorMessage = error?.let { stringRes(it) },
                positiveButton = ButtonState(
                    text = stringRes(R.string.sign_keepkey_transaction_positive),
                    onClick = ::onConfirmClick,
                    isEnabled = !loading,
                ),
                negativeButton = ButtonState(
                    text = stringRes(R.string.sign_keepkey_transaction_negative),
                    onClick = ::onCancelClick,
                    isEnabled = !loading,
                ),
                onBack = ::onBack,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = buildIdleState(),
        )

    private fun onConfirmClick() {
        if (isLoading.value) return
        isLoading.update { true }
        errorMessage.update { null }
        viewModelScope.launch {
            runCatching { keepKeyProposalRepository.signAndSubmit() }
                .onSuccess {
                    navigationRouter.replace(TransactionProgressArgs)
                }
                .onFailure { e ->
                    errorMessage.update { e.message ?: "Signing failed" }
                    isLoading.update { false }
                }
        }
    }

    private fun onCancelClick() {
        viewModelScope.launch {
            cancelProposalFlow()
        }
    }

    private fun onBack() {
        if (!isLoading.value) {
            viewModelScope.launch { cancelProposalFlow() }
        }
    }

    private fun buildIdleState() =
        SignKeepKeyTransactionState(
            title = stringRes(R.string.keepkey_signing_title),
            subtitle = stringRes(R.string.keepkey_signing_subtitle),
            isLoading = false,
            errorMessage = null,
            positiveButton = ButtonState(
                text = stringRes(R.string.sign_keepkey_transaction_positive),
                onClick = ::onConfirmClick,
            ),
            negativeButton = ButtonState(
                text = stringRes(R.string.sign_keepkey_transaction_negative),
                onClick = ::onCancelClick,
            ),
            onBack = ::onBack,
        )
}
