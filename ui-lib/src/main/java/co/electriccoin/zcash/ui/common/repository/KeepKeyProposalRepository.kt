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
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportException
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportProvider
import com.google.protobuf.ByteString
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashPCZTAction
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashPCZTActionAck
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignPCZT
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignedPCZT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private const val MSG_ZCASH_SIGN_PCZT = 1300
private const val MSG_ZCASH_PCZT_ACTION = 1301
private const val MSG_ZCASH_PCZT_ACTION_ACK = 1302
private const val MSG_ZCASH_SIGNED_PCZT = 1303
private const val MSG_FAILURE = 3

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
                val signatures = signWithDevice(redactedPczt, keepKeyAccount)

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

    // Drives the ZcashSignPCZT → ZcashPCZTAction × N → ZcashSignedPCZT message exchange.
    // Returns one 64-byte RedPallas signature per Orchard action.
    private suspend fun signWithDevice(
        redactedPczt: Pczt,
        keepKeyAccount: KeepKeyAccount,
    ): List<ByteArray> =
        withContext(Dispatchers.IO) {
            // TODO(sdk): Extract n_actions, digests, and bundle metadata from redactedPczt.
            //   Requires a new SDK method, e.g.:
            //     Synchronizer.getPcztSigningParams(Pczt): PcztSigningParams
            //   where PcztSigningParams holds nActions, headerDigest, orchardDigest, etc.
            //   Until available, these are left unset; a real device will reject the message.
            val initRequest =
                ZcashSignPCZT.newBuilder()
                    .setAccount(keepKeyAccount.sdkAccount.accountUuid.value.hashCode() and 0x7FFFFFFF)
                    .setPcztData(ByteString.copyFrom(redactedPczt.toByteArray()))
                    // n_actions, total_amount, fee, digests, orchard metadata — TODO(sdk)
                    .build()

            val (ackType, ackBytes) = transportProvider.sendMessage(MSG_ZCASH_SIGN_PCZT, initRequest.toByteArray())
            if (ackType == MSG_FAILURE) throw KeepKeyTransportException("Device returned Failure on ZcashSignPCZT")
            check(ackType == MSG_ZCASH_PCZT_ACTION_ACK) {
                "Expected ZcashPCZTActionAck ($MSG_ZCASH_PCZT_ACTION_ACK) but got $ackType"
            }

            var nextIndex = ZcashPCZTActionAck.parseFrom(ackBytes).nextIndex

            // TODO(sdk): Replace with actual nActions from the PCZT.
            val nActions = 0
            val signatures = mutableListOf<ByteArray>()

            for (i in 0 until nActions) {
                check(nextIndex == i) { "Device requested action $nextIndex but host expected $i" }

                // TODO(sdk): Populate action fields from redactedPczt.orchardActions[i].
                //   Fields: alpha, cvNet, value, isSpend, nullifier, cmx, epk,
                //           encCompact, encMemo, encNoncompact, rk, outCiphertext.
                val actionMsg =
                    ZcashPCZTAction.newBuilder()
                        .setIndex(i)
                        .build()

                val (responseType, responseBytes) = transportProvider.sendMessage(
                    MSG_ZCASH_PCZT_ACTION,
                    actionMsg.toByteArray(),
                )
                if (responseType == MSG_FAILURE) {
                    throw KeepKeyTransportException("Device returned Failure on ZcashPCZTAction[$i]")
                }

                when (responseType) {
                    MSG_ZCASH_PCZT_ACTION_ACK -> {
                        nextIndex = ZcashPCZTActionAck.parseFrom(responseBytes).nextIndex
                    }

                    MSG_ZCASH_SIGNED_PCZT -> {
                        val signed = ZcashSignedPCZT.parseFrom(responseBytes)
                        signatures.addAll(signed.signaturesList.map { it.toByteArray() })
                    }

                    else -> error("Unexpected response type $responseType after ZcashPCZTAction[$i]")
                }
            }

            signatures
        }

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
