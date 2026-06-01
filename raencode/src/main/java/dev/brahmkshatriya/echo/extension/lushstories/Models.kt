package dev.brahmkshatriya.echo.extension.lushstories

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ImageSizes(
    val adult: Boolean? = null,
    val colour: String? = null,
    @SerialName("large-watermarked")
    val large_watermarked: String? = null,
    @SerialName("large-thumbnail")
    val large_thumbnail: String? = null,
    val medium: String? = null,
    val mp3: String? = null
)

@Serializable
data class CoverImage(
    val id: Int? = null,
    val sizes: ImageSizes? = null
)

@Serializable
data class Genre(
    val slug: String? = null,
    val name: String? = null
)

@Serializable
data class Tag(
    val text: String? = null,
    val value: Int? = null,
    val name: String? = null
)

@Serializable
data class AuthorLinks(
    val profile: String? = null
)

@Serializable
data class Author(
    val id: Int? = null,
    val username: String? = null,
    val genders: String? = null,
    val avatar: CoverImage? = null,
    val links: AuthorLinks? = null,
    val role: String? = null
)

@Serializable
data class Audio(
    val duration: String? = null,
    val id: Int? = null,
    val sizes: ImageSizes? = null
)

@Serializable
data class StoryItem(
    val id: Int? = null,
    val title: String? = null,
    val snippet: String? = null,
    val oneLiner: String? = null,
    val slug: String? = null,
    val published: String? = null,
    val readTime: String? = null,
    val commentsCount: Int? = 0,
    val views: String? = null,
    val score: Int? = 0,
    val body: String? = null,
    val coverImage: CoverImage? = null,
    val genre: Genre? = null,
    val tags: List<Tag>? = emptyList(),
    val author: Author? = null,
    val audio: Audio? = null
)

@Serializable
data class StoryDetailResponse(
    val story: StoryItem
)

@Serializable
data class StoriesListResponse(
    val data: List<StoryItem>,
    val links: Map<String, String?>? = emptyMap(),
    val meta: MetaData? = null
)

@Serializable
data class MetaData(
    val current_page: Int? = 1,
    val last_page: Int? = 1,
    val total: Int? = 0
)
