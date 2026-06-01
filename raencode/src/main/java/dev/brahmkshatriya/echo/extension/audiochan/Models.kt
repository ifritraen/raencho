package dev.brahmkshatriya.echo.extension.audiochan

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AudiochanResponse<T>(
    val data: T
)

@Serializable
data class AudioFile(
    val id: String? = null,
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
    val id: String? = null,
    val username: String? = null,
    val display_name: String? = null,
    val avatar: String? = null
)

@Serializable
data class Credit(
    val id: String? = null,
    val display_name: String? = null,
    val role: String? = null,
    val user: User? = null
)

@Serializable
data class TagSurrogate(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val category: String? = null
)

object TagSerializer : KSerializer<Tag> {
    override val descriptor: SerialDescriptor = TagSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Tag) {
        val surrogate = TagSurrogate(value.id, value.name, value.slug, value.category)
        encoder.encodeSerializableValue(TagSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Tag {
        val input = decoder as? JsonDecoder ?: throw Exception("This serializer only supports JSON")
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive) {
            val str = element.content
            Tag(id = str, name = str, slug = str)
        } else {
            val surrogate = input.json.decodeFromJsonElement(TagSurrogate.serializer(), element)
            Tag(
                id = surrogate.id ?: surrogate.slug ?: surrogate.name ?: "",
                name = surrogate.name ?: surrogate.slug ?: surrogate.id ?: "",
                slug = surrogate.slug ?: surrogate.id,
                category = surrogate.category
            )
        }
    }
}

@Serializable(with = TagSerializer::class)
data class Tag(
    val id: String,
    val name: String,
    val slug: String? = null,
    val category: String? = null
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
