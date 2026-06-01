package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
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
import dev.brahmkshatriya.echo.common.models.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class JapaneseASMR : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient, LyricsClient {

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
        val posts = doc.select("li.site-archive-post")
        val albums = mutableListOf<Album>()

        for (el in posts) {
            val titleEl = el.selectFirst("h2.entry-title a") ?: continue
            val title = titleEl.text().trim()
            val href = titleEl.attr("href") // e.g. https://japaneseasmr.com/146306/
            val id = href.trimEnd('/').substringAfterLast('/')

            val imgEl = el.selectFirst("img")
            val coverUrl = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                ?: imgEl?.attr("src")?.takeIf { it.isNotEmpty() }
                ?: "https://japaneseasmr.com/wp-content/uploads/2021/01/logo.png"

            // Parse RJ code from title or post excerpt text
            val text = el.text()
            val rjCode = "RJ\\d{6,8}".toRegex().find(title + " " + text)?.value ?: ""

            // Parse CV from excerpt text
            val cvMatch = "CV:\\s*([^\\n\\]\\s]+)".toRegex().find(text)
            val cv = cvMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

            albums.add(
                Album(
                    id = id,
                    title = title,
                    cover = coverUrl.toImageHolder(crop = true),
                    description = "RJ Code: $rjCode\nCV: $cv",
                    artists = listOf(Artist(id = cv, name = cv)),
                    extras = mapOf(
                        "id" to id,
                        "url" to href,
                        "rj" to rjCode,
                        "cv" to cv
                    )
                )
            )
        }
        return albums
    }

    // HomeFeedClient Implementation
    override suspend fun loadHomeFeed(): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()

            // 1. Recent Posts Shelf
            try {
                val request = Request.Builder()
                    .url("https://japaneseasmr.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val response = httpClient.newCall(request).await()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val albums = parsePosts(html)
                    if (albums.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "recent",
                                title = "Recent ASMR",
                                list = albums
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
                    .url("https://japaneseasmr.com/?orderby=post_views&order=desc")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val response = httpClient.newCall(request).await()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val albums = parsePosts(html)
                    if (albums.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "popular",
                                title = "Popular ASMR",
                                list = albums
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
                "https://japaneseasmr.com/page/$page/"
            } else {
                "https://japaneseasmr.com/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val html = response.body?.string() ?: return@withContext Page(emptyList(), null)

            val albums = parsePosts(html)
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
            val request = Request.Builder()
                .url("https://japaneseasmr.com/$id/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val html = response.body?.string() ?: return@withContext album
            
            val doc = Jsoup.parse(html)
            val contentEl = doc.selectFirst(".entry-content")
            val text = contentEl?.text() ?: ""
            val description = text.take(1000)

            val rjCode = "RJ\\d{6,8}".toRegex().find(doc.html() + " " + text)?.value ?: album.extras["rj"] ?: ""
            val cvMatch = "CV:\\s*([^\\n\\]\\s]+)".toRegex().find(text)
            val cv = cvMatch?.groupValues?.get(1)?.trim() ?: album.extras["cv"] ?: "Unknown"

            Album(
                id = album.id,
                title = album.title,
                cover = album.cover,
                description = description,
                artists = listOf(Artist(id = cv, name = cv)),
                extras = album.extras + mapOf("rj" to rjCode, "cv" to cv)
            )
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            // Load full details of album to resolve the correct RJ Code
            val loadedAlbum = loadAlbum(album)
            val rjCode = loadedAlbum.extras["rj"] ?: ""
            val streamUrl = "https://v.weeab0o.xyz/$rjCode.m3u8"

            listOf(
                Track(
                    id = loadedAlbum.id,
                    title = loadedAlbum.title,
                    cover = loadedAlbum.cover,
                    album = loadedAlbum,
                    artists = loadedAlbum.artists,
                    duration = 0L,
                    extras = mapOf("rj" to rjCode),
                    streamables = listOf(
                        Streamable(
                            id = loadedAlbum.id,
                            quality = 0,
                            type = Streamable.MediaType.Server,
                            title = loadedAlbum.title,
                            extras = mapOf("rj" to rjCode)
                        )
                    )
                )
            )
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val rjCode = streamable.extras["rj"] ?: ""
            if (rjCode.isEmpty()) throw Exception("No RJ code found for streaming")
            val streamUrl = "https://v.weeab0o.xyz/$rjCode.m3u8"
            streamUrl.toServerMedia(
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "https://japaneseasmr.com/"
                )
            )
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    // LyricsClient
    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> = PagedData.Single {
        emptyList<Lyrics>()
    }.toFeed()

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics
}
