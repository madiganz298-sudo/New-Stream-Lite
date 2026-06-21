package com.example

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), AddPlaylistDialog.AddPlaylistListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playlistManager: PlaylistManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var adapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var displayedChannels: List<Channel> = emptyList()
    
    private var isSearching = false
    private var searchFilter = ""
    private var currentTabPosition = 0 // 0 for All, 1 for Favorites
    
    private var activeAddDialog: AddPlaylistDialog? = null

    // Register file browser contracting
    private val browseFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(it) ?: "playlist.m3u"
            activeAddDialog?.onFileSelected(it, fileName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // App Theme initialization (SharedPreferences dark mode defaults)
        val prefs = getSharedPreferences("m4ditv_prefs", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("theme_is_dark", true) // Charcoal dark (default)
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistManager = PlaylistManager(this)
        favoritesManager = FavoritesManager(this)

        setupThemeButton(isDark)
        setupRecyclerView()
        setupSearch()
        setupListeners()
        setupTabs()

        // Load cached playlist or fetch default
        loadInitialData()
    }

    private fun setupThemeButton(isDark: Boolean) {
        // Sun icon for dark theme (action -> light mode), Moon icon for light theme (action -> dark mode)
        if (isDark) {
            binding.btnThemeToggle.setImageResource(R.drawable.ic_light_mode)
        } else {
            binding.btnThemeToggle.setImageResource(R.drawable.ic_dark_mode)
        }
        
        binding.btnThemeToggle.setOnClickListener {
            val prefs = getSharedPreferences("m4ditv_prefs", Context.MODE_PRIVATE)
            val currentMode = prefs.getBoolean("theme_is_dark", true)
            prefs.edit().putBoolean("theme_is_dark", !currentMode).apply()
            
            // Re-apply and recreate activity for beautiful material transition
            if (!currentMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate()
        }
    }

    private fun setupRecyclerView() {
        val displayMetrics = resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        
        // Dynamically choose column density based on viewport dimensions
        val spanCount = when {
            widthDp < 600 -> 1
            widthDp in 600.0..840.0 -> 2
            else -> 3
        }

        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvChannels.setHasFixedSize(true)

        adapter = ChannelAdapter(
            onChannelClick = { channel ->
                // Play Channel using custom fullscreen ExoPlayer Activity
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("channel", channel)
                }
                startActivity(intent)
            },
            onChannelLongClick = { channel ->
                // Favorite Toggle
                val becameFavorite = favoritesManager.toggleFavorite(channel.id)
                val toastMsg = if (becameFavorite) {
                    getString(R.string.added_to_favorites)
                } else {
                    getString(R.string.removed_from_favorites)
                }
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                
                // Partially update payloads so scrolling is totally smooth
                val index = displayedChannels.indexOfFirst { it.id == channel.id }
                if (index != -1) {
                    adapter.notifyItemChanged(index, "FAVORITE_CHANGE")
                }
                
                // If on Favorite Tab, item should drop out smoothly from active list
                if (currentTabPosition == 1) {
                    refreshDisplayList()
                }
            },
            favoritesManager = favoritesManager
        )
        binding.rvChannels.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(query: CharSequence?, p1: Int, p2: Int, p3: Int) {
                searchFilter = query?.toString()?.lowercase() ?: ""
                refreshDisplayList()
            }
            override fun afterTextChanged(p0: Editable?) {}
        })
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                refreshDisplayList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        binding.btnAddPlaylist.setOnClickListener {
            activeAddDialog = AddPlaylistDialog()
            activeAddDialog?.show(supportFragmentManager, "AddPlaylistDialog")
        }

        binding.btnRetry.setOnClickListener {
            fetchDefaultPlaylist()
        }
    }

    private fun loadInitialData() {
        allChannels = playlistManager.getAllChannels()
        if (allChannels.isEmpty()) {
            fetchDefaultPlaylist()
        } else {
            refreshDisplayList()
        }
    }

    private fun fetchDefaultPlaylist() {
        if (!Utils.isNetworkAvailable(this)) {
            showErrorLayout(getString(R.string.connection_error))
            return
        }

        binding.progressLoading.visibility = View.VISIBLE
        binding.txtLoadingState.text = getString(R.string.loading_playlist)
        binding.txtLoadingState.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
        binding.rvChannels.visibility = View.GONE

        lifecycleScope.launch {
            val content = Utils.fetchUrl("https://rizkyevory.github.io/merged_iptv_simple.m3u")
            if (content != null) {
                val parsed = withContext(Dispatchers.Default) {
                    M3UParser.parse(content)
                }
                if (parsed.isNotEmpty()) {
                    val defaultPlaylists = listOf(Playlist("Default TV", "https://rizkyevory.github.io/merged_iptv_simple.m3u", parsed))
                    playlistManager.savePlaylists(defaultPlaylists)
                    allChannels = playlistManager.getAllChannels()
                    
                    showContentLayout()
                    refreshDisplayList()
                } else {
                    showErrorLayout(getString(R.string.invalid_m3u))
                }
            } else {
                showErrorLayout(getString(R.string.fetch_failed))
            }
        }
    }

    private fun refreshDisplayList() {
        var list = if (currentTabPosition == 1) {
            val favIds = favoritesManager.getFavoriteIds()
            allChannels.filter { favIds.contains(it.id) }
        } else {
            allChannels
        }

        if (searchFilter.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase().contains(searchFilter) ||
                        it.group.lowercase().contains(searchFilter)
            }
        }

        // Limit maximum size of initial rendering to 500 items for memory headroom
        displayedChannels = if (list.size > 500) list.take(500) else list
        adapter.submitList(displayedChannels)

        // Toggle Empty warnings
        if (displayedChannels.isEmpty()) {
            binding.txtEmptyMsg.visibility = View.VISIBLE
            binding.txtEmptyMsg.text = if (currentTabPosition == 1) {
                getString(R.string.no_favorite_found)
            } else {
                getString(R.string.no_channel_found)
            }
        } else {
            binding.txtEmptyMsg.visibility = View.GONE
        }
    }

    private fun showContentLayout() {
        binding.progressLoading.visibility = View.GONE
        binding.txtLoadingState.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.rvChannels.visibility = View.VISIBLE
    }

    private fun showErrorLayout(errorMsg: String) {
        binding.progressLoading.visibility = View.GONE
        binding.txtLoadingState.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.txtErrorMsg.text = errorMsg
        binding.rvChannels.visibility = View.GONE
        binding.txtEmptyMsg.visibility = View.GONE
    }

    // AddPlaylistDialog.AddPlaylistListener Implementations:
    override fun onAddFromUrl(playlistName: String, url: String) {
        if (!Utils.isNetworkAvailable(this)) {
            Toast.makeText(this, getString(R.string.connection_error), Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressLoading.visibility = View.VISIBLE
        binding.txtLoadingState.text = getString(R.string.loading_custom_playlist)
        binding.txtLoadingState.visibility = View.VISIBLE

        lifecycleScope.launch {
            val content = Utils.fetchUrl(url)
            if (content != null) {
                val parsed = withContext(Dispatchers.Default) {
                    M3UParser.parse(content)
                }
                if (parsed.isNotEmpty()) {
                    val currentPlaylists = ArrayList(playlistManager.loadPlaylists())
                    currentPlaylists.add(Playlist(playlistName, url, parsed))
                    playlistManager.savePlaylists(currentPlaylists)
                    
                    allChannels = playlistManager.getAllChannels()
                    showContentLayout()
                    refreshDisplayList()
                    Toast.makeText(this@MainActivity, getString(R.string.playlist_added_success), Toast.LENGTH_SHORT).show()
                } else {
                    showContentLayout()
                    Toast.makeText(this@MainActivity, "Tergabung 0 channel. Pastikan format M3U8 valid.", Toast.LENGTH_LONG).show()
                }
            } else {
                showContentLayout()
                Toast.makeText(this@MainActivity, "Gagal mengunduh URL playlist", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAddFromPaste(playlistName: String, rawContent: String) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                M3UParser.parse(rawContent)
            }
            if (parsed.isNotEmpty()) {
                val currentPlaylists = ArrayList(playlistManager.loadPlaylists())
                currentPlaylists.add(Playlist(playlistName, null, parsed))
                playlistManager.savePlaylists(currentPlaylists)
                
                allChannels = playlistManager.getAllChannels()
                refreshDisplayList()
                Toast.makeText(this@MainActivity, getString(R.string.playlist_added_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.invalid_m3u), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBrowseFile() {
        browseFileLauncher.launch("*/*")
    }

    override fun onAddFromFile(playlistName: String, uri: Uri) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.txtLoadingState.text = "Membaca file..."
        binding.txtLoadingState.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val content = readFileFromUri(uri)
                val parsed = withContext(Dispatchers.Default) {
                    M3UParser.parse(content)
                }
                if (parsed.isNotEmpty()) {
                    val currentPlaylists = ArrayList(playlistManager.loadPlaylists())
                    currentPlaylists.add(Playlist(playlistName, null, parsed))
                    playlistManager.savePlaylists(currentPlaylists)
                    
                    allChannels = playlistManager.getAllChannels()
                    showContentLayout()
                    refreshDisplayList()
                    Toast.makeText(this@MainActivity, getString(R.string.playlist_added_success), Toast.LENGTH_SHORT).show()
                } else {
                    showContentLayout()
                    Toast.makeText(this@MainActivity, getString(R.string.invalid_m3u), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showContentLayout()
                Toast.makeText(this@MainActivity, "Gagal membaca file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun readFileFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: ""
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
