/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends.lan

import android.os.SystemClock
import java.net.InetAddress

enum class RejectionReason {
    NON_PRIVATE_IP,
    UNTRUSTED_NETWORK,
}

data class RejectedDevice(
    val ipAddress: InetAddress,
    val deviceName: String?,
    val reason: RejectionReason,
    val timestamp: Long = SystemClock.elapsedRealtime()
)
