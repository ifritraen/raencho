package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
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
import dev.brahmkshatriya.echo.extension.lushstories.StoriesListResponse
import dev.brahmkshatriya.echo.extension.lushstories.StoryDetailResponse
import dev.brahmkshatriya.echo.extension.lushstories.StoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class LushStories : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient, ArtistClient {

    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
                defaultValue = false
            )
        )
    }

    private val sfwOnly: Boolean
        get() = settings.getBoolean("sfwOnly") ?: false

    private val sfwBadKeywords = listOf(
        "cock", "pussy", "fucking", "fucks", "dick", "cum", "creampie", "blowjob",
        "orgasm", "masturbation", "fingering", "cunnilingus", "clit", "handjob",
        "erotic", "nsfw", "sex", "lewd", "leching", "plap", "pregnant"
    )

    private fun isSfwFiltered(story: StoryItem): Boolean {
        if (!sfwOnly) return false
        val title = story.title?.lowercase() ?: ""
        val snippet = story.snippet?.lowercase() ?: ""
        val oneLiner = story.oneLiner?.lowercase() ?: ""
        val tags = story.tags?.mapNotNull { it.text?.lowercase() ?: it.name?.lowercase() } ?: emptyList()

        if (sfwBadKeywords.any { title.contains(it) || snippet.contains(it) || oneLiner.contains(it) }) return true
        if (tags.any { tag -> sfwBadKeywords.any { tag.contains(it) } }) return true
        return false
    }

    private fun parseDurationMs(durationStr: String?): Long {
        if (durationStr.isNullOrEmpty()) return 0L
        try {
            val parts = durationStr.split(":")
            var secs = 0L
            if (parts.size == 2) {
                secs = parts[0].toLong() * 60 + parts[1].toLong()
            } else if (parts.size == 3) {
                secs = parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
            }
            return secs * 1000L
        } catch (e: Exception) {
            return 0L
        }
    }

    private fun StoryItem.getCoverUrl(): String {
        val cover = coverImage?.sizes?.medium
            ?: coverImage?.sizes?.large_thumbnail
            ?: coverImage?.sizes?.large_watermarked
            ?: author?.avatar?.sizes?.medium
            ?: author?.avatar?.sizes?.large_thumbnail
            ?: "https://www.lushstories.com/favicon.ico"
        return if (cover.startsWith("//")) "https:$cover" else cover
    }

    private fun StoryItem.toAlbum(): Album {
        val username = author?.username ?: "Unknown Author"
        val coverUrl = getCoverUrl()
        val desc = listOfNotNull(
            oneLiner?.takeIf { it.isNotEmpty() },
            snippet?.takeIf { it.isNotEmpty() },
            published?.let { "Published: $it" },
            views?.let { "Views: $it" },
            score?.let { "Score: $it" }
        ).joinToString("\n\n")

        return Album(
            id = slug ?: id?.toString() ?: "",
            title = title ?: "Untitled",
            cover = coverUrl.toImageHolder(crop = true),
            description = desc,
            artists = listOf(Artist(id = username, name = username)),
            extras = mapOf(
                "slug" to (slug ?: id?.toString() ?: "")
            )
        )
    }

    private fun StoryItem.toTrack(album: Album): Track {
        val durationMs = parseDurationMs(audio?.duration)
        val streamUrl = audio?.sizes?.mp3
        val streamables = if (!streamUrl.isNullOrEmpty()) {
            listOf(
                Streamable(
                    id = slug ?: id?.toString() ?: "",
                    quality = 0,
                    type = Streamable.MediaType.Server,
                    title = title ?: album.title,
                    extras = mapOf("url" to streamUrl)
                )
            )
        } else {
            listOf(
                Streamable(
                    id = slug ?: id?.toString() ?: "",
                    quality = 0,
                    type = Streamable.MediaType.Server,
                    title = title ?: album.title,
                    extras = mapOf("slug" to (slug ?: id?.toString() ?: ""))
                )
            )
        }

        return Track(
            id = slug ?: id?.toString() ?: "",
            title = title ?: album.title,
            cover = album.cover,
            album = album,
            artists = album.artists,
            duration = durationMs,
            extras = mapOf("slug" to (slug ?: id?.toString() ?: "")),
            streamables = streamables
        )
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()

            // 1. Trending Shelf
            try {
                val trending = fetchStories("sort=trending")
                if (trending.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "trending",
                            title = "Trending Audio Stories",
                            list = trending.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Recently Uploaded Shelf
            try {
                val newest = fetchStories("sort=new")
                if (newest.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "new",
                            title = "New Audio Stories",
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

    private suspend fun fetchStories(queryParams: String): List<StoryItem> {
        val url = "https://api.lushstories.com/stories?audio=1&per_page=10&$queryParams"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) return emptyList()
        val responseBody = response.body?.string() ?: return emptyList()

        return try {
            val listResponse = json.decodeFromString<StoriesListResponse>(responseBody)
            listResponse.data.filter { !isSfwFiltered(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // SearchFeedClient Implementation
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Continuous<Shelf> { continuation ->
        withContext(Dispatchers.IO) {
            val page = continuation?.toIntOrNull() ?: 1
            val url = "https://api.lushstories.com/stories?audio=1&per_page=10&page=$page&keywords=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val responseBody = response.body?.string() ?: return@withContext Page(emptyList(), null)

            try {
                val listResponse = json.decodeFromString<StoriesListResponse>(responseBody)
                val stories = listResponse.data.filter { !isSfwFiltered(it) }
                val albums = stories.map { it.toAlbum() }
                
                val lastPage = listResponse.meta?.last_page ?: 1
                val nextPage = if (page < lastPage && albums.isNotEmpty()) (page + 1).toString() else null

                Page(
                    data = listOf(
                        Shelf.Lists.Items(
                            id = "search_results_$page",
                            title = "Search Results - Page $page",
                            list = albums
                        )
                    ),
                    continuation = nextPage
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Page(emptyList(), null)
            }
        }
    }.toFeed()

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val slug = album.extras["slug"] ?: album.id
            val url = "https://api.lushstories.com/stories/$slug"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val responseBody = response.body?.string() ?: return@withContext album

            try {
                val detailResponse = json.decodeFromString<StoryDetailResponse>(responseBody)
                detailResponse.story.toAlbum()
            } catch (e: Exception) {
                e.printStackTrace()
                album
            }
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val slug = album.extras["slug"] ?: album.id
            val url = "https://api.lushstories.com/stories/$slug"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val responseBody = response.body?.string() ?: return@withContext emptyList<Track>()

            try {
                val detailResponse = json.decodeFromString<StoryDetailResponse>(responseBody)
                listOf(detailResponse.story.toTrack(album))
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val streamUrl = streamable.extras["url"]
            if (!streamUrl.isNullOrEmpty()) {
                return@withContext streamUrl.toServerMedia(
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                )
            }

            val slug = streamable.extras["slug"] ?: streamable.id
            val url = "https://api.lushstories.com/stories/$slug"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) throw Exception("Failed to load story details: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")

            val storyDetail = json.decodeFromString<StoryDetailResponse>(responseBody).story
            val mp3Url = storyDetail.audio?.sizes?.mp3
            if (mp3Url.isNullOrEmpty()) {
                throw Exception("Lush Stories premium audio is restricted to logged-in users. Please log in.")
            }

            mp3Url.toServerMedia(
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track
    }

    // ArtistClient Implementation
    override suspend fun loadArtist(artist: Artist): Artist = artist

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()
            try {
                val authorStories = fetchStories("user=${artist.id}")
                if (authorStories.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "artist_${artist.id}",
                            title = "Audio Stories by ${artist.name}",
                            list = authorStories.map { it.toAlbum() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            shelves
        }
    }.toFeed()
}
