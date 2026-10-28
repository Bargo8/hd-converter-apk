package com.tri0yx.hdrplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ID = "hdr_info"
        private const val NOTIF_ID = 1
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var infoText: TextView

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            playVideo(uri)
        }

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        infoText = findViewById(R.id.infoText)
        val btnPick: Button = findViewById(R.id.btnPick)

        btnPick.setOnClickListener { pickVideo.launch(arrayOf("video/*")) }

        infoText.text = deviceHdrInfo()

        createChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun playVideo(uri: Uri) {
        player?.release()
        val p = ExoPlayer.Builder(this).build()
        playerView.player = p
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
        player = p

        val fileInfo = fileHdrInfo(uri)
        infoText.text = "${deviceHdrInfo()}\n\n$fileInfo"
        showNotification("HDR Player", fileInfo)
    }

    @Suppress("DEPRECATION")
    private fun deviceHdrInfo(): String {
        val display: Display = windowManager.defaultDisplay
        val types = display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
        val names = types.map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                else -> "Unknown"
            }
        }
        val result = if (names.isEmpty()) "SDR aja" else names.joinToString(", ")
        return "Layar HP: $result"
    }

    private fun fileHdrInfo(uri: Uri): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(this, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue

                val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                } else {
                    -1
                }
                val hdr = when {
                    mime == "video/dolby-vision" -> "Dolby Vision"
                    transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                    transfer == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                    else -> "SDR"
                }
                val w = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                val h = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                return "File: $hdr · ${w}x$h · $mime"
            }
            "Track video nggak ketemu"
        } catch (e: Exception) {
            "Gagal baca info file: ${e.message}"
        } finally {
            extractor.release()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Info HDR", NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🌶 $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
