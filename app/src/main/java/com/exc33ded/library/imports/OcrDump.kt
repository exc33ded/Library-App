package com.exc33ded.library.imports

import kotlinx.serialization.Serializable

/** One photo's OCR result; the on-disk format shared by the ML Kit debug dump, the PaddleOCR script and the benchmark. */
@Serializable
data class OcrDump(val file: String, val lines: List<OcrLine>)
