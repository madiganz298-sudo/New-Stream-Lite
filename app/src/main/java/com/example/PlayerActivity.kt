package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var isOverlayVisible = true
    private val handler = Handler(Looper.getMainLooper())
    
    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force Landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup Fullscreen
        setupFullscreen()
        
        @Suppress("DEPRECATION")
        val channel = intent.getSerializableExtra("channel") as? Channel
        if (channel == null) {
            Toast.makeText(this, "Channel tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.txtPlayerChannelName.text = channel.name
        binding.txtPlayerGroup.text = channel.group
        
        setupPlayer(channel.streamUrl)
        setupListeners()
        
        // Auto-hide controls after 4 seconds initially
        showControls()
    }

    private fun setupFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    private fun setupPlayer(streamUrl: String) {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player
        
        val mediaUri = android.net.Uri.parse(streamUrl)
        val mimeType = if (streamUrl.contains(".m3u8") || streamUrl.contains("m3u8")) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.APPLICATION_M3U8 // Use HLS as a robust defaults for general IPTV urls
        }
        
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(mimeType)
            .build()
            
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
        
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        binding.playerBuffering.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        binding.playerBuffering.visibility = View.GONE
                    }
                    Player.STATE_ENDED -> {
                        binding.playerBuffering.visibility = View.GONE
                    }
                    Player.STATE_IDLE -> {
                        binding.playerBuffering.visibility = View.GONE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.playerBuffering.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, "Error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupListeners() {
        binding.btnPlayerBack.setOnClickListener {
            finish()
        }

        binding.btnPlayerPlayPause.setOnClickListener {
            resetControlsTimer()
            player?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_play)
                } else {
                    p.play()
                    binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        // Tap on player layout alternates control visibility
        binding.root.setOnClickListener {
            if (isOverlayVisible) {
                hideControls()
            } else {
                showControls()
            }
        }

        // Handle case where click is on PlayerView
        // Note: Set clickable=false on underlying surfaces so clicks bubble up, but if intercepted, handle manually
        binding.playerView.setOnClickListener {
            if (isOverlayVisible) {
                hideControls()
            } else {
                showControls()
            }
        }
        
        binding.playerControlsOverlay.setOnClickListener {
            if (isOverlayVisible) {
                hideControls()
            } else {
                showControls()
            }
        }
    }

    private fun showControls() {
        binding.playerControlsOverlay.animate()
            .alpha(1.0f)
            .setDuration(250)
            .withStartAction {
                binding.playerControlsOverlay.visibility = View.VISIBLE
            }
            .start()
        isOverlayVisible = true
        resetControlsTimer()
    }

    private fun hideControls() {
        binding.playerControlsOverlay.animate()
            .alpha(0.0f)
            .setDuration(250)
            .withEndAction {
                binding.playerControlsOverlay.visibility = View.GONE
            }
            .start()
        isOverlayVisible = false
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun resetControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 4000)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        player?.release()
        player = null
    }
}
