# Gemma 4 E2B / E4B On-Device (Android, Kotlin) for Book-Cover Vision Extraction + Summary

Researched 2026-11-21. Primary sources were read directly: GitHub source files, Google Maven metadata, HF model cards, and Google docs/blogs. Gemma 4 was released 2026-04-02, so anything dated earlier (Gemma 3n-era posts, MediaPipe LLM Inference samples, LiteRT-LM < 0.10) may be outdated.

## 1. Google AI Edge Gallery (github.com/google-ai-edge/gallery): what it is and what can be lifted

### Takeaway
Gallery is Google's Apache-2.0 Android showcase app (Kotlin, Jetpack Compose, Hilt, WorkManager, AppAuth). It runs Gemma 4 E2B/E4B through LiteRT-LM 0.18.1 and has an "Ask Image" task. Three pieces map almost directly onto a book-cover app: the WorkManager model downloader (`DownloadWorker.kt`), the LiteRT-LM engine/conversation wrapper (`LlmChatModelHelper.kt`), and the JSON model allowlist (pinned HF commit hashes).

### Cited Findings
- License: Apache-2.0 ("Licensed under the Apache License, Version 2.0"). The app is described as an "experimental Beta release." — [Gallery README](https://github.com/google-ai-edge/gallery); [LICENSE](https://raw.githubusercontent.com/google-ai-edge/gallery/main/LICENSE)
- Stack, from `Android/src/gradle/libs.versions.toml` (main, 2026-11-21): `litertlm = "0.18.1"` (`com.google.ai.edge.litertlm:litertlm-android`), `composeBom = "2026.02.00"`, Material3, navigation-compose, `kotlin = 2.2.21`, Hilt `2.60.1`, `work-runtime-ktx 2.12.0`, `net.openid:appauth 0.11.1`, Coil, compose-richtext (markdown). It also includes ML Kit GenAI `genai-prompt 1.0.0-beta5` and `genai-schema 1.0.0-alpha2` for the AICore path. — [libs.versions.toml](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/gradle/libs.versions.toml)
- App module: `minSdk = 31`, `targetSdk = 37`, compileSdk 37, `versionName = "1.0.24"`. — [app/build.gradle.kts](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/build.gradle.kts)
- Model downloads use a Hugging Face OAuth app. You have to set `clientId`/`redirectUri` in `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt` and `manifestPlaceholders["appAuthRedirectScheme"]` in `app/build.gradle.kts`, "required for model downloads to work correctly." — [DEVELOPMENT.md](https://github.com/google-ai-edge/gallery/blob/main/DEVELOPMENT.md)
- Source files worth copying, all under `Android/src/app/src/main/java/com/google/ai/edge/gallery/`. They were listed from the repo tree via the GitHub API. — [repo tree](https://github.com/google-ai-edge/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery)
  - Download: `worker/DownloadWorker.kt`, `worker/DownloadNetworkMonitor.kt`, `worker/DownloadSlowConnectionDetector.kt`, `data/DownloadRepository.kt`, `data/LiteRtLmModelDownloader.kt`, `data/ModelDownloader.kt`, `data/DownloadStorageManager.kt`, `data/ModelDownloadStatus.kt`
  - HF: `huggingface/HuggingFaceApiClient.kt`, `huggingface/HfModelUtils.kt`
  - Inference: `ui/llmchat/LlmChatModelHelper.kt` (LiteRT-LM), `runtime/LlmModelHelper.kt` (interface), `runtime/aicore/AICoreModelHelper.kt` + `AICoreModelDownloader.kt` (Gemini Nano/AICore backend), `agent/sessions/LlmSessionManager.kt`
  - UI: `ui/llmchat/*` (chat), `ui/llmsingleturn/*` (single-turn prompt lab, the closest fit for one-shot "photo in, JSON out"), `ui/common/chat/ModelDownloadStatusInfoPanel.kt`, `ui/common/modelitem/DownloadModelPanel.kt`
- Model allowlist (`model_allowlists/1_0_23.json`), Gemma 4 entries — [1_0_23.json](https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_23.json)
  - `Gemma-4-E2B-it`: modelId `litert-community/gemma-4-E2B-it-litert-lm`, file `gemma-4-E2B-it.litertlm`, `sizeInBytes 2588147712`, `minDeviceMemoryInGb 8`, `llmSupportImage true`, `llmSupportAudio true`, defaults `topK 64, topP 0.95, temperature 1.0, maxContextLength 32000, maxTokens 4000, accelerators "gpu,cpu", visionAccelerator "gpu"`, capabilities `llm_thinking`, `speculative_decoding`. The updated MTP file is pinned at commit `7fa1d78473894f7e736a21d920c3aa80f950c0db`.
  - `Gemma-4-E4B-it`: file `gemma-4-E4B-it.litertlm`, `sizeInBytes 3659530240`, `minDeviceMemoryInGb 12`, same defaults. The MTP file is pinned at commit `9695417f248178c63a9f318c6e0c56cb917cb837`.
- Image handling in Gallery: each `Bitmap` is encoded as TGA and wrapped in `Content.ImageBytes(ImageUtils.encodeTga(image))`. Images and audio go before the text, with the in-code comment "Add text after images/audio to ensure proper autoregressive token sequencing." The result is then sent with `conversation.sendMessageAsync(Contents.of(contents), MessageCallback{...}, extraContext, maxOutputToken = ...)`. — [LlmChatModelHelper.kt](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt)
- Engine config in Gallery: `EngineConfig(modelPath, backend = preferredBackend /*GPU default*/, visionBackend = if (supportImage) visionBackend else null, audioBackend = ... Backend.CPU(), maxNumTokens = maxTokens, cacheDir = ...)`. NPU is `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)`. `SamplerConfig` is set to null when running on NPU. Gallery also calls `ModelInfo.from(path)` to check `hasSpeculativeDecodingSupport()`. — same file
- Gallery caps output length through `maxOutputToken` and includes this comment: "Without the cap, a constrained reply whose schema allows unlimited whitespace can decode until the KV cache is full." — same file

### Inferences
- For a book app, the lowest-effort route is to copy the `worker/` and `data/Download*` classes and `LlmChatModelHelper.kt`, then replace the multi-task/allowlist machinery with one hardcoded model entry (repo + file + commit hash + size). Apache-2.0 allows this as long as the license and NOTICE are kept.
- The Hugging Face OAuth flow may not be needed for Gemma 4, because the litert-community Gemma 4 repos say they are not gated (see section 4). A plain HTTPS download from `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/<commit>/gemma-4-E2B-it.litertlm` would avoid it. This is an inference: the URL pattern is standard HF, but it was not tested here.

### Gaps
- I did not read `DownloadWorker.kt` in full, so I can't confirm resume/range-request behaviour or checksum verification.
- I did not find a Gallery Play Store version or date for the current main branch.

## 2. LiteRT-LM Android/Kotlin API (github.com/google-ai-edge/LiteRT-LM)

### Takeaway
Use `com.google.ai.edge.litertlm:litertlm-android:0.19.1` (latest on Google Maven, released 2026-11-05). That release added Gemma 4 vision+audio on the "Artisan" GPU backend. The Kotlin API is `Engine(EngineConfig(...))`, then `engine.createConversation(ConversationConfig(...))`, then `sendMessage` / `sendMessageAsync` (callback or Flow) with `Contents.of(Content.ImageBytes|ImageFile, Content.Text)`. It supports JSON-schema/regex constrained output through `ResponseFormat.json(schema)` plus `ConversationConfig(enableResponseFormat = true)`.

### Cited Findings
- Maven versions in `maven-metadata.xml`: `0.16.1, 0.17.0-alpha1, 0.17.0, 0.17.1, 0.18.0, 0.18.1, 0.19.0, 0.19.1`, lastUpdated `20261105192043`. — [Google Maven metadata](https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml)
- Release notes — [Releases](https://github.com/google-ai-edge/LiteRT-LM/releases)
  - v0.19.1 (05 Nov): multimodal (vision + audio) Gemma 4 on the Artisan GPU backend, plus bidirectional attention for vision on GPU. It also includes a breaking C API 1.0.0 change, which does not affect Kotlin users.
  - v0.19.0 (02 Nov): "Enables faster constrained decoding on GPUs, with full support for MTP-enabled decoding"; GPU sampling now matches CPU for the same seed.
  - v0.18.0 (06 Oct): ModelInfo, on-demand KV cache growth on NPU, and EmbeddingGemma 2.
- Repo is Apache-2.0. Kotlin is marked "Stable" for "Android apps & JVM". Platforms are Android, iOS, Web, Desktop, and IoT. — [LiteRT-LM README](https://github.com/google-ai-edge/LiteRT-LM)
- Docs use `implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")`. Pinning the version is safer. — [Kotlin getting_started.md](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md)
- **Kotlin version pitfall:** "`litertlm-android` 0.17.0, 0.17.1, 0.18.0 and 0.18.1 carry Kotlin metadata 2.4.0 ... the Kotlin code in a consuming module needs Kotlin 2.3 or newer; a 2.2.x compiler fails". 0.16.1 builds with Kotlin 2.2+. 0.19.x is not mentioned, but probably needs Kotlin 2.3+ too. — [getting_started.md](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md)
- Engine: `val engine = Engine(EngineConfig(modelPath = ".../model.litertlm", backend = Backend.GPU(), visionBackend = Backend.GPU(), audioBackend = Backend.CPU(), cacheDir = ...))`, then `engine.initialize()`. The docs warn that `initialize()` "can take a significant amount of time (e.g., up to 10 seconds)", so run it off the main thread. Setting `cacheDir` speeds up the second load. Close the engine when done. — [Get Started with LiteRT-LM on Android](https://developers.google.com/edge/litert-lm/android)
- Multimodal sample (verbatim from docs): `conversation.sendMessage(Contents.of(Content.ImageFile("/path/to/image"), Content.AudioBytes(audioBytes), Content.Text("Describe this image and audio.")))`. The Content variants are `Text`, `ImageBytes`, `ImageFile`, `AudioBytes`, and `AudioFile`. The docs still name Gemma 3n as the example multimodal model. — [getting_started.md](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md)
- `EngineConfig` (v0.19.1 tag) has `maxNumTokens: Int?` (input + output) and `maxNumImages: Int?`. — [Config.kt @ v0.19.1](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- Streaming: `sendMessageAsync(msg, callback)` with `onMessage/onDone/onError`, or the Flow form `sendMessageAsync(msg).catch{}.collect{}`. Flow is the recommended form. Errors surface as `LiteRtLmJniException` or `IllegalStateException`. — [Android docs](https://developers.google.com/edge/litert-lm/android)
- **Structured/JSON output (Kotlin):** `ResponseFormat.json(schema: String)` or `ResponseFormat.json(Map)` builds a JSON-schema constraint, and `ResponseFormat.regex(pattern)` builds a regex one. Pass it as `sendMessage(message, ..., maxOutputToken = ..., thinkingConfig = ..., responseFormat = ...)`. It throws "response_format cannot be used unless enableResponseFormat=True was passed to ConversationConfig". `ResponseFormat.kt` and `ConversationConfig.enableResponseFormat` are both present in the v0.19.1 tag. — [ResponseFormat.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/ResponseFormat.kt); [Conversation.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt); [Config.kt @ v0.19.1](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- The underlying C++ constrained decoding supports tool-call constraints or custom constraints (LLGuidance: regex, JSON schema, Lark CFG). You can use only one of the two per Conversation. — [constrained-decoding.md](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/cpp/constrained-decoding.md)
- **Visual token budget (Gemma 4 only):** `@OptIn(ExperimentalApi::class) ExperimentalFlags.visualTokenBudget = 70 | 140 | 280 | 560 | 1120`, where null means unlimited. If you set it before creating the Engine, it sets the max visual tokens per image. — [ExperimentalFlags.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt)
- MTP/speculative decoding: `ExperimentalFlags.enableSpeculativeDecoding` is now deprecated in favour of `EngineConfig.speculativeDecodingConfig` (main branch). The docs recommend MTP on GPU backends. — [ExperimentalFlags.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt); [Android docs](https://developers.google.com/edge/litert-lm/android)
- Thinking: `ThinkingConfig(enableThinking = true, thinkingTokenBudget = 1024)`. Use 0 to disable. The thought text is available in `response.channels["thought"]`. — [Android docs](https://developers.google.com/edge/litert-lm/android)
- Tool use: `ToolSet` + `@Tool`/`@ToolParam`, registered with `ConversationConfig(tools = listOf(tool(...)))`. — [Android docs](https://developers.google.com/edge/litert-lm/android)
- The GPU backend needs these entries inside `<application>`: `<uses-native-library android:name="libvndksupport.so" android:required="false"/>` and `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`. — [Android docs](https://developers.google.com/edge/litert-lm/android)

### Inferences
- The recommended book-cover call sequence: set `visualTokenBudget = 560` or `1120` before creating the Engine (cover text is small and the tech report shows OCR quality depends heavily on resolution, see section 4). Create `EngineConfig(backend = GPU, visionBackend = GPU, maxNumImages = 1)`. Create `ConversationConfig(enableResponseFormat = true, systemInstruction = ...)`. Then call `sendMessage(Contents.of(Content.ImageBytes(bytes), Content.Text(prompt)), responseFormat = ResponseFormat.json(schema), maxOutputToken = 400, thinkingConfig = ThinkingConfig(enableThinking = false))`. Schema: `{title, author, description, summary}`. Set `maxOutputToken`, because Gallery notes that a schema allowing unlimited whitespace can fill the KV cache.
- Some of these APIs exist only on `main`, which may be ahead of 0.19.1 (for example `speculativeDecodingConfig`). Verify each symbol against the pinned version's sources jar.

### Gaps
- I found no official Kotlin sample that pairs Gemma 4 image input with `ResponseFormat`. The combination is inferred from the API.
- The docs do not state whether `ImageBytes` expects PNG/JPEG or only specific encodings. Gallery uses TGA, which suggests raw or uncompressed input is safest.

## 3. MediaPipe LLM Inference vs LiteRT-LM; AICore / Gemini Nano (Gemma 4) as an alternative

### Takeaway
On Android, MediaPipe LLM Inference is deprecated or maintenance-only, and Google tells developers to migrate to LiteRT-LM Kotlin. AICore with Gemma 4 / Gemini Nano 4 through the ML Kit GenAI Prompt API (image + text) is Google's recommended production route, but in Nov 2026 it is still a Developer Preview on AICore-enabled flagships. Plan for LiteRT-LM as the general path, with AICore as an optional fast path where available. Gallery already implements both.

### Cited Findings
- The MediaPipe LLM Inference guide's banner says the Android/iOS implementations are deprecated (Web is not), elsewhere describes the API as "maintenance-only mode", and recommends "migrating your Android projects to LiteRT-LM Android (Kotlin) API". The wording differs between cached page versions. — [LLM Inference guide](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/index); [Android guide](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android)
- LiteRT-LM is described as "The production-ready orchestration layer to run LLMs with LiteRT" (page last updated 2026-10-06). — [developers.google.com/edge](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android)
- From the 2026-04-02 AICore post: Gemma 4 is "the foundation for the next generation of Gemini Nano", and code written for it "will automatically work on Gemini Nano 4-enabled devices that will be available later this year". E4B is "higher reasoning", E2B is "3x faster than the E4B". OCR ("chart understanding, visual data extraction, and handwriting recognition") is specifically said to be more accurate. Models are selected with `ModelConfig { releaseTrack = ModelReleaseTrack.PREVIEW; preference = ModelPreference.FULL /*E4B*/ }`. Preview models run on "the latest generation of specialist AI accelerators from Google, MediaTek, and Qualcomm"; "on other devices ... a CPU implementation that is not representative of final production performance." Tool calling, structured output, system prompts, and thinking were promised during the preview. — [Android Developers Blog](https://developer.android.com/blog/posts/announcing-gemma-4-in-the-ai-core-developer-preview)
- ML Kit Prompt API: `implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")` in the docs (Gallery uses `1.0.0-beta5`). It requires API 26+, is not supported on unlocked bootloaders, accepts up to 10 images per request, has an input limit under 4,000 tokens, and enforces a per-app inference quota through AICore. Status flow: `checkStatus()` returns `AVAILABLE/DOWNLOADABLE/DOWNLOADING/UNAVAILABLE`, then call `download()`. Image call: `generateContent(generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {...})`. Structured output is supported through a KSP-based schema library. — [Prompt API get started](https://developers.google.com/ml-kit/genai/prompt/android/get-started); [Gallery libs.versions.toml](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/gradle/libs.versions.toml)
- Both litert-community Gemma 4 model cards say that on supported Android devices, Android AICore (Gemini Nano) is recommended for production. — [E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm); [E4B card](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)
- Gallery ships an AICore backend (`runtime/aicore/AICoreModelHelper.kt`, `AICoreModelDownloader.kt`). — [Gallery tree](https://github.com/google-ai-edge/gallery)

### Inferences
- AICore does not download multi-GB files into your app and uses NPUs, but it covers only a minority of devices. Treat it as an optional fast path, with LiteRT-LM as the fallback.

### Gaps
- I could not find an official device list for Gemini Nano 4 / Gemma 4 on AICore, or confirmation that Nano 4 had reached general availability (non-preview) by Nov 2026. Older docs mention Pixel 10 for the Prompt API with nano-v2/v3.
- I did not find the AICore quota numbers.

## 4. Gemma 4 E2B vs E4B: sizes, RAM, OCR accuracy, license

### Takeaway
E2B is a 2.58 GB file. Gallery requires 8 GB of device RAM for it, and it uses about 676 MB CPU RAM on GPU on an S26 Ultra. E4B is 3.66 GB and needs 12 GB RAM in Gallery. E4B is clearly better at document/OCR tasks (OmniDocBench edit distance 0.181 vs 0.290). Gemma 4 is Apache-2.0, a first for Gemma, so redistribution has no Gemma Terms of Use restrictions, and the litert-community repos are not gated.

### Cited Findings
- E2B `.litertlm`: 2583 MB on disk (Gallery `sizeInBytes 2588147712`). S26 Ultra CPU: prefill 557 tok/s, decode 46.9 tok/s, TTFT 1.8 s, 1733 MB. S26 Ultra GPU: prefill 3,808 tok/s, decode 52.1 tok/s, TTFT 0.3 s, 676 MB CPU memory. MTP on GPU: 66.5 to 91.7 tok/s decode. Benchmarks used 1024 prefill / 256 decode tokens and 2048 context, text only. — [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- E4B `.litertlm`: 3654 MB (2.24 GB text decoder weights plus 0.67 GB embeddings, which are memory-mapped). S26 Ultra CPU: 195 / 17.7 tok/s, TTFT 5.3 s, 3283 MB. S26 Ultra GPU: 1,293 / 22.1 tok/s, TTFT 0.8 s, 710 MB CPU. MTP on GPU: 36.7 to 49.4 tok/s. The card says: "The repository is not gated, and the model is under the Apache 2.0 license." — [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)
- Both cards say models downloaded before 2026-05-05 must be re-downloaded to get speculative decoding. Vision and audio sub-models load on demand. Context is up to 32k (128k for the web file). — same cards
- Gallery minimum RAM: E2B 8 GB, E4B 12 GB. — [Gallery allowlist](https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_23.json)
- Gemma 4 tech report (arXiv 2607.02770, 2026-07-02), E2B vs E4B — [Gemma 4 Technical Report](https://arxiv.org/html/2607.02770v1)
  - Vision encoder is 150M parameters in both. W8A8 QAT halves encoder memory from 400 MB to 200 MB, with 44% lower on-device latency than Gemma 3n.
  - OmniDocBench 1.5 (lower is better) at 1120 visual tokens: E2B 0.290, E4B 0.181. At 280 tokens: E2B 0.496, E4B 0.307.
  - InfographicVQA at 1120 tokens: 63.9 / 70.0. At 280 tokens: 44.6 / 54.8.
  - MMMU Pro: 44.2 / 52.6.
  - These were measured in thinking mode.
- License: Gemma 4 was released 2026-04-02 under Apache 2.0, the first time Gemma has used that license instead of the Gemma Terms of Use. — [Google blog](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/); [The Decoder](https://the-decoder.com/googles-gemma-4-is-now-available-with-apache-2-0-licensing-for-the-first-time/)
- Conflict: a dev.to post (May 23) says the litert-community E2B repo is "gated, needs a read token". The current E4B card says it is not gated. The E2B card fetch did not state either way. — [dev.to samdude](https://dev.to/samdude/gemma-4-on-android-tricks-for-faster-on-device-inference-3kj5) vs [E4B card](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

### Inferences
- Default to E2B with a high visual token budget (560 or 1120) for mid-range phones, and offer E4B on 12 GB+ devices. Dropping from 1120 to 280 visual tokens badly hurts document OCR (E2B OmniDocBench goes from 0.290 to 0.496), so don't economise on visual tokens for covers.
- Apache-2.0 means you can mirror or self-host the `.litertlm` file (for example on your own CDN) with attribution. Check the HF repo's NOTICE.

### Gaps
- I found no OCR benchmark specific to book covers (stylised fonts, curved text) for Gemma 4.
- I found no non-flagship Android benchmark from Google. The S26 Ultra is the only Android device in the tables.

## 5. ML Kit Text Recognition + Gemma text-only vs Gemma vision directly

### Takeaway
I found no head-to-head benchmark for book covers. Each approach has clear tradeoffs. ML Kit Text Recognition v2 is small (about 4 MB bundled, or about 260 KB via Play Services), fast, and deterministic, but it returns unordered text blocks with no semantics. Gemma vision reads stylised cover typography and can tell title from author in one pass, but adds a 150M-parameter encoder pass and needs a high token budget. A hybrid works well: run OCR first, then give Gemma both the image and the OCR text, or use text-only Gemma when vision isn't available.

### Cited Findings
- ML Kit Text Recognition v2 (Latin): bundled `com.google.mlkit:text-recognition:16.0.1` (about 4 MB per script per architecture). Unbundled `com.google.android.gms:play-services-mlkit-text-recognition:19.0.2` (about 260 KB, model fetched by Play Services). Characters should be at least 16x16 px, and above 24x24 px gives no gain. — [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- Existing book apps (PukaPuka, ShelfShuffle) use ML Kit/Firebase text recognition plus the Google Books API to identify covers, with no LLM. PukaPuka's last commit was about 3 years ago. — [PukaPuka listing](https://awesome.ecosyste.ms/projects/github.com%2FDevansh-Maurya%2FPukaPuka); [ShelfShuffle](https://github.com/dannydabbles/ShelfShuffle)
- Prefill cost scales with input tokens. On mid-range CPU-only phones, E2B decodes at about 2 to 5 tok/s (author's claim, no device named). — [dev.to samdude](https://dev.to/samdude/gemma-4-on-android-tricks-for-faster-on-device-inference-3kj5)
- Google says Gemma 4 improves OCR-type tasks. — [AICore blog](https://developer.android.com/blog/posts/announcing-gemma-4-in-the-ai-core-developer-preview)

### Inferences
- An on-device LLM will hallucinate a "description" or "summary" of a book it can't see beyond the cover. The real blurb is on the back cover. A more reliable pipeline: OCR or vision to get title and author, then a lookup in Google Books or Open Library for the description, then Gemma (text-only, cheap) to write the summary paragraph from the retrieved description. This is a design inference and was not benchmarked.
- ML Kit is a good gate before Gemma: check that the cover has readable text, and use its output for prompt grounding ("OCR text: ...").

### Gaps
- I found no published accuracy or latency comparison of ML Kit + LLM versus a VLM on book covers.

## 6. Measured image+text latency on phones, pitfalls, and other open-source apps

### Takeaway
There are no reliable public numbers for end-to-end image+text latency with Gemma 4 on mid-range Android. Text-only flagship GPU numbers are good: TTFT 0.3 s and about 52 tok/s for E2B on an S26 Ultra. The only image-stage measurement I found (on a non-phone Qualcomm IoT board) shows the vision encoder taking 8 s on CPU and 20 s on NPU. Budget several seconds for the vision pass on non-flagship devices, and measure on your own target devices.

### Cited Findings
- On a Qualcomm QCS8275 board (not a phone), "the vision embedding stage alone is taking roughly around 8 seconds for --vision_backend=cpu and 20 seconds for --vision_backend=npu." The issue is still open. — [LiteRT-LM issue #2187](https://github.com/google-ai-edge/LiteRT-LM/issues/2187)
- v0.19.1 added Gemma 4 vision on the Artisan GPU backend with bidirectional attention. GPU is the intended vision backend, and Gallery's default `visionAccelerator` is "gpu". — [Releases](https://github.com/google-ai-edge/LiteRT-LM/releases); [Gallery allowlist](https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_23.json)
- Pitfalls reported by the community — [dev.to samdude](https://dev.to/samdude/gemma-4-on-android-tricks-for-faster-on-device-inference-3kj5)
  - GPU init can silently fall back to CPU, so log which backend actually loaded.
  - `Backend.NPU()` can SIGKILL or SIGSEGV on some devices.
  - MTP on CPU adds overhead.
  - Loading GGUF instead of `.litertlm` fails silently.
  - Large `maxTokens` with constrained JSON wastes KV cache.
  - Thinking adds 200 to 400 tokens of latency on CPU.
- Pitfalls from official sources:
  - Kotlin 2.3+ is required for litertlm 0.17+ ([getting_started.md](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md)).
  - The OpenCL `uses-native-library` manifest entries are required ([Android docs](https://developers.google.com/edge/litert-lm/android)).
  - `initialize()` can take up to about 10 s ([Android docs](https://developers.google.com/edge/litert-lm/android)).
  - Re-download models fetched before 2026-05-05 to get MTP ([E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)).
  - The NPU `.litertlm` files are SoC-specific; for example, `gemma-4-E2B-it_Qualcomm_QCS8275.litertlm` is 2.72 GB and only for that chip ([E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)).
- Other open-source or community projects:
  - **Gallery forks** (for example github.com/imesong/gallery, github.com/iml1s/gallery) — [imesong/gallery](https://github.com/imesong/gallery)
  - **NeonLib:** Android, EmbeddingGemma + LiteRT + ML Kit OCR for document retrieval — [Google AI forum](https://discuss.ai.google.dev/t/neonlib-on-device-android-document-retrieval-with-embeddinggemma-and-litert/197847)
  - **Iris:** offline visual assistant using Gemma 4 — [dev.to Iris](https://dev.to/juniormartinxo/iris-an-offline-visual-assistant-in-brazilian-portuguese-powered-by-gemma-4-2652)
  - **NeuralPocket:** Gemma 4 on Android and web — [dev.to NeuralPocket](https://dev.to/prema_ananda/neuralpocket-private-on-device-ai-with-gemma-4-android-web-4lo)
  - **Medium GDE article** on multimodal Gemma 4 E2B with LiteRT-LM + Qualcomm QNN — [Medium](https://medium.com/google-developer-experts/bringing-multimodal-gemma-4-e2b-to-the-edge-a-deep-dive-into-litert-lm-and-qualcomm-qnn-4e1e06f3030c)

### Inferences
- Gallery is by far the most complete and maintained reference to copy from. Community apps are mostly hackathon-grade.
- Ship a first-run download of about 2.6 GB, gated on Wi-Fi and free storage. Check `ActivityManager.MemoryInfo.totalMem` against 8 GB for E2B, using Gallery's thresholds, and show the OCR + Books-API flow to devices below that.

### Gaps
- I found no measured image+text latency for Gemma 4 on mid-range phones (for example Pixel 8a, Galaxy A-series, or Snapdragon 7-series). I didn't read the Iris, NeuralPocket, or Medium GDE articles in full, and they may contain anecdotal timings.
- I didn't find a GitHub project that uses Gemma 4 vision specifically for book covers.
