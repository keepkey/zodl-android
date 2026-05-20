package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeepKeyAccount
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DisconnectUseCase(
    private val accountDataSource: AccountDataSource,
    private val biometricRepository: BiometricRepository
) {
    private val logger = loggableNot("DisconnectUseCase")

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(account: WalletAccount) =
        withContext(Dispatchers.IO) {
            biometricRepository.requestBiometrics(
                BiometricRequest(message = stringRes(R.string.disconnect_hardware_wallet_biometric_message))
            )

            logger("deleteAccount $account")
            accountDataSource.deleteAccount(account)
            logger("deleteAccount success")

            val zashiAccount = accountDataSource.getZashiAccount()
            accountDataSource.selectAccount(zashiAccount)
        }

    suspend fun getHardwareWalletAccount(): WalletAccount? =
        accountDataSource
            .getAllAccounts()
            .firstOrNull { it is KeystoneAccount || it is KeepKeyAccount }
}
