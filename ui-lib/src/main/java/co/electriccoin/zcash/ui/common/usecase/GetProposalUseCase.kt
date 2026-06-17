package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.KeepKeyAccount
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeepKeyProposalRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository

class GetProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val keepKeyProposalRepository: KeepKeyProposalRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val accountDataSource: AccountDataSource,
) {
    suspend operator fun invoke(): TransactionProposal =
        when (accountDataSource.getSelectedAccount()) {
            is KeepKeyAccount -> keepKeyProposalRepository.getTransactionProposal()
            is KeystoneAccount -> keystoneProposalRepository.getTransactionProposal()
            is ZashiAccount -> zashiProposalRepository.getTransactionProposal()
        }
}
