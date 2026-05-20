package co.electriccoin.zcash.ui.screen.signkeepkeytransaction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * ZA-69: Compose UI tests for SignKeepKeyTransactionView.
 *
 * Pure View tests — no ViewModel, no transport, no emulator needed.
 */
@MediumTest
class SignKeepKeyTransactionViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- idle state ---

    @Test
    fun idleState_showsTitle() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState()) }
        }
        composeTestRule.onNodeWithText("Confirm on KeepKey").assertIsDisplayed()
    }

    @Test
    fun idleState_showsSubtitle() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState()) }
        }
        composeTestRule.onNodeWithText(
            "Review and approve the transaction on your KeepKey device."
        ).assertIsDisplayed()
    }

    @Test
    fun idleState_showsSignButton() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState()) }
        }
        composeTestRule.onNodeWithText("Sign Transaction").assertIsDisplayed()
    }

    @Test
    fun idleState_showsCancelButton() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState()) }
        }
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun idleState_noErrorShown() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState()) }
        }
        composeTestRule.onNodeWithText("Signing failed").assertDoesNotExist()
    }

    // --- loading state ---

    @Test
    fun loadingState_hidesBothButtons() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = loadingState()) }
        }
        composeTestRule.onNodeWithText("Sign Transaction").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun loadingState_stillShowsTitleAndSubtitle() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = loadingState()) }
        }
        composeTestRule.onNodeWithText("Confirm on KeepKey").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Review and approve the transaction on your KeepKey device."
        ).assertIsDisplayed()
    }

    // --- error state ---

    @Test
    fun errorState_showsErrorMessage() {
        val error = "Device disconnected during signing"
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState(errorMessage = error)) }
        }
        composeTestRule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test
    fun errorState_stillShowsButtons() {
        composeTestRule.setContent {
            ZcashTheme {
                SignKeepKeyTransactionView(
                    state = idleState(errorMessage = "Something went wrong")
                )
            }
        }
        composeTestRule.onNodeWithText("Sign Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun noError_errorMessageAbsent() {
        composeTestRule.setContent {
            ZcashTheme { SignKeepKeyTransactionView(state = idleState(errorMessage = null)) }
        }
        composeTestRule.onNodeWithText("Device disconnected during signing").assertDoesNotExist()
    }

    // --- callbacks ---

    @Test
    fun signButtonClick_firesPositiveCallback() {
        val clickCount = AtomicInteger(0)
        composeTestRule.setContent {
            ZcashTheme {
                SignKeepKeyTransactionView(
                    state = idleState(onSignClick = { clickCount.incrementAndGet() })
                )
            }
        }
        composeTestRule.onNodeWithText("Sign Transaction").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, clickCount.get(), "Sign button should fire positive callback once")
    }

    @Test
    fun cancelButtonClick_firesNegativeCallback() {
        val clickCount = AtomicInteger(0)
        composeTestRule.setContent {
            ZcashTheme {
                SignKeepKeyTransactionView(
                    state = idleState(onCancelClick = { clickCount.incrementAndGet() })
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, clickCount.get(), "Cancel button should fire negative callback once")
    }

    @Test
    fun disabledSignButton_doesNotFireCallback() {
        val clickCount = AtomicInteger(0)
        composeTestRule.setContent {
            ZcashTheme {
                SignKeepKeyTransactionView(
                    state = idleState(onSignClick = { clickCount.incrementAndGet() }, signEnabled = false)
                )
            }
        }
        composeTestRule.onNodeWithText("Sign Transaction").assertIsNotEnabled()
        assertEquals(0, clickCount.get(), "Disabled sign button must not fire callback")
    }

    // --- helpers ---

    private fun idleState(
        errorMessage: String? = null,
        onSignClick: () -> Unit = {},
        onCancelClick: () -> Unit = {},
        signEnabled: Boolean = true,
    ) = SignKeepKeyTransactionState(
        title = stringRes("Confirm on KeepKey"),
        subtitle = stringRes("Review and approve the transaction on your KeepKey device."),
        isLoading = false,
        errorMessage = errorMessage?.let { stringRes(it) },
        positiveButton = ButtonState(
            text = stringRes("Sign Transaction"),
            onClick = onSignClick,
            isEnabled = signEnabled,
        ),
        negativeButton = ButtonState(
            text = stringRes("Cancel"),
            onClick = onCancelClick,
        ),
        onBack = {},
    )

    private fun loadingState() = SignKeepKeyTransactionState(
        title = stringRes("Confirm on KeepKey"),
        subtitle = stringRes("Review and approve the transaction on your KeepKey device."),
        isLoading = true,
        errorMessage = null,
        positiveButton = ButtonState(text = stringRes("Sign Transaction"), onClick = {}),
        negativeButton = ButtonState(text = stringRes("Cancel"), onClick = {}),
        onBack = {},
    )
}
