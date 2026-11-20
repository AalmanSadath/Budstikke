package dev.aalman.budstikke.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleTest {

    private fun contact(name: String) = Contact(name = name, photoUri = null)

    @Test
    fun onePersonIsCalledByTheirFullNameOrNumber() {
        assertEquals("Ingrid Hansen", membersTitle(listOf(Member("+4711111111", contact("Ingrid Hansen")))))
        assertEquals("+4711111111", membersTitle(listOf(Member("+4711111111"))))
    }

    @Test
    fun aGroupIsCalledByFirstNamesAndNumbers() {
        val members = listOf(
            Member("+4711111111", contact("Ingrid Hansen")),
            Member("+4722222222"),
            Member("+4733333333", contact("  Sam  ")),
        )
        assertEquals("Ingrid, +4722222222, Sam", membersTitle(members))
    }

    @Test
    fun oneAddressListIsTheAddressItself() {
        assertEquals("+4711111111", AddressList.join(listOf("+4711111111")))
        assertEquals(listOf("+4711111111"), AddressList.split("+4711111111"))
    }

    @Test
    fun aGroupsAddressesComeBackInOrder() {
        val addresses = listOf("+4711111111", "+4722222222", "+4733333333")
        assertEquals(addresses, AddressList.split(AddressList.join(addresses)))
        assertEquals(emptyList<String>(), AddressList.split(""))
    }
}
