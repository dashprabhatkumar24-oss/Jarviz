package com.example

import com.example.commands.ActionCategory
import com.example.commands.OfflineCommandParser
import com.example.database.CommandLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `wake word stripping removes Hey Jarvis prefix cleanly`() {
        val raw = "Hey Jarvis, set an alarm for 6:30 tomorrow morning"
        val stripped = OfflineCommandParser.stripWakeWord(raw, "Hey Jarvis")
        assertEquals("set an alarm for 6:30 tomorrow morning", stripped)
    }

    @Test
    fun `hinglish alarm command parses hour and minute accurately`() {
        val action = OfflineCommandParser.parseCommand("Jarvis, kal subah 7 baje alarm laga do")
        assertEquals(ActionCategory.CREATE_ALARM, action.category)
        assertEquals("7", action.parameters["hour"])
        assertEquals("0", action.parameters["minute"])
        assertEquals("hi-IN", action.detectedLanguage)
    }

    @Test
    fun `contextual follow up resolves previous contact for WhatsApp message`() {
        val history = listOf(
            CommandLogEntity(
                id = 1,
                userTranscript = "Hey Jarvis, call Mom",
                assistantResponse = "Calling Mom.",
                actionCategory = ActionCategory.CALL_CONTACT.name,
                parametersJson = "{\"contact_name\":\"Mom\"}"
            )
        )
        val followUp = OfflineCommandParser.parseCommand(
            rawInput = "Actually, send her a WhatsApp message saying I'll be home in 20 minutes",
            recentHistory = history
        )
        assertEquals(ActionCategory.SEND_MESSAGE, followUp.category)
        assertEquals("Mom", followUp.parameters["contact_name"])
        assertEquals("WhatsApp", followUp.parameters["app"])
        assertTrue(followUp.parameters["message"]!!.contains("home in 20 minutes"))
        assertTrue(followUp.requiresConfirmation)
    }

    @Test
    fun `language switch command updates to Odia`() {
        val action = OfflineCommandParser.parseCommand("Speak to me in Odia")
        assertEquals(ActionCategory.SWITCH_LANGUAGE, action.category)
        assertEquals("or-IN", action.parameters["language_code"])
        assertEquals("Odia", action.parameters["language"])
    }

    @Test
    fun `combined youtube search command parses query and engine`() {
        val action = OfflineCommandParser.parseCommand("Jarvis, open YouTube and search for latest technology news")
        assertEquals(ActionCategory.SEARCH_WEB, action.category)
        assertEquals("youtube", action.parameters["engine"])
        assertEquals("latest technology news", action.parameters["query"])
    }

    @Test
    fun `contextual weather follow up resolves day after tomorrow`() {
        val history = listOf(
            CommandLogEntity(
                id = 1,
                userTranscript = "Jarvis, what's the weather tomorrow?",
                assistantResponse = "Tomorrow will be partly cloudy.",
                actionCategory = ActionCategory.WEATHER.name
            )
        )
        val followUp = OfflineCommandParser.parseCommand(
            rawInput = "What about the day after?",
            recentHistory = history
        )
        assertEquals(ActionCategory.WEATHER, followUp.category)
        assertTrue(followUp.spokenResponse.contains("day after tomorrow", ignoreCase = true))
    }
}
