/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.wakeonlan

import androidx.core.content.edit
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

private const val PACKET_TYPE_WAKEONLAN = "kdeconnect.wakeonlan"
private const val KEY_MAC_ADDRESS = "macAddress"

@LoadablePlugin
class WakeOnLanPlugin : Plugin() {

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_wakeonlan)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_wakeonlan_desc)

    override val supportedPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_WAKEONLAN)

    override val outgoingPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_WAKEONLAN)

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type == PACKET_TYPE_WAKEONLAN) {
            val mac = np.getStringOrNull(KEY_MAC_ADDRESS)
            if (!mac.isNullOrEmpty()) {
                preferences.edit {
                    putString(KEY_MAC_ADDRESS, mac)
                }
            }
            return true
        }
        return false
    }

    val savedMacAddress: String?
        get() = preferences.getString(KEY_MAC_ADDRESS, null)
}
