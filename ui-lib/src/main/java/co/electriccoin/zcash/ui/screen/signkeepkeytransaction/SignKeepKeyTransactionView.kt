package co.electriccoin.zcash.ui.screen.signkeepkeytransaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignKeepKeyTransactionView(state: SignKeepKeyTransactionState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(state.onBack) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(paddingValues),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.subtitle.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            state.errorMessage?.let { err ->
                Text(
                    text = err.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Utility.ErrorRed.utilityError700,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
            } else {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.positiveButton,
                )
                Spacer(Modifier.height(8.dp))
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.negativeButton,
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun SignKeepKeyTransactionViewPreview() =
    ZcashTheme {
        SignKeepKeyTransactionView(
            state =
                SignKeepKeyTransactionState(
                    title = stringRes("Confirm on KeepKey"),
                    subtitle = stringRes("Review and approve the transaction on your KeepKey device."),
                    isLoading = false,
                    errorMessage = null,
                    positiveButton =
                        co.electriccoin.zcash.ui.design.component.ButtonState(
                            text = stringRes("Sign Transaction"),
                            onClick = {},
                        ),
                    negativeButton =
                        co.electriccoin.zcash.ui.design.component.ButtonState(
                            text = stringRes("Cancel"),
                            onClick = {},
                        ),
                    onBack = {},
                ),
        )
    }
