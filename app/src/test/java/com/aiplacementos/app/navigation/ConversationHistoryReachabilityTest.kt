package com.aiplacementos.app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversation History is actually reachable, and its route survives refactoring.
 *
 * A screen nobody can navigate to is not a feature, and a route whose pattern and builder drift
 * apart crashes at the moment of navigation rather than at compile time -- which is exactly the
 * class of defect `VoiceAgentReachabilityTest` was written for after the voice route gained a
 * fifth segment. These guards read the real production sources from disk for the same reason.
 */
class ConversationHistoryReachabilityTest {

    @Test
    fun `the detail route builder produces a path the graph pattern accepts`() {
        val built = conversationDetailRoute(42)
        val pattern = Screen.ConversationDetail.route

        assertEquals("conversation-history/42", built)
        // Same shape once the placeholder is filled: same segment count, same prefix.
        assertEquals(
            fill(pattern, "conversationId" to "42"),
            built,
        )
        assertEquals(
            "segment count must match the pattern",
            pattern.split("/").size,
            built.split("/").size,
        )
    }

    @Test
    fun `the list route is a distinct destination from the detail route`() {
        // If these collided, opening a conversation would re-enter the list.
        assertEquals("conversation-history", Screen.ConversationHistory.route)
        assertTrue(Screen.ConversationDetail.route.startsWith("conversation-history/"))
        assertFalse(Screen.ConversationHistory.route == Screen.ConversationDetail.route)
    }

    @Test
    fun `the id is the only placeholder in the detail route`() {
        val placeholders = Regex("""\{(\w+)}""")
            .findAll(Screen.ConversationDetail.route)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("conversationId"), placeholders)
    }

    // ------------------------------------------------------------------ the graph

    @Test
    fun `the graph declares the argument it reads`() {
        val graph = source("navigation/NavGraph.kt")

        assertTrue(
            "NavGraph must declare conversationId as an Int argument",
            graph.contains("navArgument(\"conversationId\") { type = NavType.IntType }"),
        )
        assertTrue(
            "NavGraph must read the argument it declared",
            graph.contains("getInt(\"conversationId\")"),
        )
    }

    @Test
    fun `both history destinations are registered`() {
        val graph = source("navigation/NavGraph.kt")

        assertTrue(
            graph.contains("safeAreaComposable(Screen.ConversationHistory.route)"),
        )
        assertTrue(graph.contains("Screen.ConversationDetail.route"))
        assertTrue(graph.contains("ConversationHistoryScreen("))
        assertTrue(graph.contains("ConversationDetailScreen("))
    }

    @Test
    fun `the graph navigates with the route builder, not a hand-written string`() {
        val graph = source("navigation/NavGraph.kt")

        assertTrue(
            "the id position must come from conversationDetailRoute",
            graph.contains("conversationDetailRoute("),
        )
        // The failure mode this forbids: an interpolated literal that the pattern later outgrows.
        assertFalse(
            "a hand-built conversation path would drift from the pattern",
            graph.contains("navigate(\"conversation-history/"),
        )
    }

    // ------------------------------------------------------------------ the drawer

    @Test
    fun `the drawer offers Conversation History and navigates to it`() {
        val main = source("navigation/MainScreen.kt")

        assertTrue(
            "the drawer must offer the history destination",
            main.contains("DrawerItem(\"Conversation History\""),
        )
        assertTrue(
            "the drawer entry must navigate to the history route",
            main.contains("navigate(Screen.ConversationHistory.route)"),
        )
    }

    @Test
    fun `the drawer still offers the two voice journeys the history is about`() {
        // Guards against the history entry being added by replacing one of them.
        val main = source("navigation/MainScreen.kt")

        assertTrue(main.contains("DrawerItem(\"Mock Interviews\""))
        assertTrue(main.contains("Voice Career Coach"))
    }

    // ------------------------------------------------------------------ helpers

    private fun fill(pattern: String, vararg args: Pair<String, String>): String {
        var filled = pattern
        args.forEach { (name, value) -> filled = filled.replace("{" + name + "}", value) }
        return filled
    }

    private fun source(relative: String): String {
        val base = "src/main/java/com/aiplacementos/app/"
        val candidates = listOf(
            File(base + relative),
            File("app/" + base + relative),
            File("../app/" + base + relative),
        )
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) {
            "Could not locate " + relative + " from " + File(".").absolutePath +
                "; this guard must not silently scan nothing."
        }
        return found.readText()
    }
}
