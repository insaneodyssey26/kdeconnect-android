/*
 * SPDX-FileCopyrightText: 2026 Masum Ali <skmasumali.main@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceHelperTest {

    @Test
    fun testParseCsvLineStandard() {
        val line = "\"Google\",\"Pixel 7 Pro\",\"cheetah\",\"Pixel 7 Pro\""
        val expected = listOf("Google", "Pixel 7 Pro", "cheetah", "Pixel 7 Pro")
        assertEquals(expected, DeviceHelper.parseCsvLine(line))
    }

    @Test
    fun testParseCsvLineWithCommasInsideQuotes() {
        val line = "\"Samsung\",\"Galaxy Tab S7, 11-inch\",\"gts7xl\",\"SM-T870\""
        val expected = listOf("Samsung", "Galaxy Tab S7, 11-inch", "gts7xl", "SM-T870")
        assertEquals(expected, DeviceHelper.parseCsvLine(line))
    }

    @Test
    fun testParseCsvLineWithEscapedQuotes() {
        val line = "\"Brand\",\"Model with \"\"Special\"\" Name\",\"dev\",\"MODEL1\""
        val expected = listOf("Brand", "Model with \"Special\" Name", "dev", "MODEL1")
        assertEquals(expected, DeviceHelper.parseCsvLine(line))
    }

    @Test
    fun testParseCsvLineWithEmptyFields() {
        val line = "\"\",\"\",\"AD681H\",\"Smartfren Andromax AD681H\""
        val expected = listOf("", "", "AD681H", "Smartfren Andromax AD681H")
        assertEquals(expected, DeviceHelper.parseCsvLine(line))
    }

    @Test
    fun testParseCsvLineUnquoted() {
        val line = "Retail Branding,Marketing Name,Device,Model"
        val expected = listOf("Retail Branding", "Marketing Name", "Device", "Model")
        assertEquals(expected, DeviceHelper.parseCsvLine(line))
    }
}
