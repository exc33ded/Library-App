package com.exc33ded.library.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.exc33ded.library.imports.ImportRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only: queue every .jpg in filesDir/bench for import (benchmark without the photo picker).
 *   adb shell am broadcast -n com.exc33ded.library/.debug.ImportDirReceiver
 */
class ImportDirReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun imports(): ImportRepository }

    override fun onReceive(context: Context, intent: Intent) {
        val imports = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).imports()
        val files = File(context.filesDir, "bench").listFiles { f -> f.extension.equals("jpg", true) }
            ?.sortedBy { it.name }?.map { it.absolutePath }.orEmpty()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { imports.enqueue(files) } finally { pending.finish() }
        }
    }
}
