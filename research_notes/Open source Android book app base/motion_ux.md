# Modern "new Android" motion and interaction feel in Kotlin + Jetpack Compose (book library app), as of Nov 2026

Version snapshot, verified 2026-11-21 from AndroidX release pages:

| Artifact | Stable | Pre-release |
|---|---|---|
| `androidx.compose:compose-bom` | **2026.11.01** (maps to material3 1.5.0, foundation/animation 1.13.0) | `compose-bom-alpha` also exists (Now in Android uses it) |
| `androidx.compose.material3:material3` | **1.5.0** (Nov 18, 2026) | 1.6.0-alpha01 (Nov 18, 2026) |
| `androidx.compose.{ui,foundation,animation,runtime}` | **1.13.0** (Nov 18, 2026) | 1.14.0-alpha01 |
| `androidx.navigation3:navigation3-runtime` / `navigation3-ui` | **1.2.2** (Nov 18, 2026) | 1.3.0-alpha05 |
| `androidx.navigation:navigation-compose` | 2.8.0+ needed for predictive back (latest not checked) | |

Sources: [Compose releases](https://developer.android.com/jetpack/androidx/releases/compose), [material3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3), [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [navigation3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3).

## Material 3 Expressive: what's in it, which artifact/version, stable vs experimental

### Takeaway
Material 3 Expressive shipped as stable API in **material3 1.5.0 (Nov 18, 2026)**. MotionScheme, the expressive theme and color scheme, ButtonGroup/SplitButton/ToggleButton, FAB menu, FloatingToolbar and the wavy progress indicators are all non-experimental now. `LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes` and `toShape()`/`toPath()` are **still experimental**. 1.4.0 (Sep 2025) had none of the expressive APIs: they were all moved into the 1.5.0-alpha line.

### Cited Findings
- material3 1.5.0 stable on Nov 18, 2026. 1.5.0-rc01 came out Nov 4, beta01 Oct 7 and the last alpha (alpha29) Sep 23. 1.6.0-alpha01 came out Nov 18, 2026. — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- In 1.4.0-beta01 (Jul 30, 2025), every public API tagged `ExperimentalMaterial3ExpressiveApi` or `ExperimentalMaterial3ComponentOverrideApi` was removed from 1.4.0 and moved to the 1.5.0-alpha line. 1.4.0 stable came out Sep 24, 2025. In it, M3 components use `MotionScheme` internally, and it adds `HorizontalCenteredHeroCarousel`, `VerticalDragHandle`, `SecureTextField`, `Text(autoSize)`, new `SearchBar`/`SearchBarState` APIs and `TimePickerDialog`. It also stops depending on `material-icons-core` (use Material Symbols instead). — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- Graduation dates inside 1.5.0 alphas: "Graduate motion scheme from experimental" in 1.5.0-alpha15 (Feb 25, 2026). Expressive theme / `expressiveLightColorScheme` promoted in alpha18 (Apr 22, 2026), which also added a `Material3ExpressiveApi` annotation that does not require OptIn. FAB and FAB menu graduated in alpha19 (May 6, 2026). `SplitButton` in alpha20 (May 19, 2026). `ButtonGroup`, `FloatingToolbar` and `HorizontalFloatingToolbar` in alpha22 (Jun 17, 2026). `WavyLinearProgressIndicator`/`WavyCircularProgressIndicator` are stable in 1.5.0. `LocalMotionScheme` was deprecated in favor of `MaterialTheme.motionScheme` and removed in alpha27 (Aug 26, 2026). — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- APIs still experimental in 1.5.0 ("Remaining experimental APIs"): `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`, `PullToRefreshDefaults.LoadingIndicator`, `MaterialShapes` with `toShape`/`toPath`, standalone static `BottomSheet`/`Sheet`, `AppBarWithSearch` (re-annotated `@ExperimentalMaterial3Api` in alpha24) and `Modifier.carouselParallaxScrollEffect`. — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- `MotionScheme` (material3, "Added in 1.5.0") is an interface that provides every `FiniteAnimationSpec` for a `MaterialTheme`. It has two companion factories: `MotionScheme.expressive()` ("recommended ... for prominent UI elements and hero interactions") and `MotionScheme.standard()` ("utilitarian UI elements and recurring interactions"). Its spec getters are `defaultSpatialSpec()`, `fastSpatialSpec()` and `slowSpatialSpec()` for animations that change shape or bounds, and `defaultEffectsSpec()`, `fastEffectsSpec()` and `slowEffectsSpec()` for color or alpha. The current reference shows no experimental annotation. — [MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- **Conflict:** a search-result snippet and older material described `MotionScheme.expressive()`/`standard()` as `@ExperimentalMaterial3ExpressiveApi`. That was true during 1.4/early 1.5 alphas and is superseded by the 1.5.0 reference and release notes above. — [MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- `MaterialExpressiveTheme` takes a `motionScheme` parameter, and Material components such as Button and Checkbox read their default motion from it. — [MaterialExpressiveTheme reference (search snippet)](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)
- In Androidify, the app wraps everything in `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` and a `SharedTransitionLayout` exposed through `LocalSharedTransitionScope`. The camera capture button is `MaterialShapes.Cookie9Sided.toShape()`, and its press-scale uses `MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` through `.indication(interactionSource, ScaleIndicationNodeFactory(spec))`. A custom `Modifier.sharedBoundsRevealWithShapeMorph(restingShape, targetShape: RoundedPolygon)` builds `Morph(resting, target)` and passes a morph clip as `clipInOverlayDuringTransition`. `boundsTransform` defaults to `MaterialTheme.motionScheme.sharedElementTransitionSpec`. The app also uses `HorizontalFloatingToolbar`, `TextAutoSize.StepBased` and `Modifier.onLayoutRectChanged`, which is "a more performant version of onGloballyPositioned", to set the origin of a color-splash transition. — [Android Developers Blog: Androidify, building delightful UI with Compose (May 2025)](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)
- Androidify's current `libs.versions.toml` pins `material3 = "1.5.0-alpha20"`, `navigation3 = "1.1.2"`, `camerax = "1.6.1"` (it uses `androidx.camera:camera-compose`) and `baselineprofile = "1.5.0-alpha06"`. Repo last pushed Nov 18, 2026. — [android/androidify (GitHub API)](https://github.com/android/androidify)

### Inferences
- For a new app in Nov 2026, use the BOM `2026.11.01` (material3 1.5.0). Opt-in is needed only for `LoadingIndicator`, `MaterialShapes`/`toShape()` and similar, with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Check the exact annotation name against the API reference, because the release notes say "experimental" without naming the annotation.
- Recommended skeleton:
  ```kotlin
  MaterialExpressiveTheme(
      colorScheme = if (dynamic && Build.VERSION.SDK_INT >= 31)
          (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else myScheme,
      motionScheme = MotionScheme.expressive(),
      typography = AppTypography,
  ) { SharedTransitionLayout { CompositionLocalProvider(LocalSharedTransitionScope provides this) { AppNav() } } }
  // in components:
  val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
  val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spec)
  ```
- Mapping to a book app: a `ButtonGroup` or connected toggle buttons for the reading-status filter (Want / Reading / Read). A FAB menu for "Add book: scan ISBN / search / manual". `HorizontalFloatingToolbar` for detail-screen actions. `WavyLinearProgressIndicator` for reading progress (stable). `ContainedLoadingIndicator` as the pull-to-refresh indicator (experimental).
- Shape morphing: `MaterialShapes.*` are `RoundedPolygon`s from `androidx.graphics:graphics-shapes`, and you animate between two of them with `Morph(a, b)` plus a progress value. The blog uses `RoundedPolygon`/`Morph` but does not name the artifact. That attribution comes from my background knowledge.

### Gaps
- `dynamicLightColorScheme`/`dynamicDarkColorScheme`: no release note was fetched for these. They are long-standing material3 APIs for API 31+ (training knowledge, not re-verified).
- Exact name and parameter list of `MaterialExpressiveTheme` in 1.5.0: the release notes call it `materialExpressTheme` (sic). The reference page was not fully fetched.
- The m3.material.io Expressive spec pages (spring token values such as damping and stiffness per scheme) were not fetched. No numeric spring constants are confirmed here.

## Pull-to-refresh with tension/stretch, overscroll and the "rubber band" feel

### Takeaway
Use material3's `PullToRefreshBox` (stable since 1.4.0-alpha18) with a custom `indicator` that reads `state.distanceFraction`. For edge stretch, Compose already applies the Android 12+ stretch overscroll to `LazyColumn`/`LazyVerticalGrid` by default. For a custom rubber band, implement `OverscrollEffect` (its `node` API is new in foundation 1.8) or pass `rememberOverscrollEffect()` into `anchoredDraggable`/`scrollable`.

### Cited Findings
- `PullToRefreshBox(isRefreshing, onRefresh, modifier, state, indicator) { content }` wraps the scrollable content. For a custom indicator, create `val state = rememberPullToRefreshState()` and pass the same state to the box and to `PullToRefreshDefaults.Indicator(state, isRefreshing, containerColor, color, modifier.align(TopCenter))`. A fully custom indicator uses `Modifier.pullToRefresh(state, isRefreshing, threshold = PositionalThreshold, onRefresh)` and reads `state.distanceFraction` (0f no pull to 1f threshold, >1 past it) inside `graphicsLayer { alpha/scale = progress }`. — [Pull to refresh guide](https://developer.android.com/develop/ui/compose/components/pull-to-refresh)
- `PullToRefresh` became a stable API in material3 1.4.0-alpha18 (Jul 16, 2025). 1.4.0-beta02 renamed defaults (`shape` to `indicatorShape`, `containerColor` to `indicatorContainerColor`) and added `indicatorMaxDistance`. `PullToRefreshDefaults.LoadingIndicator` (expressive) remains experimental in 1.5.0. — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- `OverscrollEffect` (foundation, since 1.2.0) decorates scroll and fling events through `applyToScroll(delta, source, performScroll)` and `applyToFling(velocity, performFling)`, with `isInProgress`. Rendering moved to `open val node: DelegatableNode` (added 1.8.0). `effectModifier` was deprecated in 1.8.0, and you render with `Modifier.overscroll(effect)`. `rememberOverscrollEffect()` returns the current `OverscrollFactory`'s effect, which is the platform stretch on Android 12+. `LazyColumn` and similar components configure one automatically and take an `overscrollEffect` parameter. `withoutVisualEffect()` and `withoutEventHandling()` let you receive events in one place and draw elsewhere. — [OverscrollEffect reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/OverscrollEffect)
- The official `OffsetOverscrollEffect` sample shows a custom rubber band. It keeps an `Animatable(0f)`. In `applyToScroll` it first "relaxes" existing overscroll when the user reverses direction, then calls `performScroll`, then adds the leftover delta times 0.1f (the resistance factor) when `source == NestedScrollSource.UserInput`. In `applyToFling` it calls `performFling` and then `overscrollOffset.animateTo(0f, initialVelocity = remaining.y, animationSpec = spring())`. It renders through a `LayoutModifierNode` that calls `placeable.placeRelativeWithLayer(0, offset)`. — [OverscrollEffect reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/OverscrollEffect)
- Third-party options: Squishy (custom overscroll effects via `BaseOverscrollEffect`) and Overscrolled (render tweaks plus a threshold callback). Wolt's engineering blog describes a custom curved-header pull effect. — [Squishy](https://github.com/iamjosephmj/Squishy), [Overscrolled (klibs)](https://klibs.io/project/pChochura/Overscrolled), [Wolt blog](https://careers.wolt.com/en/blog/tech/jetpack-compose-custom-overscroll-effect)
- `HapticFeedbackType.GestureThresholdActivate` (ui 1.8.0) is meant for "a swipe/drag-style gesture, such as pull-to-refresh, where the gesture action is eligible at a certain threshold". — [HapticFeedbackType reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType)

### Inferences
- Rubber-band recipe for the library list:
  1. `PullToRefreshBox` with a custom indicator. Translate the list content by `state.distanceFraction * maxPx` with diminishing returns, for example `maxPx * (1 - 1/(1 + f*0.55f))`, inside `graphicsLayer { translationY = ... }` so no recomposition runs per frame.
  2. Fire `haptic.performHapticFeedback(GestureThresholdActivate)` once when `distanceFraction` crosses 1f. Track it with `snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged()`.
  3. Snap back with a `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)` or the theme's `motionScheme.defaultSpatialSpec()`.
  4. Keep the default stretch overscroll at the bottom edge.
- If the expressive indicator is wanted, use `PullToRefreshDefaults.LoadingIndicator` (experimental opt-in). It provides the morphing-shape loader that matches Android 16 system apps.

### Gaps
- No official doc was found that tunes the platform stretch intensity. `rememberPlatformOverscrollFactory` exposes glow color and padding, and it is unclear whether stretch can be configured.

## Predictive back gestures

### Takeaway
Predictive back is on by default for apps that target Android 15+ (no manifest flag needed). Navigation 3 supports it in every version. navigation-compose needs 2.8.0+, material3 1.3.0+ gives built-in predictive back on sheets, drawers and search, and `PredictiveBackHandler` gives raw progress for custom UI. Nav3 1.2.0 added `NavigationBackHandler` and `rememberNavigationEventState` for custom components.

### Cited Findings
- If the app targets Android 14 or lower, add `android:enableOnBackInvokedCallback="true"` on `<application>` or `<activity>`. Apps that target Android 15+ don't need it. Predictive back animations are on by default on Android 15+ devices. On Android 13/14 the user enables them in Developer options. — [Navigation with shared elements: predictive back](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation); [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- System back-to-home, cross-activity and cross-task animations require AndroidX Activity 1.6.0+. Intercepting back at the root activity disables back-to-home. navigation-compose 2.8.0+ cross-fades by default on a back swipe. Customize with `popEnterTransition`/`popExitTransition`, for example `scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 0.5f))`. Material components with built-in predictive back (material3 1.3.0+): `SearchBar`, `ModalBottomSheet` (automatic), and `ModalNavigationDrawer`/`ModalDrawerSheet`/`DismissibleDrawerSheet`/`DismissibleNavigationDrawer` (pass `drawerState` to the sheet). For custom UI, `PredictiveBackHandler` exposes progress manually. — [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup); [Predictive back overview](https://developer.android.com/develop/ui/compose/system/predictive-back)
- Navigation 3 1.2.0 (Sep 23, 2026) added `NavigationBackHandler` and `rememberNavigationEventState`. They connect `SceneState` to predictive back (`onBackCompleted`/`onBackCancelled`) without manual back-stack deltas, and also improve predictive back triggered mid-flight during forward animations. 1.3.0-alpha01 fixed the predictive back animation handoff in `NavDisplay`, and shared elements don't seek during the deferred (gesture) phase. — [navigation3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- SociaLite is the official sample shown for back-to-home and custom in-app predictive back animations. There is also an "Add predictive back animations" codelab. — [Predictive back overview](https://developer.android.com/develop/ui/compose/system/predictive-back); [codelab](https://developer.android.com/codelabs/predictive-back#0); [android/socialite](https://github.com/android/socialite)

### Inferences
- A book app with targetSdk 36 gets predictive back without any manifest change. Avoid a root-level `BackHandler` in MainActivity so the back-to-home animation keeps working.
- For the cover-to-detail transition, a back swipe on the detail screen "scrubs" the shared element back toward the grid cell when you use Nav3 or navigation-compose 2.8+ with `SharedTransitionLayout` (see the shared elements section).

### Gaps
- Exact current `navigation-compose` and `activity-compose` versions as of Nov 2026 were not fetched. The `NavDisplay` parameter names for predictive-pop specs (`predictivePopTransitionSpec`) were not verified in the fetched excerpt. See [Nav3: Animate between destinations](https://developer.android.com/guide/navigation/navigation-3/animate-destinations).

## Shared element transitions (cover grid to detail) with Navigation Compose / Navigation 3

### Takeaway
Wrap `NavDisplay` (Nav3) or `NavHost` (Nav2) in `SharedTransitionLayout`. On both screens, apply `Modifier.sharedElement(rememberSharedContentState(key = "cover-$id"), animatedVisibilityScope)` to the cover (and `sharedBounds` for containers or text whose content differs). In Nav3 the scope is `LocalNavAnimatedContentScope.current`.

### Cited Findings
- Nav3 pattern: `SharedTransitionLayout { val backStack = rememberNavBackStack(HomeRoute); NavDisplay(backStack = backStack, entryProvider = entryProvider { entry<HomeRoute> { HomeScreen(sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = LocalNavAnimatedContentScope.current, onItemClick = { backStack.add(DetailsRoute(it)) }) } ... }) }`. `NavDisplay` also accepts a `sharedTransitionScope` parameter, which animates whole `NavEntry` instances between scenes. It isn't required for element-to-element shared transitions. — [Navigation with shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)
- Nav2 pattern: wrap `NavHost` in `SharedTransitionLayout` and use `this@composable` (an `AnimatedContentScope`) as the `animatedVisibilityScope`. Element code: `Modifier.sharedElement(sharedTransitionScope.rememberSharedContentState(key = "image-$id"), animatedVisibilityScope = animatedVisibilityScope)`, with matching keys on the list and detail screens. — [Navigation with shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)
- Nav3 1.3.0-alpha05 adds `SharedTransitionScope.DeferredTransitionBehavior` (for example `FollowContainer`). 1.3.0-alpha01 renders shared elements in the outgoing scene during transitions. 1.2.2 (Nov 18, 2026) fixes a shared `NavEntry` not rendering on the incoming scene when a navigation interrupts a pop. — [navigation3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- Navigation 3 versions: 1.0.0 (Nov 19, 2025), 1.1.0 (Apr 8, 2026), 1.2.0 (Sep 23, 2026, with the Result API, KMP deep links and predictive back handlers), 1.2.2 current stable. 1.2.1 lowered `compileSdk` from 37 to 36, because 1.2 depends on Compose 1.10. — [navigation3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- The official Nav3 recipes repo has an `animations` recipe (`AnimatedActivity.kt`). I found no folder named "shared element" in its tree. A Kotlin Slack thread said the shared-element recipe was hard to find. — [android/nav3-recipes](https://github.com/android/nav3-recipes); [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/32712601/hi-everyone-is-there-any-sample-implementing-shared-element-)
- Androidify uses `sharedBounds` combined with a shape morph (`Morph` clip via `clipInOverlayDuringTransition`), with `boundsTransform = MaterialTheme.motionScheme.sharedElementTransitionSpec`. — [Androidify blog](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)

### Inferences
- Book-app specifics:
  - Key on a stable book ID (`"cover-${book.id}"`), not the grid index.
  - Use `sharedElement` for the cover image. Use `sharedBounds` plus `resizeMode = RemeasureToBounds` (or `scaleToBounds()`) for the title text and card container.
  - Make the detail screen load the same image request (same Coil memory-cache key and size) so the image is not blank mid-transition. Pass `placeholderMemoryCacheKey` in Coil.
  - Morph the corner shape from the grid's rounded rect to the detail screen's larger radius, Androidify-style.
- Put `SharedTransitionLayout` at the root and provide it via a `CompositionLocal`, as Androidify does, to avoid threading scopes through every composable.

### Gaps
- Coil-specific guidance (placeholderMemoryCacheKey) comes from background knowledge and was not verified against Coil 3 docs in this session.

## Spring physics and gesture-driven animation: springs, Animatable, AnchoredDraggable swipe actions, animateItem, haptics, dynamic color

### Takeaway
Drive every gesture-follow animation with `Animatable`/`AnchoredDraggableState` and read values in deferred lambdas (`offset {}`, `graphicsLayer {}`). Use `MaterialTheme.motionScheme` specs (spatial for bounds, effects for color/alpha) as the default spring source. Swipe-to-change-status is `Modifier.anchoredDraggable` with three anchors plus `rememberOverscrollEffect()` stretch at the edges and `SegmentTick`/`GestureThresholdActivate` haptics.

### Cited Findings
- AnchoredDraggable (foundation) parts: `DraggableAnchors { Value at px }`, `AnchoredDraggableState<T>`, saved with `rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(initialValue = ...) }`, and `Modifier.anchoredDraggable(state, Orientation.Horizontal, overscrollEffect = overscrollEffect)`. Compute anchors in `Modifier.layout { ... state.updateAnchors(anchors) }` once the size is known. Read position with `.offset { IntOffset(state.requireOffset().roundToInt(), 0) }` "to defer reading to the placement phase, avoiding unnecessary recompositions". Pass the same `rememberOverscrollEffect()` to `anchoredDraggable` and `Modifier.overscroll(...)` for a stretch past the end anchors. Use `state.animateTo(value)` for programmatic moves. Use `graphicsLayer { translationX/scale/shape/clip }` driven by `requireOffset()` for multi-property effects "without triggering recompositions". Use `SwipeToDismissBox` when the stock Material behavior is enough. — [Drag, swipe, and fling guide](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling)
- `HapticFeedbackType` constants (androidx.compose.ui):
  - `LongPress` and `TextHandleMove` (1.0.0).
  - Added in 1.8.0: `Confirm`, `Reject`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`, `SegmentTick` ("switching between a series of potential choices, e.g. items in a list or discrete points on a slider"), `SegmentFrequentTick`, `ToggleOn`, `ToggleOff` and `VirtualKey`.
  - `KeyboardTap` (1.9.0).

  Call through `LocalHapticFeedback.current.performHapticFeedback(type)`. — [HapticFeedbackType reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType)
- The official `OffsetOverscrollEffect` sample settles with `Animatable.animateTo(0f, initialVelocity = remaining.y, animationSpec = spring())`. That is the canonical velocity-preserving spring hand-off after a fling. — [OverscrollEffect reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/OverscrollEffect)
- `MotionScheme` spatial specs are for "animations that may change the shape or bounds". For color or alpha, use the effects specs, "which ensures a 'non-spatial' motion" (no overshoot). — [MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)

### Inferences
- Swipe to change reading status:
  - Use `enum class Swipe { MarkRead, Rest, MarkReading }` with anchors at `-w*0.35f, 0f, +w*0.35f`.
  - Behind the card, crossfade the status color with `animateColorAsState(target, motionScheme.defaultEffectsSpec())` and scale the icon with `motionScheme.fastSpatialSpec()`.
  - Fire `SegmentTick` when `state.targetValue` changes and `Confirm` when it settles on a non-rest anchor, then call `state.animateTo(Rest)` after committing the status.
  - Watch `snapshotFlow { state.settledValue }` in a `LaunchedEffect` to commit.
- Grid reordering and filtering: in `LazyVerticalGrid`, give items stable `key = { it.id }` and apply `Modifier.animateItem()` (fade in/out plus placement spring; it replaced `animateItemPlacement` in Compose 1.7). This is background knowledge and was not re-fetched.
- Toggles such as favorite or "owned": `ToggleOn`/`ToggleOff` haptics plus a shape-morphing `IconToggleButton` from material3 1.5 expressive.
- Dynamic color: `dynamicLightColorScheme(context)`/`dynamicDarkColorScheme(context)` on API 31+, with a fallback scheme. Optionally extract a per-book accent from cover art with `androidx.palette` for the detail screen. This is background knowledge and was not fetched this session.

### Gaps
- No fetched source covered `Modifier.animateItem()` parameters (`fadeInSpec`, `placementSpec`, `fadeOutSpec`) in Compose 1.13. Verify in the LazyGrid reference.
- No fetched source covered Compose 1.13 animation release-note highlights (for example new shared-element APIs), because the per-group release pages for animation and foundation were not opened.

## Camera capture button UX: scan animation and overlay ideas

### Takeaway
The best reference is Google's Androidify. It uses CameraX `camera-compose`, a `MaterialShapes.Cookie9Sided` capture button with a motion-scheme spring press-scale, and morphing shared bounds into the result screen. Build the ISBN-scan overlay yourself with Canvas plus `rememberInfiniteTransition`. No official "scan overlay" component exists.

### Cited Findings
- Androidify capture button: `MaterialShapes.Cookie9Sided.toShape()` clip plus `.indication(interactionSource, ScaleIndicationNodeFactory(MaterialTheme.motionScheme.defaultSpatialSpec<Float>()))`. The color-splash transition originates from the button's bounds via `Modifier.onLayoutRectChanged`. — [Androidify blog](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)
- Androidify depends on `androidx.camera:camera-compose`, `camera-core`, `camera-camera2` and `camera-lifecycle`, at camerax `1.6.1`. — [android/androidify libs.versions.toml](https://github.com/android/androidify/blob/main/gradle/libs.versions.toml)
- `MaterialShapes` and `toShape()` remain experimental in material3 1.5.0. — [material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)

### Inferences
- ISBN scanning UX ideas:
  - Dim the preview with a cut-out rounded-rect viewfinder, drawn with `drawWithContent` and `BlendMode.Clear` on an offscreen layer.
  - Run a scan line or gradient sweep driven by `rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse))` and read it in `drawBehind`.
  - On a barcode hit, spring the viewfinder corners to snap onto the detected bounding box (`Animatable<Rect>` with `motionScheme.fastSpatialSpec()`), fire `HapticFeedbackType.Confirm`, and morph the capture button into a check using `Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle)`.
  - Then `sharedBounds` the cover thumbnail into the "Add book" sheet.
  - Use `Reject` haptics when the lookup fails.
- Use a `ContainedLoadingIndicator` while the ISBN lookup runs (experimental).

### Gaps
- No fetched source covered ML Kit barcode scanning with CameraX `camera-compose` (`CameraXViewfinder`) API specifics. Another researcher or ML Kit docs should confirm.

## Best open-source showcase apps: URLs and what to copy

### Takeaway
Copy from these, in priority order:
1. **Androidify**: expressive theme, shape morph, camera button, shared bounds.
2. **Now in Android**: architecture, Nav3, baseline profiles.
3. **compose-samples** (Jetsnack): shared elements, custom motion.
4. **nav3-recipes**: Nav3 animations.
5. **SociaLite**: predictive back.
6. **Mihon**: a library-style, cover-grid app.
7. **Read You**: Material You theming polish.

### Cited Findings
- [android/androidify](https://github.com/android/androidify) (about 2.0k stars, pushed 2026-11-18): MaterialExpressiveTheme + `MotionScheme.expressive()`, `MaterialShapes`, `Morph` shared-bounds reveal, `HorizontalFloatingToolbar`, CameraX Compose, autosize text, baseline profiles plugin. — [Androidify blog](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html); [GitHub](https://github.com/android/androidify)
- [android/nowinandroid](https://github.com/android/nowinandroid) (about 22.1k stars, pushed 2026-11-21): on `androidxNavigation3 = "1.2.0"`, `material3-adaptive-navigation3 1.3.0-alpha04`, `compose-bom-alpha`, and the baseline profile setup. — [nowinandroid libs.versions.toml](https://github.com/android/nowinandroid/blob/main/gradle/libs.versions.toml)
- [android/compose-samples](https://github.com/android/compose-samples) (about 23.6k stars, pushed 2026-11-19): official Compose samples. The shared-element guide's Snack-based code comes from the Jetsnack style. — [GitHub](https://github.com/android/compose-samples); [shared elements guide](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)
- [android/nav3-recipes](https://github.com/android/nav3-recipes) (pushed 2026-11-10): `animations/AnimatedActivity.kt` recipe. — [GitHub](https://github.com/android/nav3-recipes)
- [android/socialite](https://github.com/android/socialite): the official predictive back showcase (back-to-home and custom `popExitTransition` scale). — [Predictive back overview](https://developer.android.com/develop/ui/compose/system/predictive-back)
- [android/snippets](https://github.com/android/snippets): the source of every developer.android.com snippet quoted above (pull-to-refresh, anchoredDraggable, shared elements). — [GitHub](https://github.com/android/snippets)
- [mihonapp/mihon](https://github.com/mihonapp/mihon) (about 25.5k stars, pushed 2026-11-20): a "free and open source manga reader". It is a cover-grid library app and the closest domain analog to a book library. — [GitHub](https://github.com/mihonapp/mihon)
- [ReadYouApp/ReadYou](https://github.com/ReadYouApp/ReadYou) (about 7.7k stars, last pushed 2026-08-11, moved from Ashinch/ReadYou): "An Android RSS reader presented in Material You style". — [GitHub](https://github.com/ReadYouApp/ReadYou)

### Inferences
- What to copy:
  - **Androidify:** the theme setup and `LocalSharedTransitionScope` provider, `sharedBoundsRevealWithShapeMorph`, and the camera button press indication.
  - **NiA:** module structure and the baselineprofile and benchmark modules.
  - **Mihon:** library grid density, selection mode, and per-category tabs.
  - **Read You:** dynamic color and settings UX.
  - **nav3-recipes:** `NavDisplay` transition specs.
- The Material Components Compose catalog (component demos for every expressive component) is in the androidx repo, `compose/material3/material3/integration-tests/material3-catalog`. I could not confirm a standalone GitHub repo: `material-components/material-components-android-compose-catalog` returned 404.

### Gaps
- I did not inspect whether Mihon or Read You have adopted material3 1.5 expressive APIs.
- The Material catalog app's Play Store availability was not checked.

## Performance pitfalls: baseline profiles and avoiding recomposition in animations

### Takeaway
Read animated values only in deferred lambdas (`offset {}`, `graphicsLayer {}`, `drawBehind {}`). Never read them in composition-time modifiers. Give lazy items stable keys, use `onLayoutRectChanged` over `onGloballyPositioned`, and ship a Baseline Profile (`androidx.baselineprofile` plugin) as Google's samples do.

### Cited Findings
- Reading `state.requireOffset()` inside `Modifier.offset { }` defers the read to placement and avoids recompositions during drag. `Modifier.graphicsLayer { }` "executes changes in the draw phase, ensuring high-performance animations without triggering recompositions". — [Drag, swipe, and fling guide](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling)
- The pull-to-refresh guide reads `state.distanceFraction` in a lambda inside `graphicsLayer`, for the same reason. — [Pull to refresh guide](https://developer.android.com/develop/ui/compose/components/pull-to-refresh)
- `Modifier.onLayoutRectChanged` is "a more performant version of onGloballyPositioned" (Compose 1.8). — [Androidify blog](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)
- A custom `OverscrollEffect` should render via `node` (a `Modifier.Node`/`LayoutModifierNode` with `placeRelativeWithLayer`), not the deprecated `effectModifier`. — [OverscrollEffect reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/OverscrollEffect)
- Androidify pins `baselineprofile = "1.5.0-alpha06"`. Now in Android also ships baseline profiles. — [androidify libs.versions.toml](https://github.com/android/androidify/blob/main/gradle/libs.versions.toml)

### Inferences
- Other common pitfalls (background knowledge, not fetched):
  - Don't use `animate*AsState` values in `Modifier.padding()`/`size()` per frame. Prefer `graphicsLayer` scale and translation.
  - Hoist `remember { MutableInteractionSource() }`.
  - Use `derivedStateOf` for "scrolled past threshold" flags.
  - Make cover-grid item params stable (`@Immutable` UI models, `ImmutableList`).
  - Size Coil requests to the cell.
  - Test motion in release builds with R8, because debug builds are not representative.
  - Generate profiles with a Macrobenchmark module covering app startup, grid scroll and the cover-to-detail transition.

### Gaps
- The developer.android.com Baseline Profiles and Compose performance pages were not fetched this session, so exact current `androidx.benchmark`/`baselineprofile` stable versions are unverified. Androidify's pinned version is an alpha.
