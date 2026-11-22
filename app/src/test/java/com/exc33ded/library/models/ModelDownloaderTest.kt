package com.exc33ded.library.models

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModelDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val spec = ModelSpec("t", "T", "d", ModelKind.TEXT, "r/x", "f.bin", bytes.size.toLong(), "0".repeat(64))

    private fun downloader(ignoreRange: Boolean = false, notFound: Boolean = false): ModelDownloader {
        val engine = MockEngine { req ->
            val range = req.headers[HttpHeaders.Range]
            when {
                notFound -> respond("", HttpStatusCode.NotFound)
                range != null && !ignoreRange -> {
                    val n = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    respond(bytes.copyOfRange(n, bytes.size), HttpStatusCode.PartialContent)
                }
                else -> respond(bytes, HttpStatusCode.OK)
            }
        }
        return ModelDownloader(buildDownloadClient(engine))
    }

    private fun part(prefix: Int = 0) = File(tmp.root, "f.bin.part").also { if (prefix > 0) it.writeBytes(bytes.copyOfRange(0, prefix)) }

    @Test fun freshDownloadWritesAllBytes() = runTest {
        val f = part()
        var last = -1L
        downloader().download(spec, f) { last = it }
        assertArrayEquals(bytes, f.readBytes())
        assertEquals(bytes.size.toLong(), last)
    }

    @Test fun resumesWithRange() = runTest {
        val f = part(50_000)
        downloader().download(spec, f) {}
        assertArrayEquals(bytes, f.readBytes())
    }

    @Test fun restartsWhenServerIgnoresRange() = runTest {
        val f = part(50_000)
        downloader(ignoreRange = true).download(spec, f) {}
        assertArrayEquals(bytes, f.readBytes())
    }

    @Test fun nonSuccessStatusThrows() = runTest {
        val f = part()
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { downloader(notFound = true).download(spec, f) {} } }
    }
}
