package co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiCheckboxListItem
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiCheckboxListItemState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItem
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItemState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.selectkeepkeyaccount.model.SelectKeepKeyAccountState

@Composable
fun SelectKeepKeyAccountView(state: SelectKeepKeyAccountState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = {
                    ZashiTopAppBarCloseNavigation(state.onBack)
                }
            )
        }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldScrollPadding(it)
        ) {
            HeaderSection(state = state, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(48.dp))
            Content(state)
            Spacer(Modifier.weight(1f))
            BottomSection(state = state, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun Content(state: SelectKeepKeyAccountState) {
    Column {
        state.items.forEachIndexed { index, item ->
            if (index != 0) {
                ZashiHorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            }
            when (item) {
                is ZashiCheckboxListItemState -> {
                    ZashiCheckboxListItem(
                        state = item,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }
                is ZashiExpandedCheckboxListItemState -> {
                    ZashiExpandedCheckboxListItem(
                        state = item,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSection(
    state: SelectKeepKeyAccountState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positiveButton
        )
    }
}

@Composable
private fun HeaderSection(
    state: SelectKeepKeyAccountState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Image(
            modifier = Modifier.height(32.dp),
            painter = painterResource(R.drawable.ic_item_keepkey),
            contentDescription = null,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SelectKeepKeyAccountView(
            state =
                SelectKeepKeyAccountState(
                    onBack = {},
                    title = stringRes("Confirm Account to Access"),
                    subtitle = stringRes(
                        "Review the KeepKey account before connecting."
                    ),
                    items =
                        listOf(
                            ZashiExpandedCheckboxListItemState(
                                title = stringRes("KeepKey Wallet"),
                                subtitle = stringResByAddress("u1abc...xyz"),
                                icon = R.drawable.ic_item_keepkey,
                                isSelected = true,
                                info = null,
                                onClick = {},
                            )
                        ),
                    positiveButton = ButtonState(stringRes("Connect")),
                )
        )
    }
