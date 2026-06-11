package com.deckpuller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaBoxCsvParserTest {

    private val header =
        "Binder Name,Binder Type,Name,Set code,Set name,Collector number,Foil,Rarity," +
            "Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition," +
            "Language,Purchase price currency,Added"

    private fun parse(vararg rows: String) =
        ManaBoxCsvParser.parse((listOf(header) + rows).joinToString("\n"))

    @Test
    fun `parses a simple row`() {
        val result = parse(
            "EDH,binder,Sol Ring,LTC,Commander Legends,472,normal,uncommon,2,1,sid-1,1.5,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals(1, result.cards.size)
        val c = result.cards.single()
        assertEquals("Sol Ring", c.name)
        assertEquals("sol ring", c.nameKey)
        assertEquals("LTC", c.setCode)
        assertEquals("472", c.collectorNumber)
        assertEquals("normal", c.finish)
        assertEquals(2, c.quantity)
        assertEquals("sid-1", c.scryfallId)
        assertEquals("EDH", c.binderName)
        assertTrue(result.failedLines.isEmpty())
    }

    @Test
    fun `handles quoted name containing a comma`() {
        val result = parse(
            "EDH,binder,\"Mazirek, Kraul Death Priest\",EOC,Edge of Eternities Commander,122,normal,rare,1,2,sid-2,0.4,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals("Mazirek, Kraul Death Priest", result.cards.single().name)
        assertEquals("mazirek, kraul death priest", result.cards.single().nameKey)
    }

    @Test
    fun `handles double-faced names and quoted dfc with comma`() {
        val result = parse(
            "EDH,binder,Pestilent Cauldron // Restorative Burst,STX,Strixhaven,154,normal,rare,1,3,sid-3,0.29,false,false,near_mint,en,USD,2026-01-01",
            "EDH,binder,\"Lluwen, Exchange Student // Pest Friend\",SOS,Secrets,199,foil,uncommon,1,4,sid-4,0.28,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals("pestilent cauldron // restorative burst", result.cards[0].nameKey)
        assertEquals("lluwen, exchange student // pest friend", result.cards[1].nameKey)
        assertEquals("foil", result.cards[1].finish)
    }

    @Test
    fun `maps columns by header name regardless of order`() {
        val reordered = "Name,Quantity,Set code\nSol Ring,3,LTC"
        val result = ManaBoxCsvParser.parse(reordered)
        assertEquals(1, result.cards.size)
        assertEquals(3, result.cards.single().quantity)
        assertEquals("LTC", result.cards.single().setCode)
        assertEquals("", result.cards.single().scryfallId.orEmpty())
    }

    @Test
    fun `records failed lines but imports the rest`() {
        val result = parse(
            "EDH,binder,Sol Ring,LTC,Commander,472,normal,uncommon,2,1,sid-1,1.5,false,false,near_mint,en,USD,2026-01-01",
            "EDH,binder,Broken Row,LTC,Commander,472,normal,uncommon,notanumber,1,sid-x,1.5,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals(1, result.cards.size)
        assertEquals(1, result.failedLines.size)
        assertEquals(3, result.failedLines.single())
    }

    @Test
    fun `throws when required column missing`() {
        val ex = runCatching { ManaBoxCsvParser.parse("Set code,Quantity\nLTC,1") }.exceptionOrNull()
        assertTrue(ex is ManaBoxCsvParser.MissingColumnException)
        val ex2 = runCatching { ManaBoxCsvParser.parse("Name,Set code\nSol Ring,LTC") }.exceptionOrNull()
        assertTrue(ex2 is ManaBoxCsvParser.MissingColumnException)
    }

    @Test
    fun `blank name is a failed line`() {
        val result = parse(
            "EDH,binder,,LTC,Commander,472,normal,uncommon,1,1,sid-1,1.5,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals(0, result.cards.size)
        assertEquals(1, result.failedLines.size)
    }

    @Test
    fun `unescapes doubled quotes inside a quoted field`() {
        val result = ManaBoxCsvParser.parse("Name,Quantity\n\"Ach! Hans, \"\"Run!\"\"\",1")
        assertEquals("Ach! Hans, \"Run!\"", result.cards.single().name)
    }
}
