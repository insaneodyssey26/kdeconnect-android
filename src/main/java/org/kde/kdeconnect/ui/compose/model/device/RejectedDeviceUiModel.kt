/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.model.device

import org.kde.kdeconnect.backends.lan.RejectionReason

data class RejectedDeviceUiModel(
    val id: String,
    val ipAddress: String,
    val deviceName: String?,
    val reason: RejectionReason
)
