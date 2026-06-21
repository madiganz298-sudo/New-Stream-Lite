package com.example

import android.content.Context
import android.content.SharedPreferences

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("m4ditv_prefs", Context.MODE_PRIVATE)

    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("favorites", HashSet()) ?: HashSet()
    }

    fun isFavorite(channelId: String): Boolean {
        return getFavoriteIds().contains(channelId)
    }

    fun toggleFavorite(channelId: String): Boolean {
        val current = HashSet(getFavoriteIds())
        val becameFavorite: Boolean
        if (current.contains(channelId)) {
            current.remove(channelId)
            becameFavorite = false
        } else {
            current.add(channelId)
            becameFavorite = true
        }
        prefs.edit().putStringSet("favorites", current).apply()
        return becameFavorite
    }
}
