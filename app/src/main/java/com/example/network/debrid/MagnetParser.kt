package com.example.network.debrid

import com.example.network.torrent.MagnetParser as TorrentMagnetParser

/**
 * Unified delegation object to ensure backwards compatibility with com.example.network.debrid.MagnetParser
 * while delegating all parsing and hashing logic to the canonical TorrentMagnetParser.
 */
object MagnetParser {
    fun extractInfoHash(input: String?): String? = TorrentMagnetParser.parseHash(input)
    fun isInfoHash(input: String?): Boolean = TorrentMagnetParser.isInfoHash(input)
    fun parseHash(input: String?): String? = TorrentMagnetParser.parseHash(input)
    fun base32ToHex(base32: String): String? = TorrentMagnetParser.base32ToHex(base32)
    fun toCanonicalMagnet(
        infoHash: String,
        displayName: String? = null,
        trackers: List<String> = emptyList()
    ): String = TorrentMagnetParser.toCanonicalMagnet(infoHash, displayName, trackers)
}
