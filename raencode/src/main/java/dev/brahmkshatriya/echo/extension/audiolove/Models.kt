package dev.brahmkshatriya.echo.extension.audiolove

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class Profile(
    val name: String? = null,
    val handleName: String? = null
)

@Serializable
data class AudioDetail(
    val id: String,
    val slug: String? = null,
    val name: String,
    val description: String? = null,
    val upvoteCount: Int? = 0,
    val listenCount: Int? = 0,
    val lengthSeconds: Long? = 0,
    val profileName: String? = null,
    val profileHandleName: String? = null,
    val profile: Profile? = null,
    val genderPreferences: List<Tag>? = emptyList(),
    val categories: List<Tag>? = emptyList()
)

@Serializable
data class SearchContentItem(
    val audio: AudioDetail? = null
)

@Serializable
data class SearchContentResponse(
    val isSuccess: Boolean,
    val results: List<SearchContentItem>? = emptyList()
)

@Serializable
data class AudioDetailResponse(
    val isSuccess: Boolean,
    val audio: AudioDetail? = null
)

@Serializable
data class StreamResponse(
    val isSuccess: Boolean,
    val url: String? = null,
    val error: String? = null
)
