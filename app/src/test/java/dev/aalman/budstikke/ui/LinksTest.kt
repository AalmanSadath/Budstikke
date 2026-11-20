package dev.aalman.budstikke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    private fun linked(text: String, avoid: IntRange? = null) = findLinks(text, avoid).map { text.substring(it.range) }

    @Test
    fun findsWebAddressesWithAndWithoutAScheme() {
        val text = "See https://example.com/a?b=1 or www.vg.no, and posten.no/track/123 today"
        assertEquals(listOf("https://example.com/a?b=1", "www.vg.no", "posten.no/track/123"), linked(text))
        assertEquals(
            listOf("https://example.com/a?b=1", "https://www.vg.no", "https://posten.no/track/123"),
            findLinks(text).map { it.target },
        )
    }

    @Test
    fun leavesFileNamesAndWordsWithDotsAlone() {
        assertEquals(emptyList<String>(), linked("Open report.pdf and notes.txt, e.g. this. Ok.Bye"))
    }

    @Test
    fun aDomainMustEndAtItsTopLevelDomain() {
        assertEquals(emptyList<String>(), linked("posten.nothing here"))
    }

    @Test
    fun trailingPunctuationAndBracketsStayOutside() {
        assertEquals(listOf("example.com/a"), linked("(see example.com/a)."))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Foo_(bar)"), linked("https://en.wikipedia.org/wiki/Foo_(bar)!"))
    }

    @Test
    fun findsEmailAddressesNotAsWebAddresses() {
        val links = findLinks("Write to kari@example.no please")
        assertEquals(1, links.size)
        assertEquals(LinkKind.Email, links[0].kind)
        assertEquals("mailto:kari@example.no", links[0].target)
    }

    @Test
    fun findsPhoneNumbers() {
        val links = findLinks("Ring +47 412 34 567 eller (212) 555-0101 nå")
        assertEquals(listOf("+47 412 34 567", "(212) 555-0101"), links.map { "Ring +47 412 34 567 eller (212) 555-0101 nå".substring(it.range) })
        assertEquals(listOf("tel:+4741234567", "tel:2125550101"), links.map { it.target })
        assertTrue(links.all { it.kind == LinkKind.Phone })
    }

    @Test
    fun leavesDatesAmountsAndCodesAlone() {
        assertEquals(emptyList<String>(), linked("Due 2026-11-20, total 1 000 000 kr, pi 3.14159265, code 482913"))
    }

    @Test
    fun keepsTheOneTimeCodeClear() {
        val text = "Your code is 12345678"
        val code = text.indexOf("12345678").let { it until it + 8 }
        assertEquals(emptyList<String>(), linked(text, avoid = code))
    }

    @Test
    fun aNumberInsideAnAddressIsPartOfTheAddress() {
        val links = findLinks("Track at https://posten.no/sporing/70712345678")
        assertEquals(1, links.size)
        assertEquals(LinkKind.Web, links[0].kind)
    }

    @Test
    fun theHostIsWhatFollowsAnyUserName() {
        assertEquals("evil.example", hostOf("https://bank.no@evil.example/login"))
        assertEquals("posten.no", hostOf("https://posten.no:443/track?x=1"))
        val tricky = "https://bank.no@evil.example/login?to=a@b.no"
        assertEquals("evil.example", hostRange(tricky)?.let { tricky.substring(it) })
    }

    @Test
    fun phoneShapeRules() {
        assertTrue(looksLikePhone("41234567"))
        assertFalse(looksLikePhone("123456"))
        assertFalse(looksLikePhone("20.11.2026"))
        assertFalse(looksLikePhone("412--34567"))
    }
}
