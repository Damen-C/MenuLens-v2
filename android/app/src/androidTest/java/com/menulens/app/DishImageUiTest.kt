package com.menulens.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.menulens.app.model.MenuItem
import com.menulens.app.model.MenuPreview
import com.menulens.app.ui.screens.DetailScreen
import com.menulens.app.ui.screens.ShowToStaffScreen
import com.menulens.app.ui.theme.MenuLensTheme
import com.menulens.app.viewmodel.DishImageState
import com.menulens.app.viewmodel.ResultsUiState
import org.junit.Rule
import org.junit.Test

class DishImageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val item = MenuItem(
        itemId = "dish-1",
        jpText = "天ぷらうどん",
        priceText = "980円",
        imageGenerationToken = "signed-token",
        preview = MenuPreview(
            enTitle = "Tempura udon",
            enDescription = "Udon noodle soup typically served with crisp tempura.",
            tags = listOf("wheat_possible"),
            images = emptyList()
        )
    )

    @Test
    fun loadingSuccessFailureAndRetryStatesRenderWithoutRemovingText() {
        setDetail(DishImageState.Loading)
        composeRule.onNodeWithTag("generated_image_loading").assertIsDisplayed()
        composeRule.onNodeWithText("Tempura udon").assertIsDisplayed()

        setDetail(DishImageState.Ready("/cache/dish.webp"))
        composeRule.onNodeWithTag("generated_image_ready").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_image_label").assertIsDisplayed()

        setDetail(DishImageState.Failed("timeout"))
        composeRule.onNodeWithTag("retry_generated_image").assertIsDisplayed()
        composeRule.onNodeWithText("Tempura udon").assertIsDisplayed()
        composeRule.onNodeWithText(item.preview.enDescription).assertIsDisplayed()
    }

    @Test
    fun lockedDishRendersNoEnglishContentOrImage() {
        composeRule.setContent {
            MenuLensTheme {
                DetailScreen(
                    state = ResultsUiState(items = listOf(item)),
                    itemId = item.itemId,
                    onBack = {},
                    onReveal = {},
                    onShowToStaff = {},
                    onRetryImage = {}
                )
            }
        }
        composeRule.onNodeWithTag("locked_detail").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tempura udon").assertCountEquals(0)
        composeRule.onAllNodesWithTag("generated_image_loading").assertCountEquals(0)
    }

    @Test
    fun showToStaffExposesJapaneseFieldsOnly() {
        composeRule.setContent {
            MenuLensTheme {
                ShowToStaffScreen(jpText = item.jpText, priceText = item.priceText)
            }
        }
        composeRule.onNodeWithText("これをください").assertIsDisplayed()
        composeRule.onNodeWithText(item.jpText).assertIsDisplayed()
        composeRule.onNodeWithText(item.priceText!!).assertIsDisplayed()
        composeRule.onAllNodesWithText(item.preview.enTitle).assertCountEquals(0)
        composeRule.onAllNodesWithTag("generated_image_ready").assertCountEquals(0)
    }

    private fun setDetail(imageState: DishImageState) {
        composeRule.setContent {
            MenuLensTheme {
                DetailScreen(
                    state = ResultsUiState(
                        items = listOf(item),
                        unlockedItemIds = setOf(item.itemId),
                        imageStates = mapOf(item.itemId to imageState)
                    ),
                    itemId = item.itemId,
                    onBack = {},
                    onReveal = {},
                    onShowToStaff = {},
                    onRetryImage = {}
                )
            }
        }
    }
}
