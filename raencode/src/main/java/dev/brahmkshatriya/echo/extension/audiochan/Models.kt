package dev.brahmkshatriya.echo.extension.audiochan

import kotlinx.serialization.Serializable

@Serializable
data class AudiochanResponse<T>(
    val data: T
)

@Serializable
data class AudioFile(
    val id: String,
    val url: String? = null,
    val duration: String? = null,
    val filename: String? = null,
    val mime_type: String? = null,
    val filesize: String? = null
)

@Serializable
data class TextNode(
    val text: String? = null,
    val type: String
)

@Serializable
data class DocNode(
    val type: String,
    val content: List<TextNode>? = null
)

@Serializable
data class AudioDescription(
    val type: String,
    val content: List<DocNode>? = null
) {
    fun getPlainDescription(): String {
        return content?.flatMap { node ->
            node.content?.mapNotNull { it.text } ?: emptyList()
        }?.joinToString(separator = "\n") ?: ""
    }
}

@Serializable
data class User(
    val id: String,
    val username: String,
    val display_name: String,
    val avatar: String? = null
)

@Serializable
data class Credit(
    val id: String,
    val display_name: String? = null,
    val role: String? = null,
    val user: User? = null
)

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val slug: String
)

@Serializable
data class AudiochanItem(
    val id: String,
    val title: String,
    val slug: String,
    val description: AudioDescription? = null,
    val like_count: Int? = 0,
    val published_at: String? = null,
    val audioFile: AudioFile? = null,
    val credits: List<Credit>? = emptyList(),
    val tags: List<Tag>? = emptyList()
)

@Serializable
data class AudiochanList(
    val data: List<AudiochanItem>
)

@Serializable
data class SearchResponse(
    val audios: AudiochanList
)
