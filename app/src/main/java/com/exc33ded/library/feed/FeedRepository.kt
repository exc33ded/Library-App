package com.exc33ded.library.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class FeedRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: DataStore<Preferences>,
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourceSer = ListSerializer(FeedSource.serializer())
    private val itemSer = ListSerializer(FeedItem.serializer())
    private val readSer = ListSerializer(String.serializer())
    private val cacheFile get() = File(ctx.filesDir, "feed_cache.json")
    private val cache = MutableStateFlow<List<FeedItem>?>(null)

    val sources: Flow<List<FeedSource>> = store.data.map { readSources(it) }
    val readLinks: Flow<Set<String>> = store.data.map { readReadList(it).toSet() }

    /** Cached items, loaded from disk on first access. */
    val items: Flow<List<FeedItem>> = cache.map { it ?: loadCache().also { l -> if (cache.value == null) cache.value = l } }

    private fun readSources(p: Preferences): List<FeedSource> =
        p[SOURCES]?.let { runCatching { json.decodeFromString(sourceSer, it) }.getOrNull() } ?: DefaultFeeds.all

    private fun readReadList(p: Preferences): List<String> =
        p[READ]?.let { runCatching { json.decodeFromString(readSer, it) }.getOrNull() } ?: emptyList()

    private suspend fun loadCache(): List<FeedItem> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(itemSer, cacheFile.readText()) }.getOrDefault(emptyList())
    }

    suspend fun setSources(list: List<FeedSource>) {
        store.edit { it[SOURCES] = json.encodeToString(sourceSer, list) }
    }

    suspend fun addSource(url: String) {
        val u = url.trim()
        store.edit { p ->
            val cur = readSources(p)
            if (cur.none { it.url == u }) {
                val host = runCatching { java.net.URI(u).host }.getOrNull()?.removePrefix("www.") ?: u
                p[SOURCES] = json.encodeToString(sourceSer, cur + FeedSource(u, host))
            }
        }
    }

    suspend fun removeSource(url: String) {
        store.edit { p -> p[SOURCES] = json.encodeToString(sourceSer, readSources(p).filter { it.url != url }) }
    }

    suspend fun toggle(url: String, enabled: Boolean) {
        store.edit { p ->
            p[SOURCES] = json.encodeToString(sourceSer, readSources(p).map { if (it.url == url) it.copy(enabled = enabled) else it })
        }
    }

    suspend fun resetToDefaults() = setSources(DefaultFeeds.all)

    suspend fun markRead(link: String) {
        store.edit { p ->
            val cur = readReadList(p)
            if (link !in cur) p[READ] = json.encodeToString(readSer, capReadLinks(cur + link))
        }
    }

    /** Fetches every enabled source (at most 4 at once); returns how many failed. */
    suspend fun refresh(): Int {
        val enabled = readSources(store.data.first()).filter { it.enabled }
        val gate = Semaphore(4)
        val results = coroutineScope {
            enabled.map { s ->
                async {
                    gate.withPermit {
                        try {
                            parseFeed(client.get(s.url).bodyAsText(), s.title)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        val merged = mergeItems(results.filterNotNull())
        withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(json.encodeToString(itemSer, merged)) } }
        cache.value = merged
        return results.count { it == null }
    }

    private companion object {
        val SOURCES = stringPreferencesKey("feeds_json")
        val READ = stringPreferencesKey("feeds_read")
    }
}
