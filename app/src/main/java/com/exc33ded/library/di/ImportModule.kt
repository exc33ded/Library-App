package com.exc33ded.library.di

import com.exc33ded.library.imports.AndroidPhotoPrep
import com.exc33ded.library.imports.MlKitTextReader
import com.exc33ded.library.imports.PhotoPrep
import com.exc33ded.library.imports.TextReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {
    @Binds abstract fun photoPrep(impl: AndroidPhotoPrep): PhotoPrep
    @Binds abstract fun textReader(impl: MlKitTextReader): TextReader
}
