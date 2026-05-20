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

/**
 * Drives the ZcashSignPCZT → (ZcashPCZTAction × nActions) → ZcashSignedPCZT exchange over USB.
 *
 * Extracted from KeepKeyProposalRepository so the protocol state machine can be unit-tested
 * without Android SDK or ZCash SDK type dependencies.
 */
internal class KeepKeySigningProtocol(private val transport: KeepKeyTransportProvider) {

    suspend fun sign(
        accountIndex: Int,
        pcztBytes: ByteArray,
        nActions: Int,
    ): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val initRequest =
                ZcashSignPCZT.newBuilder()
                    .setAccount(accountIndex)
                    .setPcztData(ByteString.copyFrom(pcztBytes))
                    .build()

            val (ackType, ackBytes) = transport.sendMessage(MSG_ZCASH_SIGN_PCZT, initRequest.toByteArray())
            if (ackType == MSG_FAILURE) throw KeepKeyTransportException("Device returned Failure on ZcashSignPCZT")
            check(ackType == MSG_ZCASH_PCZT_ACTION_ACK) {
                "Expected ZcashPCZTActionAck ($MSG_ZCASH_PCZT_ACTION_ACK) but got $ackType"
            }

            var nextIndex = ZcashPCZTActionAck.parseFrom(ackBytes).nextIndex
            val signatures = mutableListOf<ByteArray>()

            for (i in 0 until nActions) {
                check(nextIndex == i) { "Device requested action $nextIndex but host expected $i" }

                val actionMsg = ZcashPCZTAction.newBuilder().setIndex(i).build()
                val (responseType, responseBytes) = transport.sendMessage(MSG_ZCASH_PCZT_ACTION, actionMsg.toByteArray())

                if (responseType == MSG_FAILURE) {
                    throw KeepKeyTransportException("Device returned Failure on ZcashPCZTAction[$i]")
                }

                when (responseType) {
                    MSG_ZCASH_PCZT_ACTION_ACK -> nextIndex = ZcashPCZTActionAck.parseFrom(responseBytes).nextIndex
                    MSG_ZCASH_SIGNED_PCZT -> {
                        val signed = ZcashSignedPCZT.parseFrom(responseBytes)
                        signatures.addAll(signed.signaturesList.map { it.toByteArray() })
                    }
                    else -> error("Unexpected response type $responseType after ZcashPCZTAction[$i]")
                }
            }

            signatures
        }
}
