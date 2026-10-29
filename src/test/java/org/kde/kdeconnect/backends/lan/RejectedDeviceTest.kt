/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class RejectedDeviceTest {

    @Test
    fun testRejectedDeviceCreation() {
        val ip = InetAddress.getByName("8.8.8.8")
        val rejected = RejectedDevice(
            ipAddress = ip,
            deviceName = "Remote Laptop",
            reason = RejectionReason.NON_PRIVATE_IP
        )

        assertEquals(ip, rejected.ipAddress)
        assertEquals("Remote Laptop", rejected.deviceName)
        assertEquals(RejectionReason.NON_PRIVATE_IP, rejected.reason)
        assertNotNull(rejected.timestamp)
    }

    @Test
    fun testRejectedDeviceUntrustedNetwork() {
        val ip = InetAddress.getByName("192.168.1.50")
        val rejected = RejectedDevice(
            ipAddress = ip,
            deviceName = null,
            reason = RejectionReason.UNTRUSTED_NETWORK
        )

        assertEquals(ip, rejected.ipAddress)
        assertNull(rejected.deviceName)
        assertEquals(RejectionReason.UNTRUSTED_NETWORK, rejected.reason)
    }
}
