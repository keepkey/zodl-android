package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.screen.connectkeepkey.connected.KeepKeyConnectedArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow

private const val ORCHARD_ACCOUNT_INDEX = 0

class ImportKeepKeyAccountUseCase(
    private val accountDataSource: AccountDataSource,
    private val navigationRouter: NavigationRouter,
) {
    suspend operator fun invoke(
        ufvk: String,
        seedFingerprint: ByteArray,
        birthday: BlockHeight?,
    ) {
        val account = accountDataSource.importKeepKeyAccount(
            ufvk = ufvk,
            seedFingerprint = seedFingerprint,
            index = ORCHARD_ACCOUNT_INDEX.toLong(),
            birthday = birthday,
        )
        accountDataSource.selectAccount(account)

        if (birthday != null) {
            navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.KEEPKEY))
        } else {
            navigationRouter.forward(KeepKeyConnectedArgs)
        }
    }
}
