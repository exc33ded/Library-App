package com.exc33ded.library.di

import com.exc33ded.library.models.ModelDownloader
import com.exc33ded.library.models.buildDownloadClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {
    @Provides @Singleton
    fun modelDownloader(): ModelDownloader = ModelDownloader(buildDownloadClient(OkHttp.create()))
}
