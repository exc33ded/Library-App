package com.exc33ded.library.di

import com.exc33ded.library.BuildConfig
import com.exc33ded.library.metadata.GoogleBooksSource
import com.exc33ded.library.metadata.OpenLibrarySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

const val USER_AGENT = "LibraryApp/0.1 (https://github.com/exc33ded/Library-App)"

/** One place that configures HTTP; tests pass a MockEngine. */
fun buildHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) { requestTimeoutMillis = 15_000 }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun httpClient(): HttpClient = buildHttpClient(OkHttp.create())
    @Provides @Singleton fun openLibrary(client: HttpClient) = OpenLibrarySource(client)
    @Provides @Singleton fun googleBooks(client: HttpClient) = GoogleBooksSource(client, BuildConfig.GOOGLE_BOOKS_KEY)
}
