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
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Shelf.Companion.toShelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.audiochan.AudioFile
import dev.brahmkshatriya.echo.extension.audiochan.AudiochanItem
import dev.brahmkshatriya.echo.extension.audiochan.AudiochanResponse
import dev.brahmkshatriya.echo.extension.audiochan.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class Audiochan : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient {

    private val httpClient = OkHttpClient()
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
        get() = settings.getBoolean("sfwOnly", true)

    // Helper functions to map API model to Echo media models
    private fun AudiochanItem.toAlbum(): Album {
        val creator = credits?.firstOrNull { it.role == "voice" }?.user?.display_name
            ?: credits?.firstOrNull()?.display_name
            ?: "Unknown Creator"

        val avatar = credits?.firstOrNull { it.role == "voice" }?.user?.avatar
            ?: "https://audiochan.com/pwa/apple-touch-icon.v2.png"

        return Album(
            id = slug,
            title = title,
            cover = avatar,
            description = description?.getPlainDescription() ?: "",
            artists = listOf(Artist(id = creator, name = creator)),
            extras = mapOf(
                "slug" to slug,
                "like_count" to (like_count ?: 0).toString(),
                "published_at" to (published_at ?: "")
            )
        )
    }

    private fun AudiochanItem.toTrack(album: Album): Track {
        val durationSec = audioFile?.duration?.toDoubleOrNull() ?: 0.0
        val durationMs = (durationSec * 1000).toLong()

        return Track(
            id = slug,
            title = title,
            cover = album.cover,
            album = album,
            artists = album.artists,
            duration = durationMs,
            extras = mapOf("slug" to slug)
        )
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()

            // 1. Trending Shelf
            try {
                val trendingResponse = fetchHomeFeed("trending")
                if (trendingResponse.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "trending",
                            title = "Trending",
                            list = trendingResponse.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. New Shelf
            try {
                val newResponse = fetchHomeFeed("new")
                if (newResponse.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "new",
                            title = "New",
                            list = newResponse.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            shelves
        }
    }.toFeed()

    private suspend fun fetchHomeFeed(mode: String): List<AudiochanItem> {
        val url = "https://api.audiochan.com/audios/feed/home?limit=10&mode=$mode&exclude_own_patreon=true&sfw_only=$sfwOnly"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) return emptyList()
        val responseBody = response.body?.string() ?: return emptyList()

        return try {
            val wrapper = json.decodeFromString<AudiochanResponse<List<AudiochanItem>>>(responseBody)
            wrapper.data
        } catch (e: Exception) {
            emptyList()
        }
    }

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val url = "https://api.audiochan.com/audios/slug/${album.id}"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val responseBody = response.body?.string() ?: return@withContext album

            try {
                val item = json.decodeFromString<AudiochanResponse<AudiochanItem>>(responseBody).data
                item.toAlbum()
            } catch (e: Exception) {
                album
            }
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val url = "https://api.audiochan.com/audios/slug/${album.id}"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val responseBody = response.body?.string() ?: return@withContext emptyList<Track>()

            try {
                val item = json.decodeFromString<AudiochanResponse<AudiochanItem>>(responseBody).data
                listOf(item.toTrack(album))
            } catch (e: Exception) {
                emptyList()
            }
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val slug = streamable.id
            val url = "https://api.audiochan.com/audios/slug/$slug"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) throw Exception("Failed to load streamable media: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty streamable response body")

            val item = json.decodeFromString<AudiochanResponse<AudiochanItem>>(responseBody).data
            val streamUrl = item.audioFile?.url ?: throw Exception("No streamable URL found")
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
            val page = continuation?.toIntOrNull() ?: 1
            val url = "https://api.audiochan.com/search?q=$query&page=$page&limit=10&sort=trending&type=audios&count_mode=none&timeRange=all&sfw_only=$sfwOnly"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val responseBody = response.body?.string() ?: return@withContext Page(emptyList(), null)

            try {
                val searchResponse = json.decodeFromString<AudiochanResponse<SearchResponse>>(responseBody).data
                val items = searchResponse.audios.data.map { it.toAlbum() }
                val nextPage = if (items.isNotEmpty()) (page + 1).toString() else null
                
                Page(
                    data = listOf(
                        Shelf.Lists.Items(
                            id = "search_results_$page",
                            title = "Search Results - Page $page",
                            list = items
                        )
                    ),
                    continuation = nextPage
                )
            } catch (e: Exception) {
                Page(emptyList(), null)
            }
        }
    }.toFeed()
}
