# On-device VLM / LLM options for a 6 GB Android phone (book-cover title+author JSON, short summary from supplied text)

Researched 2026-11-21. These notes build on `gemma_on_device.md` in the same folder and don't repeat it.

Read this before using any memory number below:
- **Memory caveat.** Android phones share one pool of RAM between the CPU and GPU. Many model cards report "CPU memory" on the GPU backend, and that figure leaves out GPU-driver allocations. Treat a small number like that as a lower bound, not the total footprint.
- **Provenance caveat.** litert-community benchmark tables are self-measured by the uploaders and are not vendor-verified ([Qwen3-0.6B card disclaimer](https://huggingface.co/litert-community/Qwen3-0.6B)). Cactus Android figures come from older docs versions (v1.8 and v1.9).

## 1. Can Gemma 4 E2B actually run on a 6 GB Android device with LiteRT-LM?

### Takeaway
Probably yes for single-shot use, but there is no direct evidence. I found no first-hand report of Gemma 4 E2B with image input on a 6 GB Android phone, either success or OOM. The closest evidence is mixed:
- A maintainer says E2B with vision "fits comfortably" on a 6 GB iPhone 15. iOS has different memory limits.
- Total GPU-backend footprints of LiteRT-LM models are much larger than the "CPU memory" headlines suggest.
- The vision encoder takes about 1 GB of GPU memory.
- Gallery's 8 GB minimum is a conservative guard. It is not proof that E2B fails on 6 GB.

Treat E2B on 6 GB as "may work, test on real devices, with fallback". Do not make it the default path.

### Cited Findings
- Gallery allowlist sets E2B at `minDeviceMemoryInGb 8`, with a 2,588,147,712 B file. — [Gallery allowlist 1_0_23.json](https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_23.json) (from prior notes)
- E2B on an S26 Ultra (text only, 2048 context): CPU backend 1733 MB; GPU backend "676 MB CPU memory". The card says main weights stay resident in memory and the embedding parameters are memory-mapped. — [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- Google blog, verbatim: "Run Gemma 4 E2B using <1.5GB memory on some devices", attributed to "LiteRT's support for 2-bit and 4-bit weights" and "memory-mapped per-layer embeddings". The blog names no devices. Its only quantified non-phone platforms are a Raspberry Pi 5 on CPU (133 prefill / 7.6 decode tok/s) and a Qualcomm Dragonwing IQ8 on NPU (3,700 / 31 tok/s). — [Google Developers Blog, "Bring state-of-the-art agentic skills to the edge with Gemma 4"](https://developers.googleblog.com/en/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/)
- Google blog (2026-05-19) gives a "physical memory footprint of just 607MB on Apple mobile CPUs" for the ~2.58 GB E2B model, using XNNPACK weight caching. Per-layer embeddings are kept out of memory, and the image and audio encoders load only when a task needs them. Its only Android number is from a flagship: S26 Ultra GPU at 52 tok/s. — [Blazing fast on-device GenAI with LiteRT-LM](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/)
- LiteRT-LM issue #1933 (iPhone 15, iOS):
  - E4B crashed on image prefill; E2B worked on the same code path.
  - Maintainer, 2026-10-29: the iPhone 15 has "~6 GB physical RAM, a per-process dirty memory limit of ~3 GB". For 6 GB iOS devices, use E2B for vision and audio, because it "is much smaller and fits comfortably".
  - Root cause was memory/address-space exhaustion combined with a silent mmap failure. Fixed in v0.18.0 and later, which now reports mmap errors at engine init.
  - — [LiteRT-LM #1933](https://github.com/google-ai-edge/LiteRT-LM/issues/1933)
- LiteRT-LM issue #2421 (Pixel 9a, Tensor G4 / Mali-G715):
  - E2B GPU decode died with `CL_INVALID_COMMAND_QUEUE` after 1–3 turns.
  - Removing the vision encoder freed "a ~1 GB GPU encoder" and pushed the crash from about turn 2 to about turn 4.
  - A 4096-token KV window could not be allocated on OpenCL and fell back to CPU; 3072 fit.
  - Disabling MTP led to OOM a few turns later.
  - The reporter says the bug no longer reproduces on v0.16.0 (OpenCL memory leak fix #2699, from v0.15.0). They did **not** re-test the vision encoder on GPU.
  - Closed 2026-10-18.
  - — [LiteRT-LM #2421](https://github.com/google-ai-edge/LiteRT-LM/issues/2421)
- LiteRT-LM issue #1848: E2B vision and audio SIGSEGV on CPU (Linux x86_64 with 4/8 GB cloud instances, Python SDK). — [LiteRT-LM #1848](https://github.com/google-ai-edge/LiteRT-LM/issues/1848) (search snippet only; not opened)
- An unverified secondary claim says E2B "runs on any Android device with 6 GB+ RAM at 52 tokens/sec on GPU". The 52 tok/s figure is the S26 Ultra flagship number, so this claim is not evidence about 6 GB phones. — [dev.to guide](https://dev.to/rahul_patwa_f99f19cd1519b/gemma-4-on-android-complete-developer-guide-to-on-device-ai-f5b)
- Smaller-quantized E2B variants: the official litert-community repo ships one mobile `.litertlm` of about 2.58 GB, plus SoC-specific NPU files. I found no official int2 or "<1.5 GB" E2B file. Several third-party mirrors exist with the same name (KailzTech, zyndly, i2so) and are presumably re-uploads; I did not inspect them. — [search results: KailzTech](https://huggingface.co/KailzTech/gemma-4-E2B-it-litert-lm), [zyndly](https://huggingface.co/zyndly/gemma-4-E2B-it-litert-lm), [i2so](https://huggingface.co/i2so/gemma-4-E2B-it-litert-lm)

### Inferences
- **Why Gallery sets 8 GB (inference, not stated by Google):**
  - The GPU-backend figures exclude driver memory. On SmolVLM2 the driver took an extra ~2.5 GB (see section 2).
  - The vision encoder adds about 1 GB of GPU memory (#2421).
  - The KV cache grows with context. Gallery defaults to `maxContextLength 32000`, `maxTokens 4000`.
  - On a 6 GB Android phone, Android plus the launcher and other apps usually leave only about 2–3 GB free before the low-memory killer acts. That figure is general knowledge and unsourced.
  - Together these make 8 GB a reasonable safety floor for a general chat app.
- **For a single-shot cover call**, the risk can be lowered. Settings to try:
  - Small KV (`maxNumTokens` around 1024–2048).
  - `maxNumImages = 1`.
  - `visualTokenBudget` 280–560.
  - Thinking off.
  - Create a fresh engine or conversation per call, then close it to free memory.
  - Use CPU or GPU after measuring. The CPU footprint of 1.7 GB (S26 Ultra, text only) may actually be safer than GPU plus driver.
- The <1.5 GB and 607 MB claims both depend on mmap. Mapped pages are reclaimable, but they still create memory pressure under load. These figures are not a 6 GB guarantee.

### Gaps
- I found no measured peak RSS, LMK kill, or latency for E2B with an image on any 6 GB Android device (for example the Pixel 6a, Pixel 7a 8 GB, Galaxy A3x/A5x 6 GB, or Snapdragon 6/7-series).
- I found no Reddit or blog first-hand 6 GB reports. Searches surfaced none.
- I found no official 2-bit E2B `.litertlm` file.
- Google does not state why Gallery uses 8 GB.

## 2. Vision alternatives that fit 6 GB (params, file size, runtime, license, OCR scores, Android numbers)

### Takeaway
The best-evidenced small VLMs for 6 GB are:
- **LFM2.5-VL-1.6B**, at about 1.3 GB int4 `.litertlm`. It has GGUF and ONNX builds and a Cactus SDK. It has the only 6 GB-phone measurement I found: Pixel 6a, about 1.5 GB RAM, 15 tok/s. Its license is the LFM Open License, not Apache.
- **LFM2.5-VL-450M**, at 0.4B params, OCRBench (v1) 684, and roughly 0.6–0.7 GB RAM on budget phones via Cactus.
- **Qwen3-VL-2B**, Apache-2.0, with official GGUF (Q4 about 1.5 GB). I could not retrieve its OCR scores.
- **SmolVLM2-2.2B**, Apache-2.0 on LiteRT-LM, about 1.5 GB. Its 384×384 / 81-token image input is a real limit for cover text.

I found no usable data on Gemma 3n, Moondream, Florence-2, PaliGemma 2, MiniCPM-V, or Phi-4-mini multimodal in this pass.

### Cited Findings
**LFM2.5-VL-1.6B (Liquid AI)**
- 1.6B params. Benchmarks against LFM2-VL-1.6B:

  | Benchmark | LFM2.5-VL-1.6B | LFM2-VL-1.6B |
  |---|---|---|
  | OCRBench **v2** | 41.44 | 35.11 |
  | InfoVQA val | 62.71 | 58.35 |
  | MMStar | 50.67 | 49.87 |

- Scores come from VLMEvalKit. DocVQA and TextVQA are not reported.
- Runtimes: Transformers, llama.cpp GGUF, ONNX Runtime, MLX, vLLM. License: LFM Open License v1.0 ("lfm1.0"). — [LiquidAI/LFM2.5-VL-1.6B](https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B)
- litert-community `.litertlm` files:
  - `LFM2.5-VL-1.6B_int4.litertlm`: 1.30 GB (text int4 block-32, vision int8).
  - int8 file: 1.81 GB.
- Pixel 8a (Tensor G3, litert_lm v0.16.0, 296-token prompt):

  | Variant | Backend | Prefill (tok/s) | Decode (tok/s) | TTFT |
  |---|---|---|---|---|
  | int4 | GPU | 201 | 22.1 | 1.5 s |
  | int4 | CPU | 37 | 13.6 | 8.1 s |
  | int8 | GPU | 403 | 15.9 | 0.80 s |

- Galaxy S26 GPU peak VmHWM (text only): int4 792 MiB, int8 721 MiB.
- Vision-encode time is not reported.
- With litert-lm 0.19.0, the vision encoder on GPU failed at conversation creation on macOS for the `_int4_fixB` and `_int8` files.
- iOS Metal fails at engine creation.
- — [litert-community/LFM2.5-VL-1.6B](https://huggingface.co/litert-community/LFM2.5-VL-1.6B)
- Cactus docs v1.9 Android table, LFM2.5-VL-1.6B-INT4 at 256 px:
  - Galaxy S25 Ultra: 34 tok/s, 2 GB RAM.
  - **Pixel 6a (6 GB phone): 15 tok/s, 1.5 GB RAM.**
  - Galaxy A17 5G (Exynos, RAM variant unknown): 11 tok/s, 727 MB RAM.
  - No latency is given ("no NPU support yet").
  - — [Cactus docs v1.9](https://docs.cactuscompute.com/v1.9/)
- Liquid's docs include an Android (Jetpack Compose + Coil) example with LFM2-VL-1.6B. LFM2-VL is marked deprecated in favour of LFM2.5-VL. — [Liquid vision-models docs](https://docs.liquid.ai/lfm/models/vision-models) (search snippet)

**LFM2.5-VL-450M (Liquid AI)**
- 0.4B params: an LFM2.5-350M backbone plus an 86M SigLIP2 NaFlex encoder. Benchmarks:

  | Benchmark | LFM2.5-VL-450M | LFM2-VL-450M | SmolVLM2-500M |
  |---|---|---|---|
  | OCRBench **v1** | 684 | 657 | 609 |
  | InfoVQA val | 43.02 | 44.56 | 24.64 |
  | MMStar | 43.00 | 40.87 | 38.20 |

- The card itself says the model is "not well-suited for knowledge-intensive tasks or fine-grained OCR".
- Builds: GGUF, ONNX, MLX, LEAP. License: LFM1.0. — [LiquidAI/LFM2.5-VL-450M](https://huggingface.co/LiquidAI/LFM2.5-VL-450M)
- Cactus docs v1.8 (older), LFM2-VL-450M (previous generation):
  - Galaxy A17 5G: 4.1 s latency, 20 tok/s, 619 MB.
  - CMF Phone 2 Pro: 2.4 s, 22 tok/s, 632 MB.
  - S25 Ultra: 2.6 s, 34 tok/s, 2 GB.
  - — [Cactus docs v1.8](https://docs.cactuscompute.com/v1.8/) (via search snippet)
- The Cactus README reports LFM2.5-VL-450M image encoding in 43 ms, measured on Macs only. — [cactus-compute/cactus](https://github.com/cactus-compute/cactus) (search snippet)

**Qwen3-VL-2B-Instruct (Alibaba Qwen)**
- 2B params, Apache-2.0. About 103 community quantizations are linked. — [Qwen/Qwen3-VL-2B-Instruct](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct)
- Official GGUF repo: LLM in F16, Q8_0, and Q4_K_M, plus a separate mmproj vision encoder in F16 and Q8_0. — [Qwen/Qwen3-VL-2B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF)
- A community conversion lists FP16 at 4.6 GB, 8-bit at 2.2 GB, and 4-bit at 1.5 GB. — [search result, LM Studio / community](https://www.lmstudio.ai/qwen/qwen3-vl-2b)
- The 2B model's OCRBench and DocVQA scores are in the Qwen3-VL tech report (arXiv 2511.21631), but I could not extract the table (see Gaps).
- A third-party blog gives 4B/8B scores only (8B Instruct: OCRBench 89.6%, DocVQA 96.1%; unverified). — [codersera](https://codersera.com/blog/qwen3-vl-4b-vs-qwen3-vl-8b-benchmarks-vram-guide/)

**SmolVLM2 (Hugging Face)**
- litert-community `SmolVLM2-2.2B.litertlm`: 1,511,391,232 B, int8 vision and int4 decoder. Apache-2.0.
- Fixed **384×384 input producing 81 image tokens**. The GPU graph was updated 2026-11-15.
- Galaxy S26 (v0.19.0, 1024/256 tokens):
  - GPU: 483 prefill / 36.1 decode tok/s, TTFT 2.15 s, **peak 0.94 GB plus 2.54–2.55 GB GPU driver**.
  - CPU: 88–177 prefill / about 11 decode tok/s, **2.41 GB**.
- Pixel 11 Pro GPU: 12.8–16.4 decode tok/s.
- Benchmarks exclude the vision encoder.
- A SmolVLM2-500M sibling exists in litert-community.
- — [litert-community/SmolVLM2-2.2B](https://huggingface.co/litert-community/SmolVLM2-2.2B)
- A third-party Vulkan (VKNN) build of SmolVLM2-2.2B on a flagship GPU: vision 0.98 s, TTFT 0.85 s, 6–7.5 tok/s (fp16 file, 4.5 GB; int4 file 1.35 GB). — [katolikov/SmolVLM2-2.2B-vknn](https://huggingface.co/katolikov/SmolVLM2-2.2B-vknn)

**Gemma 4 E2B (for comparison)**
- 2.58 GB file. OmniDocBench edit distance 0.290 at 1120 visual tokens and 0.496 at 280. Apache-2.0. — see `gemma_on_device.md`, citing the [Gemma 4 tech report](https://arxiv.org/html/2607.02770v1)

### Inferences
- **Memory caveat applied to these models.** The SmolVLM2 GPU row shows a 0.94 GB process plus 2.5 GB driver. The Qwen3-0.6B GPU rows (section 3) show 2.9–4.3 GB peak versus about 2.7 GB on CPU. So the GPU backend can cost *more* total RAM than CPU. On 6 GB phones, measure both and expect CPU or Cactus to be the safer choice.
- **SmolVLM2-2.2B on covers.** Its 81-token 384×384 input is probably too coarse for small author text. Gemma 4 data shows OCR quality collapsing at low visual-token budgets. This is an inference with no cover benchmark.
- **Comparing OCR scores.** OCRBench v1 (0–1000) and v2 (percentage) are different benchmarks, so don't rank models across them. Among the 450M–500M class on v1: LFM2.5-VL-450M (684) > LFM2-VL-450M (657) > SmolVLM2-500M (609).
- **Licensing.** The LFM Open License v1.0 has commercial conditions I did not read. Review it before shipping. Gemma 4, Qwen3-VL, and SmolVLM2 are Apache-2.0.

### Gaps
- Models with no data from this pass (I did not research them, so these are not negative findings):
  - Gemma 3n E2B: still in LiteRT-LM docs as the multimodal example, but current support status was not checked.
  - Moondream 2/3, Florence-2, PaliGemma 2, MiniCPM-V small, Phi-4-mini multimodal.
  - Qwen2.5-VL-3B, and any newer Qwen VL small models (e.g. Qwen3.5-VL) released in 2026.
- Qwen3-VL-2B OCRBench, DocVQA, and TextVQA numbers. The tech-report PDF was saved but could not be parsed (no pdftoppm, and the HTML tables were not extracted).
- I found no measured vision-encode time on any 6 GB Android phone for any model.
- I found no book-cover-specific accuracy comparison.

## 3. Text-only small models for the summary step on 6 GB

### Takeaway
Writing a summary paragraph from a supplied description is easy for 0.6B–1.6B models. Gemma 3 1B int4 (529 MB, about 1.0–1.2 GB RAM, Gemma license, gated) and Qwen3-0.6B int4 (328 MiB, Apache-2.0) both have LiteRT-LM files with measured Android numbers. Gemma 4 E2B text-only gives better quality but carries the memory risk from section 1. LFM2.5-1.2B via Cactus has a Pixel 6a figure of 1 GB RAM and 15 tok/s.

### Cited Findings
**Gemma 3 1B IT int4**
- `gemma3-1b-it-int4.litertlm` and `.task`: 529 MB.
- S24 Ultra LiteRT-LM:
  - CPU: 379 prefill / 55 decode tok/s, 1,009 MB CPU RAM.
  - GPU: 2531 / 49 tok/s, 1,205 MB.
- MediaPipe CPU: 322 / 47 tok/s, TTFT 3.1 s, 1,138 MB.
- S25 Ultra NPU file: 5836 / 85 tok/s, 626 MB.
- **Gemma license, gated** (you must accept terms on Hugging Face).
- — [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)

**Qwen3-0.6B**
- `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`: 328 MiB, 4096 context. Apache-2.0.
- Throughput (prefill / decode, tok/s):

  | Device | GPU | CPU |
  |---|---|---|
  | Pixel 8a | 723 / 43.3 | 98 / 13.0 |
  | Galaxy S26 | 3,442 / 81.4 | 231 / 21.9 |

- Peak RSS with a 1,355-token prompt: **1,924–1,929 MiB on Pixel 8a**, and 2,034–2,123 MiB on S26.
- The int8 file on the GPU backend peaked at a 2,940 MB footprint (Samsung SM-S937U1) and **4,283 MB (TECNO LJ9)**.
- — [litert-community/Qwen3-0.6B](https://huggingface.co/litert-community/Qwen3-0.6B)

**LFM2.5-1.2B-INT4 (Cactus v1.9 Android table)**
- Pixel 6a: 70 prefill / 15 decode tok/s, 1 GB RAM.
- Galaxy A17 5G: 32 / 10 tok/s, 727 MB.
- S25 Ultra: 255 / 37 tok/s, 1.5 GB.
- — [Cactus docs v1.9](https://docs.cactuscompute.com/v1.9/)
- The Cactus docs list Gemma 3 1B and Qwen3 0.6B/1.7B as supported, but give no performance rows for them. — same

**Gemma 4 E2B text-only**
- S26 Ultra: CPU 1733 MB / 46.9 tok/s; GPU 676 MB CPU-side / 52.1 tok/s. — [E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)

### Inferences
- Qwen3-0.6B's peak RSS (about 1.9 GB on Pixel 8a) is unexpectedly high for a 328 MiB file. LiteRT-LM runtime overhead (XNNPACK caches, KV cache, activations) dominates small models. On a 6 GB phone, the practical difference between a 0.6B and a 1B model is smaller than file size suggests. Measure real RSS.
- For a 1-paragraph summary, roughly 100–150 output tokens at 10–15 tok/s on mid-range CPU means about 10 s plus prefill. Users will accept that for an optional "enhance" action.
- Licensing preference: Qwen3 is Apache-2.0 and ungated, which is simpler than Gemma 3 1B (Gemma license, gated). Gemma 4 E2B is also Apache-2.0.

### Gaps
- I found no litert-community numbers for Qwen3-1.7B, Llama 3.2 1B/3B, or SmolLM3 in this pass, and no summary-quality comparison among these small models.
- I found no measurement on a 6 GB LiteRT-LM device for text models. The Pixel 8a is believed to be an 8 GB phone; verify that before using it as a proxy.

## 4. Recommended split, and Kotlin libraries/apps for running models

### Takeaway (ranked recommendation for a 6 GB phone)
1. **Default path: ML Kit Text Recognition v2 plus Open Library lookup, with a tiny text LLM for the summary.**
   - Use Qwen3-0.6B int4 (328 MiB, Apache-2.0) or Gemma 3 1B int4 (529 MB, about 1.0–1.2 GB RAM, gated) via LiteRT-LM, or LFM2.5-1.2B via Cactus (1 GB RAM / 15 tok/s on Pixel 6a).
   - This has the lowest memory risk, and real descriptions come from Open Library instead of model hallucination.
   - Use a heuristic or the LLM to pick title and author from the OCR lines, with JSON constrained output.
2. **Optional enhancement: a small VLM for title/author when OCR + lookup fails.**
   - **LFM2.5-VL-1.6B int4** is the best-evidenced choice: 1.30 GB `.litertlm` or GGUF, about 1.5 GB RAM / 15 tok/s on a 6 GB Pixel 6a via Cactus. Its license needs review.
   - **Qwen3-VL-2B Q4 GGUF** (about 1.5 GB plus mmproj, Apache-2.0) is the alternative, via llama.cpp.
   - LFM2.5-VL-450M is the ultra-light fallback (about 0.6–0.7 GB). Its card warns against fine-grained OCR.
3. **Gemma 4 E2B (vision): enable only on devices with 8 GB or more RAM**, matching Gallery.
   - On 6 GB devices, offer it at most as an opt-in experiment with small KV and a single image, plus OOM fallback. There are no 6 GB Android reports either way.
   - Its main advantages are a single model for both tasks, Apache-2.0, and the strongest OCR evidence (OmniDocBench).

### Cited Findings
**Kotlin libraries and apps**
- **SmolChat-Android**:
  - Apache-2.0, about 915 stars, 204 commits.
  - Bundles llama.cpp as a git submodule and calls it through JNI (`smollm` module, NDK).
  - Neither the README nor the search results mention vision or mmproj support.
  - The `smollm` module is not stated to be on Maven.
  - — [shubham0204/SmolChat-Android](https://github.com/shubham0204/SmolChat-Android)
- **kotlinllamacpp**: a Kotlin Android binding whose changelog lists "full support for vision models (e.g., LLaVA) using mmproj files". v0.4.0 is synced with the cui-llama.rn fork. — [ljcamargo/kotlinllamacpp](https://github.com/ljcamargo/kotlinllamacpp) (search snippet; activity and license not verified)
- **llmedge** (Kotlin, llama.cpp): its FAQ says full vision support is pending llama.cpp multimodal integration on Android. — [llmedge FAQ](https://aatricks.github.io/llmedge/faq.html) (search snippet)
- **Llamatik**: Kotlin Multiplatform llama.cpp wrapper; vision support not shown. — [ferranpons/Llamatik](https://github.com/ferranpons/Llamatik) (search snippet)
- **llama.cpp mtmd**: loads two GGUFs, the LLM and an mmproj vision encoder. Arm's learning path shows an Android VLM app built this way. — [Arm learning path](https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/ai-portal-mobile-vision-language/4-understand-app/)
- **llama.rn**: a Medium/GDE post runs SmolVLM2 and Gemma on Android with it and notes that vision needs `ctx_shift: false` plus an mmproj. No RAM or latency figures are given. — [farmaker47 on Medium](https://farmaker47.medium.com/run-gemma-and-vlms-on-mobile-with-llama-cpp-dbb6e1b19a93)
- **Cactus**:
  - Has a Kotlin Multiplatform SDK (`cactus-compute/cactus-kotlin`).
  - Android docs cover vision input for LFM2-VL and LFM2.5-VL through an images array on messages.
  - Android NPU/latency work was on the roadmap for Mar–Apr 2026 (as of v1.9 docs).
  - — [Cactus docs v1.9](https://docs.cactuscompute.com/v1.9/); [Cactus Android docs v1.12](https://docs.cactuscompute.com/v1.12/android) (search snippet)
- **Google AI Edge Gallery + LiteRT-LM**: Apache-2.0. This is the most complete Kotlin reference for `.litertlm` models (Gemma 4, LFM2.5-VL, SmolVLM2, Qwen3). — see `gemma_on_device.md`
- **Gemini Nano / AICore**: Google recommends Android AICore (Gemini Nano) for production on supported devices. — [E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)

### Inferences
- **Keep one runtime.** LiteRT-LM can run every model in the ranking except Qwen3-VL-2B: Qwen3-0.6B and Gemma 3 1B text, LFM2.5-VL-1.6B and SmolVLM2 vision, and Gemma 4 E2B on 8 GB+. That avoids shipping both llama.cpp and LiteRT. LFM2.5-VL's vision-on-GPU issue in 0.19.0 suggests running vision on CPU for that model.
- **Add llama.cpp (via kotlinllamacpp or a forked SmolChat `smollm` module with mtmd) only if you pick Qwen3-VL-2B.** No litert-community Qwen3-VL file was found.
- **Gate the model tier at runtime on `ActivityManager.MemoryInfo.totalMem` and `availMem`**:
  - Under 6 GB: OCR + lookup only.
  - 6 GB: plus the text LLM and the optional small VLM.
  - 8 GB and up: Gemma 4 E2B.
  - Load one model at a time and close it after use.

### Gaps
- I found no head-to-head on book covers comparing ML Kit + LLM against a small VLM.
- License and maintenance activity of kotlinllamacpp, llmedge, and Llamatik were not verified.
- Cactus's license was not verified. The v1.9 docs page did not state it.
- I did not check MLC LLM, ExecuTorch, ONNX Runtime GenAI, or NexaSDK Android VLM support in this pass.
- I found no reliable statement of the app-usable RAM budget, or LMK thresholds, on 6 GB Android devices.
