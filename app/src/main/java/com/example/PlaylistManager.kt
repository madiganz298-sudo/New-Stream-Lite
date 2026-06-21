package com.example

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class Playlist(
    val name: String,
    val url: String?, // null for paste or file upload
    val channels: List<Channel>
)

class PlaylistManager(private val context: Context) {
    private val gson = Gson()
    private val playlistFile = File(context.filesDir, "playlists.json")

    fun savePlaylists(playlists: List<Playlist>) {
        try {
            val writer = FileWriter(playlistFile)
            gson.toJson(playlists, writer)
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadPlaylists(): List<Playlist> {
        if (!playlistFile.exists()) return emptyList()
        return try {
            val reader = FileReader(playlistFile)
            val type = object : TypeToken<List<Playlist>>() {}.type
            val list: List<Playlist>? = gson.fromJson(reader, type)
            reader.close()
            list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getAllChannels(): List<Channel> {
        val playlists = loadPlaylists()
        val allChannels = ArrayList<Channel>()
        val seenUrls = HashSet<String>()
        for (pl in playlists) {
            for (ch in pl.channels) {
                if (seenUrls.add(ch.streamUrl)) {
                    allChannels.add(ch)
                }
            }
        }
        return allChannels
    }
    
    fun clearAllPlaylists() {
        if (playlistFile.exists()) {
            playlistFile.delete()
        }
    }
}
