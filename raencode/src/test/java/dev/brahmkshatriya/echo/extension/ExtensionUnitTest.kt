package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class ExtensionUnitTest {
    private val extension: ExtensionClient = Hotaudio()
    private val searchQuery = "Dragon"
    private val user = User("", "Test User")

    @Test
    fun testDecrypt() {
        val encodedState = "yx6mFwDDDhQX+92h7KaAgRau/7pf2Qp5nrAtApsk2z8LGsGM6IDBPzT/4d24wEHKIFLpk/gGEZIyg40PT1SOMd5Fp2aS0PbIe0G2M1pVQbTbZ7wynIFz/Ri15p1mick3vSsDgD4xkjYx6UCiQxD6J0M2LnnGp53NsrpfpavBoN5bO1pZmMAoC/vB7wFRbsghK2DsHMKx7dOptZ7VpKhlLsaFF0MzX4tef99YbXmGTUpAOTzhZu76nswMfJ281D/1mFxmK56jYwscGV1+32I4g9JjR3DDxr5cRQzWazEGoJXIT2IkFP4ewouhZvakrKOBhKCepWzVRKpWzC9IVSqxS3yFy7HYvRZCKRlhcHmLY0Im9J+hzKpR8S0xi2MH8TI0kSrZppslCT3woVbiIEmdRQGvKOfpTlVBwhmnV7c1NE9+yjEDXFGNRbZYTFR8hflXriP5kbvnvKAq9AUS0CZW6g=="
        val f = java.util.Base64.getDecoder().decode(encodedState)
        val keyBytes = f.copyOfRange(f.size - 32, f.size)
        val ciphertextBytes = f.copyOfRange(0, f.size - 32)
        val nonceBytes = ByteArray(12)

        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "ChaCha20")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(nonceBytes)
        val cipher = javax.crypto.Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        val decryptedBytes = cipher.doFinal(ciphertextBytes)
        val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
        println("DECRYPTED STATE: " + decryptedStr)

        val payload = """{"tid":"58204","pid":"7042","key":"8kky42qz3xhrqwwdtyz5g4cj00","tick":"1smQXrl6tQ5qpdbJeS+mEbkeH1qMa","first":-1}"""
        fun hashString(algo: String, input: String): String {
            val bytes = java.security.MessageDigest.getInstance(algo).digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
        println("MD5: " + hashString("MD5", payload))
        println("SHA-1: " + hashString("SHA-1", payload))
        println("SHA-256: " + hashString("SHA-256", payload))

        // Fetch nozzle.js and save its string table
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url("https://hotaudio.net/nozzle.js?v=1J1Db0bF").build()
        val response = client.newCall(request).execute()
        val text = response.body?.string() ?: ""
        val startToken = "G=function(){return["
        val startIdx = text.indexOf(startToken)
        if (startIdx != -1) {
            val arrayStart = startIdx + startToken.length - 1
            var depth = 0
            var inString = false
            var escaped = false
            var endIdx = -1
            for (i in arrayStart until text.length) {
                val char = text[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (char == '\\') {
                    escaped = true
                    continue
                }
                if (char == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (char == '[') depth++
                    if (char == ']') {
                        depth--
                        if (depth == 0) {
                            endIdx = i
                            break
                        }
                    }
                }
            }
            val arrayStr = text.substring(arrayStart, endIdx + 1)
            java.io.File("d:\\C\\p6\\echo-extension\\array_raw.txt").writeText(arrayStr)
            println("Successfully saved array_raw.txt, length: ${arrayStr.length}")
        } else {
            println("G=function(){return[ not found!")
        }
    }

    @Test
    fun testX25519() {
        val privA = dev.brahmkshatriya.echo.extension.hotaudio.X25519.generatePrivateKey()
        val pubA = dev.brahmkshatriya.echo.extension.hotaudio.X25519.getPublicKey(privA)
        val privB = dev.brahmkshatriya.echo.extension.hotaudio.X25519.generatePrivateKey()
        val pubB = dev.brahmkshatriya.echo.extension.hotaudio.X25519.getPublicKey(privB)
        
        val secretA = dev.brahmkshatriya.echo.extension.hotaudio.X25519.calculateSharedSecret(privA, pubB)
        val secretB = dev.brahmkshatriya.echo.extension.hotaudio.X25519.calculateSharedSecret(privB, pubA)
        
        val hexA = secretA.joinToString("") { "%02x".format(it) }
        val hexB = secretB.joinToString("") { "%02x".format(it) }
        println("Shared Secret A: $hexA")
        println("Shared Secret B: $hexB")
        assert(hexA == hexB) { "X25519 shared secrets do not match!" }
    }

    @Test
    fun testHotaudioVm() {
        val vm = dev.brahmkshatriya.echo.extension.hotaudio.HotaudioVm()
        val signature1 = vm.sign("")
        println("VM sign(\"\") => $signature1")
        assert(signature1 == "9:00000000ffc1ca35fe3ade4123d3a912") { "Empty string signature mismatch!" }
        
        val signature2 = vm.sign("test")
        println("VM sign(\"test\") => $signature2")
        assert(signature2 == "9:000000004869b73ce1b893b419bc1585") { "\"test\" signature mismatch!" }
    }

    @Test
    fun testEmptySearch() = testIn("Testing Empty Search") {
        if (extension !is SearchFeedClient) error("SearchFeedClient is not implemented")
        val search = extension.loadSearchFeed("").pagedDataOfFirst().loadPage(null).data
        search.forEach {
            println(it)
        }
    }

    @Test
    fun testSearch() = testIn("Testing Search") {
        if (extension !is SearchFeedClient) error("SearchFeedClient is not implemented")
        println("Searching  : $searchQuery")
        val feed = extension.loadSearchFeed(searchQuery)
        println("Tabs : ${feed.tabs}")
        feed.pagedDataOfFirst().loadPage(null).data.forEach {
            println(it)
        }
    }

    @Test
    fun testHomeFeed() = testIn("Testing Home Feed") {
        if (extension !is HomeFeedClient) error("HomeFeedClient is not implemented")
        val feed = extension.loadHomeFeed()
        println("Tabs : ${feed.tabs}")
        feed.pagedDataOfFirst().loadPage(null).data.forEach {
            println(it)
        }
    }

    private suspend fun searchTrack(q: String? = null): Track {
        if (extension !is SearchFeedClient) error("SearchFeedClient is not implemented")
        val query = q ?: searchQuery
        println("Searching : $query")
        
        var foundTrack: Track? = null
        val items = extension.loadSearchFeed(query).pagedDataOfFirst().loadPage(null).data
        for (shelf in items) {
            when (shelf) {
                is Shelf.Item -> {
                    if (shelf.media is Track) foundTrack = shelf.media as Track
                    else if (shelf.media is Album && extension is AlbumClient && extension is TrackClient) {
                        foundTrack = extension.loadTracks(shelf.media as Album)?.pagedDataOfFirst()?.loadPage(null)?.data?.firstOrNull()
                    }
                }
                is Shelf.Lists.Tracks -> {
                    foundTrack = shelf.list.firstOrNull()
                }
                is Shelf.Lists.Items -> {
                    val first = shelf.list.firstOrNull()
                    if (first is Track) foundTrack = first
                    else if (first is Album && extension is AlbumClient && extension is TrackClient) {
                        foundTrack = extension.loadTracks(first)?.pagedDataOfFirst()?.loadPage(null)?.data?.firstOrNull()
                    }
                }
                else -> {}
            }
            if (foundTrack != null) break
        }
        return foundTrack ?: error("Track not found, try a different search query")
    }

    @Test
    fun testTrackGet() = testIn("Testing Track Get") {
        if (extension !is TrackClient) error("TrackClient is not implemented")
        val search = searchTrack()
        measureTimeMillis {
            val track = extension.loadTrack(search, false)
            println(track)
        }.also { println("time : $it") }
    }

    @Test
    fun testTrackStream() = testIn("Testing Track Stream") {
        if (extension !is TrackClient) error("TrackClient is not implemented")
        val search = searchTrack()
        measureTimeMillis {
            val track = extension.loadTrack(search, false)
            val streamable = track.servers.firstOrNull() ?: error("Track does not streamable")
            val stream = extension.loadStreamableMedia(streamable, false)
            println(stream)
        }.also { println("time : $it") }
    }

    @Test
    fun testTrackRadio() = testIn("Testing Track Radio") {
        if (extension !is TrackClient) {
            println("TrackClient is not implemented, skipping")
            return@testIn
        }
        if (extension !is RadioClient) {
            println("RadioClient is not implemented, skipping")
            return@testIn
        }
        val track = extension.loadTrack(searchTrack(), false)
        val radio = extension.radio(track, null)
        val radioTracks = extension.loadTracks(radio).loadAll()
        radioTracks.forEach {
            println(it)
        }
    }

    @Test
    fun testTrackShelves() = testIn("Testing Track Shelves") {
        if (extension !is TrackClient) error("TrackClient is not implemented")
        val track = extension.loadTrack(searchTrack(), false)
        val mediaItems = extension.loadFeed(track)?.pagedDataOfFirst()?.loadPage(null)?.data
        if (mediaItems.isNullOrEmpty()) println("No shelves found for track")
        else mediaItems.forEach {
            println(it)
        }
    }

    @Test
    fun testAlbumGet() = testIn("Testing Album Get") {
        if (extension !is TrackClient) error("TrackClient is not implemented")
        val small = extension.loadTrack(searchTrack(), false).album ?: error("Track has no album")
        if (extension !is AlbumClient) error("AlbumClient is not implemented")
        val album = extension.loadAlbum(small)
        println(album)
        val tracks = extension.loadTracks(album)?.pagedDataOfFirst()?.loadPage(null)?.data
        if (tracks.isNullOrEmpty()) println("No tracks found for album")
        else tracks.forEach {
            println(it)
        }
    }


    // Test Setup
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        val testSettings = MockedSettings()
        testSettings.putBoolean("sfwOnly", false)
        extension.setSettings(testSettings)
        runBlocking {
            extension.onInitialize()
            extension.onExtensionSelected()
            if (extension is LoginClient) extension.setLoginUser(user)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    private fun testIn(title: String, block: suspend CoroutineScope.() -> Unit) = runBlocking {
        println("\n-- $title --")
        block.invoke(this)
        println("\n")
    }
}