/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLanHelper {

    private const val TAG = "WakeOnLanHelper"

    /**
     * Parses a MAC address string (formats: "AA:BB:CC:DD:EE:FF", "AA-BB-CC-DD-EE-FF", or "AABBCCDDEEFF")
     * into a 6-byte array, or returns null if invalid.
     */
    fun parseMacAddress(macAddress: String): ByteArray? {
        val cleanMac = macAddress.replace(":", "").replace("-", "").trim()
        if (cleanMac.length != 12) {
            return null
        }
        val bytes = ByteArray(6)
        try {
            for (i in 0 until 6) {
                bytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            return null
        }
        return bytes
    }

    /**
     * Constructs a Wake-on-LAN magic packet: 6 bytes of 0xFF followed by 16 repetitions of the target MAC address (102 bytes total).
     */
    fun createMagicPacket(macBytes: ByteArray): ByteArray {
        require(macBytes.size == 6) { "MAC address must be 6 bytes" }
        val packet = ByteArray(102)
        // 6 bytes of 0xFF
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        // 16 iterations of target MAC address
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
        }
        return packet
    }

    /**
     * Sends Wake-on-LAN magic packets over UDP broadcast (255.255.255.255) to target ports (default 9 and 7).
     * Must be called off the main thread.
     */
    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String = "255.255.255.255",
        ports: IntArray = intArrayOf(9, 7)
    ): Boolean = withContext(Dispatchers.IO) {
        val macBytes = parseMacAddress(macAddress)
        if (macBytes == null) {
            Log.e(TAG, "Invalid MAC address format: $macAddress")
            return@withContext false
        }

        val magicPacketData = createMagicPacket(macBytes)
        try {
            val destination = InetAddress.getByName(broadcastAddress)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (port in ports) {
                    val packet = DatagramPacket(magicPacketData, magicPacketData.size, destination, port)
                    socket.send(packet)
                    Log.d(TAG, "Sent WOL magic packet to $broadcastAddress:$port for $macAddress")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Wake-on-LAN packet", e)
            false
        }
    }
}
