package co.electriccoin.zcash.ui.screen.connectkeepkey.neworactive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun KeepKeyNewOrActiveView(state: KeepKeyNewOrActiveState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding),
        ) {
            Text(
                text = state.subtitle.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = state.activeDevice,
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
            )
            Spacer(Modifier.height(12.dp))
            ZashiButton(
                state = state.newDevice,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        KeepKeyNewOrActiveView(
            state =
                KeepKeyNewOrActiveState(
                    subtitle = stringRes("New or active device?"),
                    message = stringRes("Select whether this is a new KeepKey device or an active one."),
                    newDevice = ButtonState(stringRes("New device")) {},
                    activeDevice = ButtonState(stringRes("Active device")) {},
                    onBack = {},
                )
        )
    }
