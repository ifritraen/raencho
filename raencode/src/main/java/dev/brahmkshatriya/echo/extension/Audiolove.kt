package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.audiolove.AudioDetail
import dev.brahmkshatriya.echo.extension.audiolove.SearchContentItem
import dev.brahmkshatriya.echo.extension.audiolove.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Audiolove : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private lateinit var settings: Settings

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingSwitch(
                title = "SFW Only Mode",
                key = "sfwOnly",
                defaultValue = true
            )
        )
    }

    private val sfwOnly: Boolean
        get() = settings.getBoolean("sfwOnly") ?: true

    // Helper functions to map API model to Echo media models
    private fun AudioDetail.toAlbum(): Album {
        val creator = profileName ?: profile?.name ?: profileHandleName ?: profile?.handleName ?: "Unknown Creator"
        val coverUrl = "https://audio.love/favicon.ico"

        val descriptionText = buildString {
            append(description ?: "")
            if (!genderPreferences.isNullOrEmpty()) {
                append("\n\nPreferences: ")
                append(genderPreferences.joinToString())
            }
            if (!categories.isNullOrEmpty()) {
                append("\nCategories: ")
                append(categories.joinToString())
            }
        }

        return Album(
            id = id,
            title = name,
            cover = coverUrl.toImageHolder(crop = true),
            description = descriptionText,
            artists = listOf(Artist(id = creator, name = creator)),
            extras = mapOf(
                "id" to id,
                "slug" to (slug ?: ""),
                "upvoteCount" to (upvoteCount ?: 0).toString(),
                "listenCount" to (listenCount ?: 0).toString()
            )
        )
    }

    private fun AudioDetail.toTrack(album: Album): Track {
        val durationMs = (lengthSeconds ?: 0) * 1000L

        return Track(
            id = id,
            title = name,
            cover = album.cover,
            album = album,
            artists = album.artists,
            duration = durationMs,
            extras = mapOf("id" to id),
            streamables = listOf(
                Streamable(
                    id = id,
                    quality = 0,
                    type = Streamable.MediaType.Server,
                    title = name,
                    extras = emptyMap()
                )
            )
        )
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()

            // 1. Popular Shelf
            try {
                val popular = fetchFeed(sortBy = "Popularity", take = 10)
                if (popular.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "popular",
                            title = "Popular",
                            list = popular.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Newest Shelf
            try {
                val newest = fetchFeed(sortBy = "Newest", take = 10)
                if (newest.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "newest",
                            title = "Newest",
                            list = newest.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            shelves
        }
    }.toFeed()

    private val nsfwWords = listOf(
        "nsfw", "18+", "erotic", "sex", "blowjob", "fingering", "masturbation",
        "femdom", "maledom", "porn", "fap", "dick", "cum", "handjob", "rimjob",
        "orgasm", "penetration", "anal", "facial", "cuckold", "dildo", "vibrator",
        "cock", "pussy", "clit", "sucking", "tit", "breast", "erotica", "hentai"
    )

    private fun AudioDetail.isSafe(): Boolean {
        if (!sfwOnly) return true
        val titleLower = name.lowercase()
        val descLower = (description ?: "").lowercase()
        
        val hasNsfwInTitleOrDesc = nsfwWords.any { titleLower.contains(it) || descLower.contains(it) }
        if (hasNsfwInTitleOrDesc) return false

        val prefNsfw = genderPreferences?.any { tag ->
            val nameLower = tag.name.lowercase()
            val descLowerTag = (tag.description ?: "").lowercase()
            nsfwWords.any { nameLower.contains(it) || descLowerTag.contains(it) }
        } ?: false
        if (prefNsfw) return false

        val catNsfw = categories?.any { tag ->
            val nameLower = tag.name.lowercase()
            val descLowerTag = (tag.description ?: "").lowercase()
            nsfwWords.any { nameLower.contains(it) || descLowerTag.contains(it) }
        } ?: false
        if (catNsfw) return false

        return true
    }

    private suspend fun fetchFeed(sortBy: String, query: String = "", skip: Int = 0, take: Int = 10): List<AudioDetail> = withContext(Dispatchers.IO) {
        val url = "https://api.audio.love/UserContent/SearchContent?query=$query&skip=$skip&take=$take"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val searchType = when (sortBy.lowercase()) {
            "popularity" -> "popularity"
            "newest" -> "newest"
            else -> "popularity"
        }

        val bodyJson = """
            {
              "selectedTagIds": [],
              "deselectedTagIds": [],
              "searchType": "$searchType",
              "deselectedLengths": [],
              "deselectedContentTypes": []
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(mediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()
        val responseBody = response.body?.string() ?: return@withContext emptyList()

        try {
            val res = json.decodeFromString<dev.brahmkshatriya.echo.extension.audiolove.SearchContentResponse>(responseBody)
            val items = res.results?.mapNotNull { it.audio } ?: emptyList()
            items.filter { it.isSafe() }
        } catch (e: Exception) {
            println("Fetch error: $e")
            e.printStackTrace()
            emptyList()
        }
    }

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val id = album.id
            val url = "https://api.audio.love/UserContent/Audio/$id"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext album
            val responseBody = response.body?.string() ?: return@withContext album

            try {
                val responseObj = json.decodeFromString<dev.brahmkshatriya.echo.extension.audiolove.AudioDetailResponse>(responseBody)
                val audioDetail = responseObj.audio ?: throw Exception("Inner audio detail was null")
                audioDetail.toAlbum()
            } catch (e: Exception) {
                println("loadAlbum error: $e")
                e.printStackTrace()
                album
            }
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val id = album.id
            val url = "https://api.audio.love/UserContent/Audio/$id"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val responseBody = response.body?.string() ?: return@withContext emptyList<Track>()

            try {
                val responseObj = json.decodeFromString<dev.brahmkshatriya.echo.extension.audiolove.AudioDetailResponse>(responseBody)
                val audioDetail = responseObj.audio ?: throw Exception("Inner audio detail was null")
                listOf(audioDetail.toTrack(album))
            } catch (e: Exception) {
                println("loadTracks error: $e")
                e.printStackTrace()
                emptyList()
            }
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val id = streamable.id
            val url = "https://api.audio.love/UserContent/Audio/$id/File"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to load streamable media: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty streamable response body")

            val res = json.decodeFromString<StreamResponse>(responseBody)
            val streamUrl = res.url ?: throw Exception("No streamable URL found in stream response: ${res.error}")
            streamUrl.toServerMedia()
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track
    }

    // SearchFeedClient Implementation
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Continuous<Shelf> { continuation ->
        withContext(Dispatchers.IO) {
            val page = continuation?.toIntOrNull() ?: 0
            val skip = page * 10
            val items = fetchFeed(sortBy = "Popularity", query = query, skip = skip, take = 10)
            val albums = items.map { it.toAlbum() }
            val nextPage = if (albums.isNotEmpty()) (page + 1).toString() else null

            Page(
                data = listOf(
                    Shelf.Lists.Items(
                        id = "search_results_$page",
                        title = "Search Results - Page ${page + 1}",
                        list = albums
                    )
                ),
                continuation = nextPage
            )
        }
    }.toFeed()
}
