package co.electriccoin.zcash.ui.screen.signkeepkeytransaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun SignKeepKeyTransactionScreen() {
    val vm = koinViewModel<SignKeepKeyTransactionVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    SignKeepKeyTransactionView(state)
}

@Serializable
object SignKeepKeyTransactionArgs
