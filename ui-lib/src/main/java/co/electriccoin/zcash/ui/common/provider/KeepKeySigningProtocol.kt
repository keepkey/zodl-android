package co.electriccoin.zcash.ui.common.provider

import com.google.protobuf.ByteString
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashPCZTAction
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashPCZTActionAck
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignPCZT
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignedPCZT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MSG_ZCASH_SIGN_PCZT = 1300
internal const val MSG_ZCASH_PCZT_ACTION = 1301
internal const val MSG_ZCASH_PCZT_ACTION_ACK = 1302
internal const val MSG_ZCASH_SIGNED_PCZT = 1303
internal const val MSG_FAILURE = 3

// Hardened BIP-32 / ZIP-32 index offset
private const val HARDENED = 0x80000000.toInt()

// ZIP-32 Orchard derivation path: [32', 133', account']
private const val ZIP32_PURPOSE = 32
private const val ZCASH_COIN_TYPE = 133

// Orchard alpha and sighash are each 32 bytes (one Pallas scalar / one BLS12-381 digest)
private const val ORCHARD_FIELD_BYTES = 32

/**
 * Per-action signing data for an Orchard action.
 *
 * In "legacy mode" (ZIP-244 sighash pre-computed by the host) the device needs only
 * [alpha] and [sighash] to produce the RedPallas spend-auth signature. The optional
 * [value] and [isSpend] fields are used for on-screen confirmation display.
 *
 * @param alpha   32-byte spend-authorization randomizer (uniformly random per action)
 * @param sighash 32-byte per-action sighash (ZIP-244, host-computed)
 * @param value   action value in zatoshis (for device display)
 * @param isSpend true if this action spends a note; false for output-only actions
 */
data class OrchardActionData(
    val alpha: ByteArray,
    val sighash: ByteArray,
    val value: Long = 0L,
    val isSpend: Boolean = true,
) {
    init {
        require(alpha.size == ORCHARD_FIELD_BYTES) { "alpha must be 32 bytes, got ${alpha.size}" }
        require(sighash.size == ORCHARD_FIELD_BYTES) { "sighash must be 32 bytes, got ${sighash.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrchardActionData) return false
        return alpha.contentEquals(other.alpha) &&
            sighash.contentEquals(other.sighash) &&
            value == other.value &&
            isSpend == other.isSpend
    }

    override fun hashCode(): Int {
        var result = alpha.contentHashCode()
        result = 31 * result + sighash.contentHashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + isSpend.hashCode()
        return result
    }
}

/**
 * Drives the ZcashSignPCZT → (ZcashPCZTAction × nActions) → ZcashSignedPCZT exchange over USB.
 *
 * Extracted from KeepKeyProposalRepository so the protocol state machine can be unit-tested
 * without Android SDK or ZCash SDK type dependencies.
 *
 * Two signing modes:
 * - **Legacy mode**: caller provides [actions] with pre-computed [OrchardActionData.sighash].
 *   The device signs each sighash directly using [OrchardActionData.alpha].
 * - **On-device sighash mode**: caller provides the digest fields in [ZcashSignPCZT] and
 *   leaves [OrchardActionData.sighash] blank. Not yet implemented — requires SDK support.
 *
 * When [actions] is empty, [nActions] controls the loop count (used by production code while
 * the SDK does not yet expose per-action data from a redacted PCZT).
 */
internal class KeepKeySigningProtocol(
    private val transport: KeepKeyTransportProvider
) {
    suspend fun sign(
        accountIndex: Int,
        pcztBytes: ByteArray,
        nActions: Int = 0,
        actions: List<OrchardActionData> = emptyList(),
        totalAmount: Long = 0L,
        fee: Long = 0L,
    ): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val actionCount = if (actions.isNotEmpty()) actions.size else nActions

            val initRequest =
                ZcashSignPCZT
                    .newBuilder()
                    .addAddressN(HARDENED or ZIP32_PURPOSE)
                    .addAddressN(HARDENED or ZCASH_COIN_TYPE)
                    .addAddressN(HARDENED or accountIndex)
                    .setAccount(accountIndex)
                    .setPcztData(ByteString.copyFrom(pcztBytes))
                    .setNActions(actionCount)
                    .also { if (totalAmount > 0L) it.setTotalAmount(totalAmount) }
                    .also { if (fee > 0L) it.setFee(fee) }
                    .build()

            val (ackType, ackBytes) = transport.sendMessage(MSG_ZCASH_SIGN_PCZT, initRequest.toByteArray())
            if (ackType == MSG_FAILURE) throw KeepKeyTransportException("Device returned Failure on ZcashSignPCZT")
            check(ackType == MSG_ZCASH_PCZT_ACTION_ACK) {
                "Expected ZcashPCZTActionAck ($MSG_ZCASH_PCZT_ACTION_ACK) but got $ackType"
            }

            var nextIndex = ZcashPCZTActionAck.parseFrom(ackBytes).nextIndex
            val signatures = mutableListOf<ByteArray>()

            for (i in 0 until actionCount) {
                check(nextIndex == i) { "Device requested action $nextIndex but host expected $i" }

                val actionBuilder = ZcashPCZTAction.newBuilder().setIndex(i)
                if (i < actions.size) {
                    val a = actions[i]
                    actionBuilder
                        .setAlpha(ByteString.copyFrom(a.alpha))
                        .setSighash(ByteString.copyFrom(a.sighash))
                        .setValue(a.value)
                        .setIsSpend(a.isSpend)
                }

                val (responseType, responseBytes) =
                    transport.sendMessage(MSG_ZCASH_PCZT_ACTION, actionBuilder.build().toByteArray())

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

                    else -> {
                        error("Unexpected response type $responseType after ZcashPCZTAction[$i]")
                    }
                }
            }

            signatures
        }
}
