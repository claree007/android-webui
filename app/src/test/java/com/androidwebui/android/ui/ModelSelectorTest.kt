package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.androidwebui.android.ui.shell.ModelListState
import com.androidwebui.android.ui.shell.ModelOption
import com.androidwebui.android.ui.shell.ModelSelectorSheet
import com.androidwebui.android.ui.shell.filterModels
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val models = listOf(
    ModelOption("gpt-a", "GPT Alpha", "OpenAI"),
    ModelOption("gem-b", "Gemini Beta", "Gemini"),
    ModelOption("llama", "Llama 3", "Ollama"),
)

class FilterModelsTest {
    @Test
    fun matchesNameOrIdIgnoringCase() {
        assertEquals(listOf("gem-b"), models.filterModels("GEMINI", null).map { it.id })
        assertEquals(listOf("llama"), models.filterModels("llama", null).map { it.id })
    }

    @Test
    fun groupNarrowsTheResult() {
        assertEquals(listOf("gpt-a"), models.filterModels("", "OpenAI").map { it.id })
        assertEquals(emptyList<String>(), models.filterModels("gem", "OpenAI").map { it.id })
    }

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(3, models.filterModels("  ", null).size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelSelectorTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        state: ModelListState,
        selected: String? = null,
        default: String? = null,
        onSelect: (ModelOption) -> Unit = {},
        onSetDefault: (ModelOption) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        AppTheme(true) { ModelSelectorSheet(state, selected, default, onSelect, onSetDefault, onRetry, onDismiss = {}) }
    }

    @Test
    fun listsModelsAndSelectsOne() {
        var picked: ModelOption? = null
        show(ModelListState.Loaded(models), onSelect = { picked = it })
        compose.onNodeWithText("GPT Alpha").assertIsDisplayed()
        compose.onNodeWithText("Llama 3").performClick()
        assertEquals("llama", picked?.id)
    }

    @Test
    fun searchNarrowsRowsAndReportsNoMatch() {
        show(ModelListState.Loaded(models))
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.onNodeWithText("No model matches your search.").assertIsDisplayed()
    }

    @Test
    fun selectedRowIsMarked() {
        show(ModelListState.Loaded(models), selected = "gem-b")
        compose.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }

    @Test
    fun setAsDefaultActsOnTheSelectedModel() {
        var set: ModelOption? = null
        show(ModelListState.Loaded(models), selected = "gem-b", onSetDefault = { set = it })
        compose.onNodeWithText("Set as default").performClick()
        assertEquals("gem-b", set?.id)
    }

    @Test
    fun loadingAndErrorAndEmptyStatesAreShown() {
        show(ModelListState.Loading)
        compose.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retried = false
        show(ModelListState.Error("Cannot reach server"), onRetry = { retried = true })
        compose.onNodeWithText("Cannot reach server").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun emptyListExplainsWhy() {
        show(ModelListState.Loaded(emptyList()))
        compose.onNodeWithText("No models are available. Adding a model connection is not possible yet.").assertIsDisplayed()
    }
}
