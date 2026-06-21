package com.example

import java.io.Serializable

data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val streamUrl: String,
    val logoUrl: String? = null
) : Serializable
