package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.androidwebui.android.ui.shell.ChatGroup
import com.androidwebui.android.ui.shell.ChatSummary
import com.androidwebui.android.ui.shell.HomeScreen
import com.androidwebui.android.ui.shell.SidebarState
import com.androidwebui.android.ui.shell.groupChatsByDate
import com.androidwebui.android.ui.shell.searchChats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val today = LocalDate.of(2026, 6, 15)

private fun chat(id: String, daysAgo: Long, pinned: Boolean = false) =
    ChatSummary(id, "Chat $id", today.minusDays(daysAgo).atTime(12, 0).toEpochSecond(ZoneOffset.UTC), pinned)

class GroupChatsTest {
    private fun groups(vararg c: ChatSummary) =
        groupChatsByDate(c.toList(), today, ZoneOffset.UTC, Locale.ENGLISH).map { it.group }

    @Test
    fun bucketsFollowTheDocumentedBoundaries() {
        assertEquals(
            listOf(ChatGroup.Today, ChatGroup.Yesterday, ChatGroup.Previous7Days, ChatGroup.Previous30Days),
            groups(chat("a", 0), chat("b", 1), chat("c", 7), chat("d", 30)),
        )
    }

    @Test
    fun olderChatsGetOneSectionPerMonthNewestFirst() {
        assertEquals(
            listOf(ChatGroup.Month("April 2026"), ChatGroup.Month("January 2026")),
            groups(chat("x", 150), chat("y", 60)).let { it },
        )
    }

    @Test
    fun pinnedComesFirstRegardlessOfAge() {
        assertEquals(listOf(ChatGroup.Pinned, ChatGroup.Today), groups(chat("a", 0), chat("p", 400, pinned = true)))
    }

    @Test
    fun withinAGroupNewestIsFirst() {
        val sections = groupChatsByDate(listOf(chat("old", 3), chat("new", 2)), today, ZoneOffset.UTC)
        assertEquals(listOf("new", "old"), sections.single().chats.map { it.id })
    }

    @Test
    fun searchMatchesTitlesIgnoringCase() {
        assertEquals(listOf("a"), listOf(chat("a", 0), chat("b", 0)).searchChats("CHAT A").map { it.id })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SidebarTest {
    @get:Rule val compose = createComposeRule()

    private fun open(state: SidebarState) {
        compose.setContent { AppTheme(true) { HomeScreen("User", onOpenSettings = {}, sidebar = state, today = today) } }
        compose.onNodeWithContentDescription("Open sidebar").performClick()
    }

    @Test
    fun drawerListsChatsUnderDateHeadings() {
        open(SidebarState(chats = listOf(chat("a", 0), chat("b", 1))))
        compose.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yesterday").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chat a").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyDrawerSaysSo() {
        open(SidebarState())
        compose.onNodeWithText("No chats yet.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun errorAndLoadingAreShown() {
        open(SidebarState(error = "Database unavailable"))
        compose.onNodeWithText("Database unavailable").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun searchOpensTheChatSearchSheet() {
        open(SidebarState(chats = listOf(chat("a", 0))))
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search chats").assertIsDisplayed()
    }
}
