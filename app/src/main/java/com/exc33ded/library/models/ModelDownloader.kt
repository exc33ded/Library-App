package com.exc33ded.library.models

import com.exc33ded.library.di.USER_AGENT
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Not the lookup client: no overall request timeout (downloads are GBs), but a socket timeout so a dead connection fails. */
fun buildDownloadClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
    }
}

class ModelDownloader(private val client: HttpClient) {
    /**
     * Streams [spec].url into [part], resuming from its current length with an HTTP Range request.
     * If the server ignores Range (answers 200) the file is restarted from zero. Returns when the body is fully written.
     * Throws IOException on a non-success status or a network error; cancellation is honoured between chunks.
     */
    suspend fun download(spec: ModelSpec, part: File, onProgress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        var offset = part.length()
        if (offset > spec.sizeBytes) { part.delete(); offset = 0L }
        if (offset == spec.sizeBytes) return@withContext
        client.prepareGet(spec.url) { if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-") }.execute { resp ->
            if (resp.status != HttpStatusCode.OK && resp.status != HttpStatusCode.PartialContent) throw IOException("HTTP ${resp.status.value}")
            val append = offset > 0 && resp.status == HttpStatusCode.PartialContent
            var done = if (append) offset else 0L
            resp.bodyAsChannel().toInputStream().use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done)
                    }
                }
            }
        }
    }
}
