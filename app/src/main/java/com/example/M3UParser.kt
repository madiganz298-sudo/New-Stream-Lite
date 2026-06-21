package com.example

import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentMeta: String? = null

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    currentMeta = trimmed
                } else if (!trimmed.startsWith("#")) {
                    if (currentMeta != null) {
                        val ch = parseChannel(currentMeta, trimmed)
                        if (ch != null) {
                            channels.add(ch)
                        }
                        currentMeta = null
                    } else {
                        // Stream URL without preceding EXTINF, handle gracefully
                        val name = trimmed.substringAfterLast("/").substringBeforeLast(".")
                        channels.add(Channel(
                            id = trimmed.hashCode().toString(),
                            name = if (name.length > 2) name else "Channel " + (channels.size + 1),
                            group = "Lainnya",
                            streamUrl = trimmed
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }
        return channels
    }

    private fun parseChannel(meta: String, streamUrl: String): Channel? {
        val name = meta.substringAfterLast(",").trim()
        if (name.isEmpty() && streamUrl.isEmpty()) return null

        var group = "Lainnya"
        // Try to parse group-title
        val groupIndex = meta.indexOf("group-title=\"")
        if (groupIndex != -1) {
            val start = groupIndex + "group-title=\"".length
            val end = meta.indexOf("\"", start)
            if (end != -1) {
                group = meta.substring(start, end).trim()
            }
        } else {
            // Check for unquoted group-title or alternative keywords
            val altGroupIndex = meta.indexOf("group-title=")
            if (altGroupIndex != -1) {
                val start = altGroupIndex + "group-title=".length
                val end = meta.indexOf(" ", start)
                group = if (end != -1) meta.substring(start, end).trim() else meta.substring(start).trim()
            }
        }
        
        if (group.isEmpty()) {
            group = "Lainnya"
        }

        val channelName = if (name.isNotEmpty()) name else "Channel " + streamUrl.hashCode().toString().takeLast(4)

        return Channel(
            id = streamUrl.hashCode().toString(),
            name = channelName,
            group = group,
            streamUrl = streamUrl
        )
    }
}
