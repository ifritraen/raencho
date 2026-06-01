package dev.brahmkshatriya.echo.extension.ragtag

import kotlinx.serialization.Serializable

@Serializable
data class RagtagResponse(
    val took: Int? = null,
    val timed_out: Boolean? = null,
    val hits: RagtagHitsContainer? = null
)

@Serializable
data class RagtagHitsContainer(
    val total: RagtagTotal? = null,
    val max_score: Double? = null,
    val hits: List<RagtagHit>? = null
)

@Serializable
data class RagtagTotal(
    val value: Int? = null,
    val relation: String? = null
)

@Serializable
data class RagtagHit(
    val _index: String? = null,
    val _id: String? = null,
    val _score: Double? = null,
    val _source: RagtagSource? = null
)

@Serializable
data class RagtagSource(
    val video_id: String? = null,
    val channel_name: String? = null,
    val channel_id: String? = null,
    val upload_date: String? = null,
    val title: String? = null,
    val description: String? = null,
    val duration: Long? = null,
    val view_count: Long? = null,
    val like_count: Long? = null,
    val dislike_count: Long? = null,
    val drive_base: String? = null,
    val archived_timestamp: String? = null,
    val files: List<RagtagFile>? = null,
    val timestamps: RagtagTimestamps? = null
)

@Serializable
data class RagtagFile(
    val name: String? = null,
    val size: Long? = null
)

@Serializable
data class RagtagTimestamps(
    val publishedAt: String? = null
)
