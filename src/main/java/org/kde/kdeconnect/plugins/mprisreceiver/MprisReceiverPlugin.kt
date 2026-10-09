/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mprisreceiver

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.AppsHelper
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class MprisReceiverPlugin : Plugin() {

    // TODO: Those two are always accessed together, merge them
    private lateinit var players: HashMap<String, MprisReceiverPlayer>
    private lateinit var playerCbs: HashMap<String, MprisReceiverCallback>

    private var mediaSessionChangeListener: MediaSessionChangeListener? = null

    override fun onCreate() {
        players = HashMap()
        playerCbs = HashMap()

        val manager = ContextCompat.getSystemService(context, MediaSessionManager::class.java)
        assert(manager != null)

        assert(mediaSessionChangeListener == null)
        val listener = MediaSessionChangeListener()
        mediaSessionChangeListener = listener
        manager?.addOnActiveSessionsChangedListener(
            listener,
            ComponentName(context, NotificationReceiver::class.java),
            Handler(Looper.getMainLooper())
        )

        val activeSessions = manager?.getActiveSessions(ComponentName(context, NotificationReceiver::class.java)) ?: emptyList()
        createPlayers(activeSessions)
        sendPlayerList()
    }

    override fun onDestroy() {
        super.onDestroy()
        val manager = ContextCompat.getSystemService(context, MediaSessionManager::class.java)
        val listener = mediaSessionChangeListener
        if (manager != null && listener != null) {
            manager.removeOnActiveSessionsChangedListener(listener)
            mediaSessionChangeListener = null
        }
    }

    private fun createPlayers(sessions: List<MediaController>) {
        for (controller in sessions) {
            createPlayer(controller)
        }
    }

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_mprisreceiver)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_mprisreceiver_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("requestPlayerList")) {
            sendPlayerList()
            return true
        }

        if (!np.has("player")) {
            return false
        }
        val player = players[np.getString("player")] ?: return false

        val artUrl = np.getString("albumArtUrl", "")
        if (artUrl.isNotEmpty()) {
            val playerName = player.name
            val cb = playerCbs[playerName]
            if (cb == null) {
                Log.e(TAG, "no callback for $playerName (player likely stopped)")
                return false
            }
            // run it on a different thread to avoid blocking
            ThreadHelper.execute { sendAlbumArt(playerName, cb, artUrl) }
            return true
        }

        if (np.getBoolean("requestNowPlaying", false)) {
            sendMetadata(player)
            return true
        }

        if (np.has("SetPosition")) {
            val position = np.getLong("SetPosition", 0)
            player.position = position
        }

        if (np.has("setVolume")) {
            val volume = np.getInt("setVolume", 100)
            player.volume = volume
            // Setting volume doesn't seem to always trigger the callback
            sendMetadata(player)
        }

        if (np.has("action")) {
            when (np.getString("action")) {
                "Play" -> player.play()
                "Pause" -> player.pause()
                "PlayPause" -> player.playPause()
                "Next" -> player.next()
                "Previous" -> player.previous()
                "Stop" -> player.stop()
            }
        }

        return true
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MPRIS_REQUEST)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MPRIS)

    private inner class MediaSessionChangeListener : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
            if (controllers == null) {
                return
            }

            // Make a copy to avoid ConcurrentModificationException
            val playersCopy = ArrayList(players.values)
            for (p in playersCopy) {
                val cb = playerCbs[p.name]
                if (cb != null) {
                    p.controller.unregisterCallback(cb)
                }
            }
            playerCbs.clear()
            players.clear()

            createPlayers(controllers)
            sendPlayerList()
        }
    }

    private fun createPlayer(controller: MediaController) {
        // Skip the media session we created ourselves as KDE Connect
        if (controller.packageName == context.packageName) return

        val playerName = AppsHelper.appNameLookup(context, controller.packageName)
        val player = MprisReceiverPlayer(controller, playerName)
        val cb = MprisReceiverCallback(this, player)
        controller.registerCallback(cb, Handler(Looper.getMainLooper()))
        playerCbs[player.name] = cb
        players[player.name] = player
    }

    private fun sendPlayerList() {
        val np = NetworkPacket(PACKET_TYPE_MPRIS).apply {
            set("playerList", players.keys)
            set("supportAlbumArtPayload", true)
        }
        device.sendPacket(np)
    }

    internal fun sendAlbumArt(playerName: String, cb: MprisReceiverCallback, requestedUrl: String?) {
        // NOTE: It is possible that the player gets killed in the middle of this method.
        // The proper thing to do this case would be to abort the send - but that gets into the
        //   territory of async cancellation or putting a lock.
        // For now, we just continue to send the art- cb stores the bitmap, so it will be valid.
        //   cb will get GC'd after this method completes.
        val localArtUrl = cb.artUrl
        if (localArtUrl == null) {
            Log.w(TAG, "art not found!")
            return
        }
        val artUrl = requestedUrl ?: localArtUrl
        if (requestedUrl != null && requestedUrl != localArtUrl) {
            Log.w(TAG, "sendAlbumArt: Doesn't match current url")
            Log.d(TAG, "current:   $localArtUrl")
            Log.d(TAG, "requested: $requestedUrl")
            return
        }
        val p = cb.artAsArray
        if (p == null) {
            Log.w(TAG, "sendAlbumArt: Failed to get art stream")
            return
        }
        val np = NetworkPacket(PACKET_TYPE_MPRIS).apply {
            payload = NetworkPacket.Payload(p)
            set("player", playerName)
            set("transferringAlbumArt", true)
            set("albumArtUrl", artUrl)
        }
        device.sendPacket(np)
    }

    internal fun sendMetadata(player: MprisReceiverPlayer) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS).apply {
            set("player", player.name)
            set("title", player.title)
            set("artist", player.artist)
            val nowPlaying = listOf(player.artist, player.title)
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
            set("nowPlaying", nowPlaying) // GSConnect 50 (so, Ubuntu 22.04) needs this
            set("album", player.album)
            set("isPlaying", player.isPlaying())
            set("pos", player.position)
            set("length", player.length)
            set("canPlay", player.canPlay())
            set("canPause", player.canPause())
            set("canGoPrevious", player.canGoPrevious())
            set("canGoNext", player.canGoNext())
            set("canSeek", player.canSeek())
            set("volume", player.volume)
            val artUrl = playerCbs[player.name]?.artUrl ?: ""
            set("albumArtUrl", artUrl)
        }
        device.sendPacket(np)
    }

    override fun checkRequiredPermissions(): Boolean {
        return NotificationReceiver.hasReadNotificationsPermission(context)
    }

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_mpris)
            .setMessage(R.string.no_permission_mprisreceiver)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()

    companion object {
        private const val PACKET_TYPE_MPRIS = "kdeconnect.mpris"
        private const val PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"
        private const val TAG = "MprisReceiver"
    }
}
