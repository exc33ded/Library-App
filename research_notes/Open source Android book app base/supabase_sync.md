# Offline-first Android (Kotlin + Compose) with Supabase sync/backup — single-user book library

Research date: 2026-11-21. Primary sources fetched: supabase-kt GitHub repo and releases, Supabase docs (Kotlin install, billing, backups, project pausing, storage access control), PowerSync pricing, PowerSync Kotlin repo and docs, ElectricSQL (now electric.ax) docs, supabase-offline-kt repo. Total tool budget was limited, so a few items are marked as gaps.

## supabase-kt: version, modules, Android auth, license, maintenance

### Takeaway
supabase-kt is MIT-licensed, actively maintained Kotlin Multiplatform client. The latest stable release is **3.9.0 (3 Nov 2026)**. It ships separate Auth, Postgrest, Storage, Realtime and Functions modules, plus a `compose-auth` plugin for native Google sign-in on Android (via Credential Manager). It is still a community project, but there is a plan to make it official.

### Cited Findings
- Latest releases: **3.9.0 (03 Nov, "Latest")**, 3.9.0-beta-1 (12 Oct), 3.8.0 (26 Aug). Release pages show dates without the year; these are the 2026 releases. — [supabase-kt releases](https://github.com/supabase-community/supabase-kt/releases)
- 3.9.0 changes:
  - Storage methods now throw `StorageRestException` instead of a generic exception.
  - Many Realtime race-condition and reconnect fixes.
  - Auth masks session credentials in logs.
  - Postgrest allows multiple filters on one column.
  - Source: [supabase-kt releases](https://github.com/supabase-community/supabase-kt/releases)
- 3.8.0 changes:
  - Added the OAuth consent flow.
  - Added Postgrest `maybeSingle()`, `dryRun()` and `notIn`.
  - Added the Realtime postgres-changes filter builder.
  - Added Storage cache purge methods.
  - Source: [supabase-kt releases](https://github.com/supabase-community/supabase-kt/releases)
- Modules:
  - `auth-kt` (called `gotrue-kt` before 3.0.0)
  - `postgrest-kt`, `storage-kt`, `realtime-kt`, `functions-kt`
  - `apollo-graphql`, `compose-auth`, `compose-auth-ui`
  - `coil-integration`, `coil3-integration`, `imageloader-integration`
  - Source: [supabase-kt README](https://github.com/supabase-community/supabase-kt)
- License is MIT. The repo has about 855 stars and 109 forks. — [supabase-kt README](https://github.com/supabase-community/supabase-kt)
- Maintenance notice: the project is actively maintained, but PRs may take longer. It is "working towards making supabase-kt official," with a namespace migration and repository transfer planned. — [supabase-kt README](https://github.com/supabase-community/supabase-kt)
- Gradle setup uses a BOM:
  - `implementation(platform("io.github.jan-tennert.supabase:bom:VERSION"))`, then modules without versions, such as `io.github.jan-tennert.supabase:postgrest-kt`, `auth-kt`, `realtime-kt` and `storage-kt`.
  - You must add a Ktor client engine. Realtime needs an engine that supports WebSockets.
  - supabase-kt 3.x needs Ktor 3.x.
  - Minimum Android SDK is 26; lower versions need core library desugaring.
  - KotlinX Serialization is the default.
  - Source: [Supabase Kotlin docs: Installing](https://supabase.com/docs/reference/kotlin/installing)
- The `compose-auth` plugin provides "Native Google & Apple Auth for Compose Multiplatform targets". — [supabase-kt README](https://github.com/supabase-community/supabase-kt)
- The API reference lists a `rememberSignInWithGoogle` composable with an `onResult` callback and an OAuth fallback for platforms without native support. — [ComposeAuth API docs](https://supabase-community.github.io/supabase-kt/plugins/compose-auth/io.github.jan.supabase.compose.auth/-compose-auth/index.html)
- Community reports (Kotlin Slack, not primary) say the native Google path on Android uses Credential Manager, while JS and JVM fall back to the OAuth flow. One report says sign-out errored on devices without Credential Manager support (2.x era). — [kotlinlang Slack thread](https://slack-chats.kotlinlang.org/t/16920770/hello-i-am-using-supabase-version-2-2-3-my-device-does-not-s)
- With Credential Manager you must use the **Web** client ID, not the Android client ID (third-party source, Auth0). This matches the usual Supabase Google setup. — [Auth0 Google native docs](https://auth0.com/docs/authenticate/identity-providers/social-identity-providers/google-native)
- Google's own codelab covers Credential Manager "Sign in with Google" on Android. — [Google codelab](https://codelabs.developers.google.com/sign-in-with-google-android)

### Inferences
- For a single-user personal app, Google native sign-in via `compose-auth` is the lowest-friction option: one tap and no email round-trip. Email OTP or magic link through `auth-kt` is a fallback. Magic links need deep-link handling on Android, while a 6-digit email OTP avoids that.
- Pin the BOM to 3.9.0, then check for 3.9.x patch releases before starting.

### Gaps
- I did not fetch the official supabase.com guide for Google native sign-in with Kotlin or the magic link/OTP Kotlin reference pages. The exact `compose-auth` setup (Web client ID config and nonce handling) should be checked there.
- The README did not explicitly mention email OTP or magic link. Supabase Auth supports both in general, but I did not verify the Kotlin API names in this session.

## Offline-first options: Room + custom sync vs PowerSync vs ElectricSQL/others

### Takeaway
For one user, a few hundred to a few thousand books, and 2–3 devices, the simplest robust approach is **Room as the source of truth plus a small custom sync**:
- Each row has a client-generated UUID, an `updated_at` timestamp, and a `deleted` tombstone.
- Conflicts resolve by last-write-wins.
- A WorkManager job pushes dirty rows, then pulls rows changed since the last sync.

PowerSync is the mature "batteries-included" alternative, with an Apache-2.0 Kotlin SDK, a Supabase connector and a Room integration. However, it adds a second hosted service: its free tier deactivates after about 1 week of inactivity, and paid plans start at $49/month. ElectricSQL has no Kotlin/Android client.

### Cited Findings
**Android guidance**
- Android's official offline-first guide makes the local database the source of truth. It recommends WorkManager for syncing local changes when the network is available. — [Android Developers: Build an offline-first app](https://developer.android.com/topic/architecture/data-layer/offline-first). I saw this in search results but did not fetch it this session; the guide is well known.

**PowerSync Kotlin SDK**
- The repo is Apache-2.0 licensed.
- Artifacts are `com.powersync:core`, the optional `com.powersync:connector-supabase`, and the optional `com.powersync:powersync-compose`.
- It has `integrations/room` and `integrations/sqldelight` modules.
- Demos are `demos/android-supabase-todolist`, a native Android app with Supabase, and `demos/supabase-todolist`, a KMP app for Android, iOS and Desktop.
- Source: [powersync-kotlin GitHub](https://github.com/powersync-ja/powersync-kotlin)
- Docs:
  - The install example pins **1.12.0** and says to check GitHub releases for the latest. Version **1.14.1** added initial web support, so the current version is at least 1.14.x.
  - Targets: Android, JVM (Linux/macOS/Windows), Apple (iOS/macOS/tvOS/watchOS), and Web (experimental).
  - The docs say the SDK can be used with Room and SQLDelight.
  - Source: [PowerSync Kotlin SDK docs](https://docs.powersync.com/client-sdks/reference/kotlin)
- PowerSync docs and search snippets say the Android demo shows file/attachment handling. I did not verify this directly. — [PowerSync examples](https://docs.powersync.com/intro/examples.md)

**PowerSync Cloud pricing**
- **Free:** 2 GB synced per month, 500 MB hosted data, 50 peak concurrent clients, and 2 instances.
  - Free projects are "deactivated after 1 week of inactivity". Another place on the same page says more than 2 weeks, so the page is inconsistent.
- **Pro:** from $49/month. **Team:** from $599/month.
- **Self-hosted Open Edition:** free, and supports Postgres.
- Source: [PowerSync pricing](https://www.powersync.com/pricing)

**ElectricSQL** (now hosted at electric.ax)
- The client-development guide names only **TypeScript and Elixir** official clients and does not mention a Kotlin or Android client. — [Electric client development guide](https://electric.ax/docs/guides/client-development)
- The search turned up no Electric Kotlin client. The only non-TS client found was an unofficial Dart client. — [electric_dart](https://github.com/SkillDevs/electric_dart)

**Other Kotlin sync libraries found** (not verified in depth)
- **SyncForge:** a KMP sync engine using Room plus WorkManager, an outbox pattern and per-entity conflict strategies including LWW. It is not Supabase-specific. — [klibs.io SyncForge](https://klibs.io/project/Arsenoal/syncforge)
- **UstadMobile/Door:** a Room-based KMP offline-first layer. Its last push was around August 2024, so it is likely stale. — [awesome.ecosyste.ms Door](https://awesome.ecosyste.ms/projects/github.com%2FUstadMobile%2Fdoor)
- **Turso libSQL** has a Kotlin client limited to the Android Gradle Plugin. — [Turso Kotlin quickstart](https://docs.turso.tech/sdk/kotlin/quickstart.md)

### Inferences
**Recommended design (Option A, Room + custom LWW sync)**
- Tables: `books`, `categories`, `book_categories` (if many-to-many), and statuses as an enum column or a small table.
- Every synced table has these columns:
  - `id uuid` (client-generated)
  - `user_id uuid default auth.uid()`
  - `updated_at timestamptz`
  - `deleted boolean default false`
- Locally, add a `dirty` flag, or keep a separate outbox.
- **Push:** upsert dirty rows through `postgrest.from("books").upsert(list)`. Tombstones are pushed as `deleted = true`, not hard deletes.
- **Pull:** `select().gt("updated_at", lastPulledAt)`, then merge with LWW by `updated_at`.
- **Timestamps:** have a Postgres trigger set `updated_at = now()` on the server. This gives a single clock for pull cursors. Devices then compare server timestamps, which avoids clock skew between devices.
- **Scheduling:** run a WorkManager `PeriodicWorkRequest` with a network constraint, plus a one-time sync on app start and after edits.
- Optional: subscribe to Supabase Realtime only while the app is in the foreground.
- For a single user, conflicts are rare (the same user editing the same book on two devices while offline), so LWW per row is acceptable.

**Comparing the options**
- PowerSync removes hand-written sync code and handles streaming, partial sync and the upload queue. But it requires a separate service, sync rules config, and a PowerSync instance that can be deactivated when inactive. That is more moving parts than this use case justifies.
- PowerSync becomes worthwhile if the app later adds sharing between users, larger datasets, or a need for real-time multi-device updates.
- Electric is not viable for native Kotlin.

### Gaps
- I did not fetch the exact latest PowerSync Kotlin release number (≥1.14.1) or its stability label. The docs page did not state alpha, beta or stable.
- I did not read the PowerSync Room integration README, so its maturity is unknown.
- PowerSync's inactivity-deactivation period is inconsistent on its own pricing page (1 week vs. more than 2 weeks).

## Open-source Android apps / sample repos for Room/SQLDelight + Supabase sync

### Takeaway
There is no widely adopted open-source Android app that does Room + Supabase custom sync. The best code to copy is:
1. **PowerSync's `android-supabase-todolist` demo** (Apache-2.0) for Supabase auth/connector wiring and attachments.
2. **supabase-offline-kt** (MIT, early stage) for an offline write queue on top of supabase-kt.
3. **Google's offline-first guidance and the Now in Android pattern** for Room + WorkManager sync structure.

### Cited Findings
- **powersync-ja/powersync-kotlin** (Apache-2.0)
  - Demos: `demos/android-supabase-todolist` and `demos/supabase-todolist` (KMP).
  - Setup: open the demo in Android Studio and put the PowerSync URL plus the Supabase URL and anon key in `local.properties`.
  - Sources: [powersync-kotlin GitHub](https://github.com/powersync-ja/powersync-kotlin); [search summary of README](https://github.com/powersync-ja/powersync-kotlin)
- **nesfeder/supabase-offline-kt** (MIT)
  - An offline-first plugin for supabase-kt Postgrest and Storage on KMP (Android and iOS).
  - Offline writes "wait in a queue on the device and are sent, in the order they were made" when the network returns. Reads fall back to a device cache, and Storage downloads are cached on disk.
  - Client-generated IDs make retries idempotent.
  - Version 0.4.0 targets supabase-kt 3.9. It has 2 stars, 0 forks and 1,366 commits, with CI, coverage, docs and a sample todo app.
  - LWW and tombstone strategy are not documented on the README.
  - Source: [supabase-offline-kt GitHub](https://github.com/nesfeder/supabase-offline-kt)
- A Medium tutorial covers "offline capability using Supabase and Room" (Novumlogic). It is a tutorial, not a maintained repo. — [Medium: Supabase and Room](https://medium.com/novumlogic/supercharge-your-android-app-with-offline-capability-using-supabase-and-room-47cf93f0374f)
- **SyncForge** offers a Room/WorkManager outbox and LWW for KMP, not specific to Supabase. — [klibs.io SyncForge](https://klibs.io/project/Arsenoal/syncforge)

### Inferences
What to reuse:
- **From the PowerSync demo:** the Supabase auth/session setup and the attachment queue pattern (local file, then upload, then path stored in the row). Both carry over even without PowerSync.
- **From supabase-offline-kt:** its outbox/idempotent upsert design. Read its architecture doc before deciding whether to depend on it; with 2 stars, depending on it is risky.
- **For Room + WorkManager structure:** Google's [Now in Android](https://github.com/android/nowinandroid) app (Apache-2.0) is the canonical reference for a Room/WorkManager sync worker, though it is not Supabase. I did not fetch it this session; I know of it from prior knowledge.

### Gaps
- I did not open the Medium tutorial or Now in Android this session, so licenses and the current code are unverified for them.
- The GitHub search for "supabase-kt room sync" turned up no well-starred dedicated repo. This is a real gap in the ecosystem, not just a gap in the search.

## Supabase Storage for cover images: upload/resize, RLS, free-tier limits

### Takeaway
Store covers in a private bucket under the path `{auth.uid()}/{bookId}.jpg`, with owner-only RLS policies on `storage.objects`. **Resize and compress on the device before upload**, because Storage Image Transformations are not available on the Free plan. The Free plan gives 1 GB of file storage, 500 MB of database per project and 5 GB of egress. Free projects pause after about 7 days of low activity.

### Cited Findings
- Free plan limits:
  - 2 free projects
  - **500 MB database** per project
  - **1 GB file storage**
  - **5 GB egress**
  - 50,000 MAU
  - 500k Edge Function invocations
  - Realtime: 2M messages and 200 peak connections
  - **Storage Image Transformations are unavailable on Free**
  - Source: [Supabase billing docs](https://supabase.com/docs/guides/platform/billing-on-supabase)
- Pausing:
  - Free projects are paused after insufficient database activity "over the past week".
  - Dashboard visits, API calls and app requests all count as activity. You get a warning email about a week before the pause.
  - A paused project can be restored "for up to **1 year**" and returns to its previous state with data and config.
  - Pro and higher plans are never paused for inactivity.
  - Source: [Supabase project pausing docs](https://supabase.com/docs/guides/platform/free-project-pausing)
  - Conflict: a 2024 changelog said the restore window was 90 days. — [Supabase changelog](https://supabase.com/changelog/27497-paused-free-plan-projects-are-restorable-for-90-days). The current docs say 1 year.
- RLS on `storage.objects`:
  - Policies check the first path segment, `(storage.foldername(name))[1] = (select auth.jwt()->>'sub')`, together with `bucket_id = '...'`.
  - Upsert, meaning overwrite, needs both SELECT and UPDATE policies.
  - Source: [Supabase Storage access control](https://supabase.com/docs/guides/storage/security/access-control)
- Example policies (pattern from the docs above; `auth.uid()::text` is equivalent to the `sub` claim):
  ```sql
  create policy "covers owner insert" on storage.objects for insert to authenticated
    with check (bucket_id = 'covers' and (storage.foldername(name))[1] = (select auth.jwt()->>'sub'));
  create policy "covers owner select" on storage.objects for select to authenticated
    using (bucket_id = 'covers' and (storage.foldername(name))[1] = (select auth.jwt()->>'sub'));
  create policy "covers owner update" on storage.objects for update to authenticated
    using (bucket_id = 'covers' and (storage.foldername(name))[1] = (select auth.jwt()->>'sub'))
    with check (bucket_id = 'covers' and (storage.foldername(name))[1] = (select auth.jwt()->>'sub'));
  create policy "covers owner delete" on storage.objects for delete to authenticated
    using (bucket_id = 'covers' and (storage.foldername(name))[1] = (select auth.jwt()->>'sub'));
  ```
- supabase-kt has `storage-kt` plus Coil/Coil3 integrations for loading Storage images. — [supabase-kt README](https://github.com/supabase-community/supabase-kt)
- supabase-offline-kt caches Storage downloads on disk. — [supabase-offline-kt](https://github.com/nesfeder/supabase-offline-kt)

### Inferences
**Capacity**
- At about 100–200 KB per cover (resized to roughly 600 px wide, JPEG or WebP, quality ~80), 1 GB holds about 5,000–10,000 covers. That is ample for a personal library.
- Book rows use far less than 500 MB.

**Upload pattern**
1. Pick the image or fetch the cover URL.
2. Decode and downscale with `BitmapFactory` and `inSampleSize`, or with Coil.
3. Compress to WebP or JPEG.
4. Save locally in the app files directory and record `cover_local_path` in Room.
5. Mark the cover as pending upload.
6. The sync worker uploads it to `covers/{uid}/{bookId}.webp` with `upsert = true` and sets `cover_path` on the row.
7. Other devices download it lazily via an authenticated download or a signed URL, then cache it.

**Bucket and pausing**
- A private bucket plus owner RLS is enough. Postgres table RLS should also be `user_id = auth.uid()` on all tables.
- To avoid free-tier pausing, the app's periodic WorkManager sync on any device counts as API activity. An app used at least weekly should keep the project alive. If nobody opens the app for 1+ week, the project pauses. Data is safe (restorable within 1 year), but sync fails until it is resumed from the dashboard. The app must therefore handle a paused backend as "offline".

### Gaps
- I did not fetch the Free plan's maximum upload file size; I believe it is 50 MB but did not verify. It is irrelevant for resized covers.
- I did not verify current Pro plan pricing (historically $25/month) in this session, because the pricing page fetch failed.

## Backup: Supabase point-in-time vs app-level JSON/CSV export

### Takeaway
The Free plan gets **no automatic daily backups and no PITR**. Supabase itself tells Free users to export their data regularly with the CLI. Database backups also **never include Storage objects**. For this app, an in-app JSON export of books, categories and statuses, plus a zip of the cover files, is the most practical backup. Optionally add a scheduled `supabase db dump` from a PC or GitHub Action.

### Cited Findings
- Automatic daily backups exist only for Pro (7-day retention), Team (14 days) and Enterprise (up to 30 days). — [Supabase backups docs](https://supabase.com/docs/guides/platform/backups)
- PITR is a paid add-on for Pro and higher:
  - Pricing: about $100/month for 7 days ($0.137/hour), about $200/month for 14 days, and about $400/month for 28 days.
  - Enabling PITR replaces daily backups. The worst-case RPO is 2 minutes.
  - Source: [Supabase backups docs](https://supabase.com/docs/guides/platform/backups)
- Free-tier projects should "regularly export their data" using the Supabase CLI `db dump` command and keep off-site backups. — [Supabase backups docs](https://supabase.com/docs/guides/platform/backups)
- Database backups contain only metadata about Storage objects. Restoring a backup does not restore deleted objects. — [Supabase backups docs](https://supabase.com/docs/guides/platform/backups)

### Inferences
- PITR is overkill and expensive (about $125+/month including Pro) for a personal library.
- With Option A, every device already holds a full local copy in Room, so Supabase acts as both the sync hub and the first backup layer.
- Add **app-level export**: serialize Room tables to JSON with kotlinx.serialization (CSV optional, for spreadsheets) and zip them with the cover files. Save via the Storage Access Framework (`ACTION_CREATE_DOCUMENT`) so the user can store it in Google Drive or locally. Add a matching import with an upsert-by-id merge.
- The JSON export doubles as a migration path if the user ever leaves Supabase. `db dump` from the CLI is a useful extra for the Postgres side.

### Gaps
- I did not verify whether Free plan projects can download any automatic backup from the dashboard; the docs list daily backups only for paid plans.
