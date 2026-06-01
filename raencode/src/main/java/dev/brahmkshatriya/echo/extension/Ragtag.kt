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
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.ragtag.RagtagFile
import dev.brahmkshatriya.echo.extension.ragtag.RagtagResponse
import dev.brahmkshatriya.echo.extension.ragtag.RagtagSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class Ragtag : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient {

    private val httpClient = OkHttpClient.Builder()
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
        return emptyList()
    }

    // Helper functions to map RagtagSource to Echo models
    private fun RagtagSource.toAlbum(): Album {
        val creator = channel_name ?: "Unknown Creator"
        val prefixedDriveBase = if (drive_base?.startsWith("gd:") == true || drive_base?.startsWith("s3:") == true) drive_base else "gd:$drive_base"
        val coverFile = files?.firstOrNull { it.name?.endsWith(".webp") == true || it.name?.endsWith(".jpg") == true || it.name?.endsWith(".png") == true }?.name
            ?: "$video_id.webp"
        val coverUrl = "https://content.archive.ragtag.moe/$prefixedDriveBase/$video_id/$coverFile"

        return Album(
            id = video_id ?: "",
            title = title ?: "Untitled",
            cover = coverUrl.toImageHolder(crop = true),
            description = description ?: "",
            artists = listOf(Artist(id = channel_id ?: "Unknown", name = creator)),
            extras = mapOf(
                "video_id" to (video_id ?: ""),
                "drive_base" to (drive_base ?: "")
            )
        )
    }

    private fun RagtagSource.toTrack(album: Album): Track {
        val durationMs = (duration ?: 0) * 1000L
        val filesJson = try {
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(RagtagFile.serializer()), files ?: emptyList())
        } catch (e: Exception) {
            "[]"
        }

        val streamableExtras = mapOf(
            "drive_base" to (drive_base ?: ""),
            "files" to filesJson
        )

        return Track(
            id = video_id ?: "",
            title = title ?: "Untitled",
            cover = album.cover,
            album = album,
            artists = album.artists,
            duration = durationMs,
            extras = mapOf("video_id" to (video_id ?: "")),
            streamables = listOf(
                Streamable(
                    id = video_id ?: "",
                    quality = 0,
                    type = Streamable.MediaType.Server,
                    title = title ?: "Untitled",
                    extras = streamableExtras
                )
            )
        )
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()

            // 1. Trending Shelf (sort by view_count desc)
            try {
                val list = fetchList("sort=view_count&sort_order=desc")
                if (list.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "trending",
                            title = "Trending",
                            list = list
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Recently Uploaded Shelf
            try {
                val list = fetchList("sort=upload_date&sort_order=desc")
                if (list.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "new",
                            title = "Recently Uploaded",
                            list = list
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Recently Archived Shelf
            try {
                val list = fetchList("sort=archived_timestamp&sort_order=desc")
                if (list.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "archived",
                            title = "Recently Archived",
                            list = list
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            shelves
        }
    }.toFeed()

    private suspend fun fetchList(queryParams: String): List<Album> {
        val url = "https://archive.ragtag.moe/api/v1/search?$queryParams&size=10"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) return emptyList()
        val responseBody = response.body?.string() ?: return emptyList()

        return try {
            val apiResponse = json.decodeFromString<RagtagResponse>(responseBody)
            apiResponse.hits?.hits?.mapNotNull { it._source?.toAlbum() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // SearchFeedClient Implementation
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Continuous<Shelf> { continuation ->
        withContext(Dispatchers.IO) {
            val from = continuation?.toIntOrNull() ?: 0
            val url = "https://archive.ragtag.moe/api/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&from=$from&size=10"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val responseBody = response.body?.string() ?: return@withContext Page(emptyList(), null)

            try {
                val apiResponse = json.decodeFromString<RagtagResponse>(responseBody)
                val hits = apiResponse.hits?.hits ?: emptyList()
                val albums = hits.mapNotNull { it._source?.toAlbum() }
                val nextFrom = if (albums.size >= 10) (from + 10).toString() else null

                Page(
                    data = listOf(
                        Shelf.Lists.Items(
                            id = "search_results_$from",
                            title = "Search Results - $from",
                            list = albums
                        )
                    ),
                    continuation = nextFrom
                )
            } catch (e: Exception) {
                Page(emptyList(), null)
            }
        }
    }.toFeed()

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val url = "https://archive.ragtag.moe/api/v1/search?v=${album.id}"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val responseBody = response.body?.string() ?: return@withContext album

            try {
                val apiResponse = json.decodeFromString<RagtagResponse>(responseBody)
                val hit = apiResponse.hits?.hits?.firstOrNull() ?: return@withContext album
                hit._source?.toAlbum() ?: album
            } catch (e: Exception) {
                album
            }
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val url = "https://archive.ragtag.moe/api/v1/search?v=${album.id}"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val responseBody = response.body?.string() ?: return@withContext emptyList<Track>()

            try {
                val apiResponse = json.decodeFromString<RagtagResponse>(responseBody)
                val hit = apiResponse.hits?.hits?.firstOrNull() ?: return@withContext emptyList<Track>()
                val source = hit._source ?: return@withContext emptyList<Track>()
                listOf(source.toTrack(album))
            } catch (e: Exception) {
                emptyList()
            }
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val videoId = streamable.id
            val driveBase = streamable.extras["drive_base"] ?: ""
            val filesJson = streamable.extras["files"] ?: ""

            val files = try {
                json.decodeFromString<List<RagtagFile>>(filesJson)
            } catch (e: Exception) {
                emptyList()
            }

            val bestFile = files.firstOrNull { it.name?.endsWith(".f251.webm") == true }
                ?: files.firstOrNull { it.name?.contains(".f140.") == true || it.name?.endsWith(".m4a") == true }
                ?: files.firstOrNull { it.name?.endsWith(".webm") == true && !it.name.contains(".f248") && !it.name.contains(".f247") }
                ?: files.firstOrNull { it.name?.endsWith(".mkv") == true }
                ?: files.firstOrNull { it.name?.endsWith(".mp4") == true }
                ?: files.firstOrNull { it.name?.contains(".") == true && !it.name.endsWith(".json") && !it.name.endsWith(".jpg") && !it.name.endsWith(".webp") && !it.name.endsWith(".vtt") && !it.name.endsWith(".ytt") }

            val filename = bestFile?.name ?: "$videoId.mkv"
            val prefixedDriveBase = if (driveBase.startsWith("gd:") || driveBase.startsWith("s3:")) driveBase else "gd:$driveBase"
            val streamUrl = "https://content.archive.ragtag.moe/$prefixedDriveBase/$videoId/$filename"
            
            streamUrl.toServerMedia()
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track
    }
}
