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
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
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
        val albums = mutableListOf<Album>()

        // 1. Try standard WordPress post loop container selectors first
        val postElements = doc.select("article, .post, .type-post, .td-block-span4, .td-block-span12, .entry-card, .loop-post")
        for (el in postElements) {
            val aEl = el.selectFirst("h2 a, h3 a, .entry-title a, .post-title a, a[href*=\"/japaneseasmr.com/\"]") ?: el.selectFirst("a") ?: continue
            val href = aEl.attr("href") ?: continue
            val cleanHref = href.substringBefore("?").trimEnd('/')
            val id = cleanHref.substringAfterLast('/')
            if (id.isEmpty() || id.toIntOrNull() == null) continue

            val title = aEl.text().trim().ifEmpty { 
                el.selectFirst("img")?.attr("alt")?.trim() ?: "JapaneseASMR Track" 
            }

            val imgEl = el.selectFirst("img")
            val coverUrl = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                ?: imgEl?.attr("src")?.takeIf { it.isNotEmpty() }
                ?: "https://japaneseasmr.com/wp-content/uploads/2021/01/logo.png"

            val rjCode = "RJ\\d{6,8}".toRegex().find(title)?.value ?: ""

            albums.add(
                Album(
                    id = id,
                    title = title,
                    cover = coverUrl.toImageHolder(crop = true),
                    description = if (rjCode.isNotEmpty()) "RJ Code: $rjCode" else "JapaneseASMR Post",
                    artists = listOf(Artist(id = "JapaneseASMR", name = "JapaneseASMR")),
                    extras = mapOf(
                        "id" to id,
                        "url" to cleanHref,
                        "rj" to rjCode
                    )
                )
            )
        }

        // 2. Fallback to extracting all numeric links if standard selectors returned empty
        if (albums.isEmpty()) {
            val allLinks = doc.select("a[href]")
            for (link in allLinks) {
                val href = link.attr("href") ?: continue
                if (!href.contains("japaneseasmr.com/")) continue
                val cleanHref = href.substringBefore("?").trimEnd('/')
                val id = cleanHref.substringAfterLast('/')
                if (id.toIntOrNull() != null) {
                    val title = link.text().trim()
                    if (title.length < 5) continue
                    val imgEl = link.selectFirst("img") ?: link.parent()?.selectFirst("img")
                    val coverUrl = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("src")?.takeIf { it.isNotEmpty() }
                        ?: "https://japaneseasmr.com/wp-content/uploads/2021/01/logo.png"

                    val rjCode = "RJ\\d{6,8}".toRegex().find(title)?.value ?: ""

                    albums.add(
                        Album(
                            id = id,
                            title = title,
                            cover = coverUrl.toImageHolder(crop = true),
                            description = if (rjCode.isNotEmpty()) "RJ Code: $rjCode" else "JapaneseASMR Post",
                            artists = listOf(Artist(id = "JapaneseASMR", name = "JapaneseASMR")),
                            extras = mapOf(
                                "id" to id,
                                "url" to cleanHref,
                                "rj" to rjCode
                            )
                        )
                    )
                }
            }
        }

        return albums.distinctBy { it.id }
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

            // 1. Recent Shelf
            try {
                val request = Request.Builder()
                    .url("https://japaneseasmr.com/")
                    .header("User-Agent", chromeUserAgent)
                    .build()
                val response = httpClient.newCall(request).await()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val albums = parsePosts(html)
                    if (albums.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "recent",
                                title = "Recent Japanese ASMR",
                                list = albums
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Popular Shelf
            try {
                val request = Request.Builder()
                    .url("https://japaneseasmr.com/?orderby=post_views&order=desc")
                    .header("User-Agent", chromeUserAgent)
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
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", chromeUserAgent)
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
                        title = "Results - Page $page",
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
            val targetUrl = album.extras["url"] ?: "https://japaneseasmr.com/$id/"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", chromeUserAgent)
                .build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext album
            val html = response.body?.string() ?: return@withContext album

            val doc = Jsoup.parse(html)
            val entryContent = doc.selectFirst(".entry-content, p")
            val text = entryContent?.text() ?: ""
            val description = text.take(1000)

            // Extract tags
            val tagEls = doc.select("a[href*=tag]")
            val tags = tagEls.map { it.text().trim() }.filter { it.isNotEmpty() }
            val formattedTags = if (tags.isNotEmpty()) "\n\nTags: " + tags.distinct().joinToString(", ") else ""

            val rjCode = "RJ\\d{6,8}".toRegex().find(doc.html() + " " + text)?.value ?: album.extras["rj"] ?: ""

            Album(
                id = album.id,
                title = album.title,
                cover = album.cover,
                description = description + formattedTags,
                artists = album.artists,
                extras = album.extras + mapOf("rj" to rjCode)
            )
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val id = album.extras["id"] ?: album.id
            val targetUrl = album.extras["url"] ?: "https://japaneseasmr.com/$id/"
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", chromeUserAgent)
                .build()
            val response = httpClient.newCall(request).await()
            val html = if (response.isSuccessful) response.body?.string() ?: "" else ""

            val doc = Jsoup.parse(html)
            val rjCode = "RJ\\d{6,8}".toRegex().find(html + " " + doc.text())?.value ?: album.extras["rj"] ?: ""

            val baseToUrls = mutableMapOf<String, MutableList<String>>()

            // 1. Scrape player audio/video elements if any
            val players = doc.select("audio, video")
            for (player in players) {
                val sources = player.select("source")
                for (source in sources) {
                    val srcUrl = source.attr("src") ?: ""
                    if (srcUrl.isNotEmpty() && (srcUrl.contains(".mp3") || srcUrl.contains(".m4a") || srcUrl.contains(".m3u8"))) {
                        val cleanUrl = srcUrl.substringBefore("?")
                        val base = cleanUrl.substringBeforeLast("/")
                        baseToUrls.getOrPut(base) { mutableListOf() }.add(srcUrl)
                    }
                }
            }

            // 2. Scrape data-attributes/sources if no player elements found
            if (baseToUrls.isEmpty()) {
                val dataElements = doc.select("[data-audio-src], [data-src]")
                for (el in dataElements) {
                    var srcUrl = el.attr("data-audio-src").trim()
                    if (srcUrl.isEmpty()) {
                        srcUrl = el.attr("data-src").trim()
                    }
                    if (srcUrl.isNotEmpty() && (srcUrl.contains(".mp3") || srcUrl.contains(".m4a") || srcUrl.contains(".m3u8"))) {
                        val cleanUrl = srcUrl.substringBefore("?")
                        val base = cleanUrl.substringBeforeLast("/")
                        baseToUrls.getOrPut(base) { mutableListOf() }.add(srcUrl)
                    }
                }
            }

            // 3. Fallback to standard RJ Code pipeline using weeab0o resolver
            if (baseToUrls.isEmpty() && rjCode.isNotEmpty()) {
                val fallbackUrl = "https://v.weeab0o.xyz/$rjCode.m3u8"
                baseToUrls.getOrPut("https://v.weeab0o.xyz/$rjCode") { mutableListOf() }.add(fallbackUrl)
            }

            val tracks = mutableListOf<Track>()
            baseToUrls.entries.forEachIndexed { index, entry ->
                val base = entry.key
                val urls = entry.value

                val bestUrl = urls.firstOrNull { it.contains(".m4a") }
                    ?: urls.firstOrNull { it.contains(".mp3") }
                    ?: urls.firstOrNull { it.contains(".m3u8") }
                    ?: urls.first()

                val filename = bestUrl.substringAfterLast("/")
                val suffix = filename.substringBeforeLast(".").trim()
                val partTitle = if (suffix.isNotEmpty() && suffix != "1") "Part $suffix" else "Main"
                val trackTitle = if (baseToUrls.size > 1) "${album.title} - $partTitle" else album.title

                val trackId = "${album.id}_$index"

                tracks.add(
                    Track(
                        id = trackId,
                        title = trackTitle,
                        cover = album.cover,
                        album = album,
                        artists = album.artists,
                        duration = 0L,
                        extras = mapOf("src" to bestUrl, "rj" to rjCode),
                        streamables = listOf(
                            Streamable(
                                id = trackId,
                                quality = 0,
                                type = Streamable.MediaType.Server,
                                title = trackTitle,
                                extras = mapOf("src" to bestUrl, "rj" to rjCode)
                            )
                        )
                    )
                )
            }

            tracks
        }
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val srcUrl = streamable.extras["src"] ?: throw Exception("No streamable source found")
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

            srcUrl.toServerMedia(
                headers = mapOf(
                    "User-Agent" to chromeUserAgent,
                    "Referer" to "https://japaneseasmr.com/"
                )
            )
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    // LyricsClient Implementation
    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> = PagedData.Single {
        emptyList<Lyrics>()
    }.toFeed()

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics
}
