# Reusable Open-Source Building Blocks for a Kotlin + Jetpack Compose Book Library App

Research date: 2026-11-21. Version numbers were read live from Google Maven (dl.google.com/android/maven2) and Maven Central (repo1.maven.org) `maven-metadata.xml`, and from the GitHub API, on that date.

Quick version table (verified 2026-11-21):

| Block | Artifact | Latest stable | License | Maintenance |
|---|---|---|---|---|
| CameraX core / Compose viewfinder | `androidx.camera:camera-core`, `androidx.camera:camera-compose` | 1.7.0 (released 2026-11-18) | Apache-2.0 (AndroidX) | Active |
| CameraX to ML Kit bridge | `androidx.camera:camera-mlkit-vision` (MlKitAnalyzer) | 1.7.0 | Apache-2.0 | Active |
| ML Kit Barcode, bundled | `com.google.mlkit:barcode-scanning` | 17.3.0 | Proprietary Google SDK (free; ML Kit terms) | Active |
| ML Kit Barcode, unbundled | `com.google.android.gms:play-services-mlkit-barcode-scanning` | 18.3.1 | Google Play services terms | Active |
| Google Code Scanner | `com.google.android.gms:play-services-code-scanner` | 16.1.0 | Google Play services terms | Active |
| ML Kit Text Recognition v2 (Latin) | `com.google.mlkit:text-recognition` / `com.google.android.gms:play-services-mlkit-text-recognition` | 16.0.1 / 19.0.2 | ML Kit terms | Active |
| Photo Picker contracts | `androidx.activity:activity-compose` | 1.13.0 (1.14.0-alpha03 in preview) | Apache-2.0 | Active |
| Background queue | `androidx.work:work-runtime-ktx` | 2.12.0 | Apache-2.0 | Active |
| RSS/Atom parsing (KMP) | `com.prof18.rssparser:rssparser` | 6.2.0 (2026-11-08) | Apache-2.0 | Active (last push 2026-11-20, ~624 stars) |
| RSS/Atom parsing (JVM) | `com.rometools:rome` | 2.1.0 (2023-03-12) | Apache-2.0 | Stale (last push 2024-08-08) |
| Charts | `com.patrykandpatrick.vico:compose-m3` | 3.3.1 stable (GitHub release 2026-08-28); 3.4.0-next.1 preview (2026-10-28) | Apache-2.0 | Very active (last push 2026-11-21, ~3.2k stars) |
| Charts (alt, has pie/donut) | `io.github.ehsannarmani:compose-charts` | 1.0.0 (2026-07-21) | Apache-2.0 | Active (last push 2026-07-30, ~925 stars) |
| Image loading | `io.coil-kt.coil3:coil-compose` | 3.6.3 (2026-09-18) | Apache-2.0 | Very active (~12k stars) |

Table sources: versions come from `maven-metadata.xml` at `https://dl.google.com/android/maven2/<group path>/<artifact>/` (AndroidX, ML Kit, Play services) and `https://repo1.maven.org/maven2/<group path>/<artifact>/` (Coil, Vico, RSS-Parser, Rome, ComposeCharts). License, star counts and last-push dates come from `https://api.github.com/repos/<owner>/<repo>`. The AndroidX "Apache-2.0" and ML Kit/Play services license cells, and the "Active" status for Google artifacts, were not checked individually. They rest on general knowledge plus recent release dates, so treat them as unverified.

## Camera + barcode: which option is simplest for ISBN EAN-13?

### Takeaway
For a "scan one book's ISBN" flow, the Google Code Scanner (`play-services-code-scanner:16.1.0`) is the simplest. It needs no camera permission and no camera UI code: you call `startScan()` with `Barcode.FORMAT_EAN_13` and get a string back. Use CameraX 1.7.0 `camera-compose` with `CameraXViewfinder`, plus ML Kit Barcode through `MlKitAnalyzer`, only if you need a custom in-app scanner UI, such as continuous scanning of a whole shelf or a scan overlay.

### Cited Findings
- Code Scanner dependency is `com.google.android.gms:play-services-code-scanner:16.1.0`. Scanning happens inside Google Play services, so the app works "without requiring your app to request camera permission" and only receives the result. — [ML Kit Code Scanner docs](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)
- Code Scanner usage: `GmsBarcodeScannerOptions.Builder().setBarcodeFormats(...)`, then `GmsBarcodeScanning.getClient(context, options).startScan()`, with success, canceled and failure listeners. `barcode.rawValue` holds the ISBN string. — [ML Kit Code Scanner docs](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)
- Code Scanner auto-zoom is available from 16.1.0 (`enableAutoZoom()`, off by default). It requires API 23+ and Play services. The module is unbundled and downloads on first use unless you add manifest meta-data `com.google.mlkit.vision.DEPENDENCIES = barcode_ui` or use ModuleInstallClient. It has no custom UI. — [ML Kit Code Scanner docs](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)
- ML Kit Barcode, bundled (`com.google.mlkit:barcode-scanning:17.3.0`): about 2.4 MB, model statically linked and available immediately. Unbundled (`play-services-mlkit-barcode-scanning:18.3.1`): about 200 KB, model downloaded via Play services. Scan calls made before the download finishes return no results. — [ML Kit Barcode Android docs](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- Format constant is `Barcode.FORMAT_EAN_13`, passed to `BarcodeScannerOptions.Builder().setBarcodeFormats()`. For EAN-13 the barcode should be at least 190 px wide. 1280x720 or 1920x1080 input is recommended. Auto zoom (`setZoomSuggestionOptions` with a `ZoomCallback`) was added in bundled 17.2.0 and unbundled 18.3.0. — [ML Kit Barcode Android docs](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- CameraX `camera-compose` is stable at 1.7.0, released 2026-11-18. `CameraXViewfinder(surfaceRequest = ..., modifier = ...)` was introduced in 1.5.0-alpha01. 1.7.0 adds built-in pinch-to-zoom (`isPinchToZoomEnabled`), tap-to-focus (`isTapToFocusEnabled`), `onStreamStateChanged` and screen flash. `camera-mlkit-vision` (MlKitAnalyzer) is also stable at 1.7.0. — [AndroidX Camera release notes](https://developer.android.com/jetpack/androidx/releases/camera)
- Versions checked live: camera-core, camera-compose and camera-mlkit-vision are all 1.7.0. — [Google Maven metadata](https://dl.google.com/android/maven2/androidx/camera/camera-compose/maven-metadata.xml)
- Official samples: `googlesamples/mlkit` (Apache-2.0, Kotlin, ~4.3k stars, pushed 2026-10-26) and `android/camera-samples` (~5.5k stars, pushed 2026-11-03). — [googlesamples/mlkit](https://github.com/googlesamples/mlkit); [android/camera-samples](https://github.com/android/camera-samples)

### Inferences
- Compose usage for the simplest path is a button whose `onClick` calls `scanner.startScan()`, with no permission prompt or `Preview` code. Wrap the Task in `suspendCancellableCoroutine` or use `kotlinx-coroutines-play-services` `.await()`.
- ISBN validation: accept only EAN-13 values that start with 978 or 979 (the Bookland prefixes) and pass the checksum. This rejects store price stickers. This is standard ISBN knowledge, not taken from a cited source.
- Custom path: `LifecycleCameraController` + `MlKitAnalyzer(listOf(barcodeScanner), COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result -> ... }`, set via `controller.setImageAnalysisAnalyzer`. Render it with `CameraXViewfinder` or `PreviewView` in `AndroidView`. This combines the release notes with common CameraX usage. Check it against the camera-samples repo.
- Choose bundled barcode-scanning (2.4 MB) for the custom scanner if first-run reliability matters more than APK size. The unbundled model can silently return nothing until it has downloaded.

### Gaps
- I did not find an official doc that names "the simplest" option. The recommendation is my inference from the API surfaces.
- The exact MlKitAnalyzer Compose sample code was not fetched from the primary source.

## Batch import from gallery (30-50 photos), WorkManager queue, still-image barcode detection

### Takeaway
`PickMultipleVisualMedia(maxItems)` handles 30-50 photos. The platform caps the selection at `MediaStore.getPickImagesMaxLimit()`, so read that value instead of hardcoding it. Call `takePersistableUriPermission` on each URI. Enqueue a WorkManager 2.12.0 job that runs `InputImage.fromFilePath(context, uri)` through an ML Kit barcode scanner restricted to EAN-13, then falls back to OCR.

### Cited Findings
- Compose: `rememberLauncherForActivityResult(PickMultipleVisualMedia(5)) { uris -> ... }`, then `launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))`. This needs androidx.activity 1.7.0+. — [Photo picker docs](https://developer.android.com/training/data-storage/shared/photopicker)
- "The platform limits the maximum number of files that you can ask the user to select... call `getPickImagesMaxLimit()`." The limit is ignored when the picker falls back to `ACTION_OPEN_DOCUMENT`. — [Photo picker docs](https://developer.android.com/training/data-storage/shared/photopicker)
- The picker is available natively on Android 11+ with Google System Updates. Android 4.4-10 can get a backport through Play services if you add a `ModuleDependencies` service with `photopicker_activity:0:required` meta-data to the manifest. — [Photo picker docs](https://developer.android.com/training/data-storage/shared/photopicker)
- URI access lasts only until device restart or app stop by default. For long-running background work, call `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`. There is a cap of 5,000 grants per app, after which the oldest grant is dropped. — [Photo picker docs](https://developer.android.com/training/data-storage/shared/photopicker)
- Still images: `InputImage.fromFilePath(context, uri)` (throws IOException) feeds the same `BarcodeScanner.process()` API. — [ML Kit Barcode Android docs](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- Latest stable versions: activity-compose 1.13.0; work-runtime-ktx 2.12.0. — [Google Maven metadata (activity)](https://dl.google.com/android/maven2/androidx/activity/activity-compose/maven-metadata.xml); [Google Maven metadata (work)](https://dl.google.com/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml)

### Inferences
- Pattern: persist the picked URIs to a Room "import_queue" table with a status column. Enqueue one `CoroutineWorker`, or one unique work chain. It processes items sequentially and throttles metadata lookups to Open Library's 1 to 3 requests per second (see next section). Compose observes progress through `WorkManager.getWorkInfosForUniqueWorkFlow(...)`. Downscale large photos before ML Kit; 1920 px on the long side is ample per ML Kit's size guidance.
- Use the bundled barcode model in the worker. With the unbundled model, a worker running before the model download completes would get empty results.

### Gaps
- The typical numeric value of `getPickImagesMaxLimit()` (often reported as 100) was not confirmed from a primary source. Check it on-device.

## Book metadata APIs: Open Library vs Google Books vs ISBNdb; non-English coverage; Kotlin clients

### Takeaway
Use Open Library as the primary free source: no key, ISBN lookup via `/isbn/{isbn}.json` or `/api/books?bibkeys=ISBN:...&jscmd=data`, covers via covers.openlibrary.org. Send an identifying User-Agent, which raises the limit from 1 to 3 requests per second. Use Google Books as the fallback. In practice it needs an API key: a keyless call during this research returned HTTP 429, because unauthenticated requests share an exhausted quota. There is no mature Kotlin client for either API, so write a small Retrofit/Ktor interface.

### Cited Findings
- Open Library rate limits: "1 request per second" by default, "3 requests per second" for identified requests (User-Agent header with app name plus contact email or phone). The page asks developers not to bulk-download metadata via the API and to use the monthly dumps instead. — [Open Library Developers API](https://openlibrary.org/developers/api)
- Open Library exposes the Books (Work/Edition), Search, Covers and Authors APIs. — [Open Library Developers API](https://openlibrary.org/developers/api)
- Covers API: `https://covers.openlibrary.org/b/isbn/{ISBN}-{S|M|L}.jpg`. Lookups by ISBN, OCLC or LCCN are limited to "100 requests/IP ... every 5 minutes", returning 403 beyond that. Lookups by CoverID or OLID are not limited. `?default=false` returns 404 instead of a blank image. — [Open Library Covers API](https://openlibrary.org/dev/docs/api/covers)
- Search API: `search.json` supports `sort=new|old|random|key`, `fields=` (avoid `*`), `lang=` (prefers but doesn't exclude), and `language:fre`-style filters (exclude). Pagination uses `limit` with `offset`/`page`. — [Open Library Search API](https://openlibrary.org/dev/docs/api/search)
- Live test, 2026-11-21: Open Library resolved Italian (9788845292613, "Il signore degli anelli"), German (9783608939811, "Die Gefährten"), French (9782070612888) and Spanish (9788420674209) ISBNs via `/api/books?bibkeys=...&jscmd=data`. A Japanese test ISBN (9784003220115) returned 404. That ISBN passes the checksum, but I have not confirmed it as an assigned ISBN. — direct test against [openlibrary.org/api/books](https://openlibrary.org/api/books?bibkeys=ISBN:9788845292613&format=json&jscmd=data)
- Google Books: "Requests to the Books API for public data must be accompanied by an identifier, which can be an API key or an access token". The `isbn:` keyword works in `q`, along with `langRestrict` (ISO-639-1) and `orderBy=newest`. — [Google Books API "Using the API"](https://developers.google.com/books/docs/v1/using)
- Live test, 2026-11-21: keyless `GET https://www.googleapis.com/books/v1/volumes?q=isbn:...` returned 429 "Quota exceeded for quota metric 'Queries' and limit 'Queries per day'" on a shared Google project. — direct test against [googleapis.com/books/v1/volumes](https://www.googleapis.com/books/v1/volumes?q=isbn:9780261103573)
- Google Books quota: older sources cite a default of 1,000 requests/day, while more recent user reports cite 20,000,000/day. The sources conflict, and Google's docs don't publish a number. — [Google Developer forum quota thread](https://discuss.google.dev/t/requesting-higher-quota-for-google-books-api-bookquest-app/286093); [google-api-java-client group](https://groups.google.com/g/google-api-java-client/c/_TFuPpAKSew)
- Kotlin clients: only tiny ones exist, e.g. `untitled-li/openlibrary-kotlin` (~2 stars). Example apps that use both APIs: `armutyus/ninova` ("Bookshelf app that uses Google Books API and OpenLibrary API", ~13 stars). — [GitHub search](https://github.com/search?q=openlibrary+language%3AKotlin&type=repositories)

### Inferences
- Recommended lookup chain: Open Library ISBN, then Google Books with a key and `q=isbn:`, then OCR text search on Open Library `search.json?q=<title words>`. Cache results and cover URLs in Room so each ISBN is fetched once. For covers, request by cover ID (`/b/id/{cover_i}-M.jpg`, taken from the edition or search response) to avoid the 100-per-5-minutes ISBN cover limit during batch import.
- Ship the Google Books API key restricted to the app's package name and SHA-1 in Cloud Console. Keyless use is not viable.
- ISBNdb is a paid option. I did not verify its current pricing (see Gaps).

### Gaps
- ISBNdb current pricing and plans were not verified; I fetched no primary source.
- Google's official current Books API daily quota number was not found in primary docs.
- No systematic coverage study for non-English or Asian-language ISBNs was found. The spot test above is anecdotal.

## On-device OCR fallback: ML Kit Text Recognition v2

### Takeaway
ML Kit Text Recognition v2 runs fully on-device and is free. Latin is `com.google.mlkit:text-recognition:16.0.1` (bundled, about 4 MB per script per ABI) or `play-services-mlkit-text-recognition:19.0.2` (unbundled, about 260 KB). Separate artifacts cover Chinese, Devanagari, Japanese and Korean. Use it on the cover photo when no barcode is found, then send the largest text lines as an Open Library or Google Books title search.

### Cited Findings
- Artifacts: Latin `text-recognition:16.0.1` / `play-services-mlkit-text-recognition:19.0.2`; Chinese, Devanagari, Japanese and Korean each `text-recognition-<script>:16.0.1` / `play-services-mlkit-text-recognition-<script>:16.0.2`. — [ML Kit Text Recognition v2 Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- Size: bundled about 4 MB per script per architecture; unbundled about 260 KB. — [ML Kit Text Recognition v2 Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- API: `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(inputImage)` returns `Text`, which is split into `TextBlock`, `Line`, `Element` and `Symbol`, each with a bounding box. Characters should be at least 16x16 px; above 24x24 gives no gain. — [ML Kit Text Recognition v2 Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

### Inferences
- Heuristic: the title is usually the `Line` with the tallest bounding box. Combine the 2-3 largest lines into a `search.json?q=` query and show the top 5 candidates for the user to confirm.

### Gaps
- No primary-source benchmark of cover OCR accuracy (stylized fonts) was found.

## New-release feeds: RSS parser libraries, free feeds, and Open Library author "new works"

### Takeaway
Use prof18 RSS-Parser 6.2.0 (Apache-2.0, KMP, coroutine-based, handles RSS, Atom and RDF). Rome 2.1.0 hasn't had a release since March 2023. Working free feeds, verified live: Book Riot new-books, Kirkus Reviews, NYT Books, Lit Hub, Reactor/Tor, and Porchlight's new-release Atom feed. Penguin Random House's `/feed/` returns HTML, not a feed. Open Library has no "subscribe to author" API. You can poll `search.json?author_key=<OLID>&sort=new` per followed author and diff the results, and that works.

### Cited Findings
- RSS-Parser: coordinates `com.prof18.rssparser:rssparser`. Kotlin Multiplatform (Android, JVM, iOS, macOS, JS, wasm). Parses RSS, Atom and RDF. Suspending `RssParser().getRssChannel(url)` and `parse(xmlString)`. Apache-2.0. Caching was removed in 6.0 ("not meant to provide a caching solution"). — [prof18/RSS-Parser](https://github.com/prof18/RSS-Parser)
- RSS-Parser latest is 6.2.0 (GitHub release 2026-11-08; Maven Central matches), ~624 stars, last push 2026-11-20. — [GitHub releases](https://github.com/prof18/RSS-Parser/releases); [Maven Central](https://repo1.maven.org/maven2/com/prof18/rssparser/rssparser/maven-metadata.xml)
- Rome: latest 2.1.0 (2023-03-12), last repo push 2024-08-08, Apache-2.0. — [rometools/rome](https://github.com/rometools/rome)
- Feeds that returned valid RSS or Atom on 2026-11-21:
  - Book Riot new books, whose items included "6 of the Best New Book Releases Out November 17, 2026". — [bookriot.com/category/new-books/feed/](https://bookriot.com/category/new-books/feed/)
  - Kirkus Reviews, a review feed. — [kirkusreviews.com/feeds/rss/](https://www.kirkusreviews.com/feeds/rss/)
  - NYT Books section. — [rss.nytimes.com/.../Books.xml](https://rss.nytimes.com/services/xml/rss/nyt/Books.xml)
  - Lit Hub. — [lithub.com/feed/](https://lithub.com/feed/)
  - Reactor (formerly Tor.com), SFF. — [reactormag.com/feed/](https://reactormag.com/feed/)
  - Porchlight Books new releases, Atom. — [porchlightbooks.com/blogs/new-releases.atom](https://www.porchlightbooks.com/blogs/new-releases.atom)
  - Publishers Weekly "recent", valid RSS 2.0. It is served with gzip Content-Encoding, which OkHttp decodes transparently. — [publishersweekly.com/pw/feeds/recent/index.xml](https://www.publishersweekly.com/pw/feeds/recent/index.xml)
- Penguin Random House `/feed/` returned an HTML page, not a feed. PRH publishes monthly "new book releases" articles instead. — [PRH The Read Down](https://www.penguinrandomhouse.com/the-read-down/new-book-releases-november/)
- Open Library author works: `/authors/{OLID}/works.json?limit=&offset=` is paginated (Tolkien OL26320A: size 367). `search.json?author_key=OL26320A&sort=new&fields=title,first_publish_year,key` returned the 2026 works first. The search docs show `search.json?author=tolkien&sort=new`. — [Open Library Search API](https://openlibrary.org/dev/docs/api/search); direct test against [openlibrary.org/search.json](https://openlibrary.org/search.json?author_key=OL26320A&sort=new&limit=3&fields=title,first_publish_year,key)
- Google Books `orderBy=newest` combined with `q=inauthor:"Name"` is an alternative author-newest query. — [Google Books API "Using the API"](https://developers.google.com/books/docs/v1/using)

### Inferences
- `sort=new` on Open Library surfaces new editions and translations (e.g. "O Hobbit ou ida e volta", 2026), not only new works. For author-follow alerts, filter by the user's language and dedupe by work key. Run a periodic WorkManager job (e.g. daily), keep a "last seen work keys" set per author, and stay under 1-3 requests per second.
- Store feed items in Room yourself, since RSS-Parser no longer caches.
- Rome is a JVM library that predates Kotlin Multiplatform (general knowledge). With no release since 2023, it is the weaker choice for a new Android app.

### Gaps
- NYT Books API (bestseller lists, needs a free key) docs couldn't be fetched: developer.nytimes.com blocked the fetcher. Rate limits are unverified.
- Goodreads: no public new-release feed was found. Goodreads shelf RSS exists per user, but I did not verify it, and the URL I tested returned 404. The Goodreads API has been closed to new developers since 2020 (general knowledge, unverified here).
- I found no confirmed per-publisher new-release RSS feed from the big five publishers.

## Profile/stats: Compose charts (books per month, progress rings) and badge implementations

### Takeaway
Use Vico (Apache-2.0, ~3.2k stars, very active; 3.3.1 stable, 3.4.0-next.1 preview) for a books-per-month column chart. For progress rings, Compose's built-in `CircularProgressIndicator(progress = { ... })` or a 10-line `Canvas.drawArc` is enough. Vico also has pie charts, but they are marked experimental. ehsannarmani ComposeCharts 1.0.0 (Apache-2.0) is an alternative. I found no reusable badge or achievement library. Habitica Android (GPL-3.0) and small apps such as readbooks-v2-android show the in-app pattern.

### Cited Findings
- Vico: "a powerful and extensible chart library for Compose Multiplatform". Apache-2.0, ~3.2k stars, last push 2026-11-21. Latest GitHub release v3.3.1 (2026-08-28). Maven Central `compose` / `compose-m3` 3.4.0-next.1 (2026-10-28, a pre-release). Older `multiplatform` / `core` artifacts stop at 2.5.2, and `views` is at 3.2.3. — [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico); [Maven Central](https://repo1.maven.org/maven2/com/patrykandpatrick/vico/compose-m3/maven-metadata.xml)
- Vico official column-chart starter (Compose): `val modelProducer = remember { CartesianChartModelProducer() }`, then `LaunchedEffect(Unit) { modelProducer.runTransaction { columnModel { series(5, 6, 5, ...) } } }`, then `CartesianChartHost(rememberCartesianChart(rememberColumnCartesianLayer(), startAxis = VerticalAxis.rememberStart(), bottomAxis = HorizontalAxis.rememberBottom()), modelProducer)`. — [Vico guide: Starter examples](https://guide.vico.patrykandpatrick.com/cartesian-charts/starter-examples.md)
- Vico modules: `compose`, plus optional `compose-m2`, `compose-m3` (Material theming) and `compose-glance` (chart images for Glance app widgets). — [Vico guide: Getting started](https://guide.vico.patrykandpatrick.com/getting-started.md)
- Vico now has pie charts (`PieChart`, `PieChartHost`, `PieChartModelProducer`), but "Pie charts are experimental; they'll remain available, but breaking changes are more likely." — [Vico guide: Pie chart stability](https://guide.vico.patrykandpatrick.com/pie-charts/stability.md); [Vico llms.txt index](https://guide.vico.patrykandpatrick.com/llms.txt)
- ComposeCharts (`io.github.ehsannarmani:compose-charts`) 1.0.0, released 2026-07-21, Apache-2.0, ~925 stars. — [ehsannarmani/ComposeCharts](https://github.com/ehsannarmani/ComposeCharts); [Maven Central](https://repo1.maven.org/maven2/io/github/ehsannarmani/compose-charts/maven-metadata.xml)
- Habitica Android: Kotlin, GPL-3.0, ~1.8k stars, pushed 2026-11-13. It is a gamified app with achievements. — [HabitRPG/habitica-android](https://github.com/HabitRPG/habitica-android)
- `fbaldhagen/readbooks-v2-android`: "Android ebook reader powered by Gutendex & OpenLibrary, with ... reading goals, and achievements. Built with Kotlin & Jetpack Compose" (~4 stars). — [readbooks-v2-android](https://github.com/fbaldhagen/readbooks-v2-android)
- `tn3w/Shelf`: Kotlin, MIT, "Private, offline book app for Android: find any book, scan covers, track reading streaks..." (~17 stars, pushed 2026-11-20). — [tn3w/Shelf](https://github.com/tn3w/Shelf)

### Inferences
- Pin Vico 3.3.1 stable rather than the `-next` pre-release. For books-per-month, pass 12 monthly counts to `columnModel { series(...) }` and use a `CartesianValueFormatter` for month labels on the bottom axis.
- Badges are simple enough to build in-house. Define them as data (`id`, `threshold`, `metric` such as books finished, pages, streak days or genres), evaluate them in a use case after each book-status change, store unlocks in Room, and render a grid with greyed-out locked icons.
- Progress rings don't need a library. A second chart library (ComposeCharts) is only worth adding if you want a stable donut chart and would rather avoid Vico's experimental pie API.

### Gaps
- No dedicated open-source Android "achievements" library with meaningful adoption was found (GitHub search for Kotlin badges/achievements surfaced only student projects).
- I did not check whether Vico pie charts support a donut (hole) style.

## Image loading/caching: Coil 3

### Takeaway
Coil 3.6.3 (`io.coil-kt.coil3:coil-compose`, Apache-2.0, released 2026-09-18, ~12k stars, actively maintained) is the standard. Add the network artifact, e.g. `coil-network-okhttp`, because Coil 3 doesn't bundle networking. Then use `AsyncImage(model = coverUrl, ...)`.

### Cited Findings
- `io.coil-kt.coil3:coil-compose` latest release is 3.6.3 (Maven Central lastUpdated 2026-09-18; GitHub release 3.6.3, 2026-09-18). Apache-2.0, ~12k stars, last push 2026-11-21. — [Maven Central](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml); [coil-kt/coil](https://github.com/coil-kt/coil)

- The Coil README says to "Import the Compose library and a networking library": `io.coil-kt.coil3:coil-compose:3.6.3` + `io.coil-kt.coil3:coil-network-okhttp:3.6.3`, then load with the `AsyncImage` composable. `coil-network-okhttp` is also at 3.6.3 on Maven Central. — [Coil README](https://github.com/coil-kt/coil); [Maven Central](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-network-okhttp/maven-metadata.xml)

### Inferences
- Give Coil the same OkHttp client that carries the identifying User-Agent, so Open Library cover requests count as identified traffic.

### Gaps
- None material.

## Example repos to copy from (summary)

### Takeaway
No high-star, all-in-one Kotlin + Compose book-library app exists. Use the Google samples for camera and ML Kit and Now in Android for architecture, and borrow feature ideas from small book apps.

### Cited Findings
- `android/nowinandroid`: Apache-2.0, Kotlin, ~22k stars, pushed 2026-11-21. Reference Compose architecture (modularization, Room, WorkManager sync). — [android/nowinandroid](https://github.com/android/nowinandroid)
- `googlesamples/mlkit`: Apache-2.0, barcode and text recognition samples. — [googlesamples/mlkit](https://github.com/googlesamples/mlkit)
- `android/camera-samples`: CameraX samples. — [android/camera-samples](https://github.com/android/camera-samples)
- `tn3w/Shelf`: MIT, Kotlin, offline book tracker with cover scanning and streaks. — [tn3w/Shelf](https://github.com/tn3w/Shelf)
- `jernejml/ShelfScanner`: Apache-2.0, "Minimalistic android app for scanning ISBN barcodes". — [jernejml/ShelfScanner](https://github.com/jernejml/ShelfScanner)
- `armutyus/ninova`: uses Google Books and Open Library. — [armutyus/ninova](https://github.com/armutyus/ninova)
- `anubhav-auth/Bookpedia`: Compose Multiplatform app with Open Library search and Room. — [Bookpedia](https://github.com/anubhav-auth/Bookpedia)
- `mateusz-bak/openreads`: GPL-2.0, ~1.7k stars, the most popular open-source reading tracker. It is Flutter/Dart, so use it for UX and feature reference only. — [openreads](https://github.com/mateusz-bak/openreads)
- `bayang/jelu`: MIT, Kotlin, ~755 stars, a self-hosted server-side book tracker. Useful for its data model and import ideas. — [bayang/jelu](https://github.com/bayang/jelu)
- `HabitRPG/habitica-android`: GPL-3.0, achievements and badges reference. Copying code imposes GPL. — [habitica-android](https://github.com/HabitRPG/habitica-android)

### Inferences
- Watch the licenses. Code from GPL repos (Habitica, Openreads) can't be copied into a non-GPL app. Apache and MIT repos (Now in Android, ML Kit samples, Shelf, ShelfScanner) are safe to adapt with attribution.

### Gaps
- Most of the Kotlin book apps found have very few stars, so their code quality was not audited.
