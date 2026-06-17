package co.electriccoin.zcash.ui.screen.selectkeepkeyaccount

import kotlinx.serialization.Serializable

// birthday = -1L encodes a null birthday (new-device path)
@Serializable
data class SelectKeepKeyAccountArgs(
    val ufvk: String,
    val seedFingerprintHex: String,
    val unifiedAddress: String,
    val birthday: Long,
)
