package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.KeepKeyAccount
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.provider.KeepKeySigningProtocol
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportException
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

interface KeepKeyProposalRepository {
    val transactionProposal: Flow<TransactionProposal?>

    val submitState: Flow<SubmitProposalState?>

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createProposal(zecSend: ZecSend)

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactInputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactInputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactOutputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactOutputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal()

    @Throws(IllegalStateException::class, KeepKeyTransportException::class)
    suspend fun signAndSubmit(): SubmitResult

    fun clear()

    suspend fun getTransactionProposal(): TransactionProposal
}

@Suppress("TooManyFunctions")
class KeepKeyProposalRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val proposalDataSource: ProposalDataSource,
    private val transportProvider: KeepKeyTransportProvider,
) : KeepKeyProposalRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val signingProtocol = KeepKeySigningProtocol(transportProvider)

    override val transactionProposal = MutableStateFlow<TransactionProposal?>(null)
    override val submitState = MutableStateFlow<SubmitProposalState?>(null)

    override suspend fun createProposal(zecSend: ZecSend) {
        createProposalInternal {
            proposalDataSource.createProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend,
            )
        }
    }

    override suspend fun createExactInputSwapProposal(
        zecSend: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal =
        createProposalInternal {
            proposalDataSource.createExactInputProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend,
                quote = quote,
            )
        }

    override suspend fun createExactOutputSwapProposal(
        zecSend: ZecSend,
        quote: SwapQuote,
    ): ExactOutputSwapTransactionProposal =
        createProposalInternal {
            proposalDataSource.createExactOutputProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend,
                quote = quote,
            )
        }

    override suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal =
        createProposalInternal {
            proposalDataSource.createZip321Proposal(
                account = accountDataSource.getSelectedAccount(),
                zip321Uri = zip321Uri,
            )
        }

    override suspend fun createShieldProposal() {
        createProposalInternal {
            proposalDataSource.createShieldProposal(
                account = accountDataSource.getSelectedAccount(),
            )
        }
    }

    @Suppress("UseCheckOrError", "ThrowingExceptionsWithoutMessageOrCause", "TooGenericExceptionCaught")
    override suspend fun signAndSubmit(): SubmitResult =
        scope.async {
            val proposal =
                transactionProposal.value
                    ?: throw IllegalStateException("No transaction proposal")

            val keepKeyAccount = accountDataSource.getSelectedAccount() as? KeepKeyAccount
                ?: throw IllegalStateException("Selected account is not a KeepKey account")

            submitState.update { SubmitProposalState.Submitting }

            try {
                // 1. Create PCZT from the proposal and add ZK proofs.
                val rawPczt =
                    proposalDataSource.createPcztFromProposal(
                        account = keepKeyAccount,
                        proposal = proposal.proposal,
                    )
                val pcztWithProofs = proposalDataSource.addProofsToPczt(rawPczt.clonePczt())

                // 2. Redact the PCZT so only signing-relevant fields are sent to the device.
                val redactedPczt = proposalDataSource.redactPcztForSigner(pcztWithProofs.clonePczt())

                // 3. Drive the KeepKey signing exchange over USB, collect RedPallas signatures.
                // TODO(sdk): pass nActions from redactedPczt once SDK exposes it; 0 means no Orchard actions for now.
                val signatures = signingProtocol.sign(
                    accountIndex = keepKeyAccount.sdkAccount.accountUuid.value.hashCode() and 0x7FFFFFFF,
                    pcztBytes = redactedPczt.toByteArray(),
                    nActions = 0, // TODO(sdk): extract from redactedPczt once SDK exposes it
                )

                // 4. TODO(sdk): Insert the RedPallas signatures into the PCZT.
                //
                //    The ZCash Android SDK does not yet expose a method to embed spend auth
                //    signatures into a Pczt.  The needed method signature is:
                //
                //      Synchronizer.addSpendAuthSigsToPczt(pczt: Pczt, sigs: List<ByteArray>): Pczt
                //
                //    Each entry in `sigs` is a 64-byte RedPallas signature for the corresponding
                //    Orchard action's spend_auth_sig field.  Once this SDK method exists, replace
                //    the UnsupportedOperationException below with the real call.
                val pcztWithSignatures = insertSignaturesIntoPczt(redactedPczt, signatures)

                // 5. Finalize and broadcast.
                val result =
                    proposalDataSource.submitTransaction(
                        pcztWithProofs = pcztWithProofs,
                        pcztWithSignatures = pcztWithSignatures,
                    )
                submitState.update { SubmitProposalState.Result(result) }
                result
            } catch (e: Exception) {
                Twig.error(e) { "KeepKey signAndSubmit failed" }
                submitState.update { SubmitProposalState.Result(SubmitResult.Error(e)) }
                throw e
            }
        }.await()

    // TODO(sdk): Replace this stub with a real SDK call once the method is available.
    // See the signAndSubmit() comment above for the required SDK method signature.
    @Suppress("UNUSED_PARAMETER")
    private fun insertSignaturesIntoPczt(pczt: Pczt, signatures: List<ByteArray>): Pczt =
        throw UnsupportedOperationException(
            "Signature insertion requires Synchronizer.addSpendAuthSigsToPczt — implement in the ZCash SDK first"
        )

    override suspend fun getTransactionProposal(): TransactionProposal =
        transactionProposal.filterNotNull().first()

    override fun clear() {
        transactionProposal.update { null }
        submitState.update { null }
    }

    private inline fun <T : TransactionProposal> createProposalInternal(block: () -> T): T {
        val proposal =
            try {
                block()
            } catch (e: TransactionProposalNotCreatedException) {
                Twig.error(e) { "Unable to create KeepKey proposal" }
                transactionProposal.update { null }
                throw e
            } catch (e: InsufficientFundsException) {
                Twig.error(e) { "Insufficient funds for KeepKey proposal" }
                transactionProposal.update { null }
                throw e
            }
        transactionProposal.update { proposal }
        return proposal
    }
}
