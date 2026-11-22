package com.androidwebui.android.ui.shell

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class ChatSummary(val id: String, val title: String, val updatedAtSeconds: Long, val pinned: Boolean = false)

data class FolderSummary(val id: String, val name: String)

/** Sidebar group heading. Months older than 30 days are named by month and year. */
sealed interface ChatGroup {
    data object Pinned : ChatGroup
    data object Today : ChatGroup
    data object Yesterday : ChatGroup
    data object Previous7Days : ChatGroup
    data object Previous30Days : ChatGroup
    data class Month(val label: String) : ChatGroup
}

data class ChatSection(val group: ChatGroup, val chats: List<ChatSummary>)

/** Groups chats newest first: pinned, today, yesterday, previous 7 and 30 days, then one section per month. */
fun groupChatsByDate(
    chats: List<ChatSummary>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<ChatSection> {
    val sorted = chats.sortedByDescending { it.updatedAtSeconds }
    val out = LinkedHashMap<ChatGroup, MutableList<ChatSummary>>()
    for (chat in sorted) {
        val group = if (chat.pinned) ChatGroup.Pinned else {
            val date = Instant.ofEpochSecond(chat.updatedAtSeconds).atZone(zone).toLocalDate()
            val daysAgo = today.toEpochDay() - date.toEpochDay()
            when {
                daysAgo <= 0 -> ChatGroup.Today
                daysAgo == 1L -> ChatGroup.Yesterday
                daysAgo <= 7 -> ChatGroup.Previous7Days
                daysAgo <= 30 -> ChatGroup.Previous30Days
                else -> ChatGroup.Month("${date.month.getDisplayName(TextStyle.FULL, locale)} ${date.year}")
            }
        }
        out.getOrPut(group) { mutableListOf() } += chat
    }
    val order = listOf(ChatGroup.Pinned, ChatGroup.Today, ChatGroup.Yesterday, ChatGroup.Previous7Days, ChatGroup.Previous30Days)
    val fixed = order.mapNotNull { g -> out[g]?.let { ChatSection(g, it) } }
    val months = out.filterKeys { it is ChatGroup.Month }.map { (g, v) -> ChatSection(g, v) }
    return fixed + months
}

fun List<ChatSummary>.searchChats(query: String): List<ChatSummary> {
    val q = query.trim()
    return if (q.isEmpty()) this else filter { it.title.contains(q, ignoreCase = true) }
}
