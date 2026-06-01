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
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class EroASMR : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private lateinit var settings: Settings

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun getSettingItems(): List<Setting> {
        return emptyList()
    }

    private fun parsePosts(html: String): List<Album> {
        val doc = Jsoup.parse(html)
        val posts = doc.select(".dt-image-link")
        val albums = mutableListOf<Album>()

        for (el in posts) {
            val href = el.attr("href") ?: continue
            if (!href.contains("/video/")) continue
            val id = href.trimEnd('/').substringAfterLast('/')
            val title = el.attr("title")?.trim() ?: el.selectFirst("img")?.attr("alt")?.trim() ?: "EroASMR Video"

            val imgEl = el.selectFirst("img")
            val coverUrl = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                ?: imgEl?.attr("src")?.takeIf { it.isNotEmpty() }
                ?: "https://eroasmr.com/media/2019/11/logo_eroasmr.png"

            val duration = el.selectFirst(".video-duration")?.text()?.trim() ?: ""

            albums.add(
                Album(
                    id = id,
                    title = title,
                    cover = coverUrl.toImageHolder(crop = true),
                    description = "Duration: $duration",
                    artists = listOf(Artist(id = "EroASMR", name = "EroASMR")),
                    extras = mapOf(
                        "id" to id,
                        "url" to href,
                        "duration" to duration
                    )
                )
            )
        }
        return albums
    }

    private fun parseDuration(durationStr: String?): Long {
        if (durationStr.isNullOrEmpty()) return 0L
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toLong() * 1000
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            // 1. Recent Posts Shelf
            try {
                val request = Request.Builder()
                    .url("https://eroasmr.com/")
                    .header("User-Agent", chromeUserAgent)
                    .build()
                val response = httpClient.newCall(request).await()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val albums = parsePosts(html)
                    if (albums.isNotEmpty()) {
                        // Remove duplicates preserving order
                        val distinctAlbums = albums.distinctBy { it.id }
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "recent",
                                title = "Recent Erotic ASMR",
                                list = distinctAlbums
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Popular Posts Shelf
            try {
                val request = Request.Builder()
                    .url("https://eroasmr.com/top-100-porn-asmr-videos-of-all-time/")
                    .header("User-Agent", chromeUserAgent)
                    .build()
                val response = httpClient.newCall(request).await()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val albums = parsePosts(html)
                    if (albums.isNotEmpty()) {
                        val distinctAlbums = albums.distinctBy { it.id }
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "popular",
                                title = "Top 100 Erotic ASMR",
                                list = distinctAlbums
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            shelves
        }
    }.toFeed()

    // SearchFeedClient Implementation
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Continuous<Shelf> { continuation ->
        withContext(Dispatchers.IO) {
            val page = continuation?.toIntOrNull() ?: 1
            val url = if (query.isEmpty()) {
                "https://eroasmr.com/page/$page/"
            } else {
                "https://eroasmr.com/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
            }
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", chromeUserAgent)
                .build()

            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val html = response.body?.string() ?: return@withContext Page(emptyList(), null)

            val albums = parsePosts(html).distinctBy { it.id }
            val nextPage = if (albums.isNotEmpty()) (page + 1).toString() else null

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
        }
    }.toFeed()

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val id = album.extras["id"] ?: album.id
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            val targetUrl = album.extras["url"] ?: "https://eroasmr.com/video/$id/"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", chromeUserAgent)
                .build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val html = response.body?.string() ?: return@withContext album
            
            val doc = Jsoup.parse(html)
            
            // Extract description: excited vampire presses her cold lips...
            val entryContent = doc.selectFirst(".entry-content, p")
            val text = entryContent?.text() ?: ""
            val description = text.take(1000)

            // Extract tags
            val tagEls = doc.select("a[href*=video-tag]")
            val tags = tagEls.map { it.text().trim() }.filter { it.isNotEmpty() }
            val formattedTags = if (tags.isNotEmpty()) "\n\nTags: " + tags.joinToString(", ") else ""

            Album(
                id = album.id,
                title = album.title,
                cover = album.cover,
                description = description + formattedTags,
                artists = album.artists,
                extras = album.extras
            )
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val id = album.extras["id"] ?: album.id
            val targetUrl = album.extras["url"] ?: "https://eroasmr.com/video/$id/"
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", chromeUserAgent)
                .build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val html = response.body?.string() ?: return@withContext emptyList<Track>()
            
            val doc = Jsoup.parse(html)
            
            val videoSource = doc.selectFirst("video source") ?: doc.selectFirst("source[src$=.mp4]") ?: doc.selectFirst("source")
            val srcUrl = videoSource?.attr("src")?.trim() ?: ""

            if (srcUrl.isEmpty()) {
                return@withContext emptyList<Track>()
            }

            val durationStr = album.extras["duration"]
            val durationMs = parseDuration(durationStr)

            val trackId = "${album.id}_0"
            val trackTitle = album.title

            val track = Track(
                id = trackId,
                title = trackTitle,
                cover = album.cover,
                album = album,
                artists = album.artists,
                duration = durationMs,
                extras = mapOf("src" to srcUrl),
                streamables = listOf(
                    Streamable(
                        id = trackId,
                        quality = 0,
                        type = Streamable.MediaType.Server,
                        title = trackTitle,
                        extras = mapOf("src" to srcUrl)
                    )
                )
            )

            listOf(track)
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val srcUrl = streamable.extras["src"] ?: throw Exception("No streamable source found")
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            
            srcUrl.toServerMedia(
                headers = mapOf(
                    "User-Agent" to chromeUserAgent,
                    "Referer" to "https://eroasmr.com/"
                )
            )
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track
}
