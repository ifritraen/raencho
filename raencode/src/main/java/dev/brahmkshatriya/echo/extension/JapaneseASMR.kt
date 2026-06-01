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
            val id = album.extras["id"] ?: album.id
            val request = Request.Builder()
                .url("https://japaneseasmr.com/$id/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                .header("Cookie", "HMWP7ObvRJ4h_ozh9d_djnMKYcKMUR4C_mtme0ioWAQ-1780295129-1.2.1.1-XqmJMSPGsiEdDyqCj4_CkLdCzDHpjOyU2PcOOgJ_Xq0RxpliJ0bvMc7qRxJk_l3A__ZcPH886h8AaRTJUO.cwu7G21omKq_FHJ1sMdh8uhoPBLyvxXOtZIEsCsf3TaCG_AinCqeIsG975UUjWShDmHYQzeLp8MhIb_nXfufIEV5PV8qyhy0TiG7tcsYIC30cAo2ELtyGCV11m1UavErCIQ37VmsvJrcKFKBlC7rlCAkNCPdjDMcJ4JLz5I_Ss3MpiVhXlPp0dSTAFksEtz3l1qSrIs0ZNa7jTVr3EyajFZnQ9LuDlQVgw2xpX_sw_x2KOTkEx1Rc.CzZnkqd22cxTjSkKHfFve_Vzxa3H32p0oeRMaEM_2ARMzj7TPluRnBErUXRQ4VdU6mDg1aiuehN4X7oKaRn6nTLv9_kTo0oDNA")
                .build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext emptyList<Track>()
            val html = response.body?.string() ?: return@withContext emptyList<Track>()
            
            val doc = Jsoup.parse(html)
            val text = doc.selectFirst(".entry-content")?.text() ?: ""
            val rjCode = "RJ\\d{6,8}".toRegex().find(doc.html() + " " + text)?.value ?: album.extras["rj"] ?: ""

            // Group URLs by base name to avoid multiple formats (.mp3 vs .m4a) creating duplicate tracks
            val baseToUrls = mutableMapOf<String, MutableList<String>>()

            // 1. Scrape player elements
            val players = doc.select("audio, video")
            for (player in players) {
                val sources = player.select("source")
                for (source in sources) {
                    val srcUrl = source.attr("src") ?: ""
                    if (srcUrl.isNotEmpty() && (srcUrl.contains("weeab0o") || srcUrl.contains(".mp3") || srcUrl.contains(".m4a") || srcUrl.contains(".m3u8"))) {
                        val cleanUrl = srcUrl.substringBefore("?")
                        val base = cleanUrl.substringBeforeLast(".")
                        if (base.isNotEmpty()) {
                            baseToUrls.getOrPut(base) { mutableListOf() }.add(srcUrl)
                        }
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
                        val base = cleanUrl.substringBeforeLast(".")
                        if (base.isNotEmpty()) {
                            baseToUrls.getOrPut(base) { mutableListOf() }.add(srcUrl)
                        }
                    }
                }
            }

            // 3. Scrape script variables if no player elements found
            if (baseToUrls.isEmpty()) {
                val scriptTags = doc.select("script")
                for (script in scriptTags) {
                    val content = script.html()
                    if (content.contains("audioSrc")) {
                        val match = "audioSrc\\s*=\\s*'([^']+)'".toRegex().find(content)
                            ?: "audioSrc\\s*=\\s*\"([^\"]+)\"".toRegex().find(content)
                        val srcUrl = match?.groupValues?.get(1)
                        if (!srcUrl.isNullOrEmpty()) {
                            val base = srcUrl.substringBeforeLast(".")
                            baseToUrls.getOrPut(base) { mutableListOf() }.add(srcUrl)
                        }
                    }
                }
            }

            // 3. Fallback to standard RJ Code .m3u8 structure
            if (baseToUrls.isEmpty() && rjCode.isNotEmpty()) {
                val fallbackUrl = "https://v.weeab0o.xyz/$rjCode.m3u8"
                baseToUrls.getOrPut("https://v.weeab0o.xyz/$rjCode") { mutableListOf() }.add(fallbackUrl)
            }

            val tracks = mutableListOf<Track>()
            baseToUrls.entries.forEachIndexed { index, entry ->
                val base = entry.key
                val urls = entry.value

                // Choose the best quality/format (prefer .m4a or .m3u8 or .mp3)
                val bestUrl = urls.firstOrNull { it.contains(".m4a") }
                    ?: urls.firstOrNull { it.contains(".m3u8") }
                    ?: urls.firstOrNull { it.contains(".mp3") }
                    ?: urls.first()

                val filename = base.substringAfterLast("/")
                val suffix = filename.substringAfter(rjCode).trim()
                val partTitle = if (suffix.isNotEmpty()) suffix else "Main"
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
                        extras = mapOf("src" to bestUrl),
                        streamables = listOf(
                            Streamable(
                                id = trackId,
                                quality = 0,
                                type = Streamable.MediaType.Server,
                                title = trackTitle,
                                extras = mapOf("src" to bestUrl)
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
            
            // Turnstile Bypass Headers & Persistent Cookies
            val chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            val cfCookie = "HMWP7ObvRJ4h_ozh9d_djnMKYcKMUR4C_mtme0ioWAQ-1780295129-1.2.1.1-XqmJMSPGsiEdDyqCj4_CkLdCzDHpjOyU2PcOOgJ_Xq0RxpliJ0bvMc7qRxJk_l3A__ZcPH886h8AaRTJUO.cwu7G21omKq_FHJ1sMdh8uhoPBLyvxXOtZIEsCsf3TaCG_AinCqeIsG975UUjWShDmHYQzeLp8MhIb_nXfufIEV5PV8qyhy0TiG7tcsYIC30cAo2ELtyGCV11m1UavErCIQ37VmsvJrcKFKBlC7rlCAkNCPdjDMcJ4JLz5I_Ss3MpiVhXlPp0dSTAFksEtz3l1qSrIs0ZNa7jTVr3EyajFZnQ9LuDlQVgw2xpX_sw_x2KOTkEx1Rc.CzZnkqd22cxTjSkKHfFve_Vzxa3H32p0oeRMaEM_2ARMzj7TPluRnBErUXRQ4VdU6mDg1aiuehN4X7oKaRn6nTLv9_kTo0oDNA"
            
            // Self-healing check: prioritize progressive streams (.mp3 / .m4a) to avoid ExoPlayer UnrecognizedInputFormatException
            var targetUrl = srcUrl
            if (targetUrl.endsWith(".m3u8")) {
                targetUrl = targetUrl.replace(".m3u8", ".mp3")
            }
            
            try {
                val checkRequest = Request.Builder()
                    .url(targetUrl)
                    .head()
                    .header("User-Agent", chromeUserAgent)
                    .header("Cookie", cfCookie)
                    .header("Referer", "https://japaneseasmr.com/")
                    .header("Range", "bytes=0-0")
                    .build()
                
                val checkResponse = httpClient.newCall(checkRequest).await()
                
                // If progressive check fails with 404 or similar, fallback to alternative files on the storage CDN
                if (checkResponse.code == 404 || checkResponse.code == 403) {
                    val alternatives = if (targetUrl.endsWith(".mp3")) {
                        listOf(targetUrl.replace(".mp3", ".m4a"), targetUrl.replace(".mp3", ".m3u8"))
                    } else if (targetUrl.endsWith(".m4a")) {
                        listOf(targetUrl.replace(".m4a", ".mp3"), targetUrl.replace(".m4a", ".m3u8"))
                    } else {
                        emptyList()
                    }
                    
                    for (alt in alternatives) {
                        val altReq = Request.Builder()
                            .url(alt)
                            .head()
                            .header("User-Agent", chromeUserAgent)
                            .header("Cookie", cfCookie)
                            .header("Referer", "https://japaneseasmr.com/")
                            .header("Range", "bytes=0-0")
                            .build()
                        val altResp = httpClient.newCall(altReq).await()
                        if (altResp.isSuccessful || altResp.code == 206) {
                            targetUrl = alt
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            targetUrl.toServerMedia(
                headers = mapOf(
                    "User-Agent" to chromeUserAgent,
                    "Cookie" to cfCookie,
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
