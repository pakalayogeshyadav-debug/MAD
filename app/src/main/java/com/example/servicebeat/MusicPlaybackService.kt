package com.example.servicebeat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection

import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicPlaybackService : Service() {

    private val binder = LocalBinder()
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val CHANNEL_ID = "servicebeat_channel"
    private val EVENT_CHANNEL_ID = "service_events_channel"
    private val NOTIFICATION_ID = 1
    private val EVENT_NOTIFICATION_ID = 2
    
    companion object {
        var isRunning = false
    }
    
    // Allows the activity to communicate with the service directly
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ServiceLog.add("Service created")
        
        initializePlayer()
        createNotificationChannel()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
            
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()
            
        // engine_sound.wav is in res/raw/
        val mediaItem = MediaItem.fromUri("android.resource://$packageName/${R.raw.engine_sound}")
        player?.setMediaItem(mediaItem)
        player?.repeatMode = Player.REPEAT_MODE_ALL
        player?.volume = 1.0f
        player?.prepare()

        mediaSession = MediaSession.Builder(this, player!!).build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
                ServiceLog.add("Playback state changed: playing=$isPlaying")
                Log.d("ServiceBeat", "isPlaying = $isPlaying")
                Log.d("ServiceBeat", "Player state = ${player?.playbackState}")
                Log.d("ServiceBeat", "volume = ${player?.volume}")
                Log.d("ServiceBeat", "duration = ${player?.duration}")
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ServiceBeat", "Playback error", error)
                ServiceLog.add("Playback Error: ${error.message}")
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLog.add("onStartCommand()")
        
        when (intent?.action) {
            "ACTION_STOP" -> {
                Log.d("ServiceBeat", "Stop requested via intent")
                ServiceLog.add("Stop requested via intent")
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                showServiceStoppedNotification()
                stopSelf()
            }
            "ACTION_PLAY_PAUSE" -> {
                Log.d("ServiceBeat", "Play/Pause requested via intent")
                if (player?.isPlaying == true) {
                    player?.pause()
                } else {
                    player?.play()
                }
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                ServiceLog.add("Foreground service started")
                showServiceStartedNotification()
                
                // Only auto-play if we just started fresh
                if (player?.isPlaying == false) {
                    Log.d("ServiceBeat", "Starting playback from startCommand")
                    player?.play()
                }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        ServiceLog.add("onBind()")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceLog.add("onUnbind()")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceLog.add("Service destroyed")
        isRunning = false
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    // --- Public API for Bound Clients (MainActivity) ---

    fun play() {
        Log.d("ServiceBeat", "Play requested via binder")
        player?.play()
    }

    fun pause() {
        Log.d("ServiceBeat", "Pause requested via binder")
        player?.pause()
    }

    fun stopPlayback() {
        player?.stop()
        player?.seekTo(0)
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return player?.duration ?: 0L
    }
    
    // --- Download Features ---
    private var downloadProgress = 0
    private var isDownloading = false

    fun startDownload() {
        if (isDownloading) return
        isDownloading = true
        downloadProgress = 0
        ServiceLog.add("Download started via Bound Service")
        
        // Fetch dynamic data from the Media Player ON THE MAIN THREAD before moving to background
        val isPlayingStr = if (player?.isPlaying == true) "PLAYING" else "PAUSED/STOPPED"
        val curPos = (player?.currentPosition ?: 0L) / 1000
        val totDur = (player?.duration ?: 0L) / 1000
        val progressStr = String.format(Locale.US, "%02d:%02d / %02d:%02d", curPos / 60, curPos % 60, totDur / 60, totDur % 60)
        
        Thread {
            for (i in 1..100) {
                Thread.sleep(50) // Simulate a 5-second download
                downloadProgress = i
            }
            
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                val htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>ServiceBeat Audit Report</title>
                        <style>
                            body { font-family: monospace; background-color: #0A0A0A; color: #F2F2F2; padding: 20px; }
                            .container { border: 2px solid #FF3B30; padding: 20px; max-width: 600px; margin: auto; background-color: #17191C; }
                            h2 { text-align: center; color: #FF3B30; border-bottom: 1px dashed #444; padding-bottom: 10px; }
                            .row { display: flex; justify-content: space-between; margin-bottom: 10px; border-bottom: 1px solid #333; padding-bottom: 5px; }
                            .label { font-weight: bold; color: #0A84FF; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h2>SERVICEBEAT - VEHICLE TELEMETRY REPORT</h2>
                            <div class="row"><span class="label">Export Timestamp :</span><span>$timeStamp</span></div>
                            <div class="row"><span class="label">Telemetry Source :</span><span>V8 Engine Simulator</span></div>
                            <div class="row"><span class="label">Engine State     :</span><span>$isPlayingStr</span></div>
                            <div class="row"><span class="label">Runtime Prog.    :</span><span>$progressStr</span></div>
                            <div class="row"><span class="label">Simulated RPM    :</span><span style="color: #FF3B30;">3,240 RPM</span></div>
                            <div class="row"><span class="label">Oil Temp         :</span><span style="color: #FF3B30;">210°F</span></div>
                            <div class="row"><span class="label">IPC Mechanism    :</span><span>Android IBinder / ServiceConnection</span></div>
                            <div class="row"><span class="label">Storage Location :</span><span>Device Public Downloads Folder</span></div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                // Save directly to the public Downloads folder using MediaStore (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "ServiceBeat_Report_${System.currentTimeMillis()}.html")
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1) // Tells system file is being written
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(htmlContent.toByteArray())
                        }
                        // Release pending status so it instantly appears in "Recents" and Files app
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        
                        ServiceLog.add("HTML saved! Check 'Recents' or 'Downloads' in Files app.")
                    } else {
                        ServiceLog.add("Failed to create MediaStore entry")
                    }
                } else {
                    // Fallback for older Android versions (may require storage permission)
                    val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val publicFile = File(publicDownloadDir, "ServiceBeat_Report_${System.currentTimeMillis()}.html")
                    try {
                        FileOutputStream(publicFile).use { out ->
                            out.write(htmlContent.toByteArray())
                        }
                        // Force MediaScanner to index it so it appears in "Recents" immediately
                        MediaScannerConnection.scanFile(
                            this@MusicPlaybackService,
                            arrayOf(publicFile.absolutePath),
                            arrayOf("text/html"),
                            null
                        )
                        ServiceLog.add("HTML saved! Check 'Recents' in Files app.")
                    } catch (e: Exception) {
                        // Safe fallback to app-specific directory if permission is denied
                        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                        val fallbackFile = File(downloadDir, "ServiceBeat_Report.html")
                        FileOutputStream(fallbackFile).use { out ->
                            out.write(htmlContent.toByteArray())
                        }
                        ServiceLog.add("Permission denied. Saved to App-Specific Downloads instead.")
                    }
                }
                
                Log.d("ServiceBeat", "HTML Download generation finished.")
            } catch (e: Exception) {
                ServiceLog.add("Download save failed: ${e.message}")
            }
            isDownloading = false
        }.start()
    }

    fun getDownloadProgress(): Int = downloadProgress

    fun isDownloading(): Boolean = isDownloading

    // --- Notification Handling ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ServiceBeat Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media playback controls"
            }
            
            val eventChannel = NotificationChannel(
                EVENT_CHANNEL_ID,
                "Service Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Service started and stopped events"
            }
            
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(eventChannel)
        }
    }

    private fun buildNotification(): Notification {
        val isPlaying = player?.isPlaying ?: false
        val status = if (isPlaying) "Engine Running • Telemetry Demo" else "Engine Off • Telemetry Demo"
        
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = "ACTION_PLAY_PAUSE"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ServiceBeat")
            .setContentText(status)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun showServiceStartedNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Telemetry Service Started")
            .setContentText("ServiceBeat automotive service is now running.")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(EVENT_NOTIFICATION_ID, notification)
    }

    private fun showServiceStoppedNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Telemetry Service Stopped")
            .setContentText("ServiceBeat automotive service has stopped.")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(EVENT_NOTIFICATION_ID + 1, notification)
    }
}
