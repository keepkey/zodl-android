package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeepKeyAccount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetKeepKeyStatusUseCase(
    private val accountDataSource: AccountDataSource,
) {
    fun observe() =
        accountDataSource.allAccounts
            .map {
                val enabled = it?.none { account -> account is KeepKeyAccount } ?: false
                if (enabled) Status.ENABLED else Status.UNAVAILABLE
            }.distinctUntilChanged()
}
