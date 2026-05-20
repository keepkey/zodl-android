package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.zcash.ui.common.crypto.Blake2b
import co.electriccoin.zcash.ui.common.crypto.OrchardUfvkEncoder
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportException
import co.electriccoin.zcash.ui.common.provider.KeepKeyTransportProvider
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashGetOrchardFVK
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashOrchardFVK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MSG_ZCASH_GET_ORCHARD_FVK = 1304
private const val MSG_ZCASH_ORCHARD_FVK = 1305
private const val ORCHARD_ACCOUNT_INDEX = 0
private val SEED_FP_PERSONAL = "KeepKey_Seed_FP ".toByteArray(Charsets.US_ASCII)

data class KeepKeyFvkData(
    val ufvk: String,
    val seedFingerprint: ByteArray,
    val unifiedAddress: String,
)

class GetKeepKeyOrchardFVKUseCase(
    private val transportProvider: KeepKeyTransportProvider,
) {
    suspend operator fun invoke(): KeepKeyFvkData {
        val granted = transportProvider.requestPermission()
        if (!granted) throw KeepKeyTransportException("USB permission denied")
        transportProvider.connect()

        val request = ZcashGetOrchardFVK.newBuilder()
            .setAccount(ORCHARD_ACCOUNT_INDEX)
            .build()
        val (responseType, responseBytes) = transportProvider.sendMessage(
            MSG_ZCASH_GET_ORCHARD_FVK,
            request.toByteArray(),
        )
        check(responseType == MSG_ZCASH_ORCHARD_FVK) {
            "Unexpected KeepKey response type: $responseType (expected $MSG_ZCASH_ORCHARD_FVK)"
        }
        val fvk = ZcashOrchardFVK.parseFrom(responseBytes)
        val ak = fvk.ak.toByteArray()
        val nk = fvk.nk.toByteArray()
        val rivk = fvk.rivk.toByteArray()

        val ufvk = OrchardUfvkEncoder.encode(ak, nk, rivk, VersionInfo.NETWORK)
        val seedFingerprint = Blake2b.hash(ak + nk + rivk, personal = SEED_FP_PERSONAL).copyOf(32)
        val unifiedAddress = withContext(Dispatchers.Default) {
            DerivationTool.getInstance().deriveUnifiedAddress(
                viewingKey = ufvk,
                network = VersionInfo.NETWORK,
            )
        }

        return KeepKeyFvkData(ufvk, seedFingerprint, unifiedAddress)
    }
}
