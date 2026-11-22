package com.exc33ded.library.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.exc33ded.library.imports.OcrDump
import com.exc33ded.library.imports.PhotoPrep
import com.exc33ded.library.imports.TextReader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Debug builds only: run the real photo prep + ML Kit OCR over the .jpg files in filesDir/bench and write filesDir/ocr_mlkit.json
 * (format: List<OcrDump>). Pull with: adb exec-out run-as com.exc33ded.library cat files/ocr_mlkit.json
 *   adb shell am broadcast -n com.exc33ded.library/.debug.OcrDumpReceiver
 */
class OcrDumpReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun prep(): PhotoPrep; fun reader(): TextReader }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val files = File(context.filesDir, "bench").listFiles { f -> f.extension.equals("jpg", true) }?.sortedBy { it.name }.orEmpty()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dumps = files.map { f ->
                    val prepared = deps.prep().prepare(f.absolutePath, "ocrdump-${f.nameWithoutExtension}")
                    OcrDump(f.name, deps.reader().read(prepared))
                }
                File(context.filesDir, "ocr_mlkit.json").writeText(Json.encodeToString(dumps))
                File(context.filesDir, "ocr_mlkit.done").writeText(dumps.size.toString())
            } finally { pending.finish() }
        }
    }
}
