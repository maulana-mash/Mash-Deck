package com.mash.deck.discovery

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int
)

data class SavedServer(
    val name: String,
    val host: String,
    val port: Int
)
