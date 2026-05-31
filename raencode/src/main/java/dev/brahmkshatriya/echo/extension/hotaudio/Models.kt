package dev.brahmkshatriya.echo.extension.hotaudio

import kotlinx.serialization.Serializable

@Serializable
data class HaTrack(
    val key: String? = null,
    val title: String? = null
)

@Serializable
data class HaState(
    val pid: String? = null,
    val isParty: Boolean? = false,
    val tick: String? = null,
    val key: String? = null,
    val mode: String? = null,
    val order: List<Int>? = emptyList(),
    val tracks: Map<String, HaTrack>? = emptyMap()
)

@Serializable
data class HaListenResponse(
    val url: String,
    val keys: Map<String, String>? = emptyMap(),
    val length15s: Int? = 0,
    val allowed: Boolean? = true
)
