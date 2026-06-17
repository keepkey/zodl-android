package co.electriccoin.zcash.ui.screen.connectkeepkey.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepKeyConnectView(state: KeepKeyConnectState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(state.onBackClick) },
            )
        }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(it)
        ) {
            HeaderSection()
            Spacer(Modifier.height(24.dp))
            InstructionsSection()
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))
            state.errorMessage?.let { err ->
                Text(
                    text = err.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Utility.ErrorRed.utilityError700,
                )
                Spacer(Modifier.height(8.dp))
            }
            BottomSection(state)
        }
    }
}

@Composable
private fun HeaderSection() {
    Column {
        Text(
            text = stringResource(R.string.connect_keepkey_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connect_keepkey_subtitle),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@Composable
private fun InstructionsSection() {
    val itemPadding = PaddingValues(top = 8.dp, end = 20.dp, bottom = 8.dp)
    Column {
        Text(
            text = stringResource(R.string.connect_keepkey_item_title),
            style = ZashiTypography.textLg,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        ZashiListItem(
            title = stringResource(R.string.connect_keepkey_item_1),
            contentPadding = itemPadding,
            icon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keepkey),
        )
        ZashiListItem(
            title = stringResource(R.string.connect_keepkey_item_2),
            contentPadding = itemPadding,
            icon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keepkey),
        )
        ZashiListItem(
            title = stringResource(R.string.connect_keepkey_item_3),
            contentPadding = itemPadding,
            icon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keepkey),
        )
    }
}

@Composable
private fun BottomSection(state: KeepKeyConnectState) {
    if (state.isLoading) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
    } else {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.connect_keepkey_positive),
            onClick = state.onConnectClick,
        )
    }
}

@PreviewScreens
@Composable
private fun KeepKeyConnectViewPreview() =
    ZcashTheme {
        KeepKeyConnectView(
            state =
                KeepKeyConnectState(
                    isLoading = false,
                    errorMessage = null,
                    onBackClick = {},
                    onConnectClick = {},
                )
        )
    }
