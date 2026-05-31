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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class Literotica : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient, ArtistClient {

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

    private fun isSfwFiltered(title: String, description: String): Boolean {
        if (!sfwOnly) return false
        val normalizedTitle = title.lowercase()
        val normalizedDesc = description.lowercase()
        return sfwBadKeywords.any { normalizedTitle.contains(it) || normalizedDesc.contains(it) }
    }

    // Common Jsoup HTML parser for stories lists
    private fun parseStoriesHtml(html: String): List<Album> {
        val doc = Jsoup.parse(html)
        val articles = doc.select("div[role=article]")
        val albums = mutableListOf<Album>()

        for (el in articles) {
            val titleEl = el.selectFirst("a[href*=/s/]") ?: continue
            val title = titleEl.text().trim()
            val href = titleEl.attr("href") // e.g. https://www.literotica.com/s/slug
            val slug = href.substringAfter("/s/").trim()

            val descEl = el.selectFirst("p[class*=description]")
            val description = descEl?.text()?.trim() ?: ""

            if (isSfwFiltered(title, description)) continue

            val ratingEl = el.selectFirst("[title=Rating]")
            val rating = ratingEl?.text()?.trim()?.replace("Rating:", "")?.trim() ?: "N/A"

            val authorEl = el.selectFirst("a[href*=/authors/]")
            val author = authorEl?.text()?.trim() ?: "Unknown"
            val authorHref = authorEl?.attr("href") ?: ""
            val authorId = authorHref.substringAfter("/authors/").substringBefore("/works/").trim()

            val timeEl = el.selectFirst("time")
            val date = timeEl?.text()?.trim() ?: ""

            val coverUrl = "https://www.literotica.com/favicon.ico"

            albums.add(
                Album(
                    id = slug,
                    title = title,
                    cover = coverUrl.toImageHolder(crop = true),
                    description = "$description\n\nRating: $rating | Approved: $date",
                    artists = listOf(Artist(id = authorId, name = author)),
                    extras = mapOf(
                        "slug" to slug,
                        "url" to href,
                        "authorId" to authorId,
                        "authorName" to author,
                        "rating" to rating,
                        "date" to date
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

            // 1. New Shelf
            try {
                val req = Request.Builder().url("https://www.literotica.com/c/audio-sex-stories/new-audio-sex-stories").build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val newAlbums = parseStoriesHtml(html)
                    if (newAlbums.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "new",
                                title = "New Erotic Audio",
                                list = newAlbums
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Weekly Trending Shelf
            try {
                val req = Request.Builder().url("https://www.literotica.com/c/audio-sex-stories?period=week").build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val weekly = parseStoriesHtml(html)
                    if (weekly.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "trending_weekly",
                                title = "Trending (Weekly)",
                                list = weekly
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Monthly Trending Shelf
            try {
                val req = Request.Builder().url("https://www.literotica.com/c/audio-sex-stories?period=month").build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val monthly = parseStoriesHtml(html)
                    if (monthly.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "trending_monthly",
                                title = "Trending (Monthly)",
                                list = monthly
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
            val url = "https://search.literotica.com/?query=${java.net.URLEncoder.encode(query, "UTF-8")}&type=submissions&page=$page"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return@withContext Page(emptyList(), null)
            val html = response.body?.string() ?: return@withContext Page(emptyList(), null)

            try {
                // Find state='...' from script tags robustly
                val stateRegex = """state\s*=\s*'(\{.+})'""".toRegex()
                val match = stateRegex.find(html) ?: return@withContext Page(emptyList(), null)
                val stateStr = unescapeJsString(match.groupValues[1])
                
                // Deserialize robustly
                val stateJson = json.parseToJsonElement(stateStr).jsonObject
                val searchObj = stateJson["search"]?.jsonObject ?: return@withContext Page(emptyList(), null)
                val listArr = searchObj["list"]?.jsonArray ?: return@withContext Page(emptyList(), null)

                val albums = mutableListOf<Album>()
                for (itemVal in listArr) {
                    val item = itemVal.jsonObject
                    val type = item["type"]?.jsonPrimitive?.content ?: ""
                    
                    // Category ID: 39 is Audio
                    val categoryId = item["category"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val isAudio = type == "audio" || categoryId == 39
                    if (!isAudio) continue

                    val title = item["title"]?.jsonPrimitive?.content ?: ""
                    val description = item["description"]?.jsonPrimitive?.content ?: ""

                    if (isSfwFiltered(title, description)) continue

                    val slug = item["url"]?.jsonPrimitive?.content ?: ""
                    val authorName = item["authorname"]?.jsonPrimitive?.content ?: "Unknown"
                    val authorId = item["author"]?.jsonPrimitive?.content ?: ""
                    val rate = item["rate_all"]?.jsonPrimitive?.content ?: "N/A"
                    val dateApprove = item["date_approve"]?.jsonPrimitive?.content ?: ""

                    val coverUrl = "https://www.literotica.com/favicon.ico"

                    albums.add(
                        Album(
                            id = slug,
                            title = title,
                            cover = coverUrl.toImageHolder(crop = true),
                            description = "$description\n\nRating: $rate | Approved: $dateApprove",
                            artists = listOf(Artist(id = authorId, name = authorName)),
                            extras = mapOf(
                                "slug" to slug,
                                "url" to "https://www.literotica.com/s/$slug",
                                "authorId" to authorId,
                                "authorName" to authorName,
                                "rating" to rate,
                                "date" to dateApprove
                            )
                        )
                    )
                }

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
            } catch (e: Exception) {
                e.printStackTrace()
                Page(emptyList(), null)
            }
        }
    }.toFeed()

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val storyUrl = album.extras["url"] ?: "https://www.literotica.com/s/${album.id}"
            val req = Request.Builder().url(storyUrl).build()
            val resp = httpClient.newCall(req).await()
            if (!resp.isSuccessful) return@withContext album
            val html = resp.body?.string() ?: return@withContext album

            val doc = Jsoup.parse(html)
            val storyTextEl = doc.selectFirst("div.b-story-body-text") ?: doc.selectFirst("div.aa_ht")
            val description = storyTextEl?.text()?.trim() ?: album.description

            Album(
                id = album.id,
                title = album.title,
                cover = album.cover,
                description = description,
                artists = album.artists,
                extras = album.extras
            )
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // TrackClient Implementation
    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        val streamUrl = "https://www.literotica.com/s/${album.id}"
        listOf(
            Track(
                id = album.id,
                title = album.title,
                cover = album.cover,
                album = album,
                artists = album.artists,
                duration = 0L, // dynamic
                extras = mapOf("url" to streamUrl),
                streamables = listOf(
                    Streamable(
                        id = album.id,
                        quality = 0,
                        type = Streamable.MediaType.Server,
                        title = album.title,
                        extras = mapOf("url" to streamUrl)
                    )
                )
            )
        )
    }.toFeed()

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val storyUrl = streamable.extras["url"] ?: "https://www.literotica.com/s/${streamable.id}"
            val req = Request.Builder().url(storyUrl).build()
            val resp = httpClient.newCall(req).await()
            if (!resp.isSuccessful) throw Exception("Failed to load story page: ${resp.code}")
            val html = resp.body?.string() ?: throw Exception("Empty story page body")

            val doc = Jsoup.parse(html)
            val audioTag = doc.selectFirst("audio")
            var src = audioTag?.attr("src")

            if (src.isNullOrEmpty()) {
                val sourceTag = audioTag?.selectFirst("source")
                src = sourceTag?.attr("src")
            }

            if (src.isNullOrEmpty()) {
                throw Exception("No streamable audio source found on the page")
            }

            src.toServerMedia(
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    // ArtistClient Implementation
    override suspend fun loadArtist(artist: Artist): Artist = artist

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val url = "https://www.literotica.com/authors/${artist.id}/works/audio"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).await()
            if (!resp.isSuccessful) return@withContext emptyList<Shelf>()
            val html = resp.body?.string() ?: return@withContext emptyList<Shelf>()

            val albums = parseStoriesHtml(html)
            if (albums.isNotEmpty()) {
                listOf(
                    Shelf.Lists.Items(
                        id = "artist_audio_${artist.id}",
                        title = "Audio by ${artist.name}",
                        list = albums
                    )
                )
            } else {
                emptyList()
            }
        }
    }.toFeed()

    private fun unescapeJsString(s: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                when (next) {
                    '\'' -> { sb.append("'"); i += 2 }
                    '"' -> { sb.append("\""); i += 2 }
                    '\\' -> { sb.append("\\"); i += 2 }
                    'n' -> { sb.append("\\n"); i += 2 }
                    'r' -> { sb.append("\\r"); i += 2 }
                    't' -> { sb.append("\\t"); i += 2 }
                    else -> { sb.append('\\'); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString().replace("\\'", "'")
    }
}
