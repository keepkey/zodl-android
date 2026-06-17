package co.electriccoin.zcash.ui.screen.connectkeepkey.estimation

import kotlinx.serialization.Serializable

@Serializable
data class KeepKeyEstimationArgs(
    val blockHeight: Long,
)
