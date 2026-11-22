package com.exc33ded.library.models

enum class ModelKind { TEXT, VISION }

data class ModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val kind: ModelKind,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val recommended: Boolean = false,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

/** All Apache-2.0 and not gated on Hugging Face (no account or token needed). Sizes and hashes from the HF API, 2026-11-22. */
object ModelCatalog {
    val all: List<ModelSpec> = listOf(
        ModelSpec(
            "qwen3-0.6b", "Qwen3 0.6B", "Small and fast. Fixes misread titles and writes short summaries.", ModelKind.TEXT,
            "litert-community/Qwen3-0.6B", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", 343_490_560L,
            "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0", recommended = true,
        ),
        ModelSpec(
            "qwen3-1.7b", "Qwen3 1.7B", "Better quality than 0.6B, but uses more memory.", ModelKind.TEXT,
            "litert-community/Qwen3-1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977_184_032L,
            "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8",
        ),
        ModelSpec(
            "qwen3.5-0.8b-vl", "Qwen3.5 0.8B Vision", "Reads book covers directly, including non-Latin scripts.", ModelKind.VISION,
            "litert-community/Qwen3.5-0.8B", "Qwen3.5-0.8B-VL_int8.litertlm", 1_303_724_032L,
            "7413f51eafb82e7cf0b707fa506eddfc76c10433b26fa8f0dc2f051970d2feb5",
        ),
        ModelSpec(
            "gemma4-e2b", "Gemma 4 E2B", "Best at reading covers, but heavy for a 6 GB phone.", ModelKind.VISION,
            "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_588_147_712L,
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
