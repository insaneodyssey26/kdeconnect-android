/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.os.Handler
import android.os.Looper
import androidx.annotation.GuardedBy
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect_tp.R
import java.util.ArrayList

/**
 * A type of [BackgroundJob] that sends Files to another device.
 *
 * We represent the individual upload requests as [NetworkPacket]s.
 *
 * Each packet should have a 'filename' property and a payload. If the payload is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * [addNetworkPacket].
 *
 * The I/O-part of this file sending is handled by
 * [Device.sendPacketBlocking].
 *
 * @see CompositeReceiveFileJob
 * @see Device.SendPacketStatusCallback
 */
class CompositeUploadFileJob(device: Device, callback: Callback<Void?>) : BackgroundJob<Device, Void?>(device, callback) {

    private var isRunning: Boolean = false
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentFileName: String = ""
    private var currentFileNum: Int = 0
    private var updatePacketPending: Boolean = false
    private var totalSend: Long = 0
    private var prevProgressPercentage: Int = 0
    private val uploadNotification: UploadNotification = UploadNotification(device, id)

    private val lock: java.lang.Object = java.lang.Object() // Use to protect concurrent access to the variables below

    @GuardedBy("lock")
    private val networkPacketList: MutableList<NetworkPacket> = ArrayList()

    private var currentNetworkPacket: NetworkPacket? = null
    private val sendPacketStatusCallback: SendPacketStatusCallback = SendPacketStatusCallback()

    @GuardedBy("lock")
    private var totalNumFiles: Int = 0

    @GuardedBy("lock")
    private var totalPayloadSize: Long = 0

    private val device: Device
        get() = requestInfo

    override fun run() {
        var done: Boolean

        isRunning = true

        synchronized(lock) {
            done = networkPacketList.isEmpty()
        }

        try {
            while (!done && !isCancelled) {
                synchronized(lock) {
                    currentNetworkPacket = networkPacketList.removeAt(0)
                }

                val packet = currentNetworkPacket!!
                currentFileName = packet.getString("filename")
                currentFileNum++

                setProgress(prevProgressPercentage)

                addTotalsToNetworkPacket(packet)

                // We set sendPayloadFromSameThread to true so this call blocks until the payload
                // has been received by the other end, so payloads are sent one by one.
                if (!device.sendPacketBlocking(packet, sendPacketStatusCallback, true)) {
                    throw RuntimeException("Sending packet failed")
                }

                synchronized(lock) {
                    done = networkPacketList.isEmpty()
                }
            }

            if (isCancelled) {
                uploadNotification.cancel()
            } else {
                uploadNotification.setFinished(
                    device.context.resources.getQuantityString(
                        R.plurals.sent_files_title,
                        currentFileNum,
                        device.name,
                        currentFileNum,
                    ),
                )
                uploadNotification.show()

                reportResult(null)
            }
        } catch (e: RuntimeException) {
            val failedFiles: Int
            synchronized(lock) {
                failedFiles = totalNumFiles - currentFileNum + 1
                uploadNotification.setFailed(
                    device.context.resources.getQuantityString(
                        R.plurals.send_files_fail_title,
                        failedFiles,
                        device.name,
                        failedFiles,
                        totalNumFiles,
                    ),
                )
            }

            uploadNotification.show()
            reportError(e)
        } finally {
            isRunning = false

            for (networkPacket in networkPacketList) {
                networkPacket.payload?.close()
            }
            networkPacketList.clear()
        }
    }

    private fun addTotalsToNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            networkPacket.set(SharePlugin.KEY_NUMBER_OF_FILES, totalNumFiles)
            networkPacket.set(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE, totalPayloadSize)
        }
    }

    private fun setProgress(progress: Int) {
        synchronized(lock) {
            uploadNotification.setProgress(
                progress,
                device.context.resources.getQuantityString(
                    R.plurals.outgoing_files_text,
                    totalNumFiles,
                    currentFileName,
                    currentFileNum,
                    totalNumFiles,
                ),
            )
        }
        uploadNotification.show()
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            networkPacketList.add(networkPacket)

            totalNumFiles++

            if (networkPacket.payloadSize >= 0) {
                totalPayloadSize += networkPacket.payloadSize
            }

            uploadNotification.setTitle(
                device.context.resources.getQuantityString(
                    R.plurals.outgoing_file_title,
                    totalNumFiles,
                    totalNumFiles,
                    device.name,
                ),
            )

            // If we are already sending a file, update the notification right away to show the new number of files
            if (isRunning && currentFileNum > 0) {
                setProgress(prevProgressPercentage.toInt())
            }

            // Give SharePlugin some time to add more NetworkPackets
            if (isRunning && !updatePacketPending) {
                updatePacketPending = true
                handler.post { sendUpdatePacket() }
            }
        }
    }

    /**
     * Use this to send metadata ahead of all the other [networkPacketList] packets.
     */
    private fun sendUpdatePacket() {
        val np = NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST_UPDATE)

        synchronized(lock) {
            np.set("numberOfFiles", totalNumFiles)
            np.set("totalPayloadSize", totalPayloadSize)
            updatePacketPending = false
        }

        device.sendPacket(np)
    }

    override fun cancel() {
        super.cancel()

        currentNetworkPacket?.cancel()
    }

    private inner class SendPacketStatusCallback : Device.SendPacketStatusCallback() {
        override fun onPayloadProgressChanged(percent: Int) {
            val payloadSize = currentNetworkPacket?.payloadSize ?: 0
            val send = totalSend + (payloadSize * (percent.toFloat() / 100))
            val progress = ((send * 100) / totalPayloadSize).toInt()

            if (progress != prevProgressPercentage) {
                setProgress(progress)
                prevProgressPercentage = progress
            }
        }

        override fun onSuccess() {
            if (currentNetworkPacket?.payloadSize == 0L) {
                synchronized(lock) {
                    if (networkPacketList.isEmpty()) {
                        setProgress(100)
                    }
                }
            }

            totalSend += currentNetworkPacket?.payloadSize ?: 0
        }

        override fun onFailure(e: Throwable) {
            // Handled in the run() function when sendPacketBlocking returns false
        }
    }
}
