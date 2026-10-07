package com.example.servicebeat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.servicebeat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private var musicService: MusicPlaybackService? = null
    private var isBound = false

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            updateHandler.postDelayed(this, 500)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.LocalBinder
            musicService = binder.getService()
            isBound = true
            ServiceLog.add("Activity connected to Bound Service")
            updateUI()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            isBound = false
            ServiceLog.add("Activity disconnected from Bound Service")
            updateUI()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            ServiceLog.add("Notification permission granted")
        } else {
            ServiceLog.add("Notification permission denied")
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ServiceLog.add("MainActivity onCreate()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnStartService.setOnClickListener {
            val intent = Intent(this, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            ServiceLog.add("Requested startForegroundService")
        }

        binding.btnStopService.setOnClickListener {
            val intent = Intent(this, MusicPlaybackService::class.java).apply {
                action = "ACTION_STOP"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            ServiceLog.add("Requested stopService via ACTION_STOP")
        }

        binding.btnBindService.setOnClickListener {
            if (!isBound) {
                val intent = Intent(this, MusicPlaybackService::class.java)
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
                ServiceLog.add("Requested bindService")
            }
        }

        binding.btnUnbindService.setOnClickListener {
            if (isBound) {
                unbindService(connection)
                isBound = false
                musicService = null
                ServiceLog.add("Requested unbindService")
                updateUI()
            }
        }

        binding.btnPlay.setOnClickListener {
            if (isBound) {
                musicService?.play()
            } else {
                Toast.makeText(this, "Not Bound to Service!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPause.setOnClickListener {
            if (isBound) {
                musicService?.pause()
            } else {
                Toast.makeText(this, "Not Bound to Service!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownload.setOnClickListener {
            if (isBound) {
                musicService?.startDownload()
            } else {
                Toast.makeText(this, "Must BIND service first to download!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefreshLog.setOnClickListener {
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Do NOT stop service or unbind here to allow background playback.
        updateHandler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // If we want to unbind automatically when activity is destroyed to avoid leaks
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun updateUI() {
        binding.textLog.text = ServiceLog.getLogs()

        if (MusicPlaybackService.isRunning) {
            binding.textServiceStatus.text = "Service: RUNNING"
        } else {
            binding.textServiceStatus.text = "Service: STOPPED"
        }

        if (isBound) {
            binding.textConnectionStatus.text = "Connection: BOUND"
        } else {
            binding.textConnectionStatus.text = "Connection: UNBOUND"
        }

        if (isBound && musicService != null) {
            val isPlaying = musicService!!.isPlaying()
            binding.textPlaybackStatus.text = if (isPlaying) "Engine: ● RUNNING" else "Engine: ○ OFF"
            
            val current = musicService!!.getCurrentPosition()
            val total = musicService!!.getDuration()
            
            if (total > 0) {
                binding.textProgress.text = String.format("%02d:%02d / %02d:%02d", 
                    (current / 1000) / 60, (current / 1000) % 60,
                    (total / 1000) / 60, (total / 1000) % 60
                )
            } else {
                binding.textProgress.text = "00:00 / 00:00"
            }
            
            val isDownloading = musicService!!.isDownloading()
            val progress = musicService!!.getDownloadProgress()
            
            if (isDownloading) {
                binding.textDownloadProgress.text = "Downloading: $progress%"
            } else if (progress == 100) {
                binding.textDownloadProgress.text = "Saved to Downloads!"
            } else {
                binding.textDownloadProgress.text = "Ready to download"
            }
            binding.btnDownload.isEnabled = !isDownloading
        } else {
            binding.textPlaybackStatus.text = "Engine: ?"
            binding.textProgress.text = "00:00 / 00:00"
            binding.textDownloadProgress.text = "Bind to download"
            binding.btnDownload.isEnabled = false
        }
    }
}
