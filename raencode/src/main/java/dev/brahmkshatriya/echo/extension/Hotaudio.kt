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
import dev.brahmkshatriya.echo.extension.hotaudio.HaListenResponse
import dev.brahmkshatriya.echo.extension.hotaudio.HaState
import dev.brahmkshatriya.echo.extension.hotaudio.HaTrack
import dev.brahmkshatriya.echo.extension.hotaudio.HotaudioVm
import dev.brahmkshatriya.echo.extension.hotaudio.X25519
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Hotaudio : ExtensionClient, HomeFeedClient, AlbumClient, TrackClient, SearchFeedClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val cookiesList = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Cookie>()
    private var customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    init {
        try {
            val file = listOf(
                java.io.File("hotaudio_cookies.json"),
                java.io.File("../hotaudio_cookies.json"),
                java.io.File("d:\\C\\p6\\echo-extension\\hotaudio_cookies.json")
            ).firstOrNull { it.exists() }
            if (file != null) {
                val data = json.decodeFromString<dev.brahmkshatriya.echo.extension.hotaudio.CookieData>(file.readText())
                customUserAgent = data.user_agent
                
                val cookie = okhttp3.Cookie.Builder()
                    .domain("hotaudio.net")
                    .path("/")
                    .name("cf_clearance")
                    .value(data.cf_clearance)
                    .secure()
                    .httpOnly()
                    .build()
                cookiesList.add(cookie)
                println("SUCCESSFULLY LOADED CLOUDFLARE COOKIES AND UA: UA=$customUserAgent, COOKIE=${data.cf_clearance}")
            } else {
                println("NO COOKIE FILE FOUND AT PATHS")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", customUserAgent)
            }
            if (original.header("Accept") == null) {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", "en-US,en;q=0.9")
            }
            chain.proceed(builder.build())
        }
        .cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                println("COOKIEJAR SAVE: url=${url}, cookies=$cookies")
                cookiesList.removeAll { oldCookie ->
                    cookies.any { newCookie -> newCookie.name == oldCookie.name && newCookie.domain == oldCookie.domain }
                }
                cookiesList.addAll(cookies)
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                val matched = cookiesList.filter { it.matches(url) }
                println("COOKIEJAR LOAD: url=${url}, matched=$matched")
                return matched
            }
        })
        .build()
        

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

    private fun isSfwFiltered(title: String, tags: List<String>): Boolean {
        if (!sfwOnly) return false
        val sfwBadKeywords = listOf(
            "cock", "pussy", "fucking", "fucks", "dick", "cum", "creampie", "blowjob",
            "orgasm", "masturbation", "fingering", "cunnilingus", "clit", "handjob",
            "erotic", "nsfw", "sex", "lewd", "leching", "plap", "pregnant"
        )
        val normalizedTitle = title.lowercase()
        if (sfwBadKeywords.any { normalizedTitle.contains(it) }) return true
        if (tags.any { tag -> sfwBadKeywords.any { tag.lowercase().contains(it) } }) return true
        return false
    }

    // Parses feed HTML
    private fun parseFeedHtml(html: String): List<Album> {
        val doc = Jsoup.parse(html)
        val items = doc.select("div.bg-surface.border-surface-lighter")
        val albums = mutableListOf<Album>()

        for (item in items) {
            val titleEl = item.selectFirst("a.text-foreground") ?: continue
            val title = titleEl.text().trim()
            val url = titleEl.attr("href") // e.g. /u/author/slug
            
            val creatorEl = item.selectFirst("a.font-bold")
            val creator = creatorEl?.text()?.trim() ?: "Unknown"
            
            // Duration & Date
            var duration = ""
            var date = ""
            val tags = mutableListOf<String>()
            
            val tagmElements = item.select("div.tagm")
            for (i in 0 until tagmElements.size) {
                val tagm = tagmElements[i]
                val txt = tagm.text()
                if (txt.contains("length:")) {
                    duration = txt.substringAfter("length:").trim()
                } else if (txt.contains("on:")) {
                    date = txt.substringAfter("on:").trim()
                }
            }
            
            val tagElements = item.select("a.tag")
            for (i in 0 until tagElements.size) {
                val t = tagElements[i]
                tags.add(t.text().trim())
            }

            if (isSfwFiltered(title, tags)) continue

            val coverUrl = "https://hotaudio.net/favicon.ico"
            
            albums.add(
                Album(
                    id = url,
                    title = title,
                    cover = coverUrl.toImageHolder(crop = true),
                    description = "Duration: $duration | Published: $date\nTags: ${tags.joinToString()}",
                    artists = listOf(Artist(id = creator, name = creator)),
                    extras = mapOf(
                        "url" to url,
                        "creator" to creator,
                        "duration" to duration,
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
            
            // 1. Trending Shelf (Home page)
            try {
                val req = Request.Builder().url("https://hotaudio.net/").build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val trending = parseFeedHtml(html)
                    if (trending.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "trending",
                                title = "Trending",
                                list = trending
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. New Shelf
            try {
                val req = Request.Builder().url("https://hotaudio.net/?sort=new").build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val newest = parseFeedHtml(html)
                    if (newest.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "new",
                                title = "New",
                                list = newest
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
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves = mutableListOf<Shelf>()
            try {
                val url = "https://hotaudio.net/?search=" + java.net.URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder().url(url).build()
                val resp = httpClient.newCall(req).await()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val results = parseFeedHtml(html)
                    if (results.isNotEmpty()) {
                        shelves.add(
                            Shelf.Lists.Items(
                                id = "search_results",
                                title = "Search Results",
                                list = results
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

    // AlbumClient Implementation
    override suspend fun loadAlbum(album: Album): Album {
        return withContext(Dispatchers.IO) {
            val detailUrl = "https://hotaudio.net" + album.id
            val req = Request.Builder().url(detailUrl).build()
            val resp = httpClient.newCall(req).await()
            if (!resp.isSuccessful) return@withContext album
            val html = resp.body?.string() ?: ""
            
            val doc = Jsoup.parse(html)
            val descEl = doc.selectFirst("div.prose.prose-ha.break-words")
            val description = descEl?.text()?.trim() ?: album.description

            // Extract haState
            val haState = decryptHaState(html)
            
            Album(
                id = album.id,
                title = album.title,
                cover = album.cover,
                description = description,
                artists = album.artists,
                extras = album.extras.toMutableMap().apply {
                    if (haState != null) {
                        put("pid", haState.pid ?: "")
                        put("tick", haState.tick ?: "")
                        put("key", haState.key ?: "")
                    }
                }
            )
        }
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? = PagedData.Single {
        withContext(Dispatchers.IO) {
            val detailUrl = "https://hotaudio.net" + album.id
            val req = Request.Builder().url(detailUrl).build()
            val resp = httpClient.newCall(req).await()
            if (!resp.isSuccessful) return@withContext emptyList<Track>()
            val html = resp.body?.string() ?: ""
            
            val haState = decryptHaState(html) ?: return@withContext emptyList<Track>()
            val tracksList = mutableListOf<Track>()
            
            val tick = haState.tick ?: ""
            val pid = haState.pid ?: ""
            val serverKey = haState.key ?: ""
            
            haState.tracks?.forEach { (trackId, haTrack) ->
                val trackKey = haTrack.key ?: ""
                val title = haTrack.title ?: album.title
                
                val track = Track(
                    id = trackId,
                    title = title,
                    cover = album.cover,
                    album = album,
                    artists = album.artists,
                    duration = 0L, // dynamic
                    extras = mapOf(
                        "tid" to trackId,
                        "pid" to pid,
                        "key" to trackKey,
                        "tick" to tick,
                        "serverKey" to serverKey
                    ),
                    streamables = listOf(
                        Streamable(
                            id = trackId,
                            quality = 0,
                            type = Streamable.MediaType.Server,
                            title = title,
                            extras = mapOf(
                                "tid" to trackId,
                                "pid" to pid,
                                "key" to trackKey,
                                "tick" to tick,
                                "serverKey" to serverKey
                            )
                        )
                    )
                )
                tracksList.add(track)
            }
            
            tracksList
        }
    }.toFeed()

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    private fun String.hexToByteArray(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        }
        return data
    }

    // ChaCha20-Poly1305 encrypt (matches browser Ce cipher: blockSize=64, nonceLength=12, tagLength=16)
    private fun chaCha20Poly1305Encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "ChaCha20")
        val spec = javax.crypto.spec.IvParameterSpec(nonce)
        val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plaintext)
    }

    // ChaCha20-Poly1305 decrypt
    private fun chaCha20Poly1305Decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "ChaCha20")
        val spec = javax.crypto.spec.IvParameterSpec(nonce)
        val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    private fun decryptHaState(html: String): HaState? {
        try {
            val stateRegex = """var\s+__ha_state\s*=\s*"([^"]+)"""".toRegex()
            val match = stateRegex.find(html) ?: return null
            val encodedState = match.groupValues[1]
            
            val decoded = Base64.getDecoder().decode(encodedState)
            val keyBytes = decoded.copyOfRange(decoded.size - 32, decoded.size)
            val ciphertextBytes = decoded.copyOfRange(0, decoded.size - 32)
            val nonceBytes = ByteArray(12) // 12-byte zero nonce

            val secretKey = SecretKeySpec(keyBytes, "ChaCha20")
            val ivSpec = IvParameterSpec(nonceBytes)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedBytes = cipher.doFinal(ciphertextBytes)
            
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
            return json.decodeFromString<HaState>(decryptedStr)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // TrackClient Implementation
    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        return withContext(Dispatchers.IO) {
            val tid = streamable.id
            val pid = streamable.extras["pid"] ?: ""
            val key = streamable.extras["key"] ?: ""
            val tick = streamable.extras["tick"] ?: ""
            val serverKey = streamable.extras["serverKey"] ?: ""
            
            var targetTid = tid
            var targetPid = pid
            var targetKey = key
            var targetTick = tick
            var targetServerKey = serverKey

            // Execute ECDH key agreement (X25519)
            val privA = X25519.generatePrivateKey()
            val pubA = X25519.getPublicKey(privA)
            val pubAStr = pubA.joinToString("") { "%02x".format(it) }

            // Calculate shared secret and derive session key via SHA-256
            val serverKeyBytes = targetServerKey.hexToByteArray()
            val sharedSecret = X25519.calculateSharedSecret(privA, serverKeyBytes)
            val md = MessageDigest.getInstance("SHA-256")
            val sessionKey = md.digest(sharedSecret)

            // Generate payload JSON
            val payload = """{"tid":"$targetTid","pid":"$targetPid","key":"$targetKey","tick":"$targetTick","first":-1}"""
            val signature = HotaudioVm().sign(payload)

            println("DEBUG loadStreamableMedia: extras=${streamable.extras}")
            println("DEBUG PAYLOAD: $payload")
            println("DEBUG SIGNATURE: $signature")
            println("DEBUG X-Key: $pubAStr")

            // Derive nonce: SHA-256 of signature UTF-8 bytes, take first 12 bytes (matches browser: Ae(E).slice(0,12))
            val signatureBytes = signature.toByteArray(Charsets.UTF_8)
            val signatureHash = md.digest(signatureBytes)
            val nonce = signatureHash.copyOfRange(0, 12)

            // Encrypt request body with ChaCha20-Poly1305 (matches browser Ce cipher)
            val encryptedPayload = chaCha20Poly1305Encrypt(payload.toByteArray(Charsets.UTF_8), sessionKey, nonce)

            val listenUrl = "https://hotaudio.net/api/v1/audio/listen?key=$targetKey"
            
            val httpUrl = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("hotaudio.net")
                .encodedPath("/api/v1/audio/listen")
                .addQueryParameter("key", targetKey)
                .build()
            val cookies = httpClient.cookieJar.loadForRequest(httpUrl)
            println("COOKIES FOR REQUEST: $cookies")

            val reqBody = encryptedPayload.toRequestBody("application/vnd.hotaudio.crypt+json".toMediaType())

            val req = Request.Builder()
                .url(listenUrl)
                .post(reqBody)
                .addHeader("X-Signature", signature)
                .addHeader("X-Key", pubAStr)
                .addHeader("Content-Type", "application/vnd.hotaudio.crypt+json")
                .addHeader("User-Agent", customUserAgent)
                .addHeader("Referer", "https://hotaudio.net/")
                .addHeader("Origin", "https://hotaudio.net")
                .addHeader("Accept", "*/*")
                .build()

            val resp = httpClient.newCall(req).await()
            val isEncrypted = resp.header("Content-Type")?.contains("application/vnd.hotaudio.crypt+json") == true
            
            val respBytes = resp.body?.bytes() ?: ByteArray(0)
            println("LISTEN RESPONSE: status=${resp.code}, encrypted=$isEncrypted, bodyLen=${respBytes.size}")
            val decryptedBytes = if (isEncrypted) {
                // Browser increments nonce[0] for response decryption (k[0]++)
                val decNonce = nonce.clone()
                decNonce[0] = (decNonce[0] + 1).toByte()
                chaCha20Poly1305Decrypt(respBytes, sessionKey, decNonce)
            } else {
                respBytes
            }
            
            val respBody = String(decryptedBytes, Charsets.UTF_8)
            println("DECRYPTED LISTEN RESPONSE: $respBody")
            
            val listenResponse = json.decodeFromString<HaListenResponse>(respBody)
            
            // Build Streamable.Media
            listenResponse.url.toServerMedia(
                headers = mapOf(
                    "User-Agent" to customUserAgent
                )
            )
        }
    }
}
