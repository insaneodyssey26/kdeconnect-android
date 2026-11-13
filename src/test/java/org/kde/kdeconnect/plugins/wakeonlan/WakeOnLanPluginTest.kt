/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.wakeonlan

import android.content.Context
import androidx.preference.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.MockSharedPreference
import org.kde.kdeconnect.NetworkPacket

class WakeOnLanPluginTest {

    private val context: Context = mockk()
    private val device: Device = mockk()
    private val mockPreferences = MockSharedPreference()

    @Before
    fun setUp() {
        mockkStatic(PreferenceManager::class)
        every { context.getSharedPreferences(any(), any()) } returns mockPreferences
        every { context.packageName } returns "org.kde.kdeconnect_tp"
        every { device.deviceId } returns "test_device"
        every { device.context } returns context
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testReceiveWakeOnLanPacket() {
        val plugin = WakeOnLanPlugin().also {
            it.setContext(context, device)
            it.onCreate()
        }

        assertNull(plugin.savedMacAddress)

        val packet = NetworkPacket("kdeconnect.wakeonlan").apply {
            this["macAddress"] = "00:11:22:33:44:55"
        }

        val handled = plugin.onPacketReceived(packet)
        assertTrue(handled)
        assertEquals("00:11:22:33:44:55", plugin.savedMacAddress)
    }
}
