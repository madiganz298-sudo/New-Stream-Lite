package com.example

import java.io.Serializable

enum class ChannelStatus {
    UNKNOWN, ONLINE, OFFLINE
}

data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    private val channelStatus: ChannelStatus? = ChannelStatus.UNKNOWN
) : Serializable {
    @Transient
    private var transientStatus: ChannelStatus? = null

    var status: ChannelStatus
        get() = transientStatus ?: channelStatus ?: ChannelStatus.UNKNOWN
        set(value) {
            transientStatus = value
        }
}
