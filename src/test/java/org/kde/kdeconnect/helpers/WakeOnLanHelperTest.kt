/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.helpers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanHelperTest {

    @Test
    fun testParseMacAddressColonSeparated() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val bytes = WakeOnLanHelper.parseMacAddress(mac)
        assertNotNull(bytes)
        assertEquals(6, bytes!!.size)
        assertArrayEquals(
            byteArrayOf(
                0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
                0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()
            ),
            bytes
        )
    }

    @Test
    fun testParseMacAddressHyphenSeparated() {
        val mac = "00-11-22-33-44-55"
        val bytes = WakeOnLanHelper.parseMacAddress(mac)
        assertNotNull(bytes)
        assertArrayEquals(
            byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
            bytes
        )
    }

    @Test
    fun testParseMacAddressWithoutSeparators() {
        val mac = "0123456789ab"
        val bytes = WakeOnLanHelper.parseMacAddress(mac)
        assertNotNull(bytes)
        assertArrayEquals(
            byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte()),
            bytes
        )
    }

    @Test
    fun testParseMacAddressInvalidLength() {
        assertNull(WakeOnLanHelper.parseMacAddress("AA:BB:CC:DD"))
        assertNull(WakeOnLanHelper.parseMacAddress(""))
        assertNull(WakeOnLanHelper.parseMacAddress("AA:BB:CC:DD:EE:FF:11"))
    }

    @Test
    fun testParseMacAddressInvalidHex() {
        assertNull(WakeOnLanHelper.parseMacAddress("GG:HH:II:JJ:KK:LL"))
    }

    @Test
    fun testCreateMagicPacketStructure() {
        val macBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte())
        val packet = WakeOnLanHelper.createMagicPacket(macBytes)

        assertEquals(102, packet.size)

        // First 6 bytes must be 0xFF
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i])
        }

        // Following 16 iterations must be the MAC address
        for (i in 0 until 16) {
            val offset = 6 + i * 6
            for (j in 0 until 6) {
                assertEquals(macBytes[j], packet[offset + j])
            }
        }
    }
}
