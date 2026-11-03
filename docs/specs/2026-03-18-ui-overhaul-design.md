# UI/UX Overhaul Design Spec

**Date:** 2026-03-18
**Status:** Approved
**Scope:** Full UI audit and upgrade against Material Design 3, automotive UX guidelines, and accessibility standards

## Context

Countersteer is a motorcycle navigation app used with phones handlebar-mounted in portrait orientation. The rider wears gloves, experiences vibration, and can only glance at the screen for <1 second at a time. This context drives every decision in this spec.

**Current state:** 13 Compose screens, Material 3 dark-only theme, MVVM + Hilt architecture, MapLibre maps. The UI is functional but has inconsistent touch target sizing, hardcoded colors, missing accessibility semantics, no Material You support, and no unified component library.

**Approach:** Design System First — build a shared foundation (tokens, components, theme upgrade), then re-skin all 13 screens against it in priority order.

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Target form factor | Phone portrait only | Handlebar mount context |
| Theme | Dark-only + Material You dynamic accent | Outdoor visibility + personalization |
| Animation strategy | Context-aware: 150ms riding / 300ms planning | Safety during navigation, polish during planning |
| Priority order | Touch targets > Edge-to-edge > Typography > Everything else | Safety first, platform compliance second |

---

## 1. Design System Foundation

### 1.1 Design Tokens — `ui/theme/CountersteerDesign.kt`

Centralized object for all spacing, sizing, and animation constants.

```
Spacing scale:     4dp / 8dp / 12dp / 16dp / 24dp / 32dp / 48dp
Touch targets:
  - Standard: 80dp minimum (Google automotive guideline 76dp + vibration buffer)
  - Riding: 96dp for critical actions (end navigation, save/discard)
Target spacing:    28dp between interactive elements
Corner radii:      small=8dp / medium=12dp / large=16dp / full=CircleShape
Animation duration:
  - Riding: 150ms (tween)
  - Planning: 300ms (spring, StiffnessMediumLow)
```

### 1.2 Riding Context — `LocalRidingContext`

A `CompositionLocal<Boolean>` provided at the theme level, set to `true` when on `ActiveNavigationScreen`.

Components query this to switch:
- Animation durations
- Touch target minimums (80dp standard vs 96dp riding)
- Information density

**Files changed:**
- New: `ui/theme/CountersteerDesign.kt`
- Modified: `ui/theme/Theme.kt` — provides `LocalRidingContext`

---

## 2. Theme Upgrade

### 2.1 Material You Dynamic Color

On API 31+, use `dynamicDarkColorScheme(context)` with fixed safety color overrides:

**Dynamic (personalized from wallpaper):**
- `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`
- `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`
- `surface`, `surfaceVariant`, `onSurface`, `onSurfaceVariant`, `outline`

**Fixed (never dynamic — safety-critical recognition):**
- Error/danger: `#CC6666` — speed alerts, crash detection, off-route
- Warning/tertiary: `#F0C674` — near speed limit, GPS acquiring
- Safe/secondary override when used for speed: `#B5BD68` — under speed limit

**Note:** `minSdk` is 31, so `dynamicDarkColorScheme` is always available. No API-level fallback branch needed.

### 2.2 Edge-to-Edge & Splash Screen

**MainActivity.kt changes (call order matters):**
1. `installSplashScreen()` with `setKeepOnScreenCondition { onboardingState == null }`
2. `enableEdgeToEdge()` (move before `super.onCreate()`)
3. `super.onCreate(savedInstanceState)`
4. `window.isNavigationBarContrastEnforced = false`

**Remove:** Manual splash/loading Box in `CountersteerNavigation.kt` (the `onboardingComplete == null` branch with CircularProgressIndicator) — replaced by Splash Screen API.

**New:** `res/values/themes.xml` with splash screen theme if not present.

### 2.3 Predictive Back

Add to `AndroidManifest.xml`:
```xml
android:enableOnBackInvokedCallback="true"
```

Navigation Compose 2.8+ handles cross-fade automatically. No custom `PredictiveBackHandler` needed.

**Files changed:**
- `ui/theme/Theme.kt` — `dynamicDarkColorScheme` + safety overrides
- `ui/theme/Color.kt` — bump `Outline` from `#555555` to `#666666` (contrast fix)
- `app/MainActivity.kt` — splash screen + edge-to-edge reorder
- `AndroidManifest.xml` — predictive back flag
- `CountersteerNavigation.kt` — remove manual splash Box

---

## 3. Core Components

### 3.1 RidingButton

Replaces direct `ExtendedFloatingActionButton` usage across the app. Wraps M3 `ExtendedFloatingActionButton` with:
- Minimum height: `CountersteerDesign.touchTarget.standard` (80dp), `riding` (96dp) when `LocalRidingContext`
- Icon size: 28dp
- Text style: `titleMedium` (16sp, medium weight)
- Shape: `RoundedCornerShape(CountersteerDesign.cornerRadius.large)` (16dp)
- Elevation: 6dp default

**File:** New `ui/components/RidingButton.kt`

### 3.2 StateContainer

Unified composable for loading, error, and empty states. Replaces ad-hoc implementations scattered across screens.

```kotlin
@Composable
fun StateContainer(
    state: UiState,  // Loading / Error(message, onRetry) / Empty(icon, title, subtitle)
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,  // shown when state is Success
)
```

- Loading: `CircularProgressIndicator` (primary) + optional message
- Error: Icon + error message + subtitle + Retry button (RidingButton)
- Empty: Faded icon + title + subtitle

**File:** New `ui/components/StateContainer.kt`

### 3.3 AlertBanner (upgrade)

Current `AlertBanner.kt` upgraded with:
- Full-banner tap-to-dismiss (touch target spans entire banner, min 80dp height)
- `LiveRegionMode.Assertive` for TalkBack
- `semantics { stateDescription = "Alert: ${alert.displayName}" }`
- Custom accessibility action: "Dismiss alert"
- Exit animation: `fadeOut` only (faster perceived dismissal vs. current `slideOut + fadeOut`)

**File:** Modified `ui/components/AlertBanner.kt`

### 3.4 SpeedDisplay (upgrade)

Replaces the existing `SpeedLimitDisplay.kt` and expands it. The current `SpeedLimitDisplay` composable and `speedColor()` function are absorbed into this new component. Extracted from `BottomInfoStrip` into reusable component with:
- Color-coded: green (safe) → amber (within 10km/h) → red with glow (over limit)
- Pulsing scale animation on speed limit sign when exceeding (1.0 → 1.1 → 1.0, 300ms)
- `liveRegion = Polite` — announces at threshold crossings only, not every km/h
- `contentDescription = "$speed kilometers per hour"`

**File:** New `ui/components/SpeedDisplay.kt` (extracted from `ActiveNavigationScreen.kt`)

### 3.5 CountersteerTopBar

Wrapper around M3 `TopAppBar` with:
- Back button touch target: 56dp (up from default ~40dp)
- Action icon touch targets: 56dp
- Built-in `WindowInsets.statusBars` padding
- `semantics { heading() }` on title

**File:** New `ui/components/CountersteerTopBar.kt`

---

## 4. Typography & Information Hierarchy

### 4.1 Type Scale Changes in `Type.kt`

**Floor bump — minimum 14sp everywhere:**
- `labelSmall`: 11sp → 14sp (keep `FontWeight.Medium`, `0.5sp` letter spacing)
- `labelMedium`: 12sp → 14sp (keep `FontWeight.Medium`, `0.5sp` letter spacing)

All other sizes remain unchanged — the current scale is already well-designed.

### 4.2 Riding Information Hierarchy

Three levels for ActiveNavigationScreen:

| Level | Content | Style | Size |
|-------|---------|-------|------|
| 1 — Priority | Speed, distance to next turn | `displayLarge` / `displaySmall` | 72sp / 36sp |
| 2 — Context | Turn instruction text | `bodyLarge` | 18sp (existing) |
| 3 — Peripheral | ETA, distance remaining, labels | `headlineLarge` / `labelLarge` | 28sp / 14sp |

### 4.3 Planning Information Hierarchy

Standard M3 type scale with the 14sp floor. No changes to existing usage patterns in planning screens beyond the floor bump.

**Files changed:**
- `ui/theme/Type.kt` — labelSmall and labelMedium size bump

---

## 5. Navigation & Transitions

### 5.1 NavHost Transitions

Add to `NavHost` in `CountersteerNavigation.kt`:

```kotlin
enterTransition = { slideIntoContainer(SlideDirection.Start, tween(animDuration)) }
exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(animDuration)) }
popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(animDuration)) }
popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(animDuration)) }
```

Where `animDuration` reads from `LocalRidingContext` (150ms vs 300ms).

### 5.2 Per-Screen Overrides

- `ActiveNavigation`: `enterTransition = { EnterTransition.None }` — instant, don't delay the rider
- `ActiveNavigation → RideSummary`: `fadeIn` / `fadeOut` — context shift
- `Onboarding`: keep existing `slideInHorizontally` / `slideOutHorizontally`
- All others: inherit NavHost defaults

### 5.3 In-Screen Animations

- Keep: `animateContentSize()` on InstructionBar
- Keep: `animateColorAsState()` for speed color
- New: scale pulse on speed limit sign when exceeding threshold
- Refined: AlertBanner exit → `fadeOut` only

**Files changed:**
- `ui/navigation/CountersteerNavigation.kt` — NavHost transitions
- `ui/screens/ActiveNavigationScreen.kt` — override to None enter

---

## 6. Screen-by-Screen Redesign

### Priority 1: ActiveNavigationScreen

| Change | Detail |
|--------|--------|
| End nav FAB | 64dp → 80dp via `CountersteerDesign.touchTarget.standard` |
| Hardcoded colors | All `Color(0x...)` → `MaterialTheme.colorScheme.*` |
| Speed semantics | `liveRegion = Polite`, announce at threshold crossings |
| Turn semantics | `liveRegion = Assertive` on new maneuver |
| Content descriptions | "Turn right in 200 meters" not "Turn direction" |
| Off-route warning | `Color(0xEE000000)` → `errorContainer` / `onErrorContainer` |
| Bottom strip | Add `WindowInsets.navigationBars` padding |
| Instruction text | Raw `18.sp` → `bodyLarge` from type scale |
| Speed display | Extract to `SpeedDisplay` component |

### Priority 2: HomeScreen

| Change | Detail |
|--------|--------|
| Action buttons | `ExtendedFloatingActionButton` → `RidingButton`, 64dp → 80dp, gap 16dp → 28dp |
| GPS indicator | Hardcoded colors → theme tokens, add `liveRegion = Polite` |
| Icon descriptions | `null` → "Explore routes" / "Search destination" |
| Bottom safe area | Add `WindowInsets.navigationBars` padding to button row |

### Priority 3: RouteOptionsScreen

| Change | Detail |
|--------|--------|
| Route cards | 80dp min touch target height |
| Start nav button | → `RidingButton`, 80dp, full width |
| Route labels | Raw sp → `titleLarge` |
| Loading state | → `StateContainer.Loading` |
| Error state | → `StateContainer.Error` with retry |

### Remaining Screens

**ExploreScreen:**
- Compass direction buttons → 80dp touch targets
- Distance slider thumb → 32dp (glove-friendly)
- "Find Routes" → `RidingButton`
- Theme token migration

**DestinationScreen:**
- Search bar → 56dp height
- Result list items → 80dp min height
- Favorites → `LazyColumn` with keys
- Empty state → `StateContainer.Empty`

**RideHistoryScreen:**
- Ride items → 80dp min height
- Empty state → migrate to `StateContainer.Empty`
- `LazyColumn` → add `key` + `contentType`
- Stats action → `CountersteerTopBar`

**RideDetailScreen:**
- `CountersteerTopBar` wrapper
- Delete confirmation dialog → 80dp buttons
- Stats values → `titleLarge`, labels → `labelLarge`
- Theme token migration

**RideSummaryScreen:**
- Save/Discard → `RidingButton` pair, 80dp
- Ride stats → consistent card
- Enter transition → fade
- Theme token migration

**RideStatsScreen:**
- Tab row → 56dp height
- Stat cards → `LazyVerticalGrid` with keys
- Empty state → `StateContainer.Empty`
- `CountersteerTopBar`

**SettingsScreen:**
- All list items → 80dp min height
- Toggle switches → proper touch area
- Section headers → `semantics { heading() }`
- `CountersteerTopBar`

**OnboardingScreen:**
- Keep existing transitions
- Next/Continue/Permission buttons → 80dp
- Progress indicator → theme tokens

**OfflineMapsScreen / BackupRestoreScreen / ApiKeysScreen:**
- `CountersteerTopBar` wrapper
- All buttons → 80dp touch targets
- Loading/error → `StateContainer`
- Input fields → 56dp height
- Theme token migration

---

## 7. Accessibility

### 7.1 Semantics Annotations

| Element | Annotation |
|---------|-----------|
| Speed display | `liveRegion = Polite`, announce at threshold crossings only |
| Turn instructions | `liveRegion = Assertive` on new maneuver |
| Off-route warning | `liveRegion = Assertive`, `stateDescription = "Off route"` |
| Screen titles | `semantics { heading() }` on all section headers |
| All icons | Descriptive `contentDescription` (action + context) |

### 7.2 Contrast Fixes

- `Outline`: `#555555` → `#666666` (3.0:1 → 3.8:1 against background)
- All other color pairs already meet WCAG AA
- 14sp floor ensures all text qualifies as "large text" (3:1 threshold)

### 7.3 Custom Accessibility Actions

- HomeScreen long-press marker: `customActions` alternative for TalkBack
- AlertBanner dismiss: `customActions` with "Dismiss alert"

---

## 8. Performance

### 8.1 Compose Compiler

Strong skipping mode is enabled by default with Kotlin 2.1+ and Compose BOM 2025.08.00. Verify it is not explicitly disabled in `app/build.gradle.kts`. No flag needed.

### 8.2 Stability

Add `@Immutable` to: `NavigationState`, `AlertInfo`, route-related data classes.

New `stability_config.conf` at project root for external types:
```
org.maplibre.android.geometry.LatLng
org.maplibre.geojson.Point
kotlinx.datetime.*
```

Reference in build config:
```kotlin
composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
```

### 8.3 Derived State

- `isOverSpeedLimit` via `derivedStateOf` — avoids recomposing entire bottom strip on every speed tick
- `showScrollToTop` for RideHistoryScreen if scroll-to-top is added

### 8.4 Lazy Layout Optimization

All `LazyColumn` and `LazyVerticalGrid` usages:
- Add `key = { it.id }` (stable identity)
- Add `contentType = { it.type }` (composition reuse)

### 8.5 Deferred Reads

- Speed glow effect → `Modifier.drawBehind { }` to avoid composition-phase color reads
- Any animated overlay offsets → `Modifier.offset { IntOffset(...) }` lambda form

---

## Files Summary

### New Files
| File | Purpose |
|------|---------|
| `ui/theme/CountersteerDesign.kt` | Design tokens + `LocalRidingContext` |
| `ui/components/RidingButton.kt` | Glove-friendly button component |
| `ui/components/StateContainer.kt` | Unified loading/error/empty states |
| `ui/components/SpeedDisplay.kt` | Extracted speed + limit display |
| `ui/components/CountersteerTopBar.kt` | Consistent top bar wrapper |
| `stability_config.conf` | Compose stability config for external types |
| `res/values/themes.xml` | Splash screen theme (if not present) |

### Modified Files
| File | Changes |
|------|---------|
| `ui/theme/Theme.kt` | Dynamic color, `LocalRidingContext` provider |
| `ui/theme/Color.kt` | Outline contrast bump |
| `ui/theme/Type.kt` | labelSmall/labelMedium floor to 14sp |
| `app/MainActivity.kt` | Splash screen API, edge-to-edge reorder |
| `AndroidManifest.xml` | Predictive back flag |
| `ui/navigation/CountersteerNavigation.kt` | NavHost transitions, remove splash Box |
| `ui/components/AlertBanner.kt` | Accessibility, animation refinement |
| `ui/screens/ActiveNavigationScreen.kt` | All Priority 1 changes |
| `ui/screens/HomeScreen.kt` | All Priority 2 changes |
| `ui/screens/RouteOptionsScreen.kt` | All Priority 3 changes |
| All remaining screen files | Design system adoption, accessibility, tokens |
| `app/build.gradle.kts` | Strong skipping mode, stability config |
